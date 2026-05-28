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

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public ApiKeyFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip auth for public endpoints
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKeyValue = extractApiKey(request);

        if (apiKeyValue == null) {
            sendError(response, 401, "API key required. Pass it as X-API-Key header or ?api_key= param.");
            return;
        }

        Optional<ApiKey> apiKeyOpt = apiKeyService.validateKey(apiKeyValue);

        if (apiKeyOpt.isEmpty()) {
            sendError(response, 401, "Invalid or inactive API key.");
            return;
        }

        ApiKey apiKey = apiKeyOpt.get();

        if (!apiKeyService.isDeveloperActive(apiKey)) {
            sendError(response, 403, "Your developer account is pending approval or has been suspended.");
            return;
        }

        if (!apiKeyService.isWithinDailyLimit(apiKey)) {
            sendError(response, 429, "Daily request limit reached. Limit: " + apiKey.getDailyLimit() + " requests/day.");
            return;
        }

        // Attach api key to request for controllers to use
        request.setAttribute("apiKey", apiKey);
        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        // Support both header and query param
        String header = request.getHeader("X-API-Key");
        if (header != null && !header.isBlank()) return header;
        return request.getParameter("api_key");
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/v1/developers/register") ||
               path.startsWith("/api/v1/districts") ||
               path.startsWith("/admin") ||
               path.startsWith("/actuator/health") ||
               path.equals("/") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"success": false, "message": "%s", "status": %d}
                """.formatted(message, status));
    }
}
