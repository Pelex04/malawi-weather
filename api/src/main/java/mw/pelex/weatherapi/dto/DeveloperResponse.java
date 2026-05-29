package mw.pelex.weatherapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import mw.pelex.weatherapi.model.Developer;

import java.time.LocalDateTime;

/**
 * Safe outbound DTO for developer data.
 * Deliberately excludes apiKeys to prevent leaking live key values in list endpoints.
 */
@Data
@AllArgsConstructor
public class DeveloperResponse {

    private Long id;
    private String name;
    private String email;
    private String appName;
    private String appDescription;
    private Developer.Status status;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static DeveloperResponse from(Developer d) {
        return new DeveloperResponse(
            d.getId(),
            d.getName(),
            d.getEmail(),
            d.getAppName() != null ? d.getAppName() : "",
            d.getAppDescription() != null ? d.getAppDescription() : "",
            d.getStatus(),
            d.getIsActive(),
            d.getCreatedAt()
        );
    }
}
