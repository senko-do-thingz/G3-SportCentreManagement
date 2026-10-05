package com.sportify.catalog.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfTrainingAttendanceResponse {
    private Long bookingId;
    private String bookingCode;
    private Long memberId;
    private String memberCode;
    private String memberFullName;
    private String sportName;
    private Integer remainingSessions;
    private LocalDateTime confirmedAt;
    private String recordedByName;
    private String message;
}
