package mw.pelex.weatherapi.repository;

import mw.pelex.weatherapi.model.District;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {
    Optional<District> findByNameIgnoreCase(String name);
    List<District> findByRegionIgnoreCase(String region);
    boolean existsByNameIgnoreCase(String name);
}
