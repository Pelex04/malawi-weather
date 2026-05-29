package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeveloperRepository extends JpaRepository<Developer, Long> {

    boolean existsByEmail(String email);

    Optional<Developer> findByEmail(String email);

    List<Developer> findByStatus(Developer.Status status);

    /** Used by stats endpoint — no full table scan in Java. */
    @Query("SELECT COUNT(d) FROM Developer d")
    Long countAll();

    @Query("SELECT COUNT(d) FROM Developer d WHERE d.status = :status")
    Long countByStatus(@Param("status") Developer.Status status);

    /** Legacy boolean query — kept for backwards compatibility with any existing callers. */
    List<Developer> findByIsActive(Boolean isActive);
}
