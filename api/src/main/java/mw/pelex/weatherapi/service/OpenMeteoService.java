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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenMeteoService {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoService.class);

    // FIX B13: separate TTL constants — CacheConfig now also uses these values.
    // Current weather: fresh every 30 minutes.
    // Forecast:        fresh every 3 hours (180 min) — daily data changes slowly.
    static final int CURRENT_TTL_MINUTES  = 30;
    static final int FORECAST_TTL_MINUTES = 180;

    // FIX S7: hard timeout prevents thread starvation when Open-Meteo is slow.
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Value("${openmeteo.base-url}")
    private String baseUrl;

    private final WebClient                webClient;
    private final WeatherCacheRepository   weatherCacheRepository;

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

    public OpenMeteoService(WebClient.Builder webClientBuilder,
                            WeatherCacheRepository weatherCacheRepository) {
        this.webClient             = webClientBuilder
            .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024)) // 512 KB cap
            .build();
        this.weatherCacheRepository = weatherCacheRepository;
    }

    // ── Current weather ───────────────────────────────────────────────────────

    @Cacheable(value = "currentWeather", key = "#district.name.toLowerCase()")
    public WeatherResponse getCurrentWeather(District district) {
        Optional<WeatherCache> dbCache = weatherCacheRepository
            .findByDistrictNameIgnoreCaseAndCacheType(district.getName(), WeatherCache.Type.CURRENT);

        if (dbCache.isPresent() && !dbCache.get().isExpired()) {
            log.debug("L2 cache hit — current weather: {}", district.getName());
            return parseCurrentWeather(dbCache.get().getResponseJson(), district);
        }

        try {
            String json = fetchFromUpstream(buildCurrentWeatherUrl(district));
            upsertDbCache(district.getName(), WeatherCache.Type.CURRENT, json, CURRENT_TTL_MINUTES);
            return parseCurrentWeather(json, district);

        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("Open-Meteo 429 — current weather: {}", district.getName());
            return dbCache.map(c -> staleCurrentWeather(c, district))
                .orElseThrow(() -> new WeatherUnavailableException(
                    "Weather data temporarily unavailable for " + district.getName() + ". Please retry shortly."));

        } catch (Exception e) {
            log.error("Open-Meteo fetch failed — current weather {}: {}", district.getName(), e.getMessage());
            return dbCache.map(c -> staleCurrentWeather(c, district))
                .orElseThrow(() -> new WeatherUnavailableException(
                    "Weather data unavailable for " + district.getName() + ". Please retry shortly."));
        }
    }

    // ── Forecast ──────────────────────────────────────────────────────────────

    @Cacheable(value = "forecast", key = "#district.name.toLowerCase()")
    public ForecastResponse getForecast(District district) {
        Optional<WeatherCache> dbCache = weatherCacheRepository
            .findByDistrictNameIgnoreCaseAndCacheType(district.getName(), WeatherCache.Type.FORECAST);

        if (dbCache.isPresent() && !dbCache.get().isExpired()) {
            log.debug("L2 cache hit — forecast: {}", district.getName());
            return parseForecast(dbCache.get().getResponseJson(), district);
        }

        try {
            String json = fetchFromUpstream(buildForecastUrl(district));
            upsertDbCache(district.getName(), WeatherCache.Type.FORECAST, json, FORECAST_TTL_MINUTES);
            return parseForecast(json, district);

        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("Open-Meteo 429 — forecast: {}", district.getName());
            return dbCache.map(c -> staleForecast(c, district))
                .orElseThrow(() -> new WeatherUnavailableException(
                    "Forecast temporarily unavailable for " + district.getName() + ". Please retry shortly."));

        } catch (Exception e) {
            log.error("Open-Meteo fetch failed — forecast {}: {}", district.getName(), e.getMessage());
            return dbCache.map(c -> staleForecast(c, district))
                .orElseThrow(() -> new WeatherUnavailableException(
                    "Forecast unavailable for " + district.getName() + ". Please retry shortly."));
        }
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanExpiredCache() {
        weatherCacheRepository.deleteExpired(LocalDateTime.now());
        log.info("Cleaned expired weather cache entries");
    }

    // ── HTTP fetch ────────────────────────────────────────────────────────────

    private String fetchFromUpstream(String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(HTTP_TIMEOUT)   // FIX S7 — never hang indefinitely
            .block();
    }

    // ── DB cache write (FIX B5) ───────────────────────────────────────────────
    //
    // BEFORE: always called save() which INSERTs a new row — duplicates possible under
    //         concurrent requests that both miss Caffeine and the DB simultaneously.
    // NOW:    find-then-update (UPSERT semantics). A DB UNIQUE constraint on
    //         (district_name, cache_type) provides the final safety net.
    //         See WeatherCache entity and the migration note below.

    @Transactional
    protected void upsertDbCache(String districtName, WeatherCache.Type type,
                                 String json, int ttlMinutes) {
        WeatherCache entry = weatherCacheRepository
            .findByDistrictNameIgnoreCaseAndCacheType(districtName, type)
            .orElseGet(WeatherCache::new);

        entry.setDistrictName(districtName.toLowerCase());
        entry.setCacheType(type);
        entry.setResponseJson(json);
        entry.setCachedAt(LocalDateTime.now());
        entry.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        weatherCacheRepository.save(entry);
    }

    // ── Stale-cache helpers ───────────────────────────────────────────────────

    private WeatherResponse staleCurrentWeather(WeatherCache cache, District district) {
        WeatherResponse resp = parseCurrentWeather(cache.getResponseJson(), district);
        resp.setStale(true);
        resp.setStaleSince(cache.getCachedAt());
        return resp;
    }

    private ForecastResponse staleForecast(WeatherCache cache, District district) {
        ForecastResponse resp = parseForecast(cache.getResponseJson(), district);
        resp.setStale(true);
        resp.setStaleSince(cache.getCachedAt());
        return resp;
    }

    // ── URL builders ──────────────────────────────────────────────────────────

    private String buildCurrentWeatherUrl(District d) {
        return baseUrl
            + "?latitude=" + d.getLatitude()
            + "&longitude=" + d.getLongitude()
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "is_day,precipitation,weather_code,pressure_msl,wind_speed_10m,"
            + "wind_direction_10m,cloud_cover,visibility,uv_index"
            + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
            + "&timezone=Africa/Blantyre"
            + "&forecast_days=1";
    }

    private String buildForecastUrl(District d) {
        return baseUrl
            + "?latitude=" + d.getLatitude()
            + "&longitude=" + d.getLongitude()
            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"
            + "precipitation_probability_max,wind_speed_10m_max,weather_code,"
            + "uv_index_max,sunrise,sunset"
            + "&timezone=Africa/Blantyre"
            + "&forecast_days=7";
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private WeatherResponse parseCurrentWeather(String jsonStr, District district) {
        JSONObject json    = new JSONObject(jsonStr);
        JSONObject current = json.getJSONObject("current");
        JSONObject daily   = json.getJSONObject("daily");

        int    weatherCode = current.getInt("weather_code");
        double windDeg     = current.getDouble("wind_direction_10m");

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
            .windDirection(compassDirection(windDeg))
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
        JSONObject json  = new JSONObject(jsonStr);
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

    private static String compassDirection(double degrees) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                         "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int) Math.round(((degrees % 360) / 22.5)) % 16];
    }

    // ── Typed exception ───────────────────────────────────────────────────────

    public static class WeatherUnavailableException extends RuntimeException {
        public WeatherUnavailableException(String message) { super(message); }
    }
}
