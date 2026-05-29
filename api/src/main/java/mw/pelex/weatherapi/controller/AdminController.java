package mw.pelex.weatherapi.controller;

import mw.pelex.weatherapi.dto.ApiResponse;
import mw.pelex.weatherapi.dto.DeveloperResponse;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.repository.UsageLogRepository;
import mw.pelex.weatherapi.service.ApiKeyService;
import mw.pelex.weatherapi.service.DeveloperService;
import org.springframework.http.ResponseEntity;
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

    // ── Developer management ─────────────────────────────────────────────────

    @GetMapping("/developers")
    public ResponseEntity<ApiResponse<List<DeveloperResponse>>> getAllDevelopers() {
        List<DeveloperResponse> result = developerService.getAllDevelopers()
                .stream()
                .map(DeveloperResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/developers/pending")
    public ResponseEntity<ApiResponse<List<DeveloperResponse>>> getPendingDevelopers() {
        List<DeveloperResponse> result = developerService.getPendingApprovals()
                .stream()
                .map(DeveloperResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
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

    // ── Developer lookup ─────────────────────────────────────────────────────

    /**
     * Returns developer info with their active API key.
     * Used by the Next.js login route — credentials are verified server-side,
     * this endpoint is not callable from the browser.
     */
    @GetMapping("/developers/by-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeveloperByEmail(@RequestParam String email) {
        return developerService.findByEmail(email)
                .map(dev -> {
                    var keys = apiKeyService.getKeysByDeveloper(dev.getId());
                    var activeKey = keys.stream().filter(ApiKey::getIsActive).findFirst();
                    return ResponseEntity.ok(ApiResponse.success(Map.<String, Object>of(
                            "id",        dev.getId(),
                            "name",      dev.getName(),
                            "email",     dev.getEmail(),
                            "appName",   dev.getAppName() != null ? dev.getAppName() : "",
                            "isActive",  dev.getIsActive(),
                            "status",    dev.getStatus().name(),
                            "apiKey",    activeKey.map(ApiKey::getKeyValue).orElse("")
                    )));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── API key management ───────────────────────────────────────────────────

    @GetMapping("/keys/developer/{developerId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getKeysByDeveloper(@PathVariable Long developerId) {
        var result = apiKeyService.getKeysByDeveloper(developerId)
                .stream()
                .map(k -> Map.<String, Object>of(
                        "id",            k.getId(),
                        "isActive",      k.getIsActive(),
                        "totalRequests", k.getTotalRequests(),
                        "dailyLimit",    k.getDailyLimit(),
                        "createdAt",     k.getCreatedAt()
                        // keyValue intentionally omitted — shown only at generation time
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/keys/{id}/toggle")
    public ResponseEntity<ApiResponse<String>> toggleKey(
            @PathVariable Long id,
            @RequestParam boolean active) {
        apiKeyService.toggleKey(id, active);
        return ResponseEntity.ok(ApiResponse.success("Key " + (active ? "activated" : "deactivated")));
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        // All counts use DB-level COUNT queries — no full table scans in Java
        Long todayRequests   = usageLogRepository.countTodayTotalRequests();
        Long totalDevelopers = developerService.countAll();
        Long pendingCount    = developerService.countByStatus(Developer.Status.PENDING_VERIFICATION);
        List<Object[]> topDistricts = usageLogRepository.findMostQueriedDistricts();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "todayRequests",        todayRequests,
                "pendingApprovals",     pendingCount,
                "totalDevelopers",      totalDevelopers,
                "mostQueriedDistricts", topDistricts.stream()
                        .limit(5)
                        .map(row -> Map.of("district", row[0], "count", row[1]))
                        .toList()
        )));
    }
}
