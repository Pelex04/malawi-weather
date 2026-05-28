package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyValue(String keyValue);
    Optional<ApiKey> findByKeyValueAndIsActiveTrue(String keyValue);
    List<ApiKey> findByDeveloperId(Long developerId);

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE u.apiKey.id = :keyId AND CAST(u.timestamp AS date) = CURRENT_DATE")
    Long countTodayUsage(Long keyId);
}