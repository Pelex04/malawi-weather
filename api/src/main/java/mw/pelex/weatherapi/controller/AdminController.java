package mw.pelex.weatherapi.controller;

import mw.pelex.weatherapi.dto.ApiResponse;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.repository.UsageLogRepository;
import mw.pelex.weatherapi.service.ApiKeyService;
import mw.pelex.weatherapi.service.DeveloperService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api")
public class AdminController {

    private final DeveloperService developerService;
    private final ApiKeyService apiKeyService;
    private final UsageLogRepository usageLogRepository;

    public AdminController(DeveloperService developerService,
                           ApiKeyService apiKeyService,
                           UsageLogRepository usageLogRepository) {
        this.developerService = developerService;
        this.apiKeyService = apiKeyService;
        this.usageLogRepository = usageLogRepository;
    }

    // --- Developer management ---

    @GetMapping("/developers")
    public ResponseEntity<ApiResponse<List<Developer>>> getAllDevelopers() {
        return ResponseEntity.ok(ApiResponse.success(developerService.getAllDevelopers()));
    }

    @GetMapping("/developers/pending")
    public ResponseEntity<ApiResponse<List<Developer>>> getPendingDevelopers() {
        return ResponseEntity.ok(ApiResponse.success(developerService.getPendingApprovals()));
    }

    @PostMapping("/developers/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveDeveloper(@PathVariable Long id) {
        try {
            ApiKey key = developerService.approveDeveloper(id);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "message", "Developer approved",
                    "apiKey", key.getKeyValue(),
                    "developerId", id
            )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/developers/{id}/revoke")
    public ResponseEntity<ApiResponse<String>> revokeDeveloper(@PathVariable Long id) {
        try {
            developerService.revokeDeveloper(id);
            return ResponseEntity.ok(ApiResponse.success("Developer account suspended"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // --- API key management ---

    @GetMapping("/keys/developer/{developerId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getKeysByDeveloper(@PathVariable Long developerId) {
        var keys = apiKeyService.getKeysByDeveloper(developerId);
        var result = keys.stream()
                .map(k -> Map.<String, Object>of(
                        "id", k.getId(),
                        "keyValue", k.getKeyValue(),
                        "isActive", k.getIsActive(),
                        "totalRequests", k.getTotalRequests(),
                        "dailyLimit", k.getDailyLimit()
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/developers/by-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeveloperByEmail(@RequestParam String email) {
        return developerService.findByEmail(email)
                .map(dev -> {
                    var keys = apiKeyService.getKeysByDeveloper(dev.getId());
                    var activeKey = keys.stream().filter(k -> k.getIsActive()).findFirst();
                    return ResponseEntity.ok(ApiResponse.success(Map.<String, Object>of(
                            "id", dev.getId(),
                            "name", dev.getName(),
                            "email", dev.getEmail(),
                            "appName", dev.getAppName() != null ? dev.getAppName() : "",
                            "isActive", dev.getIsActive(),
                            "apiKey", activeKey.map(k -> k.getKeyValue()).orElse("")
                    )));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/keys/{id}/toggle")
    public ResponseEntity<ApiResponse<String>> toggleKey(
            @PathVariable Long id,
            @RequestParam boolean active) {
        apiKeyService.toggleKey(id, active);
        return ResponseEntity.ok(ApiResponse.success("Key " + (active ? "activated" : "deactivated")));
    }

    // --- Stats ---

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Long todayRequests = usageLogRepository.countTodayTotalRequests();
        List<Object[]> topDistricts = usageLogRepository.findMostQueriedDistricts();
        List<Developer> pendingCount = developerService.getPendingApprovals();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "todayRequests", todayRequests,
                "pendingApprovals", pendingCount.size(),
                "totalDevelopers", developerService.getAllDevelopers().size(),
                "mostQueriedDistricts", topDistricts.stream()
                        .limit(5)
                        .map(row -> Map.of("district", row[0], "count", row[1]))
                        .toList()
        )));
    }
    
}
