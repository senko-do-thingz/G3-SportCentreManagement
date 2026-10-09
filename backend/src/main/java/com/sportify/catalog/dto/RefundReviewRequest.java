package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;
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

    private BigDecimal amountApproved;

    private String managerNote;
}
