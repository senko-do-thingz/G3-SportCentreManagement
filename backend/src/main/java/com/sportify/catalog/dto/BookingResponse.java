package com.sportify.catalog.dto;

import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.TrainingFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {
    private Long id;
    private String bookingCode;
    private Long sessionId;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String sportName;
    private TrainingFormat trainingType;
    private Long memberId;
    private String memberCode;
    private String memberFullName;
    private Long packageRegistrationId;
    private BookingStatus status;
    private LocalDateTime bookedAt;
}
