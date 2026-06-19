package mw.pelex.weatherapi.exception;

import mw.pelex.weatherapi.dto.ApiResponse;
import mw.pelex.weatherapi.service.OpenMeteoService.WeatherUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler.
 *
 * ADDS: Centralises all error-to-HTTP-response mapping that was previously
 * duplicated across every controller method as try/catch blocks.
 *
 * Controllers can now throw typed exceptions and rely on this handler
 * to produce consistent ApiResponse error payloads.
 *
 * Covers:
 *  - WeatherUnavailableException  → 503 Service Unavailable
 *  - IllegalArgumentException     → 400 Bad Request  (e.g. "Email already registered")
 *  - Validation failures          → 400 Bad Request  (Bean Validation errors)
 *  - Unhandled exceptions         → 500 Internal Server Error
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WeatherUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleWeatherUnavailable(WeatherUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred. Please try again."));
    }
}
