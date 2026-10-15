package com.sportify.catalog.entity;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 30)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberProfile member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_registration_id")
    private SportPackageRegistration packageRegistration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private UserAccount cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (bookedAt == null) {
            bookedAt = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
