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

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false; // Admin must approve

    @OneToMany(mappedBy = "developer", cascade = CascadeType.ALL)
    private List<ApiKey> apiKeys;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
