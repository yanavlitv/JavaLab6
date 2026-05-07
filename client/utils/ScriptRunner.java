package client.utils;

import client.console.CommandParser;
import client.network.RequestSender;
import common.CommandRequest;
import common.CommandResponse;
import common.CommandType;
import models.*;
import models.enums.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class ScriptRunner {
    private static Set<String> runningScripts = new HashSet<>();
    private final RequestSender requestSender;
    private final CommandParser commandParser;

    public ScriptRunner(RequestSender requestSender, CommandParser commandParser) {
        this.requestSender = requestSender;
        this.commandParser = commandParser;
    }

    public void executeScript(String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            System.out.println("Ошибка: файл '" + filename + "' не найден");
            return;
        }

        if (!file.canRead()) {
            System.out.println("Ошибка: нет прав на чтение файла '" + filename + "'");
            return;
        }

        String absolutePath;
        try {
            absolutePath = file.getCanonicalPath();
        } catch (IOException e) {
            absolutePath = file.getAbsolutePath();
        }

        if (runningScripts.contains(absolutePath)) {
            System.out.println("Ошибка: обнаружена рекурсия в скрипте '" + filename + "'");
            return;
        }

        runningScripts.add(absolutePath);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            System.out.println("ВЫПОЛНЕНИЕ СКРИПТА: " + filename);

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                System.out.println("  [" + lineNumber + "] > " + line);

                String[] parts = line.split("\\s+", 2);
                String cmdName = parts[0].toLowerCase();
                String args = parts.length > 1 ? parts[1] : "";

                // Защита от рекурсии
                if (cmdName.equals("execute_script")) {
                    System.out.println("  Ошибка: execute_script не может быть вызвана из скрипта");
                    continue;
                }

                if (cmdName.equals("exit")) {
                    System.out.println("  Команда exit прервала скрипт");
                    System.out.println("Завершение работы клиента...");
                    System.exit(0);
                    break;
                }

                try {
                    CommandRequest request = buildRequestFromScript(cmdName, args, reader);
                    if (request == null) continue;

                    requestSender.send(request);
                    CommandResponse response = requestSender.receiveResponse();
                    commandParser.printResponse(response);

                } catch (Exception e) {
                    System.out.println("  Ошибка в строке " + lineNumber + ": " + e.getMessage());
                    break;
                }
            }

            System.out.println("СКРИПТ ЗАВЕРШЕН\n");

        } catch (FileNotFoundException e) {
            System.out.println("Ошибка: файл не найден - " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Ошибка при выполнении скрипта: " + e.getMessage());
        } finally {
            runningScripts.remove(absolutePath);
        }
    }

    private CommandRequest buildRequestFromScript(String cmdName, String args, BufferedReader reader) throws IOException {
        switch (cmdName) {
            case "help": return new CommandRequest.Builder(CommandType.HELP).build();
            case "info": return new CommandRequest.Builder(CommandType.INFO).build();
            case "show": return new CommandRequest.Builder(CommandType.SHOW).build();
            case "clear": return new CommandRequest.Builder(CommandType.CLEAR).build();
            case "exit": return new CommandRequest.Builder(CommandType.EXIT).build();
            case "average_of_salary": return new CommandRequest.Builder(CommandType.AVERAGE_OF_SALARY).build();
            case "print_field_descending_end_date": return new CommandRequest.Builder(CommandType.PRINT_FIELD_DESCENDING_END_DATE).build();
            case "remove_key":
                if (args.isEmpty()) throw new IllegalArgumentException("Укажите ключ");
                return new CommandRequest.Builder(CommandType.REMOVE_KEY).withKey(args).build();
            case "remove_lower_key":
                if (args.isEmpty()) throw new IllegalArgumentException("Укажите ключ");
                return new CommandRequest.Builder(CommandType.REMOVE_LOWER_KEY).withKey(args).build();
            case "filter_greater_than_status":
                if (args.isEmpty()) throw new IllegalArgumentException("Укажите статус");
                try {
                    return new CommandRequest.Builder(CommandType.FILTER_GREATER_THAN_STATUS)
                            .withStatus(Status.valueOf(args.toUpperCase())).build();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Неверный статус");
                }
            case "insert":
                if (args.isEmpty()) throw new IllegalArgumentException("Укажите ключ");
                Worker insertWorker = readWorkerFromScript(reader);
                return new CommandRequest.Builder(CommandType.INSERT).withKey(args).withWorker(insertWorker).build();
            case "update":
                if (args.isEmpty()) throw new IllegalArgumentException("Укажите ID");
                int id = Integer.parseInt(args);
                Worker updateWorker = readWorkerFromScript(reader);
                updateWorker.setId(id);
                return new CommandRequest.Builder(CommandType.UPDATE).withId(id).withWorker(updateWorker).build();
            case "remove_lower":
                Worker lowerWorker = readWorkerFromScript(reader);
                return new CommandRequest.Builder(CommandType.REMOVE_LOWER).withWorker(lowerWorker).build();
            case "replace_if_lower":
                if (args.isEmpty()) throw new IllegalArgumentException("Укажите ключ");
                Worker replaceWorker = readWorkerFromScript(reader);
                return new CommandRequest.Builder(CommandType.REPLACE_IF_LOWER).withKey(args).withWorker(replaceWorker).build();
            default:
                System.out.println("  Неизвестная команда: " + cmdName);
                return null;
        }
    }

    private Worker readWorkerFromScript(BufferedReader reader) throws IOException {
        String name = readNonEmptyLine(reader);
        double x = Double.parseDouble(readNonEmptyLine(reader).replace(',', '.'));
        Float y = Float.parseFloat(readNonEmptyLine(reader).replace(',', '.'));
        float salary = Float.parseFloat(readNonEmptyLine(reader).replace(',', '.'));

        String endDateStr = readNonEmptyLine(reader);
        LocalDate endDate = endDateStr.isEmpty() || endDateStr.equals("null") ? null : LocalDate.parse(endDateStr);

        String positionStr = readNonEmptyLine(reader);
        Position position = positionStr.isEmpty() || positionStr.equals("null") ? null : Position.valueOf(positionStr.toUpperCase());

        Status status = Status.valueOf(readNonEmptyLine(reader).toUpperCase());

        String hasOrg = readNonEmptyLine(reader);
        Organization organization = null;
        if (hasOrg.equalsIgnoreCase("y") || hasOrg.equalsIgnoreCase("yes")) {
            Integer turnover = Integer.parseInt(readNonEmptyLine(reader));
            long employees = Long.parseLong(readNonEmptyLine(reader));
            String typeStr = readNonEmptyLine(reader);
            OrganizationType type = typeStr.isEmpty() || typeStr.equals("null") ? null : OrganizationType.valueOf(typeStr.toUpperCase());
            organization = new Organization(turnover, employees, type);
        }

        Coordinates coords = new Coordinates(x, y);
        return new Worker(null, name, coords, salary, endDate, position, status, organization);
    }

    private String readNonEmptyLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line;
            }
        }
        throw new IOException("Неожиданный конец файла при чтении данных");
    }
}