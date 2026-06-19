package mw.pelex.weatherapi.service;

import jakarta.servlet.http.HttpServletRequest;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.model.UsageLog;
import mw.pelex.weatherapi.repository.ApiKeyRepository;
import mw.pelex.weatherapi.repository.UsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyRepository    apiKeyRepository;
    private final UsageLogRepository  usageLogRepository;
    private final SecureRandom        secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         UsageLogRepository usageLogRepository) {
        this.apiKeyRepository   = apiKeyRepository;
        this.usageLogRepository = usageLogRepository;
    }

    // ── Key generation ────────────────────────────────────────────────────────

    public ApiKey generateKeyForDeveloper(Developer developer) {
        ApiKey apiKey = new ApiKey();
        apiKey.setDeveloper(developer);
        apiKey.setKeyValue(generateKey());
        apiKey.setIsActive(true);
        return apiKeyRepository.save(apiKey);
    }

    // ── Validation (FIX P1 + B6) ─────────────────────────────────────────────
    //
    // BEFORE: every request hit the DB for key validation.
    // NOW:    Caffeine "apiKeys" cache (5-min TTL) absorbs the hot path.
    //         Revoked keys are evicted immediately via @CacheEvict in toggleKey().

    @Cacheable(value = "apiKeys", key = "#keyValue", unless = "#result == null || !#result.isPresent()")
    public Optional<ApiKey> validateKey(String keyValue) {
        return apiKeyRepository.findByKeyValueAndIsActiveTrue(keyValue);
    }

    public boolean isDeveloperActive(ApiKey apiKey) {
        // Use the fine-grained status enum (Developer.Status.ACTIVE) rather than the
        // boolean isActive, which can drift. isActive is kept for backwards compat.
        return apiKey.getDeveloper().getIsActive()
            && apiKey.getDeveloper().getStatus() == Developer.Status.ACTIVE;
    }

    public boolean isWithinDailyLimit(ApiKey apiKey) {
        // FIX P2: this query needs an index on (api_key_id, timestamp).
        // See migration note in UsageLogRepository.
        Long todayCount = apiKeyRepository.countTodayUsage(apiKey.getId());
        return todayCount < apiKey.getDailyLimit();
    }

    // ── Usage logging (FIX P6) ────────────────────────────────────────────────
    //
    // BEFORE: INSERT into usage_logs was synchronous — it was on the hot request path.
    // NOW:    @Async executes on a separate thread pool (configure AsyncConfig if needed).
    //         The HTTP response is returned immediately; the log write happens in background.
    //
    // NOTE: totalRequests on ApiKey is updated here too. Under high concurrency this can
    // produce lost-update races (two threads read 99, both write 100). For accurate counts
    // use a DB-level atomic increment:  UPDATE api_keys SET total_requests = total_requests + 1
    // The current approach is acceptable for approximate stats at free-tier scale.

    @Async
    @Transactional
    public void logUsage(ApiKey apiKey, String endpoint, String district,
                         int responseCode, HttpServletRequest request) {
        try {
            UsageLog entry = new UsageLog();
            entry.setApiKey(apiKey);
            entry.setEndpoint(endpoint);
            entry.setDistrictQueried(district);
            entry.setResponseCode(responseCode);
            entry.setIpAddress(extractClientIp(request));
            usageLogRepository.save(entry);

            // Atomic-enough for stats at this scale; see note above for high-traffic upgrade path
            apiKey.setLastUsedAt(LocalDateTime.now());
            apiKey.setTotalRequests(apiKey.getTotalRequests() + 1);
            apiKeyRepository.save(apiKey);
        } catch (Exception e) {
            // Never let logging failures surface to the caller
            log.error("Failed to log API usage for key {}: {}", apiKey.getId(), e.getMessage());
        }
    }

    // ── Key toggle ────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "apiKeys", allEntries = true)   // evict whole cache — key value unknown here
    public void toggleKey(Long keyId, boolean active) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            key.setIsActive(active);
            apiKeyRepository.save(key);
            log.info("API key {} {}", keyId, active ? "activated" : "deactivated");
        });
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public List<ApiKey> getKeysByDeveloper(Long developerId) {
        return apiKeyRepository.findByDeveloperId(developerId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String generateKey() {
        // Format: mww_<32 URL-safe base64 chars>
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return "mww_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
