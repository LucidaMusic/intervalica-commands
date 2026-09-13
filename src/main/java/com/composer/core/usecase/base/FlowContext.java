package com.composer.core.usecase.base;

public class FlowContext {
    public static class CancelException extends Exception {
        public CancelException() { super("Flow cancelled by user command."); }
    }
    public static class ExitException extends Exception {
        public ExitException() { super("Application execution lifecycle termination requested."); }
    }
}
