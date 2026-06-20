package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.District;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {

    /**
     * FIX P3: Districts are seeded once and never change.
     * Caching with a 24-hour TTL (see CacheConfig) eliminates the DB round-trip
     * on every single weather request — the busiest query in the entire API.
     */
    @Cacheable(value = "districts", key = "#name.toLowerCase()")
    Optional<District> findByNameIgnoreCase(String name);

    @Cacheable(value = "districts", key = "'all'")
    List<District> findAllByOrderByNameAsc();
}
