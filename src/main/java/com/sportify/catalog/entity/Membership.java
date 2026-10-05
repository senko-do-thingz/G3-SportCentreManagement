package com.sportify.catalog.entity;

import com.sportify.core.audit.BaseAuditableEntity;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * One registration / membership period. {@code price_amount}, {@code duration_days} and the selected sports
 * are snapshots taken at registration time, so later plan edits never change existing memberships.
 * <p>
 * Step 2A: schema mapping only. Registration / renewal / cancellation logic belongs to Step 2B.
 */
@Deprecated
@Entity
@Table(name = "membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_code", nullable = false, unique = true, length = 20)
    private String registrationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberProfile member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 20)
    private RegistrationType registrationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_membership_id")
    private Membership previousMembership;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private RegistrationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.PENDING_PAYMENT;

    @Column(name = "price_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Inclusive validity end. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** Member or receptionist who created the registration. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserAccount createdByUser;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    /** Sports selected for this period ({@code membership_sport}). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "membership_sport",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_id")
    )
    @Builder.Default
    private Set<Sport> sports = new HashSet<>();
}
