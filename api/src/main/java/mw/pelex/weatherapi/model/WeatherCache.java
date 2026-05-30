package mw.pelex.weatherapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistent weather cache stored in PostgreSQL.
 *
 * Survives Render free-tier restarts (unlike in-memory Caffeine cache).
 * Caffeine is still used as the L1 cache for hot requests within a running
 * instance — this table is the L2 / cold-start fallback.
 */
@Entity
@Table(name = "weather_cache", indexes = {
    @Index(name = "idx_weather_cache_district_type", columnList = "district_name, cache_type")
})
@Data
@NoArgsConstructor
public class WeatherCache {

    public enum Type { CURRENT, FORECAST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "district_name", nullable = false, length = 100)
    private String districtName;

    @Enumerated(EnumType.STRING)
    @Column(name = "cache_type", nullable = false, length = 10)
    private Type cacheType;

    /** Full JSON response from Open-Meteo, stored as text */
    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "cached_at", nullable = false)
    private LocalDateTime cachedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public static WeatherCache of(String districtName, Type type, String json, int ttlMinutes) {
        WeatherCache c = new WeatherCache();
        c.setDistrictName(districtName.toLowerCase());
        c.setCacheType(type);
        c.setResponseJson(json);
        c.setCachedAt(LocalDateTime.now());
        c.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        return c;
    }
}
