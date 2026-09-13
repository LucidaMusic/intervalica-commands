package com.composer.infrastructure.cli;

import com.composer.core.domain.model.CommandHistoryManager;
import com.composer.core.domain.model.Song;
import com.composer.core.usecase.base.FlowContext;
import com.composer.core.usecase.base.TransactionalFlow;
import com.composer.core.usecase.impl.AddChordFlow;
import com.composer.infrastructure.ui.SongMonitorWindow;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
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
          "^add[- ]chord(?:\\s+(\\d+))?$", "add-chord <optional-count>", "Launches interactive multi-step composition flow.",
          (input, matcher, s, h, ui) -> {
              String inlineArg = (matcher.groupCount() >= 1) ? matcher.group(1) : null;
              new AddChordFlow(h, ui).execute(s, inlineArg);
          }
        ));

        // 2. Timeline Commands
        registry.add(new RegisteredRoute(
          "^undo$", "undo", "Reverts last committed modifications.",
          (input, matcher, s, h, ui) -> h.undo(ui)
        ));
        registry.add(new RegisteredRoute(
          "^redo$", "redo", "Re-applies reverted sequence.",
          (input, matcher, s, h, ui) -> h.redo(ui)
        ));

        // 3. Metadata Configuration Engine
        registry.add(new RegisteredRoute(
          "^set-title\\s+(.+)$", "set-title <text>", "Updates the score title metadata.",
          (input, matcher, s, h, ui) -> {
              s.setTitle(matcher.group(1).trim());
              ui.printToTerminal("[Metadata] Title set successfully.");
          }
        ));
        registry.add(new RegisteredRoute(
          "^set-author\\s+(.+)$", "set-author <text>", "Updates the score composer name.",
          (input, matcher, s, h, ui) -> {
              s.setAuthor(matcher.group(1).trim());
              ui.printToTerminal("[Metadata] Composer author set successfully.");
          }
        ));
        registry.add(new RegisteredRoute(
          "^set-bpm\\s+(\\d+)$", "set-bpm <integer>", "Changes the musical playback tempo profile.",
          (input, matcher, s, h, ui) -> {
              int val = Integer.parseInt(matcher.group(1));
              if (val <= 0) { ui.printToTerminal("[Error] BPM must be greater than 0."); return; }
              s.setBpm(val);
              ui.printToTerminal("[Metadata] Tempo updated to " + val + " BPM.");
          }
        ));
        registry.add(new RegisteredRoute(
          "^set-freq\\s+(\\d+(?:\\.\\d+)?)$", "set-freq <decimal>", "Alters the baseline reference frequency tuning anchor.",
          (input, matcher, s, h, ui) -> {
              double val = Double.parseDouble(matcher.group(1));
              if (val <= 0) { ui.printToTerminal("[Error] Frequency must be greater than 0."); return; }
              s.setReferenceFrequency(val);
              ui.printToTerminal("[Metadata] Reference base frequency tuning set to " + val + " Hz.");
          }
        ));

        // 4. State Persistence Engine (UPDATED REGEX TO MATCH STANDALONE COMMANDS)
        registry.add(new RegisteredRoute(
          "^save$", "save", "Opens native dialogue window to serialize current score directly to disk.",
          (input, matcher, s, h, ui) -> handleNativeSave()
        ));
        registry.add(new RegisteredRoute(
          "^load$", "load", "Opens native dialogue window to deserialize and populate workspace session.",
          (input, matcher, s, h, ui) -> handleNativeLoad()
        ));

        // 5. General Utilities
        registry.add(new RegisteredRoute(
          "^help$", "help", "Generates this interactive routing matrix menu dynamically.",
          (input, matcher, s, h, ui) -> printHelp()
        ));
    }

    public boolean isRootCommand(String input) {
        String trimmed = input.trim();
        for (RegisteredRoute route : registry) {
            if (route.getPattern().matcher(trimmed).matches()) {
                return true;
            }
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

    /**
     * NATIVE WINDOWS/OS SAVE FILE DIALOGUE
     */
    private void handleNativeSave() {
        // Ejecutamos de forma síncrona en el Event Dispatch Thread de Swing para evitar bloqueos de hilos
        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Your Score Blueprint Location");

            // Forzar filtro exclusivo de nuestra app .score
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Musical Score Files (*.score)", "score");
            fileChooser.setFileFilter(filter);

            int userSelection = fileChooser.showSaveDialog(uiWindow);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                String filePath = fileToSave.getAbsolutePath();

                // Asegurar de forma amigable que el archivo termine con la extensión .score si el usuario no la escribió
                if (!filePath.toLowerCase().endsWith(".score")) {
                    filePath += ".score";
                }

                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
                    oos.writeObject(song);
                    uiWindow.printToTerminal("[Success] Score metadata successfully written onto physical block location: " + filePath);
                } catch (IOException e) {
                    uiWindow.printToTerminal("[IO Error] Failed to write binary file stream to OS explorer: " + e.getMessage());
                }
            } else {
                uiWindow.printToTerminal("[Process] Save execution aborted by user dialog action.");
            }
        });
    }

    /**
     * NATIVE WINDOWS/OS LOAD FILE DIALOGUE
     */
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

                    // Copia transaccional de los campos dentro de la instancia existente compartida por la UI
                    song.copyFrom(loadedSong);
                    uiWindow.printToTerminal("[Success] Score layout session state retrieved and mounted flawlessly from: " + fileToLoad.getName());
                } catch (Exception e) {
                    uiWindow.printToTerminal("[Format Error] Could not parse file layout footprint. Is it a valid *.score archive? - " + e.getMessage());
                }
            } else {
                uiWindow.printToTerminal("[Process] Load execution cancelled.");
            }
        });
    }

    private void printHelp() {
        uiWindow.printToTerminal("\n=== Interactive CLI Studio Matrix Dynamic Help ===");
        for (RegisteredRoute route : registry) {
            uiWindow.printToTerminal(String.format(" -> %-30s %s", route.getSyntaxHelp(), route.getDescriptionHelp()));
        }
        uiWindow.printToTerminal(String.format(" -> %-30s %s", "cancel", "Aborts current active sequence flow immediately."));
        uiWindow.printToTerminal(String.format(" -> %-30s %s", "exit / quit", "Terminates application execution context cleanly."));
        uiWindow.printToTerminal("===================================================\n");
    }
}
