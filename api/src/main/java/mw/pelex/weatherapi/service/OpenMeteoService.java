package mw.pelex.weatherapi.service;

import mw.pelex.weatherapi.dto.ForecastResponse;
import mw.pelex.weatherapi.dto.WeatherResponse;
import mw.pelex.weatherapi.model.District;
import mw.pelex.weatherapi.model.WeatherCache;
import mw.pelex.weatherapi.repository.WeatherCacheRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenMeteoService {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoService.class);

    // Cache TTL in minutes — current weather refreshes every 30 min, forecast every 3 hours
    private static final int CURRENT_TTL = 30;
    private static final int FORECAST_TTL = 180;

    @Value("${openmeteo.base-url}")
    private String baseUrl;

    private final org.springframework.web.reactive.function.client.WebClient webClient;
    private final WeatherCacheRepository weatherCacheRepository;

    private static final Map<Integer, String> WMO_CODES = Map.ofEntries(
        Map.entry(0,  "Clear sky"),
        Map.entry(1,  "Mainly clear"),
        Map.entry(2,  "Partly cloudy"),
        Map.entry(3,  "Overcast"),
        Map.entry(45, "Foggy"),
        Map.entry(48, "Depositing rime fog"),
        Map.entry(51, "Light drizzle"),
        Map.entry(53, "Moderate drizzle"),
        Map.entry(55, "Dense drizzle"),
        Map.entry(61, "Slight rain"),
        Map.entry(63, "Moderate rain"),
        Map.entry(65, "Heavy rain"),
        Map.entry(71, "Slight snow"),
        Map.entry(73, "Moderate snow"),
        Map.entry(75, "Heavy snow"),
        Map.entry(80, "Slight showers"),
        Map.entry(81, "Moderate showers"),
        Map.entry(82, "Violent showers"),
        Map.entry(95, "Thunderstorm"),
        Map.entry(96, "Thunderstorm with hail"),
        Map.entry(99, "Thunderstorm with heavy hail")
    );

    public OpenMeteoService(
            org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder,
            WeatherCacheRepository weatherCacheRepository) {
        this.webClient = webClientBuilder.build();
        this.weatherCacheRepository = weatherCacheRepository;
    }

    // ── Current weather ──────────────────────────────────────────────────────

    @Cacheable(value = "currentWeather", key = "#district.name")
    public WeatherResponse getCurrentWeather(District district) {
        // L2: check DB cache first (survives Render restarts)
        Optional<WeatherCache> dbCache = weatherCacheRepository
            .findByDistrictNameIgnoreCaseAndCacheType(district.getName(), WeatherCache.Type.CURRENT);

        if (dbCache.isPresent() && !dbCache.get().isExpired()) {
            log.debug("DB cache hit for current weather: {}", district.getName());
            return parseCurrentWeather(dbCache.get().getResponseJson(), district);
        }

        // L3: fetch from Open-Meteo
        try {
            String json = webClient.get()
                .uri(buildCurrentWeatherUrl(district.getLatitude(), district.getLongitude()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            // Persist to DB cache — overwrites stale entry if present
            WeatherCache entry = WeatherCache.of(district.getName(), WeatherCache.Type.CURRENT, json, CURRENT_TTL);
            weatherCacheRepository.save(entry);

            return parseCurrentWeather(json, district);

        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("Open-Meteo rate limited (429) for {}", district.getName());

            // Return stale DB cache rather than failing — stale data > no data
            if (dbCache.isPresent()) {
                log.info("Returning stale DB cache for {} after 429", district.getName());
                WeatherResponse stale = parseCurrentWeather(dbCache.get().getResponseJson(), district);
                stale.setStale(true);
                stale.setStaleSince(dbCache.get().getCachedAt());
                return stale;
            }
            throw new RuntimeException("Weather data temporarily unavailable. Please try again in a moment.");

        } catch (Exception e) {
            log.error("Open-Meteo fetch failed for {}: {}", district.getName(), e.getMessage());

            if (dbCache.isPresent()) {
                log.info("Returning stale DB cache for {} after error", district.getName());
                WeatherResponse stale = parseCurrentWeather(dbCache.get().getResponseJson(), district);
                stale.setStale(true);
                stale.setStaleSince(dbCache.get().getCachedAt());
                return stale;
            }
            throw new RuntimeException("Weather data unavailable for " + district.getName() + ". Please try again shortly.");
        }
    }

    // ── Forecast ─────────────────────────────────────────────────────────────

    @Cacheable(value = "forecast", key = "#district.name")
    public ForecastResponse getForecast(District district) {
        Optional<WeatherCache> dbCache = weatherCacheRepository
            .findByDistrictNameIgnoreCaseAndCacheType(district.getName(), WeatherCache.Type.FORECAST);

        if (dbCache.isPresent() && !dbCache.get().isExpired()) {
            log.debug("DB cache hit for forecast: {}", district.getName());
            return parseForecast(dbCache.get().getResponseJson(), district);
        }

        try {
            String json = webClient.get()
                .uri(buildForecastUrl(district.getLatitude(), district.getLongitude()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            WeatherCache entry = WeatherCache.of(district.getName(), WeatherCache.Type.FORECAST, json, FORECAST_TTL);
            weatherCacheRepository.save(entry);

            return parseForecast(json, district);

        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("Open-Meteo rate limited (429) for forecast {}", district.getName());

            if (dbCache.isPresent()) {
                log.info("Returning stale forecast cache for {} after 429", district.getName());
                ForecastResponse stale = parseForecast(dbCache.get().getResponseJson(), district);
                stale.setStale(true);
                stale.setStaleSince(dbCache.get().getCachedAt());
                return stale;
            }
            throw new RuntimeException("Forecast data temporarily unavailable. Please try again in a moment.");

        } catch (Exception e) {
            log.error("Open-Meteo forecast fetch failed for {}: {}", district.getName(), e.getMessage());

            if (dbCache.isPresent()) {
                ForecastResponse stale = parseForecast(dbCache.get().getResponseJson(), district);
                stale.setStale(true);
                stale.setStaleSince(dbCache.get().getCachedAt());
                return stale;
            }
            throw new RuntimeException("Forecast unavailable for " + district.getName() + ". Please try again shortly.");
        }
    }

    // ── Scheduled cleanup — removes expired DB cache entries nightly ─────────

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void cleanExpiredCache() {
        weatherCacheRepository.deleteExpired(LocalDateTime.now());
        log.info("Cleaned expired weather cache entries");
    }

    // ── URL builders ─────────────────────────────────────────────────────────

    private String buildCurrentWeatherUrl(double lat, double lon) {
        return baseUrl +
            "?latitude=" + lat +
            "&longitude=" + lon +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
            "is_day,precipitation,weather_code,pressure_msl,wind_speed_10m," +
            "wind_direction_10m,cloud_cover,visibility,uv_index" +
            "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
            "&timezone=Africa/Blantyre" +
            "&forecast_days=1";
    }

    private String buildForecastUrl(double lat, double lon) {
        return baseUrl +
            "?latitude=" + lat +
            "&longitude=" + lon +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "precipitation_probability_max,wind_speed_10m_max,weather_code," +
            "uv_index_max,sunrise,sunset" +
            "&timezone=Africa/Blantyre" +
            "&forecast_days=7";
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private WeatherResponse parseCurrentWeather(String jsonStr, District district) {
        JSONObject json = new JSONObject(jsonStr);
        JSONObject current = json.getJSONObject("current");
        JSONObject daily = json.getJSONObject("daily");

        int weatherCode = current.getInt("weather_code");
        double windDeg = current.getDouble("wind_direction_10m");

        return WeatherResponse.builder()
            .district(district.getName())
            .region(district.getRegion())
            .latitude(district.getLatitude())
            .longitude(district.getLongitude())
            .fetchedAt(LocalDateTime.now())
            .temperatureCelsius(current.getDouble("temperature_2m"))
            .feelsLikeCelsius(current.getDouble("apparent_temperature"))
            .temperatureMin(daily.getJSONArray("temperature_2m_min").getDouble(0))
            .temperatureMax(daily.getJSONArray("temperature_2m_max").getDouble(0))
            .humidity(current.getInt("relative_humidity_2m"))
            .windSpeedKph(current.getDouble("wind_speed_10m"))
            .windDirection(getWindDirection(windDeg))
            .precipitationMm(current.getDouble("precipitation"))
            .precipitationProbability(daily.getJSONArray("precipitation_probability_max").getDouble(0))
            .uvIndex(current.optInt("uv_index", 0))
            .weatherDescription(WMO_CODES.getOrDefault(weatherCode, "Unknown"))
            .weatherCode(String.valueOf(weatherCode))
            .isDay(current.getInt("is_day") == 1)
            .pressureHpa(current.getDouble("pressure_msl"))
            .visibilityKm(current.optDouble("visibility", 0) / 1000)
            .cloudCoverPercent(current.getDouble("cloud_cover"))
            .sunrise(daily.getJSONArray("sunrise").getString(0))
            .sunset(daily.getJSONArray("sunset").getString(0))
            .build();
    }

    private ForecastResponse parseForecast(String jsonStr, District district) {
        JSONObject json = new JSONObject(jsonStr);
        JSONObject daily = json.getJSONObject("daily");

        JSONArray dates      = daily.getJSONArray("time");
        JSONArray maxTemps   = daily.getJSONArray("temperature_2m_max");
        JSONArray minTemps   = daily.getJSONArray("temperature_2m_min");
        JSONArray precip     = daily.getJSONArray("precipitation_sum");
        JSONArray precipProb = daily.getJSONArray("precipitation_probability_max");
        JSONArray windSpeed  = daily.getJSONArray("wind_speed_10m_max");
        JSONArray codes      = daily.getJSONArray("weather_code");
        JSONArray uvIndex    = daily.getJSONArray("uv_index_max");
        JSONArray sunrise    = daily.getJSONArray("sunrise");
        JSONArray sunset     = daily.getJSONArray("sunset");

        List<ForecastResponse.DailyForecast> forecastList = new ArrayList<>();
        for (int i = 0; i < dates.length(); i++) {
            int code = codes.getInt(i);
            forecastList.add(ForecastResponse.DailyForecast.builder()
                .date(dates.getString(i))
                .temperatureMaxCelsius(maxTemps.getDouble(i))
                .temperatureMinCelsius(minTemps.getDouble(i))
                .precipitationMm(precip.getDouble(i))
                .precipitationProbabilityMax(precipProb.getDouble(i))
                .windSpeedMaxKph(windSpeed.getDouble(i))
                .weatherDescription(WMO_CODES.getOrDefault(code, "Unknown"))
                .uvIndexMax(uvIndex.getInt(i))
                .sunrise(sunrise.getString(i))
                .sunset(sunset.getString(i))
                .build());
        }

        return ForecastResponse.builder()
            .district(district.getName())
            .region(district.getRegion())
            .latitude(district.getLatitude())
            .longitude(district.getLongitude())
            .fetchedAt(LocalDateTime.now())
            .forecast(forecastList)
            .build();
    }

    private String getWindDirection(double degrees) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int) Math.round(((degrees % 360) / 22.5)) % 16];
    }
}
