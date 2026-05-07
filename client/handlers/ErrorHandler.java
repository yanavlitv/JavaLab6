package client.handlers;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
public class ErrorHandler {

    /**
     * Обрабатывает исключение.
     */
    public static void handle(Exception e) {
        if (e instanceof ConnectException) {
            System.err.println("Сервер недоступен. Проверьте, запущен ли он.");
            System.err.println("Адрес: localhost:2424");
        }
        else if (e instanceof SocketTimeoutException) {
            System.err.println("Превышено время ожидания ответа.");
            System.err.println("Попробуйте повторить команду.");
        }
        else if (e instanceof ClosedChannelException) {
            System.err.println("Соединение разорвано.");
            System.err.println("Перезапустите клиента для подключения.");
        }
        else if (e instanceof UnresolvedAddressException) {

            System.err.println("Неверный адрес сервера.");
            System.err.println("Проверьте настройки хоста и порта.");
        }
        else if (e instanceof IOException) {
            System.err.println("Ошибка сети: " + e.getMessage());
        }
        else {
            System.err.println("Ошибка: " + e.getMessage());
        }

    }
}