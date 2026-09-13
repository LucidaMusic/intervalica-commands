package src.main.java.com.composer.core.domain.model;

public interface Command {
    void execute();
    void undo();
}
