package com.sportify.catalog.dto;

import com.sportify.catalog.entity.CheckInResult;

import java.time.LocalDateTime;

public record FrontDeskCheckInResponse(Long id, LocalDateTime checkedInAt, CheckInResult result,
        String denialReason, String note) {
}
