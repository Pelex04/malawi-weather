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

@Repository
public interface WeatherCacheRepository extends JpaRepository<WeatherCache, Long> {

    Optional<WeatherCache> findByDistrictNameIgnoreCaseAndCacheType(
        String districtName, WeatherCache.Type cacheType
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM WeatherCache c WHERE c.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
