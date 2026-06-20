package mw.pelex.weatherapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeatherResponse {

    private String        district;
    private String        region;
    private Double        latitude;
    private Double        longitude;
    private LocalDateTime fetchedAt;

    /**
     * Which upstream provider served this response.
     * One of: "Open-Meteo", "WeatherAPI"
     * Helps with debugging and lets consumers know which source was used.
     */
    private String dataSource;

    // ── Current conditions ─────────────────────────────────────────────────────

    private Double  temperatureCelsius;
    private Double  feelsLikeCelsius;
    private Double  temperatureMin;
    private Double  temperatureMax;
    private Integer humidity;
    private Double  windSpeedKph;
    private String  windDirection;
    private Double  precipitationMm;
    private Double  precipitationProbability;
    private Integer uvIndex;
    private String  weatherDescription;
    private String  weatherCode;
    private Boolean isDay;

    // ── Atmospheric ────────────────────────────────────────────────────────────

    private Double pressureHpa;
    private Double visibilityKm;
    private Double cloudCoverPercent;

    // ── Sun ────────────────────────────────────────────────────────────────────

    private String sunrise;
    private String sunset;

    // ── Stale cache indicator ──────────────────────────────────────────────────
    // Present only when all live sources failed and the response is from a stale
    // DB cache. Consumers should treat the data as approximate.

    private Boolean       stale;
    private LocalDateTime staleSince;
}
