package src.main.java.com.composer.core.usecase.impl;

import src.main.java.com.composer.core.domain.model.AddChordCommand;
import src.main.java.com.composer.core.domain.model.CommandHistoryManager;
import src.main.java.com.composer.core.domain.model.Song;
import src.main.java.com.composer.core.domain.types.Duration;
import src.main.java.com.composer.core.domain.types.Interval;
import src.main.java.com.composer.core.usecase.base.FlowContext;
import src.main.java.com.composer.core.usecase.base.FlowStep;
import src.main.java.com.composer.core.usecase.base.TransactionalFlow;
import src.main.java.com.composer.infrastructure.ui.SongMonitorWindow;

import java.util.ArrayList;
import java.util.List;

public class AddChordFlow implements TransactionalFlow {

  private final CommandHistoryManager historyManager;
  private final SongMonitorWindow uiWindow;
  private final String customChordName;

  // Transactional intermediate variables
  private String resolvedParentId = null;
  private int overtoneCount = 0;
  private Duration chordDuration = Duration.QUARTER;

  // Helper structure to cleanly hold multi-dimensional input variables locally
  private static class NodeSpecificationBlueprint {
    Interval interval;
    int octaveShift = 0;
    boolean inverted = false;
  }

  private final NodeSpecificationBlueprint tonicBlueprint = new NodeSpecificationBlueprint();
  private final List<NodeSpecificationBlueprint> overtonesBlueprints = new ArrayList<>();

  public AddChordFlow(CommandHistoryManager historyManager, SongMonitorWindow uiWindow, String customChordName) {
    this.historyManager = historyManager;
    this.uiWindow = uiWindow;
    this.customChordName = customChordName;
  }

