package src.main.java.com.composer.core.domain.model;

public class AddChordCommand implements Command {
  private final Song song;
  private final Song.Chord chordInstance;

  public AddChordCommand(Song song, Song.Chord chordInstance) {
    this.song = song;
    this.chordInstance = chordInstance;
  }

  @Override
  public void execute() {
    song.addChordInstance(chordInstance);
  }

  @Override
  public void undo() {
    song.removeChordInstance(chordInstance);
  }
}
