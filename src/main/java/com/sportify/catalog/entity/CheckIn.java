package com.sportify.catalog.entity;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Front desk arrival record (F1-13). Never counts as class attendance.
 * <p>
 * Step 2A: schema mapping only. Check-in logic belongs to Step 2B.
 */
@Entity
@Table(name = "check_in")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberProfile member;

    /** Membership used to validate; {@code null} when denied. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @Column(name = "checked_in_at", nullable = false)
    private LocalDateTime checkedInAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInResult result;

    @Column(name = "denial_reason", length = 255)
    private String denialReason;

    /** Receptionist who recorded the check-in. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private UserAccount recordedBy;

    @Column(length = 255)
    private String note;

    @PrePersist
    void prePersist() {
        if (checkedInAt == null) {
            checkedInAt = LocalDateTime.now();
        }
    }
}
