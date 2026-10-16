package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCreateRequest {

    @Positive(message = "Member ID must be positive")
    private Long memberId;

    @NotNull(message = "Session ID is required")
    @Positive(message = "Session ID must be positive")
    private Long sessionId;

    @Positive(message = "Package registration ID must be positive")
    private Long packageRegistrationId;
}
