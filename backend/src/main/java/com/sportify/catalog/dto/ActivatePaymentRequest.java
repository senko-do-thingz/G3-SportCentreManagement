package com.sportify.catalog.dto;

import com.sportify.payment.entity.PaymentMethod;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivatePaymentRequest {
    private PaymentMethod paymentMethod;
}
