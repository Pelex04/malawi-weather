package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class WeatherAppGUI extends JFrame {
    private static final String API_KEY = "";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather?q=";

    private JTextField cityInput;
    private JTextArea weatherDisplay;
    private JButton searchButton;
    private JLabel statusLabel;

    private static final Map<String, double[]> MALAWI_CITIES = new HashMap<>();
    static {
        MALAWI_CITIES.put("lilongwe", new double[]{-13.9669, 33.7873});
        MALAWI_CITIES.put("blantyre", new double[]{-15.7861, 35.0058});
        MALAWI_CITIES.put("mzuzu", new double[]{-11.4656, 34.0207});
        MALAWI_CITIES.put("zomba", new double[]{-15.3833, 35.3333});
        MALAWI_CITIES.put("kasungu", new double[]{-13.0333, 33.4833});
    }

    public WeatherAppGUI() {

        setTitle("Pelex Weather App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(null);


        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));


        JPanel searchPanel = new JPanel(new BorderLayout(10, 0));
        cityInput = new JTextField(20);
        cityInput.setFont(new Font("Arial", Font.PLAIN, 14));
        searchButton = new JButton("Get Weather");
        searchButton.setFont(new Font("Arial", Font.BOLD, 14));
        searchPanel.add(new JLabel("Enter City: "), BorderLayout.WEST);
        searchPanel.add(cityInput, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);


        statusLabel = new JLabel("Enter a city name in Malawi");
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        statusLabel.setHorizontalAlignment(JLabel.CENTER);


        weatherDisplay = new JTextArea();
        weatherDisplay.setFont(new Font("Monospaced", Font.PLAIN, 14));
        weatherDisplay.setEditable(false);
        weatherDisplay.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(weatherDisplay);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);


        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(statusLabel, BorderLayout.CENTER);
        mainPanel.add(scrollPane, BorderLayout.SOUTH);


        add(mainPanel);


        searchButton.addActionListener(e -> fetchWeather());
        cityInput.addActionListener(e -> fetchWeather());


        cityInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    fetchWeather();
                }
            }
        });
    }

    private void fetchWeather() {
        String city = cityInput.getText().trim().toLowerCase();
        if (city.isEmpty()) {
            statusLabel.setText("Please enter a city name");
            return;
        }

        
        statusLabel.setText("Fetching weather data...");
        weatherDisplay.setText("");

        
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return getWeatherData(city);
            }

            @Override
            protected void done() {
                try {
                    String weatherData = get();
                    if (weatherData != null) {
                        parseAndDisplayWeather(weatherData, city);
                        statusLabel.setText("Weather data updated successfully");
                    } else {
                        statusLabel.setText("Could not fetch weather data. Please check the city name.");
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private String getWeatherData(String city) {
        try {
            String apiUrl;
            if (MALAWI_CITIES.containsKey(city)) {
                double[] coords = MALAWI_CITIES.get(city);
                apiUrl = "https://api.openweathermap.org/data/2.5/weather?lat=" + coords[0] +
                        "&lon=" + coords[1] + "&appid=" + API_KEY + "&units=metric";
            } else {
                apiUrl = BASE_URL + city + ",MW&appid=" + API_KEY + "&units=metric";
            }

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cache-Control", "no-cache");

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("City not found. Please check the spelling."));
                return null;
            }
            if (responseCode != 200) {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Error: Unable to fetch weather data (HTTP " + responseCode + ")"));
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() ->
                    statusLabel.setText("Error: " + e.getMessage()));
            return null;
        }
    }

    private void parseAndDisplayWeather(String jsonResponse, String city) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            StringBuilder display = new StringBuilder();


            JSONObject main = jsonObject.getJSONObject("main");
            String weatherDescription = jsonObject.getJSONArray("weather")
                    .getJSONObject(0).getString("description");
            JSONObject wind = jsonObject.getJSONObject("wind");

            display.append(String.format("Weather Report for %s, Malawi\n", capitalize(city)));
            display.append("═".repeat(40)).append("\n\n");

            display.append(String.format("Temperature: %.1f°C\n", main.getDouble("temp")));
            display.append(String.format("Feels Like: %.1f°C\n", main.getDouble("feels_like")));
            display.append(String.format("Min/Max: %.1f°C / %.1f°C\n",
                    main.getDouble("temp_min"), main.getDouble("temp_max")));
            display.append(String.format("Humidity: %d%%\n", main.getInt("humidity")));
            display.append(String.format("Pressure: %d hPa\n", main.getInt("pressure")));
            display.append(String.format("Weather: %s\n", capitalize(weatherDescription)));
            display.append(String.format("Wind Speed: %.1f m/s\n", wind.getDouble("speed")));

            if (wind.has("deg")) {
                display.append(String.format("Wind Direction: %s (%d°)\n",
                        getWindDirection(wind.getInt("deg")), wind.getInt("deg")));
            }

            if (jsonObject.has("sys")) {
                JSONObject sys = jsonObject.getJSONObject("sys");
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
                String sunrise = timeFormat.format(sys.getLong("sunrise") * 1000);
                String sunset = timeFormat.format(sys.getLong("sunset") * 1000);
                display.append(String.format("\nSunrise: %s\n", sunrise));
                display.append(String.format("Sunset: %s\n", sunset));
            }

            weatherDisplay.setText(display.toString());
        } catch (Exception e) {
            statusLabel.setText("Error parsing weather data: " + e.getMessage());
        }
    }

    private static String getWindDirection(int degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return directions[(int) Math.round(((degrees % 360) / 22.5)) % 16];
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }


        SwingUtilities.invokeLater(() -> {
            WeatherAppGUI app = new WeatherAppGUI();
            app.setVisible(true);
        });
    }
}
