package src.main.java.com.composer.infrastructure.cli;

import src.main.java.com.composer.core.domain.model.CommandHistoryManager;
import src.main.java.com.composer.core.domain.model.Song;
import src.main.java.com.composer.core.usecase.base.FlowContext;
import src.main.java.com.composer.infrastructure.ui.SongMonitorWindow;
import java.util.regex.Matcher;

@FunctionalInterface
public interface SystemAction {
  void ActionExecutor(
    String input,
    Matcher matcher, // Inyectamos el Matcher con los grupos capturados
    Song song,
    CommandHistoryManager history,
    SongMonitorWindow ui
  ) throws FlowContext.ExitException, FlowContext.CancelException;
}
