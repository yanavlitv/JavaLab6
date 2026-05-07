package client.console;

import java.util.Scanner;

public class ConsoleReader {
    private final Scanner scanner;
    public ConsoleReader(){
        this.scanner =new Scanner(System.in);
    }
    public String readLine(){
        if (scanner.hasNextLine()){
            return scanner.nextLine();
        }
        return null;
    }
    public String readLine(String prompt){
        System.out.print(prompt);
        return readLine();
    }
    public String readNonEmpty(String prompt){
        while (true) {
            String input =readLine(prompt);
            if (input != null && !input.trim().isEmpty()){
                return input.trim();
            }
            System.out.println("Ввод не может быть пустым. Попробуйте снова.");
        }
    }
}
