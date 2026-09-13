package org.intervalica.core.domain.model;

public class AddOvertoneCommand implements Command {
  private final Song song;
  private final Song.Chord parentChord;
  private final Song.HarmonicNode newOvertoneNode;

  public AddOvertoneCommand(Song song, Song.Chord parentChord, Song.HarmonicNode newOvertoneNode) {
    this.song = song;
    this.parentChord = parentChord;
    this.newOvertoneNode = newOvertoneNode;
  }

  @Override
  public void execute() {
    parentChord.getOvertones().add(newOvertoneNode);
    song.notifyListeners();
  }

  @Override
  public void undo() {
    parentChord.getOvertones().remove(newOvertoneNode);
    song.notifyListeners();
  }
}
