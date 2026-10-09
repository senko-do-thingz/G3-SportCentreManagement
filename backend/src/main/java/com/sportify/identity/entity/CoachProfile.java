package com.sportify.identity.entity;

import com.sportify.core.audit.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coach_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachProfile extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserAccount userAccount;

    @Column(name = "primary_sport_id")
    private Long primarySportId;

    @Column(length = 150)
    private String headline;

    @Column(length = 1000)
    private String bio;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(length = 255)
    private String specialties;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
