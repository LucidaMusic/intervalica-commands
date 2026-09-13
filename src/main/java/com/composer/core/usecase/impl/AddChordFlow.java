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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddChordFlow implements TransactionalFlow {

  private final CommandHistoryManager historyManager;
  private final SongMonitorWindow uiWindow;
  private final String customChordName;

  private String resolvedParentId = null;
  private int overtoneCount = 0;
  private Duration chordDuration = Duration.QUARTER;

  private static class NodeSpecificationBlueprint {
    Interval interval;
    int octaveShift = 0;
    boolean inverted = false;
  }

  private final NodeSpecificationBlueprint tonicBlueprint = new NodeSpecificationBlueprint();
  private final List<NodeSpecificationBlueprint> overtonesBlueprints = new ArrayList<>();

  // Compiled pattern to parse interval token and optional inline modifiers
  private static final Pattern BLUEPRINT_PARSER_PATTERN = Pattern.compile(
    "^([^\\s-]+)(?:\\s+-oct\\s+([+-]?\\d+))?(?:\s+(-inv))?$",
    Pattern.CASE_INSENSITIVE
  );

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

      uiWindow.printToTerminal("\n--- Define Tonic Specifications ---");
      printIntervalMenu();
      executeSpecificationWorkflowStep("Configure Tonic (e.g., '2 -oct +1 -inv' or '3/2'): ", this.tonicBlueprint);
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

    // STEP 3: Build individual Overtone specifications in single unified prompts
    if (overtoneCount > 0) {
      printIntervalMenu();
    }
    for (int i = 0; i < overtoneCount; i++) {
      int overtoneIdx = i + 1;
      NodeSpecificationBlueprint overtoneBp = new NodeSpecificationBlueprint();
      executeSpecificationWorkflowStep(
        String.format("Configure Overtone #%d (Node ID: O%d_%d): ", overtoneIdx, chordIndex, overtoneIdx),
        overtoneBp
      );
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

    AddChordCommand cmd = new AddChordCommand(targetSong, operationalChord);
    historyManager.executeCommand(cmd);

    uiWindow.printToTerminal("\n[Success] Composite multi-dimensional Chord successfully committed onto matrix graph.");
    uiWindow.printToTerminal("----------------------------------------------------------------------");
  }

  /**
   * UNIFIED COMPACT WORKFLOW STEP
   * Parses the interval token and its flags (-oct, -inv) in a single user input interaction.
   */
  private void executeSpecificationWorkflowStep(String prompt, NodeSpecificationBlueprint blueprint) throws FlowContext.CancelException, FlowContext.ExitException {
    FlowStep<NodeSpecificationBlueprint> unifiedStep = new FlowStep<>(
      prompt,
      "^.+$", // Accept any string to handle tokenization manually inside the parser block
      "Invalid entry format syntax.",
      input -> {
        Matcher matcher = BLUEPRINT_PARSER_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
          throw new IllegalArgumentException("Input format does not match required layout syntax configuration rules.");
        }

        // 1. Parse Interval Token (Can be index position number, fraction or math equation string)
        String intervalToken = matcher.group(1);
        if (intervalToken.matches("^\\d+$")) {
          int choice = Integer.parseInt(intervalToken);
          // Skip index 0 (which was the old custom token placeholder) and validate bounds
          if (choice <= 0 || choice >= Interval.values().length) {
            throw new IllegalArgumentException("Selected list index integer is out of bounds.");
          }
          blueprint.interval = Interval.values()[choice];
        } else {
          // It's a direct fraction or math formula sacada de la chistera!
          String cleanExpr = intervalToken.replaceAll("\\s+", "");
          double evaluated = Interval.evaluateMathExpression(cleanExpr);
          uiWindow.printToTerminal(String.format("   [Parsed] Expression resolved to a raw multiplier ratio of: %.4f", evaluated));
          blueprint.interval = new Interval("CHISTER_EXPR", cleanExpr);
        }

        // 2. Parse Inline Octave Shift Flag (-oct)
        if (matcher.group(2) != null) {
          String octaveToken = matcher.group(2);
          if (octaveToken.startsWith("+")) octaveToken = octaveToken.substring(1);
          blueprint.octaveShift = Integer.parseInt(octaveToken);
        } else {
          blueprint.octaveShift = 0; // Default fallback
        }

        // 3. Parse Inline Inversion Direction Flag (-inv)

        blueprint.inverted = (matcher.group(3) != null);
      });
    executeStep(unifiedStep);
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
    } uiWindow.printToTerminal("------------------------------------------");
  }

  private void printIntervalMenu() {
    uiWindow.printToTerminal("\n--- Available Proportional Just Intervals ---");
    Interval[] vals = Interval.values();
    for (int i = 1; i < vals.length; i++) {
      // Start at index 1 to omit the old CUSTOM placeholder item
      uiWindow.printToTerminal(String.format(" [%d] %s (Ratio: %s)", i, vals[i].name(), vals[i].getExpression()));
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