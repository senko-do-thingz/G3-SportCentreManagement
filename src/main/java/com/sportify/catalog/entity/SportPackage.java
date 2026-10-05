package com.sportify.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sport_package")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_format", nullable = false, length = 20)
    private TrainingFormat trainingFormat;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "session_count", nullable = false)
    private Integer sessionCount;

    @Column(name = "price_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal priceAmount;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
