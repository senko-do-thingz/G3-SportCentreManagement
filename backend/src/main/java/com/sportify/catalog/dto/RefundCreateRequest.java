package com.sportify.catalog.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundCreateRequest {

    @NotNull(message = "Package registration ID is required")
    private Long packageRegistrationId;

    @NotBlank(message = "Refund reason is required")
    private String reason;

    /**
     * Optional. When missing, the pro-rated amount of GET /api/v1/refunds/quote is used.
     * Must be greater than zero and cannot exceed the amount of the payment that bought the registration.
     */
    @Positive(message = "Amount requested must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount requested must have at most 2 decimal places")
    private BigDecimal amountRequested;
}
