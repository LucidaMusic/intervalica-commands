package org.intervalica.infrastructure.cli;




import org.intervalica.core.domain.model.CommandHistoryManager;
import org.intervalica.core.domain.model.Song;
import org.intervalica.core.usecase.base.FlowContext;
import org.intervalica.infrastructure.ui.SongMonitorWindow;

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
