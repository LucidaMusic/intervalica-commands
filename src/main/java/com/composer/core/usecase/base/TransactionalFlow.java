package com.composer.core.usecase.base;

import com.composer.core.domain.model.Song;

public interface TransactionalFlow {
    void execute(Song targetSong, String inlineArgument) 
        throws FlowContext.CancelException, FlowContext.ExitException;
}
