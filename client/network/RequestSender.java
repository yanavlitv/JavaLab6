package client.network;

import client.utils.Constants;
import common.CommandRequest;
import common.CommandResponse;
import common.SerializationHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

public class RequestSender {
    private final SocketChannel channel;
    private final ByteBuffer writeBuffer;
    private final ByteBuffer readBuffer;
    private final Selector selector;

    public RequestSender(SocketChannel channel) {
        this.channel = channel;
        this.writeBuffer = ByteBuffer.allocate(Constants.BUFFER_SIZE);
        this.readBuffer = ByteBuffer.allocate(Constants.BUFFER_SIZE);
        try {
            this.selector = Selector.open();
            this.channel.configureBlocking(false);
            this.channel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка создания селектора", e);
        }
    }

    public void send(CommandRequest request) throws IOException {
        byte[] requestBytes = SerializationHelper.serialize(request);

        writeBuffer.clear();
        writeBuffer.putInt(requestBytes.length);
        writeBuffer.put(requestBytes);
        writeBuffer.flip();

        while (writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
        }
        System.out.println("Запрос отправлен, размер: " + requestBytes.length);
    }

    public CommandResponse receiveResponse() throws IOException {
        // Ожидаем готовности канала к чтению
        selector.select(Constants.TIMEOUT_MS);

        if (selector.selectedKeys().isEmpty()) {
            throw new IOException("Таймаут ожидания ответа от сервера");
        }

        selector.selectedKeys().clear();


        readBuffer.clear();
        readBuffer.limit(4);

        int bytesRead = 0;
        while (bytesRead < 4) {
            int read = channel.read(readBuffer);
            if (read == -1) {
                throw new IOException("Сервер закрыл соединение");
            }
            if (read == 0) {
                Thread.yield();
                continue;
            }
            bytesRead += read;
        }

        readBuffer.flip();
        int dataLength = readBuffer.getInt();

        if (dataLength <= 0 || dataLength > Constants.BUFFER_SIZE - 4) {
            throw new IOException("Некорректный размер данных: " + dataLength);
        }


        readBuffer.clear();
        readBuffer.limit(dataLength);

        bytesRead = 0;
        while (bytesRead < dataLength) {
            int read = channel.read(readBuffer);
            if (read == -1) {
                throw new IOException("Сервер закрыл соединение");
            }
            if (read == 0) {
                Thread.yield();
                continue;
            }
            bytesRead += read;
        }

        readBuffer.flip();
        byte[] responseBytes = new byte[dataLength];
        readBuffer.get(responseBytes);

        CommandResponse response = SerializationHelper.deserialize(responseBytes, CommandResponse.class);
        if (response == null) {
            throw new IOException("Не удалось десериализовать ответ");
        }

        System.out.println("Ответ получен, тип: " + response.getType());
        return response;
    }

    public void close() throws IOException {
        if (selector != null && selector.isOpen()) {
            selector.close();
        }
    }
}