package org.intervalica.infrastructure.cli;

import java.util.regex.Pattern;

public record RegisteredRoute(Pattern pattern, String syntaxHelp, String descriptionHelp, SystemAction action) {
  public RegisteredRoute(String regex, String syntaxHelp, String descriptionHelp, SystemAction action) {
    this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), syntaxHelp, descriptionHelp, action);
  }
}
