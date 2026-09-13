package com.composer.infrastructure.cli;

import com.composer.core.domain.model.CommandHistoryManager;
import com.composer.core.domain.model.Song;
import com.composer.core.usecase.base.FlowContext;
import com.composer.infrastructure.ui.SongMonitorWindow;
import java.util.regex.Matcher;

@FunctionalInterface
public interface SystemAction {
  void ActionExecutor(
    String input,
    Matcher matcher,
    Song song,
    CommandHistoryManager history,
    SongMonitorWindow ui
  ) throws FlowContext.ExitException, FlowContext.CancelException;
}
