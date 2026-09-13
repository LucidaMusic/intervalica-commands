package org.intervalica.core.usecase.base;


import org.intervalica.core.domain.model.Song;

public interface TransactionalFlow {
  void execute(Song targetSong, String inlineArgument)
    throws FlowContext.CancelException, FlowContext.ExitException;
}
