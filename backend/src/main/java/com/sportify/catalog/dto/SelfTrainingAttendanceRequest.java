package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfTrainingAttendanceRequest {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;

    @Size(max = 500)
    private String note;
}
