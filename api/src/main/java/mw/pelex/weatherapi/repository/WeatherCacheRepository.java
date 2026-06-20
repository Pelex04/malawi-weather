package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.WeatherCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * FIX B5 — prevent duplicate cache rows.
 *
 * MIGRATION REQUIRED:
 *   ALTER TABLE weather_cache
 *     ADD CONSTRAINT uq_weather_cache_district_type
 *     UNIQUE (district_name, cache_type);
 *
 * The service layer now does find-then-update (UPSERT semantics).
 * The DB UNIQUE constraint is the final safety net for concurrent inserts.
 */
@Repository
public interface WeatherCacheRepository extends JpaRepository<WeatherCache, Long> {

    Optional<WeatherCache> findByDistrictNameIgnoreCaseAndCacheType(
        String districtName, WeatherCache.Type cacheType);

    /**
     * Scheduled cleanup — removes all rows whose expiresAt < now.
     * Called nightly via @Scheduled in OpenMeteoService.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM WeatherCache c WHERE c.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
