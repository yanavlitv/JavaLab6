package server.network;

import common.CommandRequest;
import common.CommandResponse;
import common.SerializationHelper;
import server.commands.CommandExecutor;
import server.utils.Constants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TCPServer {
    private static final Logger logger = Logger.getLogger(TCPServer.class.getName());
    private final int port;
    private final CommandExecutor commandExecutor;
    private volatile boolean running = true;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    private final Map<SocketChannel, ClientState> clientStates = new ConcurrentHashMap<>();

    private static class ClientState {
        enum Stage { READING_LENGTH, READING_BODY, WRITING_RESPONSE }

        Stage stage = Stage.READING_LENGTH;
        final ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        int expectedLength = 0;
        ByteBuffer dataBuffer = null;
        ByteBuffer responseBuffer = null;

        void reset() {
            stage = Stage.READING_LENGTH;
            lengthBuffer.clear();
            expectedLength = 0;
            dataBuffer = null;
            responseBuffer = null;
        }
    }

    public TCPServer(int port, CommandExecutor commandExecutor) {
        this.port = port;
        this.commandExecutor = commandExecutor;
    }

    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        logger.info("Неблокирующий сервер запущен на порту " + port);

        while (running) {
            try {
                if (selector.select(1000) == 0) {
                    continue;
                }

                if (!running) break;

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logger.log(Level.WARNING, "Ошибка в цикле селектора", e);
                }
            }
        }
        stop();
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            clientStates.put(clientChannel, new ClientState());
            logger.info("Новое подключение: " + clientChannel.getRemoteAddress());
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientState state = clientStates.get(clientChannel);

        if (state == null) {
            clientChannel.close();
            return;
        }

        try {
            switch (state.stage) {
                case READING_LENGTH:
                    int bytesRead = clientChannel.read(state.lengthBuffer);
                    if (bytesRead == -1) {
                        closeConnection(clientChannel);
                        return;
                    }

                    if (state.lengthBuffer.remaining() == 0) {
                        state.lengthBuffer.flip();
                        state.expectedLength = state.lengthBuffer.getInt();

                        if (state.expectedLength <= 0 || state.expectedLength > Constants.BUFFER_SIZE) {
                            logger.warning("Некорректный размер данных от " + getRemoteAddress(clientChannel) + ": " + state.expectedLength);
                            closeConnection(clientChannel);
                            return;
                        }

                        state.stage = ClientState.Stage.READING_BODY;
                        state.dataBuffer = ByteBuffer.allocate(state.expectedLength);

                        handleRead(key);
                    }
                    break;

                case READING_BODY:
                    int bodyBytesRead = clientChannel.read(state.dataBuffer);
                    if (bodyBytesRead == -1) {
                        closeConnection(clientChannel);
                        return;
                    }

                    if (state.dataBuffer.remaining() == 0) {
                        state.dataBuffer.flip();
                        byte[] requestBytes = new byte[state.dataBuffer.remaining()];
                        state.dataBuffer.get(requestBytes);

                        CommandRequest request = SerializationHelper.deserialize(requestBytes, CommandRequest.class);
                        if (request == null) {
                            logger.warning("Не удалось десериализовать запрос от " + getRemoteAddress(clientChannel));
                            closeConnection(clientChannel);
                            return;
                        }

                        logger.info("Команда: " + request.getType() + " от " + getRemoteAddress(clientChannel));

                        CommandResponse response = commandExecutor.execute(request);

                        byte[] responseBytes = SerializationHelper.serialize(response);
                        state.responseBuffer = ByteBuffer.allocate(4 + responseBytes.length);
                        state.responseBuffer.putInt(responseBytes.length);
                        state.responseBuffer.put(responseBytes);
                        state.responseBuffer.flip();

                        state.stage = ClientState.Stage.WRITING_RESPONSE;

                        key.interestOps(SelectionKey.OP_WRITE);

                        if (request.getType().name().equals("EXIT")) {
                            logger.info("Клиент завершил сессию: " + getRemoteAddress(clientChannel));
                        }
                    }
                    break;

                case WRITING_RESPONSE:
                    break;
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка при чтении от клиента " + getRemoteAddress(clientChannel), e);
            closeConnection(clientChannel);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientState state = clientStates.get(clientChannel);

        if (state == null || state.responseBuffer == null) {
            key.interestOps(SelectionKey.OP_READ);
            return;
        }

        try {
            clientChannel.write(state.responseBuffer);

            if (!state.responseBuffer.hasRemaining()) {
                logger.fine("Ответ успешно отправлен клиенту " + getRemoteAddress(clientChannel));

                state.reset();
                key.interestOps(SelectionKey.OP_READ);
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка при отправке ответа клиенту " + getRemoteAddress(clientChannel), e);
            closeConnection(clientChannel);
        }
    }

    private void closeConnection(SocketChannel clientChannel) {
        try {
            clientStates.remove(clientChannel);
            clientChannel.close();
            logger.info("Соединение закрыто: " + getRemoteAddress(clientChannel));
        } catch (IOException e) {
            logger.log(Level.FINE, "Ошибка при закрытии сокета", e);
        }
    }

    private String getRemoteAddress(SocketChannel clientChannel) {
        try {
            return clientChannel.getRemoteAddress().toString();
        } catch (IOException e) {
            return "unknown";
        }
    }

    public void stop() {
        running = false;
        if (selector != null) {
            selector.wakeup();
            try {
                for (SocketChannel client : clientStates.keySet()) {
                    try { client.close(); } catch (IOException ignored) {}
                }
                clientStates.clear();

                if (serverChannel != null) {
                    serverChannel.close();
                }
                selector.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Ошибка при закрытии селектора", e);
            }
        }
        logger.info("Сервер остановлен");
    }
}
