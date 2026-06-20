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
import java.util.*;

/**
 * Multi-source weather service.
 *
 * Fetch priority (configurable via WEATHER_SOURCE_STRATEGY):
 *   OPEN_METEO_FIRST (default):
 *     L1 Caffeine → L2 DB cache → L3 Open-Meteo primary → L4 WeatherAPI.com fallback → L5 stale cache
 *
 *   WEATHERAPI_FIRST:
 *     L1 Caffeine → L2 DB cache → L3 WeatherAPI.com → L4 Open-Meteo fallback → L5 stale cache
 *
 * Neon resilience: all DB operations are wrapped in try/catch so that if Neon
 * is suspended and the connection times out, the app still serves data from
 * the L1 Caffeine cache and/or fresh upstream fetches.
 */
@Service
public class WeatherSourceService {

    private static final Logger log = LoggerFactory.getLogger(WeatherSourceService.class);

    static final int CURRENT_TTL_MINUTES  = 30;
    static final int FORECAST_TTL_MINUTES = 180;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    // ── Config ─────────────────────────────────────────────────────────────────

    @Value("${openmeteo.base-url}")
    private String openMeteoUrl;

    @Value("${weatherapi.base-url:https://api.weatherapi.com/v1}")
    private String weatherApiUrl;

    @Value("${weatherapi.key:}")
    private String weatherApiKey;

    @Value("${weatherapi.enabled:false}")
    private boolean weatherApiEnabled;

    @Value("${weather.source.strategy:OPEN_METEO_FIRST}")
    private String strategy;

    private final WebClient webClient;
    private final WeatherCacheRepository weatherCacheRepository;

    // WMO weather interpretation codes → human description
    private static final Map<Integer, String> WMO_CODES = Map.ofEntries(
        Map.entry(0,  "Clear sky"),       Map.entry(1,  "Mainly clear"),
        Map.entry(2,  "Partly cloudy"),   Map.entry(3,  "Overcast"),
        Map.entry(45, "Foggy"),           Map.entry(48, "Depositing rime fog"),
        Map.entry(51, "Light drizzle"),   Map.entry(53, "Moderate drizzle"),
        Map.entry(55, "Dense drizzle"),   Map.entry(61, "Slight rain"),
        Map.entry(63, "Moderate rain"),   Map.entry(65, "Heavy rain"),
        Map.entry(71, "Slight snow"),     Map.entry(73, "Moderate snow"),
        Map.entry(75, "Heavy snow"),      Map.entry(80, "Slight showers"),
        Map.entry(81, "Moderate showers"),Map.entry(82, "Violent showers"),
        Map.entry(95, "Thunderstorm"),    Map.entry(96, "Thunderstorm with hail"),
        Map.entry(99, "Thunderstorm with heavy hail")
    );

    public WeatherSourceService(WebClient.Builder builder,
                                WeatherCacheRepository weatherCacheRepository) {
        this.webClient = builder
            .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024))
            .build();
        this.weatherCacheRepository = weatherCacheRepository;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    @Cacheable(value = "currentWeather", key = "#district.name.toLowerCase()")
    public WeatherResponse getCurrentWeather(District district) {
        // L2: DB cache (Neon-safe — wrapped in try/catch)
        Optional<WeatherCache> dbCache = safeDbLookup(district.getName(), WeatherCache.Type.CURRENT);
        if (dbCache.isPresent() && !dbCache.get().isExpired()) {
            log.debug("L2 cache hit — current weather: {}", district.getName());
            return parseOpenMeteoCurrentWeather(dbCache.get().getResponseJson(), district);
        }

        // L3+L4: try upstream sources in strategy order
        List<UpstreamAttempt> attempts = sourceOrder();
        Exception lastError = null;

        for (UpstreamAttempt attempt : attempts) {
            try {
                String json = attempt.fetch(district);
                safeDbUpsert(district.getName(), WeatherCache.Type.CURRENT, json, CURRENT_TTL_MINUTES);
                log.info("Weather fetched from {} for {}", attempt.name(), district.getName());
                return attempt.parseCurrentWeather(json, district);
            } catch (Exception e) {
                log.warn("Source {} failed for {}: {}", attempt.name(), district.getName(), e.getMessage());
                lastError = e;
            }
        }

        // L5: serve stale cache if all sources fail
        if (dbCache.isPresent()) {
            log.warn("All sources failed — serving stale cache for {}", district.getName());
            WeatherResponse resp = parseOpenMeteoCurrentWeather(dbCache.get().getResponseJson(), district);
            resp.setStale(true);
            resp.setStaleSince(dbCache.get().getCachedAt());
            return resp;
        }

        throw new WeatherUnavailableException(
            "Weather data temporarily unavailable for " + district.getName() + ". Please try again shortly.");
    }

    @Cacheable(value = "forecast", key = "#district.name.toLowerCase()")
    public ForecastResponse getForecast(District district) {
        Optional<WeatherCache> dbCache = safeDbLookup(district.getName(), WeatherCache.Type.FORECAST);
        if (dbCache.isPresent() && !dbCache.get().isExpired()) {
            log.debug("L2 cache hit — forecast: {}", district.getName());
            return parseOpenMeteoForecast(dbCache.get().getResponseJson(), district);
        }

        List<UpstreamAttempt> attempts = sourceOrder();
        Exception lastError = null;

        for (UpstreamAttempt attempt : attempts) {
            try {
                String json = attempt.fetchForecast(district);
                safeDbUpsert(district.getName(), WeatherCache.Type.FORECAST, json, FORECAST_TTL_MINUTES);
                log.info("Forecast fetched from {} for {}", attempt.name(), district.getName());
                return attempt.parseForecast(json, district);
            } catch (Exception e) {
                log.warn("Source {} forecast failed for {}: {}", attempt.name(), district.getName(), e.getMessage());
                lastError = e;
            }
        }

        if (dbCache.isPresent()) {
            log.warn("All forecast sources failed — serving stale cache for {}", district.getName());
            ForecastResponse resp = parseOpenMeteoForecast(dbCache.get().getResponseJson(), district);
            resp.setStale(true);
            resp.setStaleSince(dbCache.get().getCachedAt());
            return resp;
        }

        throw new WeatherUnavailableException(
            "Forecast temporarily unavailable for " + district.getName() + ". Please try again shortly.");
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanExpiredCache() {
        try {
            weatherCacheRepository.deleteExpired(LocalDateTime.now());
            log.info("Cleaned expired weather cache entries");
        } catch (Exception e) {
            log.warn("Cache cleanup failed (Neon may be suspended): {}", e.getMessage());
        }
    }

    // ── Source ordering ────────────────────────────────────────────────────────

    private List<UpstreamAttempt> sourceOrder() {
        UpstreamAttempt openMeteo   = new OpenMeteoAttempt();
        UpstreamAttempt weatherApi  = new WeatherApiAttempt();

        if ("WEATHERAPI_FIRST".equalsIgnoreCase(strategy) && weatherApiEnabled && !weatherApiKey.isBlank()) {
            return List.of(weatherApi, openMeteo);
        }
        // Default: Open-Meteo first, WeatherAPI as fallback (if enabled + key provided)
        if (weatherApiEnabled && !weatherApiKey.isBlank()) {
            return List.of(openMeteo, weatherApi);
        }
        return List.of(openMeteo);
    }

    // ── Neon-safe DB operations ───────────────────────────────────────────────
    //
    // Neon pauses compute after a period of inactivity. The first connection
    // after wake-up can take up to 5 seconds and may timeout. Rather than
    // crashing the weather endpoint when Neon is cold, we catch any DB exception,
    // log it, and let the caller fall through to upstream fetches.

    private Optional<WeatherCache> safeDbLookup(String districtName, WeatherCache.Type type) {
        try {
            return weatherCacheRepository.findByDistrictNameIgnoreCaseAndCacheType(districtName, type);
        } catch (Exception e) {
            log.warn("DB cache lookup failed (Neon may be waking up): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    protected void safeDbUpsert(String districtName, WeatherCache.Type type,
                                String json, int ttlMinutes) {
        try {
            WeatherCache entry = weatherCacheRepository
                .findByDistrictNameIgnoreCaseAndCacheType(districtName, type)
                .orElseGet(WeatherCache::new);

            entry.setDistrictName(districtName.toLowerCase());
            entry.setCacheType(type);
            entry.setResponseJson(json);
            entry.setCachedAt(LocalDateTime.now());
            entry.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
            weatherCacheRepository.save(entry);
        } catch (Exception e) {
            log.warn("DB cache write failed (Neon may be waking up) — data still served from memory: {}", e.getMessage());
        }
    }

    // ── HTTP fetch ─────────────────────────────────────────────────────────────

    private String httpGet(String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(HTTP_TIMEOUT)
            .block();
    }

    // ── Open-Meteo source ─────────────────────────────────────────────────────

    private String buildOpenMeteoCurrentUrl(District d) {
        return openMeteoUrl
            + "?latitude=" + d.getLatitude()
            + "&longitude=" + d.getLongitude()
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "is_day,precipitation,weather_code,pressure_msl,wind_speed_10m,"
            + "wind_direction_10m,cloud_cover,visibility,uv_index"
            + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
            + "&timezone=Africa/Blantyre&forecast_days=1";
    }

    private String buildOpenMeteoForecastUrl(District d) {
        return openMeteoUrl
            + "?latitude=" + d.getLatitude()
            + "&longitude=" + d.getLongitude()
            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"
            + "precipitation_probability_max,wind_speed_10m_max,weather_code,"
            + "uv_index_max,sunrise,sunset"
            + "&timezone=Africa/Blantyre&forecast_days=7";
    }

    private WeatherResponse parseOpenMeteoCurrentWeather(String jsonStr, District district) {
        JSONObject json    = new JSONObject(jsonStr);
        JSONObject current = json.getJSONObject("current");
        JSONObject daily   = json.getJSONObject("daily");
        int code = current.getInt("weather_code");

        return WeatherResponse.builder()
            .district(district.getName()).region(district.getRegion())
            .latitude(district.getLatitude()).longitude(district.getLongitude())
            .fetchedAt(LocalDateTime.now()).dataSource("Open-Meteo")
            .temperatureCelsius(current.getDouble("temperature_2m"))
            .feelsLikeCelsius(current.getDouble("apparent_temperature"))
            .temperatureMin(daily.getJSONArray("temperature_2m_min").getDouble(0))
            .temperatureMax(daily.getJSONArray("temperature_2m_max").getDouble(0))
            .humidity(current.getInt("relative_humidity_2m"))
            .windSpeedKph(current.getDouble("wind_speed_10m"))
            .windDirection(compassDirection(current.getDouble("wind_direction_10m")))
            .precipitationMm(current.getDouble("precipitation"))
            .precipitationProbability(daily.getJSONArray("precipitation_probability_max").getDouble(0))
            .uvIndex(current.optInt("uv_index", 0))
            .weatherDescription(WMO_CODES.getOrDefault(code, "Unknown"))
            .weatherCode(String.valueOf(code))
            .isDay(current.getInt("is_day") == 1)
            .pressureHpa(current.getDouble("pressure_msl"))
            .visibilityKm(current.optDouble("visibility", 0) / 1000)
            .cloudCoverPercent(current.getDouble("cloud_cover"))
            .sunrise(daily.getJSONArray("sunrise").getString(0))
            .sunset(daily.getJSONArray("sunset").getString(0))
            .build();
    }

    private ForecastResponse parseOpenMeteoForecast(String jsonStr, District district) {
        JSONObject json  = new JSONObject(jsonStr);
        JSONObject daily = json.getJSONObject("daily");
        JSONArray dates  = daily.getJSONArray("time");
        JSONArray maxT   = daily.getJSONArray("temperature_2m_max");
        JSONArray minT   = daily.getJSONArray("temperature_2m_min");
        JSONArray precip = daily.getJSONArray("precipitation_sum");
        JSONArray precipP= daily.getJSONArray("precipitation_probability_max");
        JSONArray wind   = daily.getJSONArray("wind_speed_10m_max");
        JSONArray codes  = daily.getJSONArray("weather_code");
        JSONArray uv     = daily.getJSONArray("uv_index_max");
        JSONArray rise   = daily.getJSONArray("sunrise");
        JSONArray set    = daily.getJSONArray("sunset");

        List<ForecastResponse.DailyForecast> list = new ArrayList<>();
        for (int i = 0; i < dates.length(); i++) {
            int c = codes.getInt(i);
            list.add(ForecastResponse.DailyForecast.builder()
                .date(dates.getString(i))
                .temperatureMaxCelsius(maxT.getDouble(i))
                .temperatureMinCelsius(minT.getDouble(i))
                .precipitationMm(precip.getDouble(i))
                .precipitationProbabilityMax(precipP.getDouble(i))
                .windSpeedMaxKph(wind.getDouble(i))
                .weatherDescription(WMO_CODES.getOrDefault(c, "Unknown"))
                .uvIndexMax(uv.getInt(i))
                .sunrise(rise.getString(i))
                .sunset(set.getString(i))
                .build());
        }
        return ForecastResponse.builder()
            .district(district.getName()).region(district.getRegion())
            .latitude(district.getLatitude()).longitude(district.getLongitude())
            .fetchedAt(LocalDateTime.now()).dataSource("Open-Meteo")
            .forecast(list).build();
    }

    // ── WeatherAPI.com source ─────────────────────────────────────────────────

    private WeatherResponse parseWeatherApiCurrent(String jsonStr, District district) {
        JSONObject json     = new JSONObject(jsonStr);
        JSONObject current  = json.getJSONObject("current");
        JSONObject condition= current.getJSONObject("condition");
        JSONObject forecast = json.optJSONObject("forecast");
        JSONObject day0     = null;
        if (forecast != null) {
            JSONArray forecastDay = forecast.getJSONArray("forecastday");
            if (!forecastDay.isEmpty()) day0 = forecastDay.getJSONObject(0).getJSONObject("day");
        }
        // WeatherAPI returns km/h for wind, mm for precip — matches our schema
        return WeatherResponse.builder()
            .district(district.getName()).region(district.getRegion())
            .latitude(district.getLatitude()).longitude(district.getLongitude())
            .fetchedAt(LocalDateTime.now()).dataSource("WeatherAPI")
            .temperatureCelsius(current.getDouble("temp_c"))
            .feelsLikeCelsius(current.getDouble("feelslike_c"))
            .temperatureMin(day0 != null ? day0.getDouble("mintemp_c") : current.getDouble("temp_c"))
            .temperatureMax(day0 != null ? day0.getDouble("maxtemp_c") : current.getDouble("temp_c"))
            .humidity(current.getInt("humidity"))
            .windSpeedKph(current.getDouble("wind_kph"))
            .windDirection(current.getString("wind_dir"))
            .precipitationMm(current.getDouble("precip_mm"))
            .precipitationProbability(day0 != null ? day0.optDouble("daily_chance_of_rain", 0) : 0)
            .uvIndex(current.optInt("uv", 0))
            .weatherDescription(condition.getString("text"))
            .weatherCode(String.valueOf(condition.getInt("code")))
            .isDay(current.getInt("is_day") == 1)
            .pressureHpa(current.getDouble("pressure_mb"))
            .visibilityKm(current.getDouble("vis_km"))
            .cloudCoverPercent(current.getDouble("cloud"))
            .sunrise(day0 != null ? json.getJSONObject("forecast").getJSONArray("forecastday").getJSONObject(0).getJSONObject("astro").getString("sunrise") : "")
            .sunset(day0 != null ? json.getJSONObject("forecast").getJSONArray("forecastday").getJSONObject(0).getJSONObject("astro").getString("sunset") : "")
            .build();
    }

    private ForecastResponse parseWeatherApiForecast(String jsonStr, District district) {
        JSONObject json     = new JSONObject(jsonStr);
        JSONArray forecastDay = json.getJSONObject("forecast").getJSONArray("forecastday");

        List<ForecastResponse.DailyForecast> list = new ArrayList<>();
        for (int i = 0; i < forecastDay.length(); i++) {
            JSONObject fd   = forecastDay.getJSONObject(i);
            JSONObject day  = fd.getJSONObject("day");
            JSONObject astro= fd.getJSONObject("astro");
            list.add(ForecastResponse.DailyForecast.builder()
                .date(fd.getString("date"))
                .temperatureMaxCelsius(day.getDouble("maxtemp_c"))
                .temperatureMinCelsius(day.getDouble("mintemp_c"))
                .precipitationMm(day.getDouble("totalprecip_mm"))
                .precipitationProbabilityMax(day.optDouble("daily_chance_of_rain", 0))
                .windSpeedMaxKph(day.getDouble("maxwind_kph"))
                .weatherDescription(day.getJSONObject("condition").getString("text"))
                .uvIndexMax((int) day.optDouble("uv", 0))
                .sunrise(astro.getString("sunrise"))
                .sunset(astro.getString("sunset"))
                .build());
        }
        return ForecastResponse.builder()
            .district(district.getName()).region(district.getRegion())
            .latitude(district.getLatitude()).longitude(district.getLongitude())
            .fetchedAt(LocalDateTime.now()).dataSource("WeatherAPI")
            .forecast(list).build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String compassDirection(double deg) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                         "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int) Math.round(((deg % 360) / 22.5)) % 16];
    }

    // ── Strategy interface ─────────────────────────────────────────────────────

    private interface UpstreamAttempt {
        String name();
        String fetch(District d) throws Exception;
        String fetchForecast(District d) throws Exception;
        WeatherResponse parseCurrentWeather(String json, District d);
        ForecastResponse parseForecast(String json, District d);
    }

    private class OpenMeteoAttempt implements UpstreamAttempt {
        public String name() { return "Open-Meteo"; }
        public String fetch(District d) { return httpGet(buildOpenMeteoCurrentUrl(d)); }
        public String fetchForecast(District d) { return httpGet(buildOpenMeteoForecastUrl(d)); }
        public WeatherResponse parseCurrentWeather(String j, District d) { return parseOpenMeteoCurrentWeather(j, d); }
        public ForecastResponse parseForecast(String j, District d) { return parseOpenMeteoForecast(j, d); }
    }

    private class WeatherApiAttempt implements UpstreamAttempt {
        public String name() { return "WeatherAPI.com"; }
        public String fetch(District d) {
            return httpGet(weatherApiUrl + "/forecast.json?key=" + weatherApiKey
                + "&q=" + d.getLatitude() + "," + d.getLongitude()
                + "&days=1&aqi=no&alerts=no");
        }
        public String fetchForecast(District d) {
            return httpGet(weatherApiUrl + "/forecast.json?key=" + weatherApiKey
                + "&q=" + d.getLatitude() + "," + d.getLongitude()
                + "&days=7&aqi=no&alerts=no");
        }
        public WeatherResponse parseCurrentWeather(String j, District d) { return parseWeatherApiCurrent(j, d); }
        public ForecastResponse parseForecast(String j, District d) { return parseWeatherApiForecast(j, d); }
    }

    // ── Typed exception ───────────────────────────────────────────────────────

    public static class WeatherUnavailableException extends RuntimeException {
        public WeatherUnavailableException(String message) { super(message); }
    }
}
