package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCreateRequest {

    private Long memberId;

    @NotNull(message = "Session ID is required")
    private Long sessionId;

    private Long packageRegistrationId;
}
