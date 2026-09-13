package org.intervalica.core.domain.model;

public interface Command {
  void execute();

  void undo();
}
