package com.sportify.catalog.entity;

import com.sportify.identity.entity.CoachProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sport_class")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "age_group_id")
    private AgeGroup ageGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private CoachProfile coach;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String level = "BEGINNER";

    @Column(name = "max_members", nullable = false)
    @Builder.Default
    private Integer maxMembers = 20;

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
