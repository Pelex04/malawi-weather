package mw.pelex.weatherapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "api_keys")
@Data
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyValue;

    @ManyToOne
    @JoinColumn(name = "developer_id", nullable = false)
    private Developer developer;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "total_requests")
    private Long totalRequests = 0L;

    @Column(name = "daily_limit")
    private Integer dailyLimit = 1000; // default daily limit per key

    @OneToMany(mappedBy = "apiKey", cascade = CascadeType.ALL)
    private List<UsageLog> usageLogs;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
