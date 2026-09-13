package src.main.java.com.composer.infrastructure.cli;

import java.util.regex.Pattern;

/**
 * Registry entry that binds metadata, regex, and behavior into a single source of truth.
 */
public class RegisteredRoute {
  private final Pattern pattern;
  private final String syntaxHelp;
  private final String descriptionHelp;
  private final SystemAction action;

  public RegisteredRoute(String regex, String syntaxHelp, String descriptionHelp, SystemAction action) {
    this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    this.syntaxHelp = syntaxHelp;
    this.descriptionHelp = descriptionHelp;
    this.action = action;
  }

  public Pattern getPattern() { return pattern; }
  public String getSyntaxHelp() { return syntaxHelp; }
  public String getDescriptionHelp() { return descriptionHelp; }
  public SystemAction getAction() { return action; }
}
