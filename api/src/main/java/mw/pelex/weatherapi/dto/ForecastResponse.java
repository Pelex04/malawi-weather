package mw.pelex.weatherapi.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.time.LocalDateTime;

@Data
@Builder
public class ForecastResponse {

    private String district;
    private String region;
    private Double latitude;
    private Double longitude;
    private LocalDateTime fetchedAt;
    private List<DailyForecast> forecast;

    @Data
    @Builder
    public static class DailyForecast {
        private String date;
        private Double temperatureMaxCelsius;
        private Double temperatureMinCelsius;
        private Double precipitationMm;
        private Double precipitationProbabilityMax;
        private Double windSpeedMaxKph;
        private String weatherDescription;
        private Integer uvIndexMax;
        private String sunrise;
        private String sunset;
    }
}
