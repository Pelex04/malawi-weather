package mw.pelex.weatherapi.controller;

import jakarta.validation.Valid;
import mw.pelex.weatherapi.dto.ApiResponse;
import mw.pelex.weatherapi.dto.DeveloperRegistrationRequest;
import mw.pelex.weatherapi.model.Developer;
import mw.pelex.weatherapi.service.DeveloperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/developers")
// @CrossOrigin removed — CORS is handled globally in CorsConfig
public class DeveloperController {

    private final DeveloperService developerService;

    public DeveloperController(DeveloperService developerService) {
        this.developerService = developerService;
    }

    /**
     * POST /api/v1/developers/register
     * Public — no key required.
     * Registers a developer in PENDING_VERIFICATION state.
     * The Next.js layer sends the verification email; this endpoint only persists.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody DeveloperRegistrationRequest request) {

        try {
            Developer developer = developerService.register(request);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "message",     "Registration received. Check your email for a verification code.",
                    "developerId", developer.getId(),
                    "email",       developer.getEmail(),
                    "status",      developer.getStatus().name()
            )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
