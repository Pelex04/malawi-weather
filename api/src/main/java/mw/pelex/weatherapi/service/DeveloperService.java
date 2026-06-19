package mw.pelex.weatherapi.service;

import mw.pelex.weatherapi.dto.DeveloperRegistrationRequest;
import mw.pelex.weatherapi.dto.DeveloperResponse;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.repository.ApiKeyRepository;
import mw.pelex.weatherapi.repository.DeveloperRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DeveloperService {

    private static final Logger log = LoggerFactory.getLogger(DeveloperService.class);

    private final DeveloperRepository developerRepository;
    private final ApiKeyRepository    apiKeyRepository;
    private final ApiKeyService       apiKeyService;
    private final PasswordEncoder     passwordEncoder;

    public DeveloperService(DeveloperRepository developerRepository,
                            ApiKeyRepository    apiKeyRepository,
                            ApiKeyService       apiKeyService,
                            PasswordEncoder     passwordEncoder) {
        this.developerRepository = developerRepository;
        this.apiKeyRepository    = apiKeyRepository;
        this.apiKeyService       = apiKeyService;
        this.passwordEncoder     = passwordEncoder;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public DeveloperResponse registerDeveloper(DeveloperRegistrationRequest req) {
        if (developerRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        Developer dev = new Developer();
        dev.setName(req.getName());
        dev.setEmail(req.getEmail().toLowerCase().trim());
        dev.setAppName(req.getAppName());
        dev.setAppDescription(req.getAppDescription());
        dev.setOrganization(req.getOrganization());
        dev.setPasswordHash(passwordEncoder.encode(req.getPassword()));

        // FIX B4: single source of truth — status drives everything.
        // isActive is derived from status (see Developer entity).
        dev.setStatus(Developer.Status.PENDING);

        Developer saved = developerRepository.save(dev);
        log.info("Developer registered: {} ({})", saved.getName(), saved.getEmail());

        return DeveloperResponse.from(saved, null);
    }

    // ── Approval ──────────────────────────────────────────────────────────────

    @Transactional
    public DeveloperResponse approveDeveloper(Long id) {
        Developer dev = findOrThrow(id);

        if (dev.getStatus() == Developer.Status.ACTIVE) {
            // Idempotent — return current state including existing key
            ApiKey existing = apiKeyRepository.findFirstByDeveloperIdOrderByCreatedAtDesc(id)
                .orElse(null);
            return DeveloperResponse.from(dev, existing != null ? existing.getKeyValue() : null);
        }

        // FIX B4: set status only — isActive is derived (@Transient or via listener)
        dev.setStatus(Developer.Status.ACTIVE);
        developerRepository.save(dev);

        ApiKey key = apiKeyService.generateKeyForDeveloper(dev);
        log.info("Developer approved: {} — key issued", dev.getEmail());

        return DeveloperResponse.from(dev, key.getKeyValue());
    }

    // ── Revocation ────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "apiKeys", allEntries = true)
    public void revokeDeveloper(Long id) {
        Developer dev = findOrThrow(id);

        // FIX B4: single write — status drives isActive
        dev.setStatus(Developer.Status.REVOKED);
        developerRepository.save(dev);

        // Deactivate all keys for this developer in one query
        apiKeyRepository.deactivateAllForDeveloper(id);

        log.info("Developer revoked: {}", dev.getEmail());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<Developer> findByEmail(String email) {
        return developerRepository.findByEmail(email.toLowerCase().trim());
    }

    public List<DeveloperResponse> getAllDevelopers() {
        return developerRepository.findAllWithLatestKey().stream()
            .map(row -> DeveloperResponse.from((Developer) row[0],
                row[1] != null ? ((ApiKey) row[1]).getKeyValue() : null))
            .toList();
    }

    public List<DeveloperResponse> getPendingDevelopers() {
        return developerRepository.findByStatus(Developer.Status.PENDING).stream()
            .map(dev -> DeveloperResponse.from(dev, null))
            .toList();
    }

    public boolean validatePassword(Developer dev, String raw) {
        return passwordEncoder.matches(raw, dev.getPasswordHash());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Developer findOrThrow(Long id) {
        return developerRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Developer not found: " + id));
    }
}
