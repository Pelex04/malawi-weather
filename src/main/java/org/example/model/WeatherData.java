package org.example.model;

import java.util.List;

public class WeatherData {
    // Current weather
    private String district;
    private String region;
    private double temperature;
    private double feelsLike;
    private double tempMin;
    private double tempMax;
    private int humidity;
    private double windSpeed;
    private String windDirection;
    private double precipitation;
    private double precipitationProbability;
    private int uvIndex;
    private String description;
    private double pressure;
    private double cloudCover;
    private String sunrise;
    private String sunset;
    private boolean isDay;
    private int weatherCode;

    // 7-day forecast
    private List<DailyForecast> forecast;

    public static class DailyForecast {
        private String date;
        private double tempMax;
        private double tempMin;
        private double precipitationMm;
        private double precipProbability;
        private double windSpeedMax;
        private String description;
        private int uvIndexMax;
        private String sunrise;
        private String sunset;

        // Getters and setters
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public double getTempMax() { return tempMax; }
        public void setTempMax(double tempMax) { this.tempMax = tempMax; }
        public double getTempMin() { return tempMin; }
        public void setTempMin(double tempMin) { this.tempMin = tempMin; }
        public double getPrecipitationMm() { return precipitationMm; }
        public void setPrecipitationMm(double precipitationMm) { this.precipitationMm = precipitationMm; }
        public double getPrecipProbability() { return precipProbability; }
        public void setPrecipProbability(double precipProbability) { this.precipProbability = precipProbability; }
        public double getWindSpeedMax() { return windSpeedMax; }
        public void setWindSpeedMax(double windSpeedMax) { this.windSpeedMax = windSpeedMax; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getUvIndexMax() { return uvIndexMax; }
        public void setUvIndexMax(int uvIndexMax) { this.uvIndexMax = uvIndexMax; }
        public String getSunrise() { return sunrise; }
        public void setSunrise(String sunrise) { this.sunrise = sunrise; }
        public String getSunset() { return sunset; }
        public void setSunset(String sunset) { this.sunset = sunset; }
    }

    // Getters and setters
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getFeelsLike() { return feelsLike; }
    public void setFeelsLike(double feelsLike) { this.feelsLike = feelsLike; }
    public double getTempMin() { return tempMin; }
    public void setTempMin(double tempMin) { this.tempMin = tempMin; }
    public double getTempMax() { return tempMax; }
    public void setTempMax(double tempMax) { this.tempMax = tempMax; }
    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    public String getWindDirection() { return windDirection; }
    public void setWindDirection(String windDirection) { this.windDirection = windDirection; }
    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }
    public double getPrecipitationProbability() { return precipitationProbability; }
    public void setPrecipitationProbability(double p) { this.precipitationProbability = p; }
    public int getUvIndex() { return uvIndex; }
    public void setUvIndex(int uvIndex) { this.uvIndex = uvIndex; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPressure() { return pressure; }
    public void setPressure(double pressure) { this.pressure = pressure; }
    public double getCloudCover() { return cloudCover; }
    public void setCloudCover(double cloudCover) { this.cloudCover = cloudCover; }
    public String getSunrise() { return sunrise; }
    public void setSunrise(String sunrise) { this.sunrise = sunrise; }
    public String getSunset() { return sunset; }
    public void setSunset(String sunset) { this.sunset = sunset; }
    public boolean isDay() { return isDay; }
    public void setDay(boolean day) { isDay = day; }
    public int getWeatherCode() { return weatherCode; }
    public void setWeatherCode(int weatherCode) { this.weatherCode = weatherCode; }
    public List<DailyForecast> getForecast() { return forecast; }
    public void setForecast(List<DailyForecast> forecast) { this.forecast = forecast; }
}
