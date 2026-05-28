package mw.pelex.weatherapi.service;

import jakarta.servlet.http.HttpServletRequest;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.model.UsageLog;
import mw.pelex.weatherapi.repository.ApiKeyRepository;
import mw.pelex.weatherapi.repository.UsageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UsageLogRepository usageLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UsageLogRepository usageLogRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.usageLogRepository = usageLogRepository;
    }

    public ApiKey generateKeyForDeveloper(Developer developer) {
        ApiKey apiKey = new ApiKey();
        apiKey.setDeveloper(developer);
        apiKey.setKeyValue(generateKey());
        apiKey.setIsActive(true);
        return apiKeyRepository.save(apiKey);
    }

    public Optional<ApiKey> validateKey(String keyValue) {
        return apiKeyRepository.findByKeyValueAndIsActiveTrue(keyValue);
    }

    public boolean isDeveloperActive(ApiKey apiKey) {
        return apiKey.getDeveloper().getIsActive();
    }

    public boolean isWithinDailyLimit(ApiKey apiKey) {
        Long todayCount = apiKeyRepository.countTodayUsage(apiKey.getId());
        return todayCount < apiKey.getDailyLimit();
    }

    @Transactional
    public void logUsage(ApiKey apiKey, String endpoint, String district,
                         int responseCode, HttpServletRequest request) {
        // Log the request
        UsageLog log = new UsageLog();
        log.setApiKey(apiKey);
        log.setEndpoint(endpoint);
        log.setDistrictQueried(district);
        log.setResponseCode(responseCode);
        log.setIpAddress(getClientIp(request));
        usageLogRepository.save(log);

        // Update key stats
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKey.setTotalRequests(apiKey.getTotalRequests() + 1);
        apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void toggleKey(Long keyId, boolean active) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            key.setIsActive(active);
            apiKeyRepository.save(key);
        });
    }

    private String generateKey() {
        // Format: mww_<32 random chars> (mww = malawi weather)
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return "mww_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public java.util.List<mw.pelex.weatherapi.model.ApiKey> getKeysByDeveloper(Long developerId) {
        return apiKeyRepository.findByDeveloperId(developerId);
    }

}