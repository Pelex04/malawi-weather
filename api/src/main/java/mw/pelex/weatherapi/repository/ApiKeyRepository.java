package mw.pelex.weatherapi.repository;

// ─────────────────────────────────────────────────────────────────────────────
//  ApiKeyRepository
// ─────────────────────────────────────────────────────────────────────────────

import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyValueAndIsActiveTrue(String keyValue);

    List<ApiKey> findByDeveloperId(Long developerId);

    Optional<ApiKey> findFirstByDeveloperIdOrderByCreatedAtDesc(Long developerId);

    /**
     * FIX P2: count today's usage for a given key.
     *
     * MIGRATION REQUIRED — add a partial index in your Flyway/Liquibase migration:
     *   CREATE INDEX idx_usage_logs_key_today
     *     ON usage_logs (api_key_id, timestamp)
     *     WHERE timestamp >= CURRENT_DATE;
     *
     * This turns a full-table scan into a tiny index scan for the hot daily-limit check.
     */
    @Query("""
        SELECT COUNT(u) FROM UsageLog u
        WHERE u.apiKey.id = :keyId
          AND CAST(u.timestamp AS date) = CURRENT_DATE
        """)
    Long countTodayUsage(@Param("keyId") Long keyId);

    /**
     * FIX P5 / DeveloperService.revokeDeveloper():
     * Deactivate all keys for a developer in a single UPDATE — no N+1 load loop.
     * Also evicts the Caffeine apiKeys cache via @CacheEvict in DeveloperService.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ApiKey k SET k.isActive = false WHERE k.developer.id = :developerId")
    void deactivateAllForDeveloper(@Param("developerId") Long developerId);
}
