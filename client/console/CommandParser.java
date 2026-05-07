package client.console;

import client.network.RequestSender;
import client.validation.InputValidator;
import common.CommandRequest;
import common.CommandType;
import common.CommandResponse;
import client.validation.InputValidator.ValidationException;
import client.utils.ScriptRunner;
import models.Coordinates;
import models.Organization;
import models.Worker;
import models.enums.OrganizationType;
import models.enums.Position;
import models.enums.Status;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CommandParser {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private final ConsoleReader reader;
    private final RequestSender requestSender;

    public CommandParser(RequestSender requestSender) {
        this.reader = new ConsoleReader();
        this.requestSender = requestSender;
    }

    public void parse(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        try {
            CommandRequest request = buildRequest(cmdName, args);
            if (request == null) return;

            requestSender.send(request);
            CommandResponse response = requestSender.receiveResponse();
            printResponse(response);

        } catch (IOException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private CommandRequest buildRequest(String cmdName, String args) {
        switch (cmdName) {
            case "help": return new CommandRequest.Builder(CommandType.HELP).build();
            case "info": return new CommandRequest.Builder(CommandType.INFO).build();
            case "show": return new CommandRequest.Builder(CommandType.SHOW).build();
            case "insert": return handleInsert(args);
            case "update": return handleUpdate(args);
            case "remove_key": return handleRemoveKey(args);
            case "clear": return new CommandRequest.Builder(CommandType.CLEAR).build();
            case "exit": return new CommandRequest.Builder(CommandType.EXIT).build();
            case "remove_lower": return handleRemoveLower();
            case "replace_if_lower": return handleReplaceIfLower(args);
            case "remove_lower_key": return handleRemoveLowerKey(args);
            case "average_of_salary": return new CommandRequest.Builder(CommandType.AVERAGE_OF_SALARY).build();
            case "filter_greater_than_status": return handleFilterGreaterThanStatus(args);
            case "print_field_descending_end_date": return new CommandRequest.Builder(CommandType.PRINT_FIELD_DESCENDING_END_DATE).build();
            case "execute_script": return handleExecuteScript(args);
            case "save": {
                System.out.println("Команда 'save' доступна только на сервере!");
                return null;
            }
            default:
                System.out.println("Неизвестная команда: " + cmdName);
                System.out.println("Введите 'help' для списка команд.");
                return null;
        }
    }

    private CommandRequest handleInsert(String args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Укажите ключ для вставки");
        }
        String key = args.trim();
        System.out.println("Создание нового работника:");
        try {
            Worker worker = readWorker();
            InputValidator.validateWorker(worker);
            return new CommandRequest.Builder(CommandType.INSERT)
                    .withKey(key)
                    .withWorker(worker)
                    .build();
        } catch (ValidationException e) {  // ← теперь должно работать
            System.out.println("Ошибка валидации: " + e.getMessage());
            return null;
        }
    }

    private CommandRequest handleUpdate(String args) {
        Integer id = parseInt(args);
        if (id == null) {
            throw new IllegalArgumentException("Укажите ID работника для обновления");
        }
        System.out.println("Обновление работника с ID=" + id);
        try {
            Worker worker = readWorker();
            worker.setId(id);
            InputValidator.validateWorker(worker);
            return new CommandRequest.Builder(CommandType.UPDATE)
                    .withId(id)
                    .withWorker(worker)
                    .build();
        } catch (InputValidator.ValidationException e) {
            System.out.println("Ошибка валидации: " + e.getMessage());
            return null;
        }
    }

    private CommandRequest handleRemoveKey(String args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Укажите ключ для удаления");
        }
        return new CommandRequest.Builder(CommandType.REMOVE_KEY)
                .withKey(args.trim())
                .build();
    }

    private CommandRequest handleRemoveLower() {
        System.out.println("Введите работника для сравнения:");
        try {
            Worker worker = readWorker();
            InputValidator.validateWorker(worker);
            return new CommandRequest.Builder(CommandType.REMOVE_LOWER)
                    .withWorker(worker)
                    .build();
        } catch (InputValidator.ValidationException e) {
            System.out.println("Ошибка валидации: " + e.getMessage());
            return null;
        }
    }

    private CommandRequest handleReplaceIfLower(String args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Укажите ключ для замены");
        }
        String key = args.trim();
        System.out.println("Введите нового работника:");
        try {
            Worker worker = readWorker();
            InputValidator.validateWorker(worker);
            return new CommandRequest.Builder(CommandType.REPLACE_IF_LOWER)
                    .withKey(key)
                    .withWorker(worker)
                    .build();
        } catch (InputValidator.ValidationException e) {
            System.out.println("Ошибка валидации: " + e.getMessage());
            return null;
        }
    }

    private CommandRequest handleRemoveLowerKey(String args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Укажите ключ для сравнения");
        }
        return new CommandRequest.Builder(CommandType.REMOVE_LOWER_KEY)
                .withKey(args.trim())
                .build();
    }

    private CommandRequest handleFilterGreaterThanStatus(String args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Укажите статус для фильтрации");
        }
        try {
            Status status = Status.valueOf(args.trim().toUpperCase());
            return new CommandRequest.Builder(CommandType.FILTER_GREATER_THAN_STATUS)
                    .withStatus(status)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неверный статус. Доступны: FIRED, HIRED, RECOMMENDED_FOR_PROMOTION, REGULAR");
        }
    }

    private CommandRequest handleExecuteScript(String args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Укажите имя файла скрипта");
        }
        ScriptRunner scriptRunner = new ScriptRunner(requestSender, this);
        scriptRunner.executeScript(args.trim());
        return null;  // Не отправляем на сервер
    }

    private Worker readWorker() {
        String name = reader.readNonEmpty("Введите имя: ");

        double x = readDouble("x (> -764): ", -764.0, null);
        Float y = readFloat("y: ", null, null, true);
        Coordinates coords = new Coordinates(x, y);

        float salary = readFloat("Зарплата (> 0): ", 0.01f, null, false);
        LocalDate endDate = readDate("Дата окончания в формате ГГГГ-ММ-ДД (Enter - null): ", true);
        Position position = readEnum("Должность (Enter - null): ", Position.class, true);
        Status status = readEnum("Статус: ", Status.class, false);

        Organization org = readOrganization();

        return new Worker(null, name, coords, salary, endDate, position, status, org);
    }

    private double readDouble(String prompt, Double min, Double max) {
        while (true) {
            String input = reader.readLine(prompt);
            try {
                double value = Double.parseDouble(input);
                if (min != null && value <= min) {
                    System.out.println("Значение должно быть > " + min);
                    continue;
                }
                if (max != null && value >= max) {
                    System.out.println("Значение должно быть < " + max);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Введите число!");
            }
        }
    }

    private Float readFloat(String prompt, Float min, Float max, boolean nullable) {
        while (true) {
            String input = reader.readLine(prompt);
            if (input.trim().isEmpty()) {
                if (nullable) return null;
                System.out.println("Ввод обязателен!");
                continue;
            }
            try {
                float value = Float.parseFloat(input);
                if (min != null && value <= min) {
                    System.out.println("Значение должно быть > " + min);
                    continue;
                }
                if (max != null && value >= max) {
                    System.out.println("Значение должно быть < " + max);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Введите число!");
            }
        }
    }

    private LocalDate readDate(String prompt, boolean nullable) {
        while (true) {
            String input = reader.readLine(prompt);
            if (input.trim().isEmpty()) {
                if (nullable) return null;
                System.out.println("Ввод обязателен!");
                continue;
            }
            try {
                return LocalDate.parse(input, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("Формат: ГГГГ-ММ-ДД (например, 2024-01-15)");
            }
        }
    }

    private <T extends Enum<T>> T readEnum(String prompt, Class<T> enumClass, boolean nullable) {
        System.out.print("Доступные значения: ");
        T[] constants = enumClass.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(constants[i]);
        }
        System.out.println();

        while (true) {
            String input = reader.readLine(prompt);
            if (input.trim().isEmpty()) {
                if (nullable) return null;
                System.out.println("Ввод обязателен!");
                continue;
            }
            try {
                return Enum.valueOf(enumClass, input.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Неверное значение");
            }
        }
    }

    private Organization readOrganization() {

        String answer = reader.readLine("Добавить организацию? (y/n): ");
        if (answer == null || !answer.trim().equalsIgnoreCase("y")) {
            return null;
        }

        Integer turnover = null;
        while (turnover == null) {
            String input = reader.readLine("Годовой оборот (> 0): ");
            try {
                turnover = Integer.parseInt(input);
                if (turnover <= 0) {
                    System.out.println("Оборот должен быть > 0");
                    turnover = null;
                }
            } catch (NumberFormatException e) {
                System.out.println("Введите число!");
            }
        }

        Long employees = null;
        while (employees == null) {
            String input = reader.readLine("Количество сотрудников (> 0): ");
            try {
                employees = Long.parseLong(input);
                if (employees <= 0) {
                    System.out.println("Количество сотрудников должно быть > 0");
                    employees = null;
                }
            } catch (NumberFormatException e) {
                System.out.println("Введите число!");
            }
        }

        OrganizationType type = readEnum("Тип организации (Enter - null): ", OrganizationType.class, true);

        return new Organization(turnover, employees, type);
    }

    private Integer parseInt(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void printResponse(CommandResponse response) {
        switch (response.getType()) {
            case SUCCESS -> System.out.println(response.getMessage());
            case ERROR -> System.err.println("Ошибка: " + response.getMessage());
            case INFO -> System.out.println(response.getMessage());
            case COLLECTION -> {
                System.out.println(response.getMessage());
                if (response.getWorkers() != null && !response.getWorkers().isEmpty()) {
                    response.getWorkers().forEach(w -> System.out.println("  " + w));
                }
            }
            case COLLECTION_WITH_KEYS -> {
                // Убираем дублирование - выводим только сообщение
                System.out.println(response.getMessage());
            }
            case AVERAGE -> {
                if (response.getAverageSalary() != null) {
                    System.out.printf("Средняя зарплата: %.2f%n", response.getAverageSalary());
                } else {
                    System.out.println(response.getMessage());
                }
            }
            case DATES -> {
                System.out.println(response.getMessage());
                if (response.getDates() != null && !response.getDates().isEmpty()) {
                    response.getDates().forEach(System.out::println);
                }
            }
            default -> System.out.println(response.getMessage());
        }
    }
}