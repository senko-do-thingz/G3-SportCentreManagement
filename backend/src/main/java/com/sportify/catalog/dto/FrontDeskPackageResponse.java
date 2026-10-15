package com.sportify.catalog.dto;

import com.sportify.catalog.entity.PackageRegistrationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FrontDeskPackageResponse(Long id, String registrationCode, Long memberId, String memberCode,
        String memberName, Long packageId, String packageName, PackageRegistrationStatus status,
        LocalDate startDate, LocalDate endDate, Integer totalSessions, Integer remainingSessions,
        BigDecimal amount, LocalDateTime requestedAt) {
}
