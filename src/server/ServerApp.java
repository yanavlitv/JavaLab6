package server;

import server.commands.CommandExecutor;
import server.io.FileManager;
import server.network.TCPServer;
import server.utils.Constants;
import models.Worker;

import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Logger;

public class ServerApp {
    private static final Logger logger = Logger.getLogger(ServerApp.class.getName());
    private static TCPServer server;
    private static CommandExecutor commandExecutor;
    private static HashMap<String, Worker> collection;
    private static boolean running = true;

    public static void main(String[] args) {
        logger.info("=== СЕРВЕР УПРАВЛЕНИЯ РАБОТНИКАМИ ===");
        String fileName = System.getenv("WORKER_DATA");
        if (fileName == null) {
            fileName = Constants.DEFAULT_FILE_NAME;
            logger.info("Используется файл по умолчанию: " + fileName);
        }

        FileManager fileManager = new FileManager();
        collection = fileManager.loadFromFile(fileName);
        logger.info("Загружено работников: " + collection.size());

        commandExecutor = new CommandExecutor(collection, fileName);

        try {
            server = new TCPServer(Constants.PORT, commandExecutor);
            Thread serverThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    logger.severe("Критическая ошибка сервера: " + e.getMessage());
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            handleServerConsole();

        } catch (Exception e) {
            logger.severe("Ошибка инициализации сервера: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleServerConsole() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.print("server> ");
            String input = scanner.nextLine().trim().toLowerCase();
            switch (input) {
                case "save":
                    commandExecutor.saveCollection();
                    System.out.println("Коллекция сохранена.");
                    break;
                case "info":
                    System.out.println("Статус сервера:");
                    System.out.println("  Порт: " + Constants.PORT);
                    System.out.println("  Коллекция: " + collection.size() + " элементов");
                    break;
                case "exit":
                    System.out.println("Завершение работы сервера...");
                    running = false;
                    server.stop();
                    commandExecutor.saveCollection();
                    System.exit(0);
                    break;
                default:
                    if (!input.isEmpty()) {
                        System.out.println("Доступные команды: save, info, exit");
                    }
                    break;
            }
        }
        scanner.close();
    }
}