  @Override
  public void execute(Song targetSong, String inlineArgument)
    throws FlowContext.CancelException, FlowContext.ExitException {

    overtonesBlueprints.clear();
    resolvedParentId = null;

    int chordIndex = targetSong.getNextChordIndex();
    String currentTonicId = "T" + chordIndex;

    uiWindow.printToTerminal("\n----------------------------------------------------------------------");
    uiWindow.printToTerminal(String.format(" >>> ADVANCED COGNITIVE WORKSPACE: Designing Chord %d (%s) <<< ", chordIndex, currentTonicId));
    if (customChordName != null && !customChordName.isBlank()) {
      uiWindow.printToTerminal(" -> Meta Tag Alias Assigned: \"" + customChordName + "\"");
    }
    uiWindow.printToTerminal("----------------------------------------------------------------------");

    boolean hasInlineCount = (inlineArgument != null && inlineArgument.matches("^\\d+$"));
    if (hasInlineCount) {
      this.overtoneCount = Integer.parseInt(inlineArgument);
      uiWindow.printToTerminal("-> Overtone architectural payload density pre-configured to: " + this.overtoneCount);
    }

    // STEP 1: Resolve Tonic Root Parent and Properties
    if (chordIndex == 1) {
      this.resolvedParentId = null;
      this.tonicBlueprint.interval = Interval.PERFECT_UNISON;
      this.tonicBlueprint.octaveShift = 0;
      this.tonicBlueprint.inverted = false;
      uiWindow.printToTerminal("-> First chord automated default anchor: absolute BASE frequency via PERFECT_UNISON (1/1).");
    } else {
      printAvailableNodesMenu(targetSong);
      FlowStep<String> stepRef = new FlowStep<>(
        "Enter Target Parent Node ID for this Tonic (or type 'BASE'): ",
        "^[a-zA-Z0-9_]+$",
        "Invalid ID pattern syntax.",
        input -> {
          if (input.equalsIgnoreCase("BASE")) {
            this.resolvedParentId = null;
          } else {
            Song.HarmonicNode parentNode = targetSong.findNodeById(input);
            if (parentNode == null) throw new IllegalArgumentException("Node ID not found.");
            this.resolvedParentId = parentNode.getId();
          }
        }
      );
      executeStep(stepRef);

      uiWindow.printToTerminal("\n--- Defining Tonic Properties ---");
      captureNodePropertiesWorkflow(this.tonicBlueprint);
    }

    // STEP 2: Request Overtone branch counts (If not inline)
    if (!hasInlineCount) {
      FlowStep<Integer> stepOvertonesCount = new FlowStep<>(
        "How many Overtones will branch out from this Tonic? (e.g., 2): ",
        "^\\d+$",
        "Please enter a valid positive integer.",
        input -> this.overtoneCount = Integer.parseInt(input)
      );
      executeStep(stepOvertonesCount);
    }

    // STEP 3: Build individual Overtone specifications
    for (int i = 0; i < overtoneCount; i++) {
      int overtoneIdx = i + 1;
      uiWindow.printToTerminal(String.format("\n--- Defining Overtone #%d (Node ID: O%d_%d) ---", overtoneIdx, chordIndex, overtoneIdx));
      NodeSpecificationBlueprint overtoneBp = new NodeSpecificationBlueprint();
      captureNodePropertiesWorkflow(overtoneBp);
      overtonesBlueprints.add(overtoneBp);
    }

    // STEP 4: Request structural duration profile
    printDurationMenu();
    FlowStep<Duration> stepDuration = new FlowStep<>(
      "Select Duration Index for this chord footprint block: ",
      "^\\d+$",
      "Invalid selection index.",
      input -> {
        int choice = Integer.parseInt(input);
        if (choice < 0 || choice >= Duration.values().length) throw new IllegalArgumentException();
        this.chordDuration = Duration.values()[choice];
      }
    );
    executeStep(stepDuration);

    // SYSTEM DAG ASSEMBLY PHASE
    Song.HarmonicNode rootNode = new Song.HarmonicNode(
      currentTonicId, resolvedParentId, tonicBlueprint.interval,
      tonicBlueprint.octaveShift, tonicBlueprint.inverted, "ROOT"
    );
    Song.Chord operationalChord = new Song.Chord("C" + chordIndex, customChordName, rootNode, chordDuration);

    for (int i = 0; i < overtonesBlueprints.size(); i++) {
      String overtoneId = String.format("O%d_%d", chordIndex, i + 1);
      NodeSpecificationBlueprint bp = overtonesBlueprints.get(i);
      Song.HarmonicNode overtoneNode = new Song.HarmonicNode(
        overtoneId, currentTonicId, bp.interval,
        bp.octaveShift, bp.inverted, "OVERTONE"
      );
      operationalChord.getOvertones().add(overtoneNode);
    }

    // Execute and push state into history manager
    AddChordCommand cmd = new AddChordCommand(targetSong, operationalChord);
    historyManager.executeCommand(cmd);

    uiWindow.printToTerminal("\n[Success] Composite multi-dimensional Chord successfully committed onto matrix graph.");
    uiWindow.printToTerminal("----------------------------------------------------------------------");
  }

