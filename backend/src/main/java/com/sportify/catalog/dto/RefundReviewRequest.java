package com.sportify.catalog.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundReviewRequest {

    @NotNull(message = "Decision (approve or reject) is required")
    private Boolean approved;

    @Positive(message = "Amount approved must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount approved must have at most 2 decimal places")
    private BigDecimal amountApproved;

    private String managerNote;
}
