package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeveloperRepository extends JpaRepository<Developer, Long> {

    Optional<Developer> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Developer> findByStatus(Developer.Status status);

    /**
     * FIX D7 / admin console: returns all developers with their latest API key
     * in a single JOIN query — avoids an N+1 per-developer key lookup.
     *
     * Returns Object[] rows: [Developer, ApiKey|null]
     */
    @Query("""
        SELECT d, k FROM Developer d
        LEFT JOIN ApiKey k ON k.developer.id = d.id
            AND k.id = (
                SELECT MAX(k2.id) FROM ApiKey k2 WHERE k2.developer.id = d.id
            )
        ORDER BY d.createdAt DESC
        """)
    List<Object[]> findAllWithLatestKey();
}
