package com.sportify.catalog.dto;

import com.sportify.payment.entity.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;

/**
 * Suggested refund of a package registration (GET /api/v1/refunds/quote, F3-07, S3-RefundSubmitted).
 * suggestedAmount = paidAmount * unusedSessions / totalSessions, rounded half up to 2 decimals and never above
 * paymentAmount. It is 0.00 when every session is used; submit then answers 400.
 * unusedSessions = remainingSessions + upcomingBookings: a booking on a session that has not started already took
 * one session from remainingSessions, and completing the refund cancels it, so it counts as unused.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundQuoteResponse {

    private Long registrationId;
    private String registrationCode;
    private String packageName;

    private Long paymentId;
    private String paymentCode;
    private PaymentMethod paymentMethod;
    private BigDecimal paymentAmount;

    private BigDecimal paidAmount;
    private Integer totalSessions;
    private Integer remainingSessions;
    private Integer upcomingBookings;
    private Integer unusedSessions;
    private Integer usedSessions;

    private BigDecimal suggestedAmount;
}
