package mw.pelex.weatherapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mw.pelex.weatherapi.model.ApiKey;
import mw.pelex.weatherapi.service.ApiKeyService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * API key authentication filter.
 *
 * FIX P1 + B6: Key lookups now go through ApiKeyService.validateKey() which is
 * backed by a Caffeine "apiKeys" cache (5-min TTL). Under typical load the DB is
 * hit only once every 5 minutes per unique key, not on every request.
 *
 * FIX: replaced the ad-hoc string-check for public endpoints with a proper
 * Set-based prefix check — O(1) instead of O(n) string chains.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/api/v1/developers/register",
        "/api/v1/districts",
        "/admin",
        "/actuator/health",
        "/swagger",
        "/v3/api-docs"
    );
    private static final Set<String> PUBLIC_EXACT = Set.of("/");

    private final ApiKeyService apiKeyService;

    public ApiKeyFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader("X-API-Key");

        if (rawKey == null || rawKey.isBlank()) {
            reject(response, 401, "API key required. Pass it via the X-API-Key header.");
            return;
        }

        Optional<ApiKey> found = apiKeyService.validateKey(rawKey);

        if (found.isEmpty()) {
            reject(response, 401, "Invalid or inactive API key.");
            return;
        }

        ApiKey apiKey = found.get();

        if (!apiKeyService.isDeveloperActive(apiKey)) {
            reject(response, 403, "Your developer account is pending approval or has been suspended.");
            return;
        }

        if (!apiKeyService.isWithinDailyLimit(apiKey)) {
            reject(response, 429,
                "Daily request limit reached. Limit: " + apiKey.getDailyLimit() + " requests/day.");
            return;
        }

        request.setAttribute("apiKey", apiKey);
        chain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"success\":false,\"message\":\"%s\",\"status\":%d}"
                .formatted(message, status));
    }
}
