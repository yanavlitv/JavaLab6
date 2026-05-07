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
                handleClient(clientSocket);
            } catch (SocketTimeoutException e) {
                // ничего
            }catch (IOException e) {
                if (running) {
                    logger.log(Level.WARNING, "Ошибка при принятии подключения", e);
                }
            }
        }
        serverSocket.close();
        logger.info("Сервер остановлен");
    }

    private void handleClient(Socket clientSocket) {
        try (clientSocket;
             DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            int dataLength = dis.readInt();
            if (dataLength <= 0 || dataLength > Constants.BUFFER_SIZE) {
                logger.warning("Некорректный размер данных от " + clientSocket.getRemoteSocketAddress());
                return;
            }

            byte[] requestBytes = new byte[dataLength];
            dis.readFully(requestBytes);

            CommandRequest request = SerializationHelper.deserialize(requestBytes, CommandRequest.class);
            if (request == null) {
                logger.warning("Не удалось десериализовать запрос");
                return;
            }

            logger.info("Команда: " + request.getType() + " от " + clientSocket.getRemoteSocketAddress());

            CommandResponse response = commandExecutor.execute(request);
            byte[] responseBytes = SerializationHelper.serialize(response);

            dos.writeInt(responseBytes.length);
            dos.write(responseBytes);
            dos.flush();


        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка при обработке клиента", e);
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