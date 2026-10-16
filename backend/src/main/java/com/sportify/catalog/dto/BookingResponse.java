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
    private Long sportId;
    private Long classId;
    private String className;
    private Long coachId;
    private String coachName;
    private Long facilityId;
    private String facilityName;
    private TrainingFormat trainingType;
    private Long memberId;
    private String memberCode;
    private String memberFullName;
    private Long packageRegistrationId;
    private Long packageId;
    private String packageName;
    private BookingStatus status;
    private LocalDateTime bookedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Long cancelledById;
    private String cancelledByName;
    private String cancelledByRole;
}
