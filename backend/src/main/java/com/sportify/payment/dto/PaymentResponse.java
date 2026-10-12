package com.sportify.payment.dto;

import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private String code;
    private PaymentStatus status;
    private BigDecimal amount;
    private PaymentMethod method;
    private String itemDescription;
    private BigDecimal originalPrice;
    private BigDecimal discount;
    private BankTransferInstructions bankTransferInstructions;
    private Long memberId;
    private String memberCode;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
