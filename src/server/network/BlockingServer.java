package server.network;

import common.CommandRequest;
import common.CommandResponse;
import common.SerializationHelper;
import server.commands.CommandExecutor;
import server.utils.Constants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlockingServer {
    private static final Logger logger = Logger.getLogger(BlockingServer.class.getName());
    private final int port;
    private final CommandExecutor commandExecutor;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public BlockingServer(int port, CommandExecutor commandExecutor) {
        this.port = port;
        this.commandExecutor = commandExecutor;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(1000);
        logger.info("Сервер запущен на порту " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Новое подключение: " + clientSocket.getRemoteSocketAddress());
                
                // Обрабатываем клиента в отдельном методе, но в том же потоке
                // Соединение НЕ закрываем после одной команды
                handleClientConnection(clientSocket);
                
            } catch (SocketTimeoutException e) {
                // Таймаут accept – просто продолжаем цикл
            } catch (IOException e) {
                if (running) {
                    logger.log(Level.WARNING, "Ошибка при принятии подключения", e);
                }
            }
        }
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        logger.info("Сервер остановлен");
    }

    private void handleClientConnection(Socket clientSocket) {
        try {
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
            
            // Устанавливаем таймаут на чтение
            clientSocket.setSoTimeout(Constants.TIMEOUT_MS);
            
            // Цикл обработки команд от одного клиента
            while (running && clientSocket.isConnected() && !clientSocket.isClosed()) {
                try {
                    // Чтение длины запроса
                    int dataLength;
                    try {
                        dataLength = dis.readInt();
                    } catch (EOFException e) {
                        // Клиент закрыл соединение нормально
                        logger.info("Клиент отключился: " + clientSocket.getRemoteSocketAddress());
                        break;
                    } catch (SocketTimeoutException e) {
                        // Таймаут, просто продолжаем ждать следующую команду
                        continue;
                    }
                    
                    if (dataLength <= 0 || dataLength > Constants.BUFFER_SIZE) {
                        logger.warning("Некорректный размер данных: " + dataLength);
                        break;
                    }
                    
                    // Чтение тела запроса
                    byte[] requestBytes = new byte[dataLength];
                    dis.readFully(requestBytes);
                    logger.fine("Получен запрос, байт: " + dataLength);
                    
                    // Десериализация запроса
                    CommandRequest request = SerializationHelper.deserialize(requestBytes, CommandRequest.class);
                    if (request == null) {
                        logger.warning("Не удалось десериализовать запрос");
                        break;
                    }
                    
                    logger.info("Команда: " + request.getType() + " от " + clientSocket.getRemoteSocketAddress());
                    
                    // Выполнение команды
                    CommandResponse response = commandExecutor.execute(request);
                    
                    // Сериализация и отправка ответа
                    byte[] responseBytes = SerializationHelper.serialize(response);
                    dos.writeInt(responseBytes.length);
                    dos.write(responseBytes);
                    dos.flush();
                    
                    logger.fine("Ответ отправлен, тип: " + response.getType());
                    
                    // Если команда exit – закрываем соединение
                    if (request.getType().name().equals("EXIT")) {
                        logger.info("Клиент завершил сессию: " + clientSocket.getRemoteSocketAddress());
                        break;
                    }
                    
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Ошибка при обработке команды: " + e.getMessage());
                    break;
                }
            }
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка при инициализации потоков: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                logger.fine("Соединение закрыто");
            } catch (IOException e) {
                logger.log(Level.FINE, "Ошибка при закрытии сокета", e);
            }
        }
    }
    
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Ошибка при закрытии серверного сокета", e);
            }
        }
    }
}
