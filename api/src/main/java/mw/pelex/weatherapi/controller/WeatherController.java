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
// @CrossOrigin removed — handled globally by CorsConfig
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

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<District>>> getAllDistricts() {
        return ResponseEntity.ok(ApiResponse.success(districtRepository.findAll()));
    }

    @GetMapping("/districts/region/{region}")
    public ResponseEntity<ApiResponse<List<District>>> getDistrictsByRegion(@PathVariable String region) {
        List<District> districts = districtRepository.findByRegionIgnoreCase(region);
        if (districts.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.success(districts));
    }

    @GetMapping("/weather/{district}")
    public ResponseEntity<ApiResponse<WeatherResponse>> getCurrentWeather(
            @PathVariable String district, HttpServletRequest request) {

        ApiKey apiKey = (ApiKey) request.getAttribute("apiKey");
        District found = districtRepository.findByNameIgnoreCase(district).orElse(null);

        if (found == null) {
            apiKeyService.logUsage(apiKey, "/weather/" + district, district, 404, request);
            return ResponseEntity.status(404)
                .body(ApiResponse.error("District '" + district + "' not found. Call /api/v1/districts for the full list."));
        }

        try {
            WeatherResponse weather = openMeteoService.getCurrentWeather(found);
            apiKeyService.logUsage(apiKey, "/weather/" + district, district, 200, request);
            return ResponseEntity.ok(ApiResponse.success(weather));
        } catch (RuntimeException e) {
            apiKeyService.logUsage(apiKey, "/weather/" + district, district, 503, request);
            return ResponseEntity.status(503)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/forecast/{district}")
    public ResponseEntity<ApiResponse<ForecastResponse>> getForecast(
            @PathVariable String district, HttpServletRequest request) {

        ApiKey apiKey = (ApiKey) request.getAttribute("apiKey");
        District found = districtRepository.findByNameIgnoreCase(district).orElse(null);

        if (found == null) {
            apiKeyService.logUsage(apiKey, "/forecast/" + district, district, 404, request);
            return ResponseEntity.status(404)
                .body(ApiResponse.error("District '" + district + "' not found. Call /api/v1/districts for the full list."));
        }

        try {
            ForecastResponse forecast = openMeteoService.getForecast(found);
            apiKeyService.logUsage(apiKey, "/forecast/" + district, district, 200, request);
            return ResponseEntity.ok(ApiResponse.success(forecast));
        } catch (RuntimeException e) {
            apiKeyService.logUsage(apiKey, "/forecast/" + district, district, 503, request);
            return ResponseEntity.status(503)
                .body(ApiResponse.error(e.getMessage()));
        }
    }
}
