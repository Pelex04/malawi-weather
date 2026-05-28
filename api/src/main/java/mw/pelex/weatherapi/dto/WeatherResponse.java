package mw.pelex.weatherapi.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class WeatherResponse {

    private String district;
    private String region;
    private Double latitude;
    private Double longitude;
    private LocalDateTime fetchedAt;

    // Current conditions
    private Double temperatureCelsius;
    private Double feelsLikeCelsius;
    private Double temperatureMin;
    private Double temperatureMax;
    private Integer humidity;
    private Double windSpeedKph;
    private String windDirection;
    private Double precipitationMm;
    private Double precipitationProbability;
    private Integer uvIndex;
    private String weatherDescription;
    private String weatherCode; // WMO code mapped to description
    private Boolean isDay;

    // Atmospheric
    private Double pressureHpa;
    private Double visibilityKm;
    private Double cloudCoverPercent;

    // Sun
    private String sunrise;
    private String sunset;
}
