package org.intervalica.core.domain.model;


import org.intervalica.core.domain.types.Interval;

public class EditNodeCommand implements Command {
  private final Song.HarmonicNode node;

  // Previous state snapshot
  private final String oldParentId;
  private final Interval oldInterval;
  private final int oldOctaveShift;
  private final boolean oldInverted;

  // Target state snapshot
  private final String newParentId;
  private final Interval newInterval;
  private final int newOctaveShift;
  private final boolean newInverted;

  public EditNodeCommand(Song.HarmonicNode node, String newParentId, Interval newInterval, int newOctaveShift, boolean newInverted) {
    this.node = node;
    this.oldParentId = node.getParentId();
    this.oldInterval = node.getInterval();
    this.oldOctaveShift = node.getOctaveShift();
    this.oldInverted = node.isInverted();

    this.newParentId = newParentId;
    this.newInterval = newInterval;
    this.newOctaveShift = newOctaveShift;
    this.newInverted = newInverted;
  }

  @Override
  public void execute() {
    node.mutateProperties(newParentId, newInterval, newOctaveShift, newInverted);
  }

  @Override
  public void undo() {
    node.mutateProperties(oldParentId, oldInterval, oldOctaveShift, oldInverted);
  }
}
