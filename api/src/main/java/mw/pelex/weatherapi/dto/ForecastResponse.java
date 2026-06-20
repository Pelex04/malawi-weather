package mw.pelex.weatherapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForecastResponse {

    private String        district;
    private String        region;
    private Double        latitude;
    private Double        longitude;
    private LocalDateTime fetchedAt;

    /** Which upstream provider served this response: "Open-Meteo" or "WeatherAPI" */
    private String dataSource;

    private List<DailyForecast> forecast;

    /** Present only when served from stale cache after all sources failed */
    private Boolean       stale;
    private LocalDateTime staleSince;

    @Data
    @Builder
    public static class DailyForecast {
        private String  date;
        private Double  temperatureMaxCelsius;
        private Double  temperatureMinCelsius;
        private Double  precipitationMm;
        private Double  precipitationProbabilityMax;
        private Double  windSpeedMaxKph;
        private String  weatherDescription;
        private Integer uvIndexMax;
        private String  sunrise;
        private String  sunset;
    }
}
