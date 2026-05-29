package mw.pelex.weatherapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "developers")
@Data
@NoArgsConstructor
public class Developer {

    public enum Status {
        PENDING_VERIFICATION,  // registered, OTP not yet confirmed
        PENDING_APPROVAL,      // verified, awaiting manual admin approval (reserved for manual-approval mode)
        ACTIVE,                // approved and active
        REVOKED                // suspended by admin
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "app_description")
    private String appDescription;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Convenience boolean used by ApiKeyFilter and Spring Security checks.
     * Always keep in sync with {@code status}.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    /**
     * Fine-grained status — avoids the original bug where revoked developers
     * were indistinguishable from pending ones (both had isActive = false).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING_VERIFICATION;

    @OneToMany(mappedBy = "developer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ApiKey> apiKeys;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = Status.PENDING_VERIFICATION;
    }
}
