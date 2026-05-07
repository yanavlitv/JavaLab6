package client.network;

import client.utils.Constants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Неблокирующий коннектор для подключения к серверу.
 */
public class TCPClient {
    private final SocketChannel channel;
    private final Selector selector;
    private boolean connected = false;

    /**
     * Конструктор: создаёт подключение.
     */
    public TCPClient(String host, int port) throws IOException {
        this.channel = SocketChannel.open();


        this.channel.configureBlocking(false);


        this.selector = Selector.open();

        //Начинаем асинхронное подключение
        boolean connectedImmediately = channel.connect(new InetSocketAddress(host, port));

        //Если подключение не завершилось сразу — ждём через селектор
        if (!connectedImmediately) {
            channel.register(selector, SelectionKey.OP_CONNECT);
            int readyChannels = selector.select(Constants.TIMEOUT_MS);
            if (readyChannels == 0) {
                throw new IOException("Таймаут подключения к серверу");
            }
            for (SelectionKey key : selector.selectedKeys()) {
                if (key.isConnectable()) {
                    if (channel.finishConnect()) {
                        connected = true;
                    } else {
                        throw new IOException("Не удалось завершить подключение");
                    }
                }

                selector.selectedKeys().remove(key);
            }
        } else {

            connected = true;
        }


        channel.register(selector, SelectionKey.OP_READ);
    }

    public SocketChannel getChannel() {
        return channel;
    }


    public Selector getSelector() {
        return selector;
    }

    public boolean isConnected() {
        return connected && channel.isConnected();
    }

    /**
     * Закрывает соединение.
     */
    public void close() throws IOException {
        if (selector != null && selector.isOpen()) {
            selector.close();
        }

        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}