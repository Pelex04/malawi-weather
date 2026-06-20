package mw.pelex.weatherapi.controller;

import mw.pelex.weatherapi.dto.ApiResponse;
import mw.pelex.weatherapi.repository.DistrictRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health endpoint.
 *
 * GET /health  — used by Render's health check and by the frontend to detect
 *                whether the backend is alive (including post-Neon-wake-up).
 *
 * Neon wake-up: after a period of inactivity Neon suspends compute. The first
 * request that hits the DB wakes it up (5-10 seconds). This endpoint attempts
 * a cheap DB ping and reports "db_status": "ok" or "db_status": "waking"
 * so callers know whether to retry.
 */
@RestController
public class HealthController {

    @Value("${weather.source.strategy:OPEN_METEO_FIRST}")
    private String sourceStrategy;

    @Value("${weatherapi.enabled:false}")
    private boolean weatherApiEnabled;

    private final DistrictRepository districtRepository;

    public HealthController(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "up");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("sourceStrategy", sourceStrategy);
        status.put("weatherApiFallbackEnabled", weatherApiEnabled);

        // Cheap DB ping — tells us if Neon has woken up yet
        try {
            long districtCount = districtRepository.count();
            status.put("db_status", "ok");
            status.put("districts_loaded", districtCount);
        } catch (Exception e) {
            status.put("db_status", "waking");
            status.put("db_note", "Neon compute is resuming. Retry in 5-10 seconds.");
        }

        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
