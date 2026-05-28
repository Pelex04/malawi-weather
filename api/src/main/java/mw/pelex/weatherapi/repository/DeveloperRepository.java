package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface DeveloperRepository extends JpaRepository<Developer, Long> {
    Optional<Developer> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Developer> findByIsActive(Boolean isActive);
}
