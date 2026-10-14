package com.sportify.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.sportify.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Body of PUT /api/v1/payments/{id}/confirm (F3-05 Verify and Record Payment).
 * The aliases accept the field names used by the front desk screen (FE-12).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmPaymentRequest {

    @NotNull(message = "Payment method is required")
    @JsonAlias({"paymentMethod"})
    private PaymentMethod method;

    /** Bank, card slip or e-wallet reference. Required for every method except CASH. */
    @Size(max = 100, message = "Reference code must be at most 100 characters")
    @JsonAlias({"paymentReference", "reference"})
    private String referenceCode;

    @NotNull(message = "Amount received is required")
    @DecimalMin(value = "0.01", message = "Amount received must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount received must have at most 10 integer digits and 2 decimals")
    private BigDecimal amountReceived;

    @Size(max = 255, message = "Note must be at most 255 characters")
    private String note;
}
