package src.main.java.com.composer.infrastructure.cli;

import src.main.java.com.composer.core.usecase.base.FlowContext;

import java.util.Scanner;

public class ConsoleInputReader {
    private static final Scanner scanner = new Scanner(System.in);

    public static String readLine() throws FlowContext.CancelException, FlowContext.ExitException {
        if (!scanner.hasNextLine()) {
            throw new FlowContext.ExitException();
        }
        String input = scanner.nextLine().trim();

        if (input.equalsIgnoreCase("cancel")) {
            System.out.println("\n[Process] Flow aborted by user. Returning to main menu...");
            throw new FlowContext.CancelException();
        }
        if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
            System.out.println("\n[Process] Shutting down workspace environment safely...");
            throw new FlowContext.ExitException();
        }
        return input;
    }
}
