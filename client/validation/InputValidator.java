package client.validation;

import models.Worker;
import models.Coordinates;
import models.Organization;

//Валидатор входных данных.

public class InputValidator {
    //Валидирует объект Worker.
    public static void validateWorker(Worker worker) throws ValidationException {
        if (worker.getName() == null || worker.getName().trim().isEmpty()) {
            throw new ValidationException("Имя не может быть пустым");
        }

        // Проверяем координаты: не null
        if (worker.getCoordinates() == null) {
            throw new ValidationException("Координаты не могут быть null");
        }

        // Валидируем координаты отдельно
        validateCoordinates(worker.getCoordinates());

        // Проверяем зарплату: должна быть > 0
        if (worker.getSalary() <= 0) {
            throw new ValidationException("Зарплата должна быть больше 0");
        }

        // Проверяем статус: не null
        if (worker.getStatus() == null) {
            throw new ValidationException("Статус не может быть null");
        }

        // Если организация есть — валидируем её
        if (worker.getOrganization() != null) {
            validateOrganization(worker.getOrganization());
        }
    }

      //Валидирует координаты.

    private static void validateCoordinates(Coordinates coords) throws ValidationException {
        if (coords.getX() <= -764) {
            throw new ValidationException("X должен быть больше -764");
        }

        if (coords.getY() <= -764) {
            throw new ValidationException("Y должен быть больше -764");
        }
    }

    //Валидирует организацию.
    private static void validateOrganization(Organization org) throws ValidationException {
        // Годовой оборот: если есть, должен быть > 0
        if (org.getAnnualTurnover() != null && org.getAnnualTurnover() <= 0) {
            throw new ValidationException("Годовой оборот должен быть больше 0");
        }

        // Количество сотрудников: если есть, должно быть > 0
        if (org.getEmployeesCount() <= 0) {
            throw new ValidationException("Количество сотрудников должно быть больше 0");
        }
    }


     //Пользовательское исключение для ошибок валидации.

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
