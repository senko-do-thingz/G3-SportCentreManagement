package com.sportify.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One payment request as the front desk sees it (F3-04 list, F3-05 confirm and fail answers).
 * itemType and itemReferenceId say what the payment pays for; invoiceId and invoiceNumber are set
 * once the payment is confirmed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffPaymentResponse {

    private Long id;
    private String code;
    private PaymentStatus status;
    private BigDecimal amount;
    private PaymentMethod method;
    private String referenceCode;
    private String notes;
    private LocalDateTime paymentTime;

    private Long memberId;
    private String memberCode;
    private String memberFullName;

    private InvoiceItemType itemType;
    private Long itemReferenceId;
    private String itemCode;
    private String itemDescription;
    private String itemStatus;
    private BigDecimal originalPrice;
    private BigDecimal discount;

    private Long confirmedByUserId;
    private String confirmedByName;

    private Long invoiceId;
    private String invoiceNumber;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Same value as referenceCode, under the name used by the front desk screen (FE-12). */
    @JsonProperty("paymentReference")
    public String getPaymentReference() {
        return referenceCode;
    }
}
