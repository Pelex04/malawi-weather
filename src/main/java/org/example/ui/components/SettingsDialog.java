package org.example.ui.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.prefs.Preferences;

public class SettingsDialog extends JDialog {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsDialog.class);
    public static final String PREF_API_KEY = "api_key";
    public static final String PREF_API_BASE = "api_base";

    private final JTextField apiKeyField;
    private final JTextField apiBaseField;
    private boolean saved = false;

    public SettingsDialog(Frame parent) {
        super(parent, "Settings", true);
        setSize(480, 240);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // API Key
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        apiKeyField = new JTextField(getSavedApiKey(), 30);
        panel.add(apiKeyField, gbc);

        // API Base URL
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("API Base URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        apiBaseField = new JTextField(getSavedApiBase(), 30);
        panel.add(apiBaseField, gbc);

        // Hint
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel hint = new JLabel("<html><small>Register at the developer portal to get your API key.<br>" +
                "Leave API Base URL as default unless self-hosting.</small></html>");
        hint.setForeground(Color.GRAY);
        panel.add(hint, gbc);

        // Buttons
        gbc.gridy = 3; gbc.gridwidth = 2;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        save.setBackground(new Color(37, 99, 235));
        save.setForeground(Color.WHITE);
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> saveAndClose());
        buttons.add(cancel);
        buttons.add(save);
        panel.add(buttons, gbc);

        add(panel);
    }

    private void saveAndClose() {
        String key = apiKeyField.getText().trim();
        String base = apiBaseField.getText().trim();
        if (key.isEmpty()) {
            JOptionPane.showMessageDialog(this, "API key cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        PREFS.put(PREF_API_KEY, key);
        PREFS.put(PREF_API_BASE, base);
        saved = true;
        dispose();
    }

    public boolean isSaved() { return saved; }

    public static String getSavedApiKey() {
        return PREFS.get(PREF_API_KEY, "");
    }

    public static String getSavedApiBase() {
        return PREFS.get(PREF_API_BASE, "https://malawi-weather-api.onrender.com/api/v1");
    }
}
