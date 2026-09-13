package com.composer;

import com.composer.core.domain.model.CommandHistoryManager;
import com.composer.core.domain.model.Song;
import com.composer.infrastructure.ui.SongMonitorWindow;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Inicialización de los datos del dominio
        Song rootSongModel = new Song();
        CommandHistoryManager historyManager = new CommandHistoryManager();

        // Lanzar la interfaz de usuario gráfica integrada unificada
        SwingUtilities.invokeLater(() -> {
            SongMonitorWindow mainWindow = new SongMonitorWindow(rootSongModel, historyManager);
            mainWindow.setVisible(true);
        });
    }
}
