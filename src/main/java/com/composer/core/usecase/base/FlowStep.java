package src.main.java.com.composer.core.usecase.base;

import lombok.Getter;

import java.util.function.Consumer;

public class FlowStep {
  @Getter
  private final String prompt;
  @Getter
  private final String regexPattern;
  @Getter
  private final String errorMessage;
  private final Consumer<String> parserAndStorer;

  public FlowStep(String prompt, String regexPattern, String errorMessage, Consumer<String> parserAndStorer) {
    this.prompt = prompt;
    this.regexPattern = regexPattern;
    this.errorMessage = errorMessage;
    this.parserAndStorer = parserAndStorer;
  }

  public void process(String input) {
    parserAndStorer.accept(input);
  }
}
