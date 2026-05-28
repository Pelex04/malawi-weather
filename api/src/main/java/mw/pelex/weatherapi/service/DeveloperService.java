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
        developer.setIsActive(false); // pending admin approval

        return developerRepository.save(developer);
    }

    @Transactional
    public ApiKey approveDeveloper(Long developerId) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new IllegalArgumentException("Developer not found"));

        developer.setIsActive(true);
        developerRepository.save(developer);

        // Generate their API key upon approval
        return apiKeyService.generateKeyForDeveloper(developer);
    }

    @Transactional
    public void revokeDeveloper(Long developerId) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new IllegalArgumentException("Developer not found"));

        developer.setIsActive(false);
        developerRepository.save(developer);

        // Disable all their keys
        developer.getApiKeys().forEach(key ->
                apiKeyService.toggleKey(key.getId(), false));
    }

    public List<Developer> getPendingApprovals() {
        return developerRepository.findByIsActive(false);
    }

    public List<Developer> getAllDevelopers() {
        return developerRepository.findAll();
    }

    public Optional<Developer> findByEmail(String email) {
        return developerRepository.findByEmail(email);
    }
}