  /**
   * COMPOSITE WORKFLOW WORKER
   * Captures Ratio Selection (with custom math operations), Octavation shifts, and Direction directions seamlessly.
   */
  private void captureNodePropertiesWorkflow(NodeSpecificationBlueprint targetBlueprint) throws FlowContext.CancelException, FlowContext.ExitException {
    printIntervalMenu();

    // A. Resolve Core Interval Ratio (Supports custom expressions input interception)
    FlowStep<Interval> stepIntervalChoice = new FlowStep<>(
      "Select Interval Index (Choose 0 for Custom Expression input): ",
      "^\\d+$",
      "Invalid selection option index position.",
      input -> {
        int choice = Integer.parseInt(input);
        if (choice < 0 || choice >= Interval.values().length) throw new IllegalArgumentException();
        targetBlueprint.interval = Interval.values()[choice];
      }
    );
    executeStep(stepIntervalChoice);

// Intercept Custom Equation Branch
    if (targetBlueprint.interval == Interval.CUSTOM) {
      FlowStep<Interval> stepCustomExpression = new FlowStep<>(
        "Enter Custom Ratio Fraction or Operation Expression (e.g., '(3/2)*2+45' or '11/8'): ",
        "^.+$",
        "Mathematical notation format parsing error.",
        input -> {
          String cleanExpr = input.replaceAll("\\s+", "");

          // FIXED: We now use the evaluated value to give rich visual feedback to the composer!
          double evaluatedRatio = Interval.evaluateMathExpression(cleanExpr);
          uiWindow.printToTerminal(String.format("   [Parsed] Expression resolved to a raw multiplier ratio of: %.4f", evaluatedRatio));

          targetBlueprint.interval = new Interval("CHISTER_EXPR", cleanExpr);
        }
      );
      executeStep(stepCustomExpression);
    }

    // B. Capture Octavation Shift Coefficient (+2, -1, 0)
    FlowStep<Integer> stepOctave = new FlowStep<>(
      "Enter Octave Shift factor (e.g., 0 for default, +2 to shift up, -1 to shift down): ",
      "^[+-]?\\d+$",
      "Please enter a valid signed integer coefficient (e.g., 0, +1, -2).",
      input -> {
        String clean = input.startsWith("+") ? input.substring(1) : input;
        targetBlueprint.octaveShift = Integer.parseInt(clean);
      }
    );
    executeStep(stepOctave);

    // C. Capture Inversion Direction Flags (-inv / none)
    FlowStep<Boolean> stepInversion = new FlowStep<>(
      "Type '-inv' to invert interval direction down, or type 'none' to keep default up direction: ",
      "^(?i)(-inv|none)$",
      "Invalid direction parameter flag. Please type exactly '-inv' or 'none'.",
      input -> targetBlueprint.inverted = input.equalsIgnoreCase("-inv")
    );
    executeStep(stepInversion);
  }


  private void executeStep(FlowStep<?> step) throws FlowContext.CancelException, FlowContext.ExitException {
    while (true) {
      uiWindow.printToTerminal(step.getPrompt());
      String rawInput = uiWindow.readInputFromUI();
      if (!rawInput.matches(step.getRegexPattern())) {
        uiWindow.printToTerminal("   [!] ERROR: " + step.getErrorMessage());
        continue;
      }
      try {
        step.process(rawInput);
        break;
      } catch (Exception e) {
        uiWindow.printToTerminal("   [!] ERROR: " + e.getMessage());
      }
    }
  }

  private void printAvailableNodesMenu(Song song) {
    uiWindow.printToTerminal("\n--- Available Relational Anchor Nodes ---");
    uiWindow.printToTerminal(" [BASE] -> Absolute Reference Frequency");
    for (Song.Chord c : song.getChords()) {
      uiWindow.printToTerminal(String.format("  • %s (Tonic Node of \"%s\")", c.getRootNode().getId(), c.getName()));
      for (Song.HarmonicNode o : c.getOvertones()) {
        uiWindow.printToTerminal(String.format("    ├── %s (Overtone Node)", o.getId()));
      }
    }
    uiWindow.printToTerminal("------------------------------------------");
  }

  private void printIntervalMenu() {
    uiWindow.printToTerminal("\n--- Available Proportional Just Intervals ---");
    Interval[] vals = Interval.values();
    for (int i = 0; i < vals.length; i++) {
      if (vals[i] == Interval.CUSTOM) {
        uiWindow.printToTerminal(" [ 0] -> ** CUSTOM MATHEMATICAL EXPRESSION (Sacado de Chistera) **");
      } else {
        uiWindow.printToTerminal(String.format(" [%2d] %s (Ratio: %s)", i, vals[i].name(), vals[i].getExpression()));
      }
    }
    uiWindow.printToTerminal("----------------------------------------------");
  }

  private void printDurationMenu() {
    uiWindow.printToTerminal("\n--- Note Duration Blueprints ---");
    Duration[] vals = Duration.values();
    for (int i = 0; i < vals.length; i++) {
      uiWindow.printToTerminal(String.format(" [%d] %s", i, vals[i].name()));
    }
    uiWindow.printToTerminal("--------------------------------");
  }
}