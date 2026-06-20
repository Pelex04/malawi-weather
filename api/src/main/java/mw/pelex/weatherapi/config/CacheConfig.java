package mw.pelex.weatherapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * L1 in-memory cache configuration.
 *
 * FIX B13: Original code used a single 30-minute TTL for BOTH caches.
 * Current weather refreshes every 30 min; forecast every 3 hours.
 * A single CaffeineCacheManager cannot set per-cache TTLs, so we register
 * two separate named caches with their own Caffeine spec.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Mirrors OpenMeteoService constants — keep in sync.
    private static final int CURRENT_WEATHER_TTL_MINUTES = 30;
    private static final int FORECAST_TTL_MINUTES        = 180;

    // API key cache: keys change rarely; 5-minute TTL gives fast revocation propagation.
    private static final int API_KEY_TTL_MINUTES         = 5;

    // Districts never change after seeding — cache for the lifetime of the process.
    private static final int DISTRICT_TTL_HOURS          = 24;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setAllowNullValues(false);

        // Register caches with individual specs
        mgr.registerCustomCache("currentWeather",
            Caffeine.newBuilder()
                .expireAfterWrite(CURRENT_WEATHER_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(50)   // 28 districts + headroom
                .recordStats()
                .build());

        mgr.registerCustomCache("forecast",
            Caffeine.newBuilder()
                .expireAfterWrite(FORECAST_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(50)
                .recordStats()
                .build());

        // FIX P1: cache validated API keys so ApiKeyFilter doesn't hit DB on every request
        mgr.registerCustomCache("apiKeys",
            Caffeine.newBuilder()
                .expireAfterWrite(API_KEY_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .build());

        // FIX P3: districts are immutable after seeding — no reason to query DB per request
        mgr.registerCustomCache("districts",
            Caffeine.newBuilder()
                .expireAfterWrite(DISTRICT_TTL_HOURS, TimeUnit.HOURS)
                .maximumSize(50)
                .recordStats()
                .build());

        return mgr;
    }
}
