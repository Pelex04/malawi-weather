package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {
    List<UsageLog> findByApiKeyIdOrderByTimestampDesc(Long apiKeyId);

    @Query("SELECT u.districtQueried, COUNT(u) as cnt FROM UsageLog u GROUP BY u.districtQueried ORDER BY cnt DESC")
    List<Object[]> findMostQueriedDistricts();

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE CAST(u.timestamp AS date) = CURRENT_DATE")
    Long countTodayTotalRequests();
}
