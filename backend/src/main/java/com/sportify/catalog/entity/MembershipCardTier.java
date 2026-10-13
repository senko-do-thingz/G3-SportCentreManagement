package com.sportify.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "membership_card_tier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipCardTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "duration_months", nullable = false)
    @Builder.Default
    private Integer durationMonths = 0;

    @Column(name = "discount_percentage", nullable = false)
    @Builder.Default
    private Integer discountPercentage = 0;

    @Column(length = 500)
    private String description;

    @Column(name = "tier_rank", nullable = false)
    @Builder.Default
    private Integer tierRank = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public Integer getRank() {
        return tierRank != null ? tierRank : 0;
    }

    public void setRank(Integer rank) {
        this.tierRank = rank != null ? rank : 0;
    }

    public int getEffectiveRank() {
        if (tierRank != null && tierRank > 0) {
            return tierRank;
        }
        if ("STANDARD".equalsIgnoreCase(code)) return 1;
        if ("SILVER".equalsIgnoreCase(code)) return 2;
        if ("GOLD".equalsIgnoreCase(code)) return 3;
        if ("VIP".equalsIgnoreCase(code)) return 4;
        return 0;
    }

    public static class MembershipCardTierBuilder {
        public MembershipCardTierBuilder rank(Integer rank) {
            return this.tierRank(rank);
        }
    }

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
