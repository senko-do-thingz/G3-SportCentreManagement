package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberCardPurchaseRequest {
    private Long memberId;

    @NotNull(message = "Card tier ID is required")
    private Long tierId;
}
