package mw.pelex.weatherapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import mw.pelex.weatherapi.dto.ApiResponse;
import mw.pelex.weatherapi.dto.ForecastResponse;
import mw.pelex.weatherapi.dto.WeatherResponse;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.District;
import mw.pelex.weatherapi.repository.DistrictRepository;
import mw.pelex.weatherapi.service.ApiKeyService;
import mw.pelex.weatherapi.service.OpenMeteoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class WeatherController {

    private final OpenMeteoService openMeteoService;
    private final DistrictRepository districtRepository;
    private final ApiKeyService apiKeyService;

    public WeatherController(OpenMeteoService openMeteoService,
                             DistrictRepository districtRepository,
                             ApiKeyService apiKeyService) {
        this.openMeteoService = openMeteoService;
        this.districtRepository = districtRepository;
        this.apiKeyService = apiKeyService;
    }

    /**
     * GET /api/v1/districts
     * Public - no key required
     * Returns all supported districts
     */
    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<District>>> getAllDistricts() {
        List<District> districts = districtRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(districts));
    }

    /**
     * GET /api/v1/districts/{region}
     * Public - no key required
     * Returns districts by region (Northern, Central, Southern)
     */
    @GetMapping("/districts/region/{region}")
    public ResponseEntity<ApiResponse<List<District>>> getDistrictsByRegion(@PathVariable String region) {
        List<District> districts = districtRepository.findByRegionIgnoreCase(region);
        if (districts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(districts));
    }

    /**
     * GET /api/v1/weather/{district}
     * Requires API key
     * Returns current weather for a district
     */
    @GetMapping("/weather/{district}")
    public ResponseEntity<ApiResponse<WeatherResponse>> getCurrentWeather(
            @PathVariable String district,
            HttpServletRequest request) {

        ApiKey apiKey = (ApiKey) request.getAttribute("apiKey");

        District found = districtRepository.findByNameIgnoreCase(district)
                .orElse(null);

        if (found == null) {
            apiKeyService.logUsage(apiKey, "/weather/" + district, district, 404, request);
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("District '" + district + "' not found. Call /api/v1/districts for the full list."));
        }

        WeatherResponse weather = openMeteoService.getCurrentWeather(found);
        apiKeyService.logUsage(apiKey, "/weather/" + district, district, 200, request);
        return ResponseEntity.ok(ApiResponse.success(weather));
    }

    /**
     * GET /api/v1/forecast/{district}
     * Requires API key
     * Returns 7-day forecast for a district
     */
    @GetMapping("/forecast/{district}")
    public ResponseEntity<ApiResponse<ForecastResponse>> getForecast(
            @PathVariable String district,
            HttpServletRequest request) {

        ApiKey apiKey = (ApiKey) request.getAttribute("apiKey");

        District found = districtRepository.findByNameIgnoreCase(district)
                .orElse(null);

        if (found == null) {
            apiKeyService.logUsage(apiKey, "/forecast/" + district, district, 404, request);
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("District '" + district + "' not found. Call /api/v1/districts for the full list."));
        }

        ForecastResponse forecast = openMeteoService.getForecast(found);
        apiKeyService.logUsage(apiKey, "/forecast/" + district, district, 200, request);
        return ResponseEntity.ok(ApiResponse.success(forecast));
    }
}
