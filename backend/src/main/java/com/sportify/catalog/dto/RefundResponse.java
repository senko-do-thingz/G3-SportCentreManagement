package com.sportify.catalog.dto;

import com.sportify.catalog.entity.RefundStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponse {
    private Long id;
    private String refundCode;
    private Long packageRegistrationId;
    private String registrationCode;
    private Long memberId;
    private String memberCode;
    private String memberFullName;
    private BigDecimal amountRequested;
    private BigDecimal amountApproved;
    private String reason;
    private RefundStatus status;
    private Long requestedById;
    private String requestedByName;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String managerNote;
    private LocalDateTime createdAt;
}
