package org.intervalica.infrastructure.cli;




import org.intervalica.core.domain.model.*;
import org.intervalica.core.domain.types.Duration;
import org.intervalica.core.domain.types.Interval;
import org.intervalica.core.usecase.base.FlowContext;
import org.intervalica.core.usecase.impl.AddChordFlow;
import org.intervalica.infrastructure.audio.AudioSynthesizerEngine;
import org.intervalica.infrastructure.ui.SongMonitorWindow;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandRouter {
  private final List<RegisteredRoute> registry = new ArrayList<>();
  private final Song song;
  private final CommandHistoryManager historyManager;
  private final SongMonitorWindow uiWindow;

  private static final Pattern SPEC_MODIFIER_PATTERN = Pattern.compile(
    "^(?:-custom\\s+\"([^\"]+)\"|([^\\s-]+))(?:\\s+-oct\\s+([+-]?\\d+))?(?:\\s+(-inv))?$",
    Pattern.CASE_INSENSITIVE
  );

  public CommandRouter(Song song, CommandHistoryManager historyManager, SongMonitorWindow uiWindow) {
    this.song = song;
    this.historyManager = historyManager;
    this.uiWindow = uiWindow;
    registerSystemRoutes();
  }

  private void registerSystemRoutes() {
    // Core Creation Flows
    registry.add(new RegisteredRoute(
      "^add[- ]chord(?:\\s+-name\\s+\"([^\"]+)\")?(?:\\s+(\\d+))?$", "add-chord -name \"<alias>\" <count>",
      "Launches composition flow with an optional name tag shortcut option.",
      (_, matcher, s, h, ui) -> new AddChordFlow(h, ui, matcher.group(1)).execute(s, matcher.group(2))
    ));

    // --- NEW ROUTE: ADD OVERTONE TO EXISTING CHORD ---
    registry.add(new RegisteredRoute(
      "^add[- ]overtone\\s+([a-zA-Z0-9_]+)$", "add-overtone <chord-id>", "Appends a new individual overtone node into an existing active chord block.",
      (_, matcher, s, h, ui) -> {
        String chordId = matcher.group(1).trim();
        Song.Chord chord = s.findChordById(chordId);
        if (chord == null) {
          ui.printToTerminal("[Error] Targeted Chord ID not found.");
          return;
        }

        // Display mnemonic specification guidelines
        ui.printToTerminal("\n=================== CHORD ADDITION SPECIFICATION LEGEND ===================");
        for (Interval interval : Interval.getSystemIntervals()) {
          ui.printToTerminal(String.format("  • %-18s -> Accepted IDs: %s", interval.name(), Arrays.toString(interval.getAliases())));
        }
        ui.printToTerminal(" Enter Overtone Specs (e.g., '5j -oct +1' or '-custom \"4/3\"'): ");

        String rawSpec = ui.readInputFromUI();
        Matcher specMatcher = SPEC_MODIFIER_PATTERN.matcher(rawSpec.trim());
        if (!specMatcher.matches()) {
          ui.printToTerminal("[Error] Malformed property specification tokens layout.");
          return;
        }

        Interval targetInterval;
        if (specMatcher.group(1) != null) {
          targetInterval = new Interval("CUSTOM_EXPR", specMatcher.group(1).replaceAll("\\s+", ""));
        } else {
          targetInterval = Interval.fromAlias(specMatcher.group(2));
          if (targetInterval == null) {
            ui.printToTerminal("[Error] Mnemonic interval alias unrecognized.");
            return;
          }
        }

        int octave = 0;
        if (specMatcher.group(3) != null) {
          String tok = specMatcher.group(3);
          octave = Integer.parseInt(tok.startsWith("+") ? tok.substring(1) : tok);
        }
        boolean inv = (specMatcher.group(4) != null);

        // Compute automatic incremental ID matching layout positioning (e.g. O1_4)
        String numericPart = chord.getChordId().replaceAll("\\D+", "");
        String overtoneId = String.format("O%s_%d", numericPart, chord.getOvertones().size() + 1);

        Song.HarmonicNode newOvertone = new Song.HarmonicNode(overtoneId, chord.getRootNode().getId(), targetInterval, octave, inv, "OVERTONE");
        AddOvertoneCommand cmd = new AddOvertoneCommand(s, chord, newOvertone);
        h.executeCommand(cmd);
        ui.printToTerminal(String.format("[Success] Dynamic overtone Node %s successfully injected into Chord %s.", overtoneId, chordId));
      }
    ));

    // --- NEW ROUTE: REMOVE SINGLE OVERTONE ---
    registry.add(new RegisteredRoute(
      "^remove[- ]overtone\\s+([a-zA-Z0-9_]+)$", "remove-overtone <overtone-id>", "Deletes a single isolated overtone node if no future branches anchor onto it.",
      (_, matcher, s, h, ui) -> {
        String overtoneId = matcher.group(1).trim();
        Song.HarmonicNode node = s.findNodeById(overtoneId);
        if (node == null || !node.getLabel().equals("OVERTONE")) {
          ui.printToTerminal("[Error] Valid target Overtone ID not found.");
          return;
        }

        // Integrity Check
        List<String> blocks = s.verifyOvertoneDeletionSafety(overtoneId);
        if (!blocks.isEmpty()) {
          ui.printToTerminal(String.format("[BLOCKING ERROR] Cannot delete overtone %s. Subsequent branches rely on its value: %s", overtoneId, blocks));
          return;
        }

        Song.Chord parentChord = s.findChordByOvertoneId(overtoneId);
        RemoveOvertoneCommand cmd = new RemoveOvertoneCommand(s, parentChord, node);
        h.executeCommand(cmd);
        ui.printToTerminal(String.format("[Success] Overtone %s removed cleanly from %s.", overtoneId, parentChord.getChordId()));
      }
    ));

    // --- NEW ROUTE: CLEAR SONG SCOPE ---
    registry.add(new RegisteredRoute(
      "^clear$", "clear", "Wipes all chord progressions from active workspace memory cleanly.",
      (input, matcher, s, h, ui) -> {
        if (s.getChords().isEmpty()) {
          ui.printToTerminal("[Warning] Workspace environment is already clear.");
          return;
        }
        ClearSongCommand cmd = new ClearSongCommand(s);
        h.executeCommand(cmd);
        ui.printToTerminal("[Success] Score workspace canvas wiped successfully. Undo is available.");
      }
    ));

    // Deletion & Modification Matrix
    registry.add(new RegisteredRoute("^remove[- ]chord\\s+([a-zA-Z0-9_]+)$", "remove-chord <chord-id>", "Deletes an entire chord block if no subsequent nodes depend on it.", (input, matcher, s, h, ui) -> {
      String targetId = matcher.group(1).trim();
      Song.Chord targetChord = s.findChordById(targetId);
      if (targetChord == null) {
        ui.printToTerminal("[Error] Chord ID not found.");
        return;
      }
      List<String> blocks = s.verifyDeletionSafety(targetId);
      if (!blocks.isEmpty()) {
        ui.printToTerminal(String.format("[BLOCKING ERROR] Cannot delete %s. Dependencies found: %s", targetId, blocks));
        return;
      }
      RemoveChordCommand cmd = new RemoveChordCommand(s, targetChord);
      h.executeCommand(cmd);
      ui.printToTerminal(String.format("[Success] Chord %s deleted safely.", targetId));
    }));
    registry.add(new RegisteredRoute("^edit[- ]chord\\s+([a-zA-Z0-9_]+)(?:\\s+-name\\s+\"([^\"]+)\")?(?:\\s+-duration\\s+([^\\s]+))?$", "edit-chord <chord-id> -name \"<text>\" -duration <alias>", "Edits descriptive metadata fields of an existing chord safely.", (input, matcher, s, h, ui) -> {
      String id = matcher.group(1);
      Song.Chord chord = s.findChordById(id);
      if (chord == null) {
        ui.printToTerminal("[Error] Chord not found.");
        return;
      }
      String targetName = (matcher.group(2) != null) ? matcher.group(2) : chord.getName();
      Duration targetDur = chord.getDuration();
      if (matcher.group(3) != null) {
        Duration resolved = Duration.fromAlias(matcher.group(3));
        if (resolved == null) {
          ui.printToTerminal("[Error] Duration token unregistered.");
          return;
        }
        targetDur = resolved;
      }
      EditChordCommand cmd = new EditChordCommand(chord, targetName, targetDur);
      h.executeCommand(cmd);
      ui.printToTerminal(String.format("[Success] Chord %s properties modified successfully.", id));
    }));

    registry.add(new RegisteredRoute("^edit[- ]node\\s+([a-zA-Z0-9_]+)\\s+-ref\\s+([^\\s]+)\\s+-properties\\s+(.+)$", "edit-node  -ref  -properties ", "Mutates node wiring vectors checking directional chronological integrity.", (input, matcher, s, h, ui) -> {
      String targetNodeId = matcher.group(1);
      String rawParentId = matcher.group(2);
      String flagsBlock = matcher.group(3).trim();
      Song.HarmonicNode node = s.findNodeById(targetNodeId);
      if (node == null) {
        ui.printToTerminal("[Error] Target Node ID not found.");
        return;
      }
      String finalParentId = rawParentId.equalsIgnoreCase("BASE") ? null : rawParentId;
      if (finalParentId != null) {
        Song.HarmonicNode testParent = s.findNodeById(finalParentId);
        if (testParent == null) {
          ui.printToTerminal("[Error] Parent anchor ID does not exist.");
          return;
        }
        if (node.getLabel().equals("ROOT")) {
          String currentChordId = "C" + targetNodeId.replaceAll("\\D+", "");
          int currentChordChronologicalPos = s.getChordChronologicalIndex(currentChordId);
          String parentChordId = "C" + finalParentId.replaceAll("\\D+", "");
          int parentChordChronologicalPos = s.getChordChronologicalIndex(parentChordId);
          if (parentChordChronologicalPos >= currentChordChronologicalPos) {
            ui.printToTerminal("[INTEGRITY ERROR] Chronological Violation: A Tonic node can ONLY anchor to parent nodes living inside PREVIOUS chords.");
            return;
          }
        } else {
          if (!finalParentId.equalsIgnoreCase("T" + targetNodeId.replaceAll("^O(\\d+)_\\d+$", "$1"))) {
            ui.printToTerminal("[INTEGRITY WARNING] Overtones are bounded to reference their own internal local Tonic node.");
            return;
          }
        }
      }
      Matcher flagMatcher = SPEC_MODIFIER_PATTERN.matcher(flagsBlock);
      if (!flagMatcher.matches()) {
        ui.printToTerminal("[Error] Malformed property specs flag tokens syntax layout.");
        return;
      }
      Interval targetInterval;
      if (flagMatcher.group(1) != null) {
        targetInterval = new Interval("CUSTOM_EXPR", flagMatcher.group(1).replaceAll("\\s+", ""));
      } else {
        targetInterval = Interval.fromAlias(flagMatcher.group(2));
        if (targetInterval == null) {
          ui.printToTerminal("[Error] Mnemonic interval alias unrecognized.");
          return;
        }
      }
      int octave = 0;
      if (flagMatcher.group(3) != null) {
        String tok = flagMatcher.group(3);
        octave = Integer.parseInt(tok.startsWith("+") ? tok.substring(1) : tok);
      }
      boolean inv = (flagMatcher.group(4) != null);
      EditNodeCommand cmd = new EditNodeCommand(node, finalParentId, targetInterval, octave, inv);
      h.executeCommand(cmd);
      ui.printToTerminal(String.format("[Success] Node %s cascading vectors recalculated perfectly.", targetNodeId));
    }));// System Settings Utilities
    registry.add(new RegisteredRoute("^undo$", "undo", "Reverts last committed modifications.", (input, matcher, s, h, ui) -> h.undo(ui)));
    registry.add(new RegisteredRoute("^redo$", "redo", "Re-applies reverted sequence.", (input, matcher, s, h, ui) -> h.redo(ui)));
    registry.add(new RegisteredRoute("^set-title\\s+(.+)$", "set-title ", "Updates the score title metadata.", (input, matcher, s, h, ui) -> {
      s.setTitle(matcher.group(1).trim());
      ui.printToTerminal("[Metadata] Title set successfully.");
    }));
    registry.add(new RegisteredRoute("^set-author\\s+(.+)$", "set-author ", "Updates the score composer name.", (input, matcher, s, h, ui) -> {
      s.setAuthor(matcher.group(1).trim());
      ui.printToTerminal("[Metadata] Composer author set successfully.");
    }));
    registry.add(new RegisteredRoute("^set-bpm\\s+(\\d+)$", "set-bpm ", "Changes the musical playback tempo profile.", (input, matcher, s, h, ui) -> {
      int val = Integer.parseInt(matcher.group(1));
      if (val <= 0) {
        ui.printToTerminal("[Error] BPM must be greater than 0.");
        return;
      }
      s.setBpm(val);
      ui.printToTerminal("[Metadata] Tempo updated to " + val + " BPM.");
    }));
    registry.add(new RegisteredRoute("^set-freq\\s+(\\d+(?:\\.\\d+)?)$", "set-freq ", "Alters the baseline reference frequency tuning anchor.", (input, matcher, s, h, ui) -> {
      double val = Double.parseDouble(matcher.group(1));
      if (val <= 0) {
        ui.printToTerminal("[Error] Frequency must be greater than 0.");
        return;
      }
      s.setReferenceFrequency(val);
      ui.printToTerminal("[Metadata] Reference base frequency tuning set to " + val + " Hz.");
    }));
    registry.add(new RegisteredRoute("^save$", "save", "Opens native dialogue window to serialize current score directly to disk.", (input, matcher, s, h, ui) -> handleNativeSave()));
    registry.add(new RegisteredRoute("^load$", "load", "Opens native dialogue window to deserialize and populate workspace session.", (input, matcher, s, h, ui) -> handleNativeLoad()));// Playback Engine Vectors
    registry.add(new RegisteredRoute("^play\\s+-node\\s+([a-zA-Z0-9_]+)$", "play -node ", "Auditions a single specific node (Tonic or Overtone) during 1 beat.", (input, matcher, s, h, ui) -> {
      String nodeId = matcher.group(1);
      Song.HarmonicNode node = s.findNodeById(nodeId);
      if (node == null) {
        ui.printToTerminal("[Audio Error] Target Node ID not found.");
        return;
      }
      Map<String, Double> freqs = s.computeFrequencies();
      double frequency = freqs.getOrDefault(node.getId(), 0.0);
      ui.printToTerminal(String.format("[Audio Engine] Auditioning Node %s -> %.2f Hz", node.getId(), frequency));
      AudioSynthesizerEngine.playFrequencies(List.of(frequency), 60.0 / s.getBpm());
    }));
    registry.add(new RegisteredRoute("^play\\s+-chord\\s+([a-zA-Z0-9_]+)$", "play -chord ", "Auditions a single specific Chord block directly utilizing its assigned duration.", (input, matcher, s, h, ui) -> {
      String chordId = matcher.group(1);
      Song.Chord targetChord = null;
      for (Song.Chord c : s.getChords()) {
        if (c.getChordId().equalsIgnoreCase(chordId)) {
          targetChord = c;
          break;
        }
      }
      if (targetChord == null) {
        ui.printToTerminal("[Audio Error] Chord ID not found.");
        return;
      }
      playSingleChordInstance(targetChord, s);
    }));
    registry.add(new RegisteredRoute("^play\\s+-range\\s+([a-zA-Z0-9_]+)\\s+([a-zA-Z0-9_]+)$", "play -range  ", "Plays a specific subsection fragment of the score.", (input, matcher, s, h, ui) -> {
      String startId = matcher.group(1);
      String endId = matcher.group(2);
      List<Song.Chord> allChords = s.getChords();
      int startIdx = -1, endIdx = -1;
      for (int i = 0; i < allChords.size(); i++) {
        if (allChords.get(i).getChordId().equalsIgnoreCase(startId)) startIdx = i;
        if (allChords.get(i).getChordId().equalsIgnoreCase(endId)) endIdx = i;
      }
      if (startIdx == -1 || endIdx == -1 || startIdx > endIdx) {
        ui.printToTerminal("[Audio Error] Invalid boundary range.");
        return;
      }
      for (int i = startIdx; i <= endIdx; i++) playSingleChordInstance(allChords.get(i), s);
    }));
    registry.add(new RegisteredRoute("^play$", "play", "Plays the entire composition score timeline from the beginning respecting BPM rules.", (input, matcher, s, h, ui) -> {
      List<Song.Chord> allChords = s.getChords();
      if (allChords.isEmpty()) {
        ui.printToTerminal("[Audio Warning] Score layout is empty.");
        return;
      }
      for (Song.Chord c : allChords) playSingleChordInstance(c, s);
    }));
    registry.add(new RegisteredRoute("^help$", "help", "Generates this interactive routing matrix menu dynamically.", (input, matcher, s, h, ui) -> printHelp()));
  }

  private void playSingleChordInstance(Song.Chord chord, Song currentSong) {
    Map<String, Double> activeFreqs = currentSong.computeFrequencies();
    List<Double> chordFrequenciesCluster = new ArrayList<>();
    chordFrequenciesCluster.add(activeFreqs.getOrDefault(chord.getRootNode().getId(), 0.0));
    for (Song.HarmonicNode overtone : chord.getOvertones()) {
      chordFrequenciesCluster.add(activeFreqs.getOrDefault(overtone.getId(), 0.0));
    }
    double secondsPerBeat = 60.0 / currentSong.getBpm();
    double chordDurationInSeconds = secondsPerBeat * chord.getDuration().beatsValue();
    AudioSynthesizerEngine.playFrequencies(chordFrequenciesCluster, chordDurationInSeconds);
  }

  public boolean isRootCommand(String input) {
    String trimmed = input.trim();
    for (RegisteredRoute route : registry) {
      if (route.pattern().matcher(trimmed).matches()) return true;
    }
    String lower = trimmed.toLowerCase();
    return lower.equals("cancel") || lower.equals("exit") || lower.equals("quit");
  }

  public void handleCommand(String rawCommandLine) throws FlowContext.ExitException, FlowContext.CancelException, FlowContext.CancelException {
    String trimmed = rawCommandLine.trim();
    for (RegisteredRoute route : registry) {
      Matcher matcher = route.pattern().matcher(trimmed);
      if (matcher.matches()) {
        route.action().ActionExecutor(trimmed, matcher, song, historyManager, uiWindow);
        return;
      }
    }
    uiWindow.printToTerminal("[Error] Command context mismatch. Type 'help' to audit syntax options.");
  }

  private void handleNativeSave() {
    SwingUtilities.invokeLater(() -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Save Your Score Blueprint Location");
      FileNameExtensionFilter filter = new FileNameExtensionFilter("Musical Score Files (*.score)", "score");
      fileChooser.setFileFilter(filter);
      int userSelection = fileChooser.showSaveDialog(uiWindow);
      if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToSave = fileChooser.getSelectedFile();
        String filePath = fileToSave.getAbsolutePath();
        if (!filePath.toLowerCase().endsWith(".score")) filePath += ".score";
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
          oos.writeObject(song);
          uiWindow.printToTerminal("[Success] Score metadata successfully written onto: " + filePath);
        } catch (IOException e) {
          uiWindow.printToTerminal("[IO Error] Failed to write file stream: " + e.getMessage());
        }
      }
    });
  }

  private void handleNativeLoad() {
    SwingUtilities.invokeLater(() -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Open Existing Score Blueprint File");
      FileNameExtensionFilter filter = new FileNameExtensionFilter("Musical Score Files (*.score)", "score");
      fileChooser.setFileFilter(filter);
      int userSelection = fileChooser.showOpenDialog(uiWindow);
      if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToLoad = fileChooser.getSelectedFile();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileToLoad))) {
          Song loadedSong = (Song) ois.readObject();
          song.copyFrom(loadedSong);
          uiWindow.printToTerminal("[Success] Score layout session state mounted from: " + fileToLoad.getName());
        } catch (Exception e) {
          uiWindow.printToTerminal("[Format Error] Could not parse file layout footprint: " + e.getMessage());
        }
      }
    });
  }

  private void printHelp() {
    uiWindow.printToTerminal("\n=== Interactive CLI Studio Matrix Dynamic Help ===");
    for (RegisteredRoute route : registry) {
      uiWindow.printToTerminal(String.format(" -> %-45s %s", route.syntaxHelp(), route.descriptionHelp()));
    }
    uiWindow.printToTerminal(String.format(" -> %-45s %s", "cancel", "Aborts current active sequence flow immediately."));
    uiWindow.printToTerminal(String.format(" -> %-45s %s", "exit / quit", "Terminates application execution context cleanly."));
    uiWindow.printToTerminal("===================================================\n");
  }
}