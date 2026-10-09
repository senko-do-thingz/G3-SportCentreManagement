package com.sportify.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CheckInResponse {
    private Long id;
    private Long memberId;
    private String memberName;
    private Long membershipId;
    private Long bookingId;
    private String bookingCode;
    private Long packageRegistrationId;
    private LocalDateTime checkedInAt;
    private String result;
    private String denialReason;
    private String note;
}
