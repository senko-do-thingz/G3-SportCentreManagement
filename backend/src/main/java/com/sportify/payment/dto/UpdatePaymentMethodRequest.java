package com.sportify.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.sportify.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePaymentMethodRequest {

    @NotNull(message = "Payment method is required")
    @JsonAlias({"method", "paymentMethod"})
    private PaymentMethod paymentMethod;
}
