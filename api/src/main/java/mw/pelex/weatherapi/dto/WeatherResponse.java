package mw.pelex.weatherapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private String weatherCode;
    private Boolean isDay;

    // Atmospheric
    private Double pressureHpa;
    private Double visibilityKm;
    private Double cloudCoverPercent;

    // Sun
    private String sunrise;
    private String sunset;

    /**
     * Present only when the response is served from a stale cache
     * (e.g. after an Open-Meteo 429). Consumers should treat the data
     * as approximate and check staleSince for how old it is.
     */
    private Boolean stale;
    private LocalDateTime staleSince;
}
