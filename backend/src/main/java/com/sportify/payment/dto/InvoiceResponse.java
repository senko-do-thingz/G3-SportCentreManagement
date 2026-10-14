package com.sportify.payment.dto;

import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Receipt of a confirmed payment (F3-06 Payment Confirmed and Receipt).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private Long id;
    private String invoiceNumber;
    private InvoiceStatus status;
    private LocalDateTime issueDate;

    private Long paymentId;
    private String paymentCode;
    private PaymentMethod paymentMethod;
    private String paymentReference;
    private LocalDateTime paymentTime;

    private Long memberId;
    private String memberCode;
    private String memberFullName;

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;

    private List<InvoiceLineResponse> lines;
}
