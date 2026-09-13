package org.intervalica.core.domain.model;

import java.util.ArrayList;
import java.util.List;

public class ClearSongCommand implements Command {
  private final Song song;
  private final List<Song.Chord> backupChords;

  public ClearSongCommand(Song song) {
    this.song = song;
    this.backupChords = new ArrayList<>(song.getChords());
  }

  @Override
  public void execute() {
    song.clearAllChords();
  }

  @Override
  public void undo() {
    for (Song.Chord c : backupChords) {
      song.addChordInstance(c);
    }
  }
}
