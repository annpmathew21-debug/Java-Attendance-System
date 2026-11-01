package com.attendease;

import com.attendease.db.Database;
import com.attendease.ui.MainFrame;

import javax.swing.*;

public class App {
    private static final String DB_FILE = "attendease.db";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Database db = new Database(DB_FILE);
                MainFrame frame = new MainFrame(db);
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}

