package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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
}
