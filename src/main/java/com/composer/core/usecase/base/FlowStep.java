package com.composer.core.usecase.base;

import java.util.function.Consumer;

public class FlowStep<T> {
    private final String prompt;
    private final String regexPattern;
    private final String errorMessage;
    private final Consumer<String> parserAndStorer;

    public FlowStep(String prompt, String regexPattern, String errorMessage, Consumer<String> parserAndStorer) {
        this.prompt = prompt;
        this.regexPattern = regexPattern;
        this.errorMessage = errorMessage;
        this.parserAndStorer = parserAndStorer;
    }

    public String getPrompt() { return prompt; }
    public String getRegexPattern() { return regexPattern; }
    public String getErrorMessage() { return errorMessage; }
    public void process(String input) { parserAndStorer.accept(input); }
}
