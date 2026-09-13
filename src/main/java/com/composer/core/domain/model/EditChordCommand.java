package src.main.java.com.composer.core.domain.model;


import src.main.java.com.composer.core.domain.types.Duration;

public class EditChordCommand implements Command {
  private final Song.Chord chord;
  private final String oldName;
  private final Duration oldDuration;
  private final String newName;
  private final Duration newDuration;

  public EditChordCommand(Song.Chord chord, String newName, Duration newDuration) {
    this.chord = chord;
    this.oldName = chord.getName();
    this.oldDuration = chord.getDuration();
    this.newName = newName;
    this.newDuration = newDuration;
  }

  @Override
  public void execute() {
    chord.mutateMetadata(newName, newDuration);
  }

  @Override
  public void undo() {
    chord.mutateMetadata(oldName, oldDuration);
  }
}
