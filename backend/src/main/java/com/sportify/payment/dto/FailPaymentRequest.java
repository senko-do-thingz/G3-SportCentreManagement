package com.sportify.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body of PUT /api/v1/payments/{id}/fail: why the front desk could not verify the payment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailPaymentRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;
}
