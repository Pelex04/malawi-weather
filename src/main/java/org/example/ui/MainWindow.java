package org.example.ui;

import org.example.model.WeatherData;
import org.example.service.WeatherService;
import org.example.ui.components.ForecastPanel;
import org.example.ui.components.SettingsDialog;
import org.example.ui.components.WeatherIconMapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class MainWindow extends JFrame {

    // --- Colors & Fonts ---
    private static final Color TEXT_PRIMARY = Color.WHITE;
    private static final Color TEXT_MUTED   = new Color(200, 220, 255);
    private static final Color GLASS_BG     = new Color(255, 255, 255, 40);
    private static final Color GLASS_BORDER = new Color(255, 255, 255, 60);
    private static final Font  FONT_HUGE    = new Font("Segoe UI", Font.BOLD, 72);
    private static final Font  FONT_LARGE   = new Font("Segoe UI", Font.PLAIN, 20);
    private static final Font  FONT_MEDIUM  = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font  FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  FONT_EMOJI   = new Font("Segoe UI Emoji", Font.PLAIN, 52);

    // --- State ---
    private String[] gradientColors = {"#2980B9", "#6DD5FA"};
    private WeatherService weatherService;

    // --- UI Components ---
    private JComboBox<String> districtCombo;
    private JLabel iconLabel, tempLabel, descLabel, regionLabel;
    private JLabel feelsLikeLabel, humidityLabel, windLabel, uvLabel;
    private JLabel minMaxLabel, pressureLabel, precipLabel, cloudLabel;
    private JLabel sunriseLabel, sunsetLabel;
    private JLabel statusLabel;
    private ForecastPanel forecastPanel;
    private JPanel mainContentPanel;
    private JButton searchButton;

    public MainWindow() {
        setTitle("Malawi Weather");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 680);
        setMinimumSize(new Dimension(720, 580));
        setLocationRelativeTo(null);

        initWeatherService();
        buildUI();
        loadDistricts();

        // If no API key saved, prompt settings on first run
        if (SettingsDialog.getSavedApiKey().isEmpty()) {
            SwingUtilities.invokeLater(this::openSettings);
        }
    }

    private void initWeatherService() {
        weatherService = new WeatherService(SettingsDialog.getSavedApiKey());
    }

    private void buildUI() {
        // Gradient background panel
        JPanel bgPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                Color c1 = Color.decode(gradientColors[0]);
                Color c2 = Color.decode(gradientColors[1]);
                GradientPaint gp = new GradientPaint(0, 0, c1, 0, getHeight(), c2);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        bgPanel.setLayout(new BorderLayout(0, 0));
        bgPanel.setBorder(new EmptyBorder(20, 24, 20, 24));
        setContentPane(bgPanel);

        // --- Top bar ---
        bgPanel.add(buildTopBar(), BorderLayout.NORTH);

        // --- Main content (shown after search) ---
        mainContentPanel = new JPanel();
        mainContentPanel.setOpaque(false);
        mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
        mainContentPanel.setVisible(false);

        mainContentPanel.add(buildHeroSection());
        mainContentPanel.add(Box.createVerticalStrut(16));
        mainContentPanel.add(buildStatsGrid());
        mainContentPanel.add(Box.createVerticalStrut(16));
        mainContentPanel.add(buildSunSection());
        mainContentPanel.add(Box.createVerticalStrut(16));
        mainContentPanel.add(buildForecastSection());

        JScrollPane scroll = new JScrollPane(mainContentPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        bgPanel.add(scroll, BorderLayout.CENTER);

        // --- Status label (shown before search) ---
        statusLabel = new JLabel("Select a district and press Search", SwingConstants.CENTER);
        statusLabel.setFont(FONT_LARGE);
        statusLabel.setForeground(TEXT_MUTED);
        bgPanel.add(statusLabel, BorderLayout.CENTER);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, 16, 0));

        // App title
        JLabel title = new JLabel("🌦 Malawi Weather");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);

        // Search area
        JPanel searchArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchArea.setOpaque(false);

        districtCombo = new JComboBox<>();
        districtCombo.setFont(FONT_SMALL);
        districtCombo.setPreferredSize(new Dimension(180, 34));
        districtCombo.addItem("Loading districts...");

        searchButton = new JButton("Search");
        searchButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchButton.setBackground(new Color(255, 255, 255, 60));
        searchButton.setForeground(TEXT_PRIMARY);
        searchButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GLASS_BORDER, 1, true),
                new EmptyBorder(6, 16, 6, 16)));
        searchButton.setFocusPainted(false);
        searchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        searchButton.addActionListener(e -> fetchWeather());

        JButton settingsBtn = new JButton("⚙ Settings");
        settingsBtn.setFont(FONT_SMALL);
        settingsBtn.setBackground(new Color(255, 255, 255, 30));
        settingsBtn.setForeground(TEXT_MUTED);
        settingsBtn.setBorder(new EmptyBorder(6, 12, 6, 12));
        settingsBtn.setFocusPainted(false);
        settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsBtn.addActionListener(e -> openSettings());

        searchArea.add(districtCombo);
        searchArea.add(searchButton);
        searchArea.add(settingsBtn);

        bar.add(title, BorderLayout.WEST);
        bar.add(searchArea, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildHeroSection() {
        JPanel hero = new JPanel(new BorderLayout(16, 0));
        hero.setOpaque(false);
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        // Left: icon + temp
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));

        iconLabel = new JLabel("☀️");
        iconLabel.setFont(FONT_EMOJI);
        left.add(iconLabel);
        left.add(Box.createHorizontalStrut(16));

        JPanel tempInfo = new JPanel();
        tempInfo.setOpaque(false);
        tempInfo.setLayout(new BoxLayout(tempInfo, BoxLayout.Y_AXIS));

        tempLabel = new JLabel("--°C");
        tempLabel.setFont(FONT_HUGE);
        tempLabel.setForeground(TEXT_PRIMARY);

        descLabel = new JLabel("--");
        descLabel.setFont(FONT_LARGE);
        descLabel.setForeground(TEXT_MUTED);

        regionLabel = new JLabel("--");
        regionLabel.setFont(FONT_SMALL);
        regionLabel.setForeground(TEXT_MUTED);

        tempInfo.add(tempLabel);
        tempInfo.add(descLabel);
        tempInfo.add(regionLabel);
        left.add(tempInfo);

        // Right: feels like + min/max
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(8, 0, 0, 0));

        feelsLikeLabel = new JLabel("Feels like: --");
        feelsLikeLabel.setFont(FONT_MEDIUM);
        feelsLikeLabel.setForeground(TEXT_PRIMARY);

        minMaxLabel = new JLabel("↑ -- ↓ --");
        minMaxLabel.setFont(FONT_SMALL);
        minMaxLabel.setForeground(TEXT_MUTED);

        right.add(feelsLikeLabel);
        right.add(Box.createVerticalStrut(6));
        right.add(minMaxLabel);

        hero.add(left, BorderLayout.WEST);
        hero.add(right, BorderLayout.EAST);
        return hero;
    }

    private JPanel buildStatsGrid() {
        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        humidityLabel  = statCard("💧", "Humidity",    "--");
        windLabel      = statCard("💨", "Wind",        "--");
        uvLabel        = statCard("☀️", "UV Index",    "--");
        precipLabel    = statCard("🌧️", "Rain",        "--");
        pressureLabel  = statCard("🔵", "Pressure",    "--");
        cloudLabel     = statCard("☁️", "Cloud Cover", "--");

        // dummy placeholders to fill grid of 8
        JPanel p1 = new JPanel(); p1.setOpaque(false);
        JPanel p2 = new JPanel(); p2.setOpaque(false);

        grid.add(humidityLabel.getParent());
        grid.add(windLabel.getParent());
        grid.add(uvLabel.getParent());
        grid.add(precipLabel.getParent());
        grid.add(pressureLabel.getParent());
        grid.add(cloudLabel.getParent());
        grid.add(p1);
        grid.add(p2);

        return grid;
    }

    private JPanel buildSunSection() {
        JPanel panel = glassCard();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 48, 8));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        sunriseLabel = new JLabel("🌅 Sunrise: --");
        sunriseLabel.setFont(FONT_MEDIUM);
        sunriseLabel.setForeground(TEXT_PRIMARY);

        sunsetLabel = new JLabel("🌇 Sunset: --");
        sunsetLabel.setFont(FONT_MEDIUM);
        sunsetLabel.setForeground(TEXT_PRIMARY);

        panel.add(sunriseLabel);
        panel.add(sunsetLabel);
        return panel;
    }

    private JPanel buildForecastSection() {
        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setOpaque(false);

        JLabel title = new JLabel("7-Day Forecast");
        title.setFont(FONT_MEDIUM);
        title.setForeground(TEXT_MUTED);

        forecastPanel = new ForecastPanel();
        section.add(title, BorderLayout.NORTH);
        section.add(forecastPanel, BorderLayout.CENTER);
        return section;
    }

    // Helper: stat card (returns the value label for updates)
    private JLabel statCard(String icon, String title, String value) {
        JPanel card = glassCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel iconLbl = new JLabel(icon + " " + title);
        iconLbl.setFont(FONT_SMALL);
        iconLbl.setForeground(TEXT_MUTED);
        iconLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(FONT_MEDIUM);
        valueLbl.setForeground(TEXT_PRIMARY);
        valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(iconLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(valueLbl);

        return valueLbl;
    }

    private JPanel glassCard() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(GLASS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(GLASS_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private void loadDistricts() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return weatherService.getAllDistricts();
            }
            @Override
            protected void done() {
                try {
                    List<String> districts = get();
                    districtCombo.removeAllItems();
                    for (String d : districts) districtCombo.addItem(d);
                    districtCombo.setSelectedItem("Lilongwe");
                } catch (Exception e) {
                    // Fallback: hardcode all 28 districts if API unreachable
                    districtCombo.removeAllItems();
                    String[] all = {
                        "Balaka","Blantyre","Chikwawa","Chiradzulu","Chitipa",
                        "Dedza","Dowa","Karonga","Kasungu","Likoma",
                        "Lilongwe","Machinga","Mangochi","Mchinji","Mulanje",
                        "Mwanza","Mzimba","Mzuzu","Nkhata Bay","Nkhotakota",
                        "Nsanje","Ntcheu","Ntchisi","Phalombe","Rumphi",
                        "Salima","Thyolo","Zomba"
                    };
                    for (String d : all) districtCombo.addItem(d);
                    districtCombo.setSelectedItem("Lilongwe");
                }
            }
        };
        worker.execute();
    }

    private void fetchWeather() {
        String district = (String) districtCombo.getSelectedItem();
        if (district == null || district.isEmpty()) return;

        String apiKey = SettingsDialog.getSavedApiKey();
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter your API key in Settings first.",
                "API Key Required", JOptionPane.WARNING_MESSAGE);
            openSettings();
            return;
        }

        searchButton.setEnabled(false);
        statusLabel.setText("Fetching weather for " + district + "...");
        statusLabel.setVisible(true);
        mainContentPanel.setVisible(false);

        SwingWorker<WeatherData, Void> worker = new SwingWorker<>() {
            @Override
            protected WeatherData doInBackground() throws Exception {
                weatherService = new WeatherService(SettingsDialog.getSavedApiKey());
                WeatherData data = weatherService.getWeather(district);
                List<WeatherData.DailyForecast> forecast = weatherService.getForecast(district);
                data.setForecast(forecast);
                return data;
            }
            @Override
            protected void done() {
                searchButton.setEnabled(true);
                try {
                    WeatherData data = get();
                    updateUI(data);
                    statusLabel.setVisible(false);
                    mainContentPanel.setVisible(true);
                } catch (Exception e) {
                    statusLabel.setText("⚠ " + e.getMessage());
                    statusLabel.setVisible(true);
                }
            }
        };
        worker.execute();
    }

    private void updateUI(WeatherData data) {
        // Update gradient
        String[] colors = WeatherIconMapper.getGradientColors(data.getWeatherCode(), data.isDay());
        gradientColors = colors;
        getContentPane().repaint();

        // Hero
        iconLabel.setText(WeatherIconMapper.getIcon(data.getWeatherCode()));
        tempLabel.setText(String.format("%.0f°C", data.getTemperature()));
        descLabel.setText(data.getDescription());
        regionLabel.setText(data.getDistrict() + " · " + data.getRegion() + " Region");
        feelsLikeLabel.setText(String.format("Feels like %.0f°C", data.getFeelsLike()));
        minMaxLabel.setText(String.format("↑ %.0f°  ↓ %.0f°", data.getTempMax(), data.getTempMin()));

        // Stats
        humidityLabel.setText(data.getHumidity() + "%");
        windLabel.setText(String.format("%.0f km/h %s", data.getWindSpeed(), data.getWindDirection()));
        uvLabel.setText(uvLabel(data.getUvIndex()));
        precipLabel.setText(String.format("%.1f mm (%.0f%%)", data.getPrecipitation(), data.getPrecipitationProbability()));
        pressureLabel.setText(String.format("%.0f hPa", data.getPressure()));
        cloudLabel.setText(String.format("%.0f%%", data.getCloudCover()));

        // Sun
        sunriseLabel.setText("🌅 Sunrise: " + formatTime(data.getSunrise()));
        sunsetLabel.setText("🌇 Sunset: " + formatTime(data.getSunset()));

        // Forecast
        if (data.getForecast() != null) {
            forecastPanel.updateForecast(data.getForecast());
        }
    }

    private String uvLabel(int uv) {
        if (uv <= 2)  return uv + " (Low)";
        if (uv <= 5)  return uv + " (Moderate)";
        if (uv <= 7)  return uv + " (High)";
        if (uv <= 10) return uv + " (Very High)";
        return uv + " (Extreme)";
    }

    private String formatTime(String dateTime) {
        // dateTime from API looks like "2024-11-01T06:23"
        if (dateTime == null || dateTime.isEmpty()) return "--";
        if (dateTime.contains("T")) return dateTime.split("T")[1];
        return dateTime;
    }

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(this);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            weatherService = new WeatherService(SettingsDialog.getSavedApiKey());
        }
    }

    public static void launch() {
        // Try FlatLaf, fall back to Nimbus, fall back to system
        try {
            Class<?> flatLaf = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            UIManager.setLookAndFeel((javax.swing.LookAndFeel) flatLaf.getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException e) {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ex) {
                // Use default
            }
        } catch (Exception e) {
            // Use default
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
