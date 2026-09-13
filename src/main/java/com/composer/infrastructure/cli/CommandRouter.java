package src.main.java.com.composer.infrastructure.cli;

import src.main.java.com.composer.core.domain.model.CommandHistoryManager;
import src.main.java.com.composer.core.domain.model.Song;
import src.main.java.com.composer.core.usecase.base.FlowContext;
import src.main.java.com.composer.core.usecase.impl.AddChordFlow;
import src.main.java.com.composer.infrastructure.audio.AudioSynthesizerEngine;
import src.main.java.com.composer.infrastructure.ui.SongMonitorWindow;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class CommandRouter {
  private final List<RegisteredRoute> registry = new ArrayList<>();
  private final Song song;
  private final CommandHistoryManager historyManager;
  private final SongMonitorWindow uiWindow;

  public CommandRouter(Song song, CommandHistoryManager historyManager, SongMonitorWindow uiWindow) {
    this.song = song;
    this.historyManager = historyManager;
    this.uiWindow = uiWindow;
    registerSystemRoutes();
  }

  private void registerSystemRoutes() {
    // 1. Core Structural Pipeline Flows
    registry.add(new RegisteredRoute(
      "^add[- ]chord(?:\\s+-name\\s+\"([^\"]+)\")?(?:\\s+(\\d+))?$",
      "add-chord -name \"<alias>\" <count>",
      "Launches composition flow with an optional name tag shortcut option.",
      (input, matcher, s, h, ui) -> new AddChordFlow(h, ui, matcher.group(1)).execute(s, matcher.group(2))
    ));

    // 2. Timeline Commands
    registry.add(new RegisteredRoute("^undo$", "undo", "Reverts last committed modifications.", (input, matcher, s, h, ui) -> h.undo(ui)));
    registry.add(new RegisteredRoute("^redo$", "redo", "Re-applies reverted sequence.", (input, matcher, s, h, ui) -> h.redo(ui)));

    // 3. Metadata Configuration Engine
    registry.add(new RegisteredRoute("^set-title\\s+(.+)$", "set-title <text>", "Updates the score title metadata.", (input, matcher, s, h, ui) -> {
      s.setTitle(matcher.group(1).trim());
      ui.printToTerminal("[Metadata] Title set successfully.");
    }));
    registry.add(new RegisteredRoute("^set-author\\s+(.+)$", "set-author <text>", "Updates the score composer name.", (input, matcher, s, h, ui) -> {
      s.setAuthor(matcher.group(1).trim());
      ui.printToTerminal("[Metadata] Composer author set successfully.");
    }));
    registry.add(new RegisteredRoute("^set-bpm\\s+(\\d+)$", "set-bpm <integer>", "Changes the musical playback tempo profile.", (input, matcher, s, h, ui) -> {
      int val = Integer.parseInt(matcher.group(1));
      if (val <= 0) {
        ui.printToTerminal("[Error] BPM must be greater than 0.");
        return;
      }
      s.setBpm(val);
      ui.printToTerminal("[Metadata] Tempo updated to " + val + " BPM.");
    }));
    registry.add(new RegisteredRoute("^set-freq\\s+(\\d+(?:\\.\\d+)?)$", "set-freq <decimal>", "Alters the baseline reference frequency tuning anchor.", (input, matcher, s, h, ui) -> {
      double val = Double.parseDouble(matcher.group(1));
      if (val <= 0) {
        ui.printToTerminal("[Error] Frequency must be greater than 0.");
        return;
      }
      s.setReferenceFrequency(val);
      ui.printToTerminal("[Metadata] Reference base frequency tuning set to " + val + " Hz.");
    }));

    // 4. State Persistence Engine
    registry.add(new RegisteredRoute("^save$", "save", "Opens native dialogue window to serialize current score directly to disk.", (input, matcher, s, h, ui) -> handleNativeSave()));
    registry.add(new RegisteredRoute("^load$", "load", "Opens native dialogue window to deserialize and populate workspace session.", (input, matcher, s, h, ui) -> handleNativeLoad()));

    // --- NEW CHANNELS: MULTI-OPTION PLAYBACK AUDIO SYNTHESIZER ROUTING BLOCKS ---

    // ROUTE A: Play specific isolated node footprint (play -node T1, play -node O1_1)
    registry.add(new RegisteredRoute(
      "^play\\s+-node\\s+([a-zA-Z0-9_]+)$", "play -node <id>", "Auditions a single specific node (Tonic or Overtone) during 1 beat.",
      (input, matcher, s, h, ui) -> {
        String nodeId = matcher.group(1);
        Song.HarmonicNode node = s.findNodeById(nodeId);
        if (node == null) {
          ui.printToTerminal("[Audio Error] Target Node ID not found in graph matrix.");
          return;
        }
        Map<String, Double> freqs = s.computeFrequencies();
        double frequency = freqs.getOrDefault(node.getId(), 0.0);
        ui.printToTerminal(String.format("[Audio Engine] Auditioning isolated Node %s -> %.2f Hz (1 Beat)", node.getId(), frequency));
        double secondsPerBeat = 60.0 / s.getBpm();
        AudioSynthesizerEngine.playFrequencies(List.of(frequency), secondsPerBeat);
      }
    ));

    // ROUTE B: Play specific isolated chord blueprint block (play -chord C1)
    registry.add(new RegisteredRoute(
      "^play\\s+-chord\\s+([a-zA-Z0-9_]+)$", "play -chord <id>", "Auditions a single specific Chord block directly utilizing its assigned duration.",
      (input, matcher, s, h, ui) -> {
        String chordId = matcher.group(1);
        Song.Chord targetChord = null;
        for (Song.Chord c : s.getChords()) {
          if (c.getChordId().equalsIgnoreCase(chordId)) {
            targetChord = c;
            break;
          }
        }
        if (targetChord == null) {
          ui.printToTerminal("[Audio Error] Target Chord ID not found.");
          return;
        }
        ui.printToTerminal(String.format("[Audio Engine] Auditioning isolated Chord %s (\"%s\")", targetChord.getChordId(), targetChord.getName()));
        playSingleChordInstance(targetChord, s);
      }
    ));

    // ROUTE C: Play a custom bounded fragment range sequence (play -range C1 C3)
    registry.add(new RegisteredRoute(
      "^play\\s+-range\\s+([a-zA-Z0-9_]+)\\s+([a-zA-Z0-9_]+)$", "play -range <start-id> <end-id>", "Plays a specific subsection fragment of the score.",
      (input, matcher, s, h, ui) -> {
        String startId = matcher.group(1);
        String endId = matcher.group(2);
        List<Song.Chord> allChords = s.getChords();
        int startIdx = -1, endIdx = -1;
        for (int i = 0; i < allChords.size(); i++) {
          if (allChords.get(i).getChordId().equalsIgnoreCase(startId)) startIdx = i;
          if (allChords.get(i).getChordId().equalsIgnoreCase(endId)) endIdx = i;
        }
        if (startIdx == -1 || endIdx == -1 || startIdx > endIdx) {
          ui.printToTerminal("[Audio Error] Invalid subset selection metrics boundary range specified.");
          return;
        }
        ui.printToTerminal(String.format("[Audio Engine] Stream playback started for Range [%s -> %s]...", startId, endId));
        for (int i = startIdx; i <= endIdx; i++) {
          playSingleChordInstance(allChords.get(i), s);
        }
        ui.printToTerminal("[Audio Engine] Range subsection stream completed.");
      }
    ));

    // ROUTE D: Play the entire structural macro song graph network from scratch (play)
    registry.add(new RegisteredRoute(
      "^play$", "play", "Plays the entire composition score timeline from the beginning respecting BPM rules.",
      (input, matcher, s, h, ui) -> {
        List<Song.Chord> allChords = s.getChords();
        if (allChords.isEmpty()) {
          ui.printToTerminal("[Audio Warning] Score layout is completely empty. Add chords first.");
          return;
        }
        ui.printToTerminal(String.format("[Audio Engine] Initializing playback thread for entire score timeline: \"%s\"...", s.getTitle()));
        for (Song.Chord c : allChords) {
          playSingleChordInstance(c, s);
        }
        ui.printToTerminal("[Audio Engine] Complete playback score timeline finished.");
      }
    ));

    // 5. General Utilities
    registry.add(new RegisteredRoute("^help$", "help", "Generates this interactive routing matrix menu dynamically.", (input, matcher, s, h, ui) -> printHelp()));
  }

  /**
   * PRIVATE AUDIO UTILITY HELPER
   * Extracts exact active node child cluster frequencies, maps time calculations, and triggers audio lines.
   */
  private void playSingleChordInstance(Song.Chord chord, Song currentSong) {
    Map<String, Double> activeFreqs = currentSong.computeFrequencies();
    List<Double> chordFrequenciesCluster = new ArrayList<>();

    // Collect Tonic Hz
    chordFrequenciesCluster.add(activeFreqs.getOrDefault(chord.getRootNode().getId(), 0.0));
    // Collect Overtones Hz
    for (Song.HarmonicNode overtone : chord.getOvertones()) {
      chordFrequenciesCluster.add(activeFreqs.getOrDefault(overtone.getId(), 0.0));
    }

    // PHYSICAL ACCUMULATIVE TEMPO CONVERSION FORMULA
    // Seconds per beat = 60 / BPM. Chord duration = seconds per beat * figure beats value.
    double secondsPerBeat = 60.0 / currentSong.getBpm();
    double chordDurationInSeconds = secondsPerBeat * chord.getDuration().getBeatsValue();

    // Stream real-time sine synthesis vectors directly onto hardware line layer
    AudioSynthesizerEngine.playFrequencies(chordFrequenciesCluster, chordDurationInSeconds);
  }

  public boolean isRootCommand(String input) {
    String trimmed = input.trim();
    for (RegisteredRoute route : registry) {
      if (route.getPattern().matcher(trimmed).matches()) return true;
    }
    String lower = trimmed.toLowerCase();
    return lower.equals("cancel") || lower.equals("exit") || lower.equals("quit");
  }

  public void handleCommand(String rawCommandLine) throws FlowContext.ExitException, FlowContext.CancelException {
    String trimmed = rawCommandLine.trim();
    for (RegisteredRoute route : registry) {
      Matcher matcher = route.getPattern().matcher(trimmed);
      if (matcher.matches()) {
        route.getAction().ActionExecutor(trimmed, matcher, song, historyManager, uiWindow);
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
      uiWindow.printToTerminal(String.format(" -> %-40s %s", route.getSyntaxHelp(), route.getDescriptionHelp()));
    }
    uiWindow.printToTerminal(String.format(" -> %-40s %s", "cancel", "Aborts current active sequence flow immediately."));
    uiWindow.printToTerminal(String.format(" -> %-40s %s", "exit / quit", "Terminates application execution context cleanly."));
    uiWindow.printToTerminal("===================================================\n");
  }
}