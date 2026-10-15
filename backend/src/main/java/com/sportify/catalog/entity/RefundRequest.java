package com.sportify.catalog.entity;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Payment;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_code", nullable = false, unique = true, length = 30)
    private String refundCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_registration_id", nullable = false)
    private SportPackageRegistration packageRegistration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberProfile member;

    @Column(name = "amount_requested", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountRequested;

    @Column(name = "amount_approved", precision = 14, scale = 2)
    private BigDecimal amountApproved;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private UserAccount requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserAccount reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "manager_note", length = 500)
    private String managerNote;

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
