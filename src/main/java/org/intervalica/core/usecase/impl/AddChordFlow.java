package org.intervalica.core.usecase.impl;


import org.intervalica.core.domain.model.AddChordCommand;
import org.intervalica.core.domain.model.CommandHistoryManager;
import org.intervalica.core.domain.model.Song;
import org.intervalica.core.domain.types.Duration;
import org.intervalica.core.domain.types.Interval;
import org.intervalica.core.usecase.base.FlowContext;
import org.intervalica.core.usecase.base.FlowStep;
import org.intervalica.core.usecase.base.TransactionalFlow;
import org.intervalica.infrastructure.ui.SongMonitorWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddChordFlow implements TransactionalFlow {

  private final CommandHistoryManager historyManager;
  private final SongMonitorWindow uiWindow;
  private final String customChordName;

  private String resolvedParentId = null;
  private int overtoneCount = 0;

  private static class NodeSpecificationBlueprint {
    Interval interval;
    int octaveShift = 0;
    boolean inverted = false;
  }

  private final NodeSpecificationBlueprint tonicBlueprint = new NodeSpecificationBlueprint();
  private final List<NodeSpecificationBlueprint> overtonesBlueprints = new ArrayList<>();

  private static final Pattern SPEC_PARSER_PATTERN = Pattern.compile(
    "^(?:-custom\\s+\"([^\"]+)\"|([^\\s-]+))(?:\\s+-oct\\s+([+-]?\\d+))?(?:\\s+(-inv))?$",
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
    uiWindow.printToTerminal(String.format(" >>> MNEMONIC GRAPH WORKSPACE: Designing Chord %d (%s) <<< ", chordIndex, currentTonicId));
    if (customChordName != null && !customChordName.isBlank()) {
      uiWindow.printToTerminal(" -> Meta Tag Alias Assigned: \"" + customChordName + "\"");
    }
    uiWindow.printToTerminal("----------------------------------------------------------------------");

    boolean hasInlineCount = (inlineArgument != null && inlineArgument.matches("^\\d+$"));
    if (hasInlineCount) {
      this.overtoneCount = Integer.parseInt(inlineArgument);
      uiWindow.printToTerminal("-> Overtone payload density pre-configured to: " + this.overtoneCount);
    }

    // STEP 1: Resolve Tonic Root Reference
    if (chordIndex == 1) {
      this.resolvedParentId = null;
      this.tonicBlueprint.interval = Interval.PERFECT_UNISON;
      this.tonicBlueprint.octaveShift = 0;
      this.tonicBlueprint.inverted = false;
      uiWindow.printToTerminal("-> First chord automated default anchor: absolute BASE frequency via PERFECT_UNISON (1/1).");
    } else {
      printAvailableNodesMenu(targetSong);
      FlowStep stepRef = new FlowStep(
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
      printIntervalMenuWithFlags();
      executeSpecificationWorkflowStep("Configure Tonic (e.g., '2M' or '-custom \"(3/2)*2\" -oct -1'): ", this.tonicBlueprint);
    }

    // STEP 2: Request Overtone branch counts
    if (!hasInlineCount) {
      FlowStep stepOvertonesCount = new FlowStep(
        "How many Overtones will branch out from this Tonic? (e.g., 2): ",
        "^\\d+$",
        "Please enter a valid positive integer.",
        input -> this.overtoneCount = Integer.parseInt(input)
      );
      executeStep(stepOvertonesCount);
    }

    // STEP 3: Build individual Overtone specifications
    if (overtoneCount > 0) {
      printIntervalMenuWithFlags();
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

    // --- REFACTORED STEP 4: MNEMONIC DURATION CAPTURE ---
    printDurationMenuWithAliases();
    FlowStep stepDuration = new FlowStep(
      "Select Chord Note Duration Figure (e.g., '2', '0.5', 'quarter', '8th'): ",
      "^.+$", // Accepts free token formats to evaluate inside the functional lambda block
      "Unregistered duration token.",
      input -> {
        Duration resolvedDuration = Duration.fromAlias(input.trim());
        if (resolvedDuration == null) {
          throw new IllegalArgumentException(String.format("The duration symbol/value '%s' is not registered inside the workspace catalog.", input));
        }
        // Now maps onto the rich entity instance
      }
    );
    executeStep(stepDuration);

    // SYSTEM DAG ASSEMBLY PHASE
    Song.HarmonicNode rootNode = new Song.HarmonicNode(
      currentTonicId, resolvedParentId, tonicBlueprint.interval,
      tonicBlueprint.octaveShift, tonicBlueprint.inverted, "ROOT"
    );
    Duration chordDuration = Duration.QUARTER;
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

  private void executeSpecificationWorkflowStep(String prompt, NodeSpecificationBlueprint blueprint) throws FlowContext.CancelException, FlowContext.ExitException {
    FlowStep unifiedStep = new FlowStep(
      prompt,
      "^.+$",
      "Invalid entry layout pattern signature syntax.",
      input -> {
        Matcher matcher = SPEC_PARSER_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
          throw new IllegalArgumentException("Syntax error. Use: '<alias>' or '-custom \"expr\"' with optional modifiers.");
        }
        String customExprToken = matcher.group(1);
        String standardAliasToken = matcher.group(2);

        if (customExprToken != null) {
          String cleanExpr = customExprToken.replaceAll("\\s+", "");
          double evaluated = Interval.evaluateMathExpression(cleanExpr);
          uiWindow.printToTerminal(String.format("   [Parsed] -custom expression resolved to ratio multiplier: %.4f", evaluated));
          blueprint.interval = new Interval("CUSTOM_EXPR", cleanExpr);
        } else if (standardAliasToken != null) {
          Interval resolved = Interval.fromAlias(standardAliasToken);
          if (resolved == null)
            throw new IllegalArgumentException(String.format("Mnemonic alias '%s' is not registered.", standardAliasToken));
          blueprint.interval = resolved;
        }

        if (matcher.group(3) != null) {
          String octaveToken = matcher.group(3);
          if (octaveToken.startsWith("+")) octaveToken = octaveToken.substring(1);
          blueprint.octaveShift = Integer.parseInt(octaveToken);
        } else {
          blueprint.octaveShift = 0;
        }
        blueprint.inverted = (matcher.group(4) != null);
      }
    );
    executeStep(unifiedStep);
  }

  private void executeStep(FlowStep step) throws FlowContext.CancelException, FlowContext.ExitException {
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
        uiWindow.printToTerminal("   [!] ERROR: " + e.getLocalizedMessage());
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

  private void printIntervalMenuWithFlags() {
    uiWindow.printToTerminal("\n=================== SCORE INPUT SPECIFICATION LEGEND ===================");
    uiWindow.printToTerminal(" Available Registered Mnemonic Aliases:");
    for (Interval interval : Interval.getSystemIntervals()) {
      uiWindow.printToTerminal(String.format("  • %-18s (Ratio: %-6s) -> Accepted IDs: %s", interval.name(), interval.getExpression(), Arrays.toString(interval.getAliases())));
    }
    uiWindow.printToTerminal("------------------------------------------------------------------------");
    uiWindow.printToTerminal(" Inline Flag Modifiers:");
    uiWindow.printToTerminal("  -> -oct     Shifts octaves up or down (e.g., '-oct +2', '-oct -1'). Default: 0.");
    uiWindow.printToTerminal("  -> -inv           Inverts the interval direction down (reciprocal calculation).");
    uiWindow.printToTerminal("  -> -custom \"expr\" Bypasses catalog to process custom math equations (e.g., -custom \" (3 / 2) * 4 \")");
    uiWindow.printToTerminal("========================================================================\n");
  }

  /*** NEW RICH NOTE INTERACTIVE MENU* Documents traditional notation glyphs, exact beat counts, and string identifiers.*/
  private void printDurationMenuWithAliases() {
    uiWindow.printToTerminal("\n=================== NOTE FIGURE BLUEPRINT MENU ===================");
    for (Duration d : Duration.getSystemDurations()) {
      uiWindow.printToTerminal(String.format("  • %-30s -> Mnemonic Trigger Keys: %s", d.visualIcon(), Arrays.toString(d.aliases())));
    }
    uiWindow.printToTerminal("==================================================================\n");
  }
}