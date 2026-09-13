package com.composer.infrastructure.ui;

import com.composer.core.domain.model.CommandHistoryManager;
import com.composer.core.domain.model.Song;
import com.composer.core.usecase.base.FlowContext;
import com.composer.infrastructure.cli.CommandRouter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class SongMonitorWindow extends JFrame {
    // Score Header Components
    private JLabel titleLabel, authorLabel, bpmLabel, freqLabel;

    private final JTextArea songStructureArea;
    private final JTextArea terminalLogArea;
    private final JTextField commandInputField;

    private final Song observedSong;
    private final CommandRouter router;
    private final LinkedBlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    // --- NEW: UI TERMINAL COMMAND HISTORY TRACKING ---
    private final List<String> commandHistory = new ArrayList<>();
    private int historyPointer = -1;
    // --------------------------------------------------

    public SongMonitorWindow(Song song, CommandHistoryManager historyManager) {
        this.observedSong = song;
        this.router = new CommandRouter(song, historyManager, this);

        setTitle("Music Composer Studio - Score Workspace");
        setSize(855, 655);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Font monospacedFont = new Font("Monospaced", Font.PLAIN, 13);
        Color backgroundColor = new Color(20, 20, 20);
        Color textColor = new Color(0, 255, 65);

        // Score Header Panel (Top Panel)
        JPanel headerPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        headerPanel.setBackground(new Color(45, 45, 45));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        Font headerFont = new Font("Serif", Font.BOLD | Font.ITALIC, 14);
        titleLabel = new JLabel(); titleLabel.setFont(headerFont); titleLabel.setForeground(Color.WHITE);
        authorLabel = new JLabel(); authorLabel.setFont(headerFont); authorLabel.setForeground(Color.LIGHT_GRAY);
        bpmLabel = new JLabel(); bpmLabel.setFont(monospacedFont); bpmLabel.setForeground(new Color(255, 204, 0));
        freqLabel = new JLabel(); freqLabel.setFont(monospacedFont); freqLabel.setForeground(new Color(204, 102, 255));

        headerPanel.add(titleLabel);
        headerPanel.add(authorLabel);
        headerPanel.add(bpmLabel);
        headerPanel.add(freqLabel);

        // Core Areas
        songStructureArea = new JTextArea();
        songStructureArea.setEditable(false);
        songStructureArea.setFont(monospacedFont);
        songStructureArea.setBackground(new Color(30, 30, 30));
        songStructureArea.setForeground(new Color(51, 153, 255));
        JScrollPane songScrollPane = new JScrollPane(songStructureArea);
        songScrollPane.setBorder(BorderFactory.createTitledBorder(
          BorderFactory.createLineBorder(Color.GRAY), " LIVE CHORD METADATA ", 0, 0, monospacedFont, Color.LIGHT_GRAY
        ));

        terminalLogArea = new JTextArea();
        terminalLogArea.setEditable(false);
        terminalLogArea.setFont(monospacedFont);
        terminalLogArea.setBackground(backgroundColor);
        terminalLogArea.setForeground(textColor);
        JScrollPane terminalScrollPane = new JScrollPane(terminalLogArea);
        terminalScrollPane.setBorder(BorderFactory.createTitledBorder(
          BorderFactory.createLineBorder(Color.GRAY), " INTERACTIVE CORE TERMINAL STREAM ", 0, 0, monospacedFont, Color.LIGHT_GRAY
        ));

        // Input Field
        commandInputField = new JTextField();
        commandInputField.setFont(monospacedFont);
        commandInputField.setBackground(backgroundColor);
        commandInputField.setForeground(textColor);
        commandInputField.setCaretColor(textColor);
        commandInputField.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(Color.DARK_GRAY),
          BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Command Submission Event Logic (Now with filtering)
        commandInputField.addActionListener(e -> {
            String text = commandInputField.getText().trim();
            if (!text.isEmpty()) {
                printToTerminal("> " + text);

                // --- NEW FILTER CRITERIA: ONLY STORE SYSTEM ROOT COMMANDS ---
                if (router.isRootCommand(text)) {
                    // Prevent consecutive duplicates in the history
                    if (commandHistory.isEmpty() || !commandHistory.getLast().equals(text)) {
                        commandHistory.add(text);
                    }
                }
                historyPointer = commandHistory.size(); // Always reset pointer position
                // -------------------------------------------------------------

                inputQueue.offer(text);
                commandInputField.setText("");
            }
        });


        // --- NEW: KEYBOARD NAVIGATION INTERCEPTOR (UP / DOWN ARROWS) ---
        commandInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (commandHistory.isEmpty()) return;

                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    // Navigate backwards through previous configurations
                    if (historyPointer > 0) {
                        historyPointer--;
                        commandInputField.setText(commandHistory.get(historyPointer));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // Navigate forwards towards recent instructions
                    if (historyPointer < commandHistory.size() - 1) {
                        historyPointer++;
                        commandInputField.setText(commandHistory.get(historyPointer));
                    } else {
                        // Beyond the last command: wipe string layout to blank state
                        historyPointer = commandHistory.size();
                        commandInputField.setText("");
                    }
                }
            }
        });
        // ----------------------------------------------------------------

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, songScrollPane, terminalScrollPane);
        splitPane.setDividerLocation(200);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JLabel promptLabel = new JLabel(" Composer-CLI> ");
        promptLabel.setFont(monospacedFont);
        promptLabel.setBackground(backgroundColor);
        promptLabel.setForeground(textColor);
        promptLabel.setOpaque(true);
        bottomPanel.add(promptLabel, BorderLayout.WEST);
        bottomPanel.add(commandInputField, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        observedSong.registerListener(this::refreshSongView);
        refreshSongView();

        printToTerminal("Workspace initialization successful.");
        printToTerminal("Type 'help' to examine available command systems.");
        printToTerminal("Use UP and DOWN arrow keys to scroll through previously typed commands.\n");

        Thread.startVirtualThread(this::startCliLoop);
    }

    public void refreshSongView() {
        SwingUtilities.invokeLater(() -> {
            titleLabel.setText("Title: " + observedSong.getTitle());
            authorLabel.setText("Composer: " + observedSong.getAuthor());
            bpmLabel.setText("Tempo: " + observedSong.getBpm() + " BPM");
            freqLabel.setText("Ref Freq: " + observedSong.getReferenceFrequency() + " Hz");
            songStructureArea.setText(observedSong.toString());
        });
    }

    public void printToTerminal(String message) {
        SwingUtilities.invokeLater(() -> {
            terminalLogArea.append(message + "\n");
            terminalLogArea.setCaretPosition(terminalLogArea.getDocument().getLength());
        });
    }

    public String readInputFromUI() throws FlowContext.CancelException, FlowContext.ExitException {
        try {
            String input = inputQueue.take();
            if (input.equalsIgnoreCase("cancel")) {
                printToTerminal("[Process] Sequence operation aborted.");
                throw new FlowContext.CancelException();
            }
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                printToTerminal("[Process] Shutting down application session...");
                throw new FlowContext.ExitException();
            }
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlowContext.ExitException();
        }
    }

    private void startCliLoop() {
        try {
            while (true) {
                String input = readInputFromUI();
                try {
                    router.handleCommand(input);
                } catch (FlowContext.CancelException e) {
                    // Swallow local sequence breaks
                }
            }
        } catch (FlowContext.ExitException | FlowContext.CancelException e) {
            System.exit(0);
        }
    }
}
