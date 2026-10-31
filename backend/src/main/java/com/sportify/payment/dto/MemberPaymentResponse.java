package com.sportify.payment.dto;

import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of GET /api/v1/payments/my (F3-03 My Payments and Receipts, S3-PaidHistory).
 * Field names follow PaymentResponse (GET /payments/{id}) so the member screens read both the same way.
 * invoiceId and invoiceNumber are null until the payment is confirmed and its receipt is issued.
 * time is paymentTime when the payment was confirmed, otherwise createdAt (when it was requested).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberPaymentResponse {

    private Long id;
    private String code;
    private PaymentStatus status;
    private BigDecimal amount;
    private PaymentMethod method;

    private InvoiceItemType itemType;
    private Long itemReferenceId;
    private String itemCode;
    private String itemDescription;
    private BigDecimal originalPrice;
    private BigDecimal discount;

    private LocalDateTime time;
    private LocalDateTime paymentTime;
    private LocalDateTime createdAt;

    private Long memberId;
    private String memberCode;

    private Long invoiceId;
    private String invoiceNumber;

    private String referenceCode;
    private String settlementType;
    private String settlementProvider;
    private Long simulatedTransactionId;
}
