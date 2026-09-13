package src.main.java.com.composer.core.usecase.base;


import src.main.java.com.composer.core.domain.model.Song;

public interface TransactionalFlow {
    void execute(Song targetSong, String inlineArgument) 
        throws FlowContext.CancelException, FlowContext.ExitException;
}
