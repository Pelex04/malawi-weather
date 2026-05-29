package mw.pelex.weatherapi.service;

import mw.pelex.weatherapi.dto.DeveloperRegistrationRequest;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.repository.DeveloperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DeveloperService {

    private final DeveloperRepository developerRepository;
    private final ApiKeyService apiKeyService;

    public DeveloperService(DeveloperRepository developerRepository, ApiKeyService apiKeyService) {
        this.developerRepository = developerRepository;
        this.apiKeyService = apiKeyService;
    }

    @Transactional
    public Developer register(DeveloperRegistrationRequest request) {
        if (developerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        Developer developer = new Developer();
        developer.setName(request.getName());
        developer.setEmail(request.getEmail());
        developer.setAppName(request.getAppName());
        developer.setAppDescription(request.getAppDescription());
        developer.setIsActive(false);
        developer.setStatus(Developer.Status.PENDING_VERIFICATION);
        return developerRepository.save(developer);
    }

    /**
     * Idempotent approval: if the developer already has an active key this returns
     * the existing key rather than generating a duplicate.
     */
    @Transactional
    public ApiKey approveDeveloper(Long developerId) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new IllegalArgumentException("Developer not found"));

        // Return existing active key — prevents duplicate key generation on retries
        Optional<ApiKey> existingKey = apiKeyService.getKeysByDeveloper(developerId)
                .stream()
                .filter(ApiKey::getIsActive)
                .findFirst();

        if (existingKey.isPresent() && developer.getIsActive()) {
            return existingKey.get();
        }

        developer.setIsActive(true);
        developer.setStatus(Developer.Status.ACTIVE);
        developerRepository.save(developer);
        return apiKeyService.generateKeyForDeveloper(developer);
    }

    @Transactional
    public void revokeDeveloper(Long developerId) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new IllegalArgumentException("Developer not found"));

        developer.setIsActive(false);
        developer.setStatus(Developer.Status.REVOKED);   // distinguishable from PENDING_VERIFICATION
        developerRepository.save(developer);

        developer.getApiKeys().forEach(key -> apiKeyService.toggleKey(key.getId(), false));
    }

    /**
     * Returns only genuinely pending developers — not revoked ones.
     * Previously both had isActive=false and were indistinguishable.
     */
    public List<Developer> getPendingApprovals() {
        return developerRepository.findByStatus(Developer.Status.PENDING_VERIFICATION);
    }

    public List<Developer> getAllDevelopers() {
        return developerRepository.findAll();
    }

    public Optional<Developer> findByEmail(String email) {
        return developerRepository.findByEmail(email);
    }

    public Long countAll() {
        return developerRepository.countAll();
    }

    public Long countByStatus(Developer.Status status) {
        return developerRepository.countByStatus(status);
    }
}
