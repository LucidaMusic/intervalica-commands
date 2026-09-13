package org.intervalica.core.domain.model;

public class RemoveOvertoneCommand implements Command {
  private final Song song;
  private final Song.Chord parentChord;
  private final Song.HarmonicNode overtoneNode;
  private int originalIndex;

  public RemoveOvertoneCommand(Song song, Song.Chord parentChord, Song.HarmonicNode overtoneNode) {
    this.song = song;
    this.parentChord = parentChord;
    this.overtoneNode = overtoneNode;
  }

  @Override
  public void execute() {
    this.originalIndex = parentChord.getOvertones().indexOf(overtoneNode);
    parentChord.getOvertones().remove(overtoneNode);
    song.notifyListeners();
  }

  @Override
  public void undo() {
    if (originalIndex >= 0 && originalIndex <= parentChord.getOvertones().size()) {
      parentChord.getOvertones().add(originalIndex, overtoneNode);
    } else {
      parentChord.getOvertones().add(overtoneNode);
    }
    song.notifyListeners();
  }
}
