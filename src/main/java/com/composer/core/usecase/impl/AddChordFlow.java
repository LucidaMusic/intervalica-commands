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

    // Transactional structural states
    private String customChordName = ""; // NEW: Variable state to hold optional chord labels
    private String resolvedParentId = null;
    private Interval tonicInterval = Interval.PERFECT_UNISON;
    private int overtoneCount = 0;
    private final List<Interval> overtoneIntervals = new ArrayList<>();
    private Duration chordDuration = Duration.QUARTER;

    public AddChordFlow(CommandHistoryManager historyManager, SongMonitorWindow uiWindow) {
        this.historyManager = historyManager;
        this.uiWindow = uiWindow;
    }

    @Override
    public void execute(Song targetSong, String inlineArgument)
      throws FlowContext.CancelException, FlowContext.ExitException {

        overtoneIntervals.clear();
        resolvedParentId = null;
        customChordName = "";

        int chordIndex = targetSong.getNextChordIndex();
        String currentTonicId = "T" + chordIndex;

        uiWindow.printToTerminal("\n-------------------------------------------------------------");
        uiWindow.printToTerminal(String.format(" >>> GRAPH WORKSPACE WIZARD: Creating Chord %d (Tonic ID: %s) <<< ", chordIndex, currentTonicId));
        uiWindow.printToTerminal("-------------------------------------------------------------");

        // --- NEW STEP 0: CAPTURE OPTIONAL CHORD ALIAS LABEL ---
        uiWindow.printToTerminal("Enter a custom name/label for this chord (e.g., 'E from A' or leave empty for default): ");
        // We bypass standard regex check using empty pattern match verification to allow free text or pure enters
        String nameInput = uiWindow.readInputFromUI();
        this.customChordName = nameInput.trim();

        // Automation criteria for the first chord
        if (chordIndex == 1) {
            this.resolvedParentId = null;
            this.tonicInterval = Interval.PERFECT_UNISON;
            uiWindow.printToTerminal("-> First chord detected. Automatically anchoring Tonic to BASE frequency via PERFECT_UNISON (1/1).");
        } else {
            // STEP 1: Resolve Parent Node Reference
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
                      if (parentNode == null) {
                          throw new IllegalArgumentException("Node ID not found in active graph ecosystem.");
                      }
                      this.resolvedParentId = parentNode.getId();
                  }
              }
            );
            executeStep(stepRef);

            // STEP 2: Resolve Interval relation to parent
            printIntervalMenu();
            FlowStep<Interval> stepTonicInt = new FlowStep<>(
              "Select Interval Index from Parent Node to this Tonic: ",
              "^\\d+$",
              "Invalid index selection.",
              input -> {
                  int choice = Integer.parseInt(input);
                  if (choice < 0 || choice >= Interval.values().length) throw new IllegalArgumentException();
                  this.tonicInterval = Interval.values()[choice];
              }
            );
            executeStep(stepTonicInt);
        }

        // STEP 3: Request Overtone branch counts
        FlowStep<Integer> stepOvertonesCount = new FlowStep<>(
          "How many Overtones will branch out from this Tonic? (e.g., 2): ",
          "^\\d+$",
          "Please enter a valid positive integer.",
          input -> this.overtoneCount = Integer.parseInt(input)
        );
        executeStep(stepOvertonesCount);

        // STEP 4: Build Overtone instances
        if (overtoneCount > 0) {
            printIntervalMenu();
        }
        for (int i = 0; i < overtoneCount; i++) {
            final int overtoneIdx = i + 1;
            FlowStep<Interval> stepOvertone = new FlowStep<>(
              String.format(" -> Select Interval Index for Overtone #%d (Node ID: O%d_%d): ", overtoneIdx, chordIndex, overtoneIdx),
              "^\\d+$",
              "Invalid selection index.",
              input -> {
                  int choice = Integer.parseInt(input);
                  if (choice < 0 || choice >= Interval.values().length) throw new IllegalArgumentException();
                  overtoneIntervals.add(Interval.values()[choice]);
              }
            );
            executeStep(stepOvertone);
        }

        // STEP 5: Request structural duration profile
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

        // TRANSACTION ASSEMBLY PHASE
        Song.HarmonicNode rootNode = new Song.HarmonicNode(currentTonicId, resolvedParentId, tonicInterval, "ROOT");
        // UPDATED: Injected customChordName variable parameter string into constructor
        Song.Chord operationalChord = new Song.Chord("C" + chordIndex, customChordName, rootNode, chordDuration);

        for (int i = 0; i < overtoneIntervals.size(); i++) {
            String overtoneId = String.format("O%d_%d", chordIndex, i + 1);
            Song.HarmonicNode overtoneNode = new Song.HarmonicNode(overtoneId, currentTonicId, overtoneIntervals.get(i), "OVERTONE");
            operationalChord.getOvertones().add(overtoneNode);
        }

        // Commit via Command History tracking pipeline
        AddChordCommand cmd = new AddChordCommand(targetSong, operationalChord);
        historyManager.executeCommand(cmd);

        uiWindow.printToTerminal("\n[Success] Relational Chord network structuralized and injected into DAG.");
        uiWindow.printToTerminal("-------------------------------------------------------------");
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
            uiWindow.printToTerminal(String.format(" [%2d] %s (Ratio: %s)", i, vals[i].name(), vals[i].getExpression()));
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
