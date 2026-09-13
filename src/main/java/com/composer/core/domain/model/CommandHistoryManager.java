package src.main.java.com.composer.core.domain.model;

import src.main.java.com.composer.infrastructure.ui.SongMonitorWindow;

import java.util.Stack;

public class CommandHistoryManager {
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();

    public synchronized void executeCommand(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
    }

    public synchronized void undo(SongMonitorWindow ui) {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            ui.printToTerminal(">>> History: Undo operation applied successfully.");
        } else {
            ui.printToTerminal("[History Warning] Nothing left to undo.");
        }
    }

    public synchronized void redo(SongMonitorWindow ui) {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.execute();
            undoStack.push(command);
            ui.printToTerminal(">>> History: Redo operation applied successfully.");
        } else {
            ui.printToTerminal("[History Warning] Nothing left to redo.");
        }
    }
}
