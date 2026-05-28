package org.example.service;

import org.example.model.WeatherData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class WeatherService {

    // When deployed, replace with your Render URL
    private static final String API_BASE = "https://malawi-weather-api.onrender.com/api/v1";
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.malawi-weather/cache/";

    private final String apiKey;

    public WeatherService(String apiKey) {
        this.apiKey = apiKey;
        new File(CACHE_DIR).mkdirs();
    }

    public WeatherData getWeather(String district) throws Exception {
        try {
            String json = fetch("/weather/" + district);
            WeatherData data = parseCurrentWeather(json, district);
            saveToCache(district + "_current", json);
            return data;
        } catch (Exception e) {
            // Try offline cache
            String cached = loadFromCache(district + "_current");
            if (cached != null) {
                WeatherData data = parseCurrentWeather(cached, district);
                data.setDescription(data.getDescription() + " (cached)");
                return data;
            }
            throw e;
        }
    }

    public List<WeatherData.DailyForecast> getForecast(String district) throws Exception {
        try {
            String json = fetch("/forecast/" + district);
            List<WeatherData.DailyForecast> forecast = parseForecast(json);
            saveToCache(district + "_forecast", json);
            return forecast;
        } catch (Exception e) {
            String cached = loadFromCache(district + "_forecast");
            if (cached != null) return parseForecast(cached);
            throw e;
        }
    }

    public List<String> getAllDistricts() throws Exception {
        String json = fetchPublic("/districts");
        JSONObject response = new JSONObject(json);
        JSONArray data = response.getJSONArray("data");
        List<String> districts = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            districts.add(data.getJSONObject(i).getString("name"));
        }
        return districts;
    }

    private String fetch(String endpoint) throws Exception {
        return doRequest(API_BASE + endpoint, apiKey);
    }

    private String fetchPublic(String endpoint) throws Exception {
        return doRequest(API_BASE + endpoint, null);
    }

    private String doRequest(String urlStr, String key) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        if (key != null) conn.setRequestProperty("X-API-Key", key);

        int code = conn.getResponseCode();
        if (code == 401) throw new Exception("Invalid API key. Please check your key in Settings.");
        if (code == 403) throw new Exception("Account pending approval or suspended.");
        if (code == 404) throw new Exception("District not found.");
        if (code == 429) throw new Exception("Daily request limit reached.");
        if (code != 200) throw new Exception("Server error (HTTP " + code + ")");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private WeatherData parseCurrentWeather(String json, String district) {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.getJSONObject("data");

        WeatherData w = new WeatherData();
        w.setDistrict(data.optString("district", district));
        w.setRegion(data.optString("region", ""));
        w.setTemperature(data.optDouble("temperatureCelsius", 0));
        w.setFeelsLike(data.optDouble("feelsLikeCelsius", 0));
        w.setTempMin(data.optDouble("temperatureMin", 0));
        w.setTempMax(data.optDouble("temperatureMax", 0));
        w.setHumidity(data.optInt("humidity", 0));
        w.setWindSpeed(data.optDouble("windSpeedKph", 0));
        w.setWindDirection(data.optString("windDirection", ""));
        w.setPrecipitation(data.optDouble("precipitationMm", 0));
        w.setPrecipitationProbability(data.optDouble("precipitationProbability", 0));
        w.setUvIndex(data.optInt("uvIndex", 0));
        w.setDescription(data.optString("weatherDescription", ""));
        w.setWeatherCode(data.optInt("weatherCode", 0));
        w.setPressure(data.optDouble("pressureHpa", 0));
        w.setCloudCover(data.optDouble("cloudCoverPercent", 0));
        w.setSunrise(data.optString("sunrise", ""));
        w.setSunset(data.optString("sunset", ""));
        w.setDay(data.optBoolean("isDay", true));
        return w;
    }

    private List<WeatherData.DailyForecast> parseForecast(String json) {
        JSONObject root = new JSONObject(json);
        JSONArray forecastArr = root.getJSONObject("data").getJSONArray("forecast");
        List<WeatherData.DailyForecast> list = new ArrayList<>();

        for (int i = 0; i < forecastArr.length(); i++) {
            JSONObject day = forecastArr.getJSONObject(i);
            WeatherData.DailyForecast f = new WeatherData.DailyForecast();
            f.setDate(day.optString("date", ""));
            f.setTempMax(day.optDouble("temperatureMaxCelsius", 0));
            f.setTempMin(day.optDouble("temperatureMinCelsius", 0));
            f.setPrecipitationMm(day.optDouble("precipitationMm", 0));
            f.setPrecipProbability(day.optDouble("precipitationProbabilityMax", 0));
            f.setWindSpeedMax(day.optDouble("windSpeedMaxKph", 0));
            f.setDescription(day.optString("weatherDescription", ""));
            f.setUvIndexMax(day.optInt("uvIndexMax", 0));
            f.setSunrise(day.optString("sunrise", ""));
            f.setSunset(day.optString("sunset", ""));
            list.add(f);
        }
        return list;
    }

    private void saveToCache(String key, String data) {
        try {
            Files.writeString(Path.of(CACHE_DIR + key + ".json"), data);
        } catch (IOException ignored) {}
    }

    private String loadFromCache(String key) {
        try {
            Path path = Path.of(CACHE_DIR + key + ".json");
            if (Files.exists(path)) return Files.readString(path);
        } catch (IOException ignored) {}
        return null;
    }
}
