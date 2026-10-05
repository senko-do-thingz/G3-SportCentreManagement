package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfTrainingAttendanceRequest {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;

    private String note;
}
