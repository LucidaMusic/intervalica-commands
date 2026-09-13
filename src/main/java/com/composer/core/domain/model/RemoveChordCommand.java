package src.main.java.com.composer.core.domain.model;

public class RemoveChordCommand implements Command {
  private final Song song;
  private final Song.Chord chordToRemove;
  private int originalIndex;

  public RemoveChordCommand(Song song, Song.Chord chordToRemove) {
    this.song = song;
    this.chordToRemove = chordToRemove;
  }

  @Override
  public void execute() {
    this.originalIndex = song.getChords().indexOf(chordToRemove);
    song.removeChordInstance(chordToRemove);
  }

  @Override
  public void undo() {
    song.insertChordInstanceAt(chordToRemove, originalIndex);
  }
}
