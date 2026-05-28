package org.example.ui.components;

import java.util.Map;

public class WeatherIconMapper {

    // Maps WMO weather codes to emoji icons
    private static final Map<Integer, String> ICONS = Map.ofEntries(
        Map.entry(0,  "☀️"),   // Clear sky
        Map.entry(1,  "🌤️"),  // Mainly clear
        Map.entry(2,  "⛅"),   // Partly cloudy
        Map.entry(3,  "☁️"),   // Overcast
        Map.entry(45, "🌫️"),  // Fog
        Map.entry(48, "🌫️"),  // Rime fog
        Map.entry(51, "🌦️"),  // Light drizzle
        Map.entry(53, "🌦️"),  // Moderate drizzle
        Map.entry(55, "🌧️"),  // Dense drizzle
        Map.entry(61, "🌧️"),  // Slight rain
        Map.entry(63, "🌧️"),  // Moderate rain
        Map.entry(65, "🌧️"),  // Heavy rain
        Map.entry(71, "❄️"),   // Slight snow
        Map.entry(73, "❄️"),   // Moderate snow
        Map.entry(75, "❄️"),   // Heavy snow
        Map.entry(80, "🌦️"),  // Slight showers
        Map.entry(81, "🌧️"),  // Moderate showers
        Map.entry(82, "⛈️"),   // Violent showers
        Map.entry(95, "⛈️"),   // Thunderstorm
        Map.entry(96, "⛈️"),   // Thunderstorm with hail
        Map.entry(99, "⛈️")    // Thunderstorm with heavy hail
    );

    public static String getIcon(int wmoCode) {
        return ICONS.getOrDefault(wmoCode, "🌡️");
    }

    // Background gradient colors based on weather + time of day
    public static String[] getGradientColors(int wmoCode, boolean isDay) {
        if (!isDay) return new String[]{"#0f0c29", "#302b63"};           // Night
        if (wmoCode == 0) return new String[]{"#2980B9", "#6DD5FA"};     // Clear
        if (wmoCode <= 2) return new String[]{"#3a7bd5", "#9FB1C6"};     // Partly cloudy
        if (wmoCode == 3) return new String[]{"#616161", "#9bc5c3"};     // Overcast
        if (wmoCode >= 51 && wmoCode <= 67) return new String[]{"#373B44", "#4286f4"}; // Rain
        if (wmoCode >= 71 && wmoCode <= 77) return new String[]{"#83a4d4", "#b6fbff"}; // Snow
        if (wmoCode >= 80 && wmoCode <= 82) return new String[]{"#4b6cb7", "#182848"}; // Showers
        if (wmoCode >= 95) return new String[]{"#232526", "#414345"};    // Storm
        return new String[]{"#2980B9", "#6DD5FA"};
    }
}
