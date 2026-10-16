package com.sportify.catalog.dto;

import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.TrainingFormat;

import java.time.LocalDate;
import java.util.List;

/** Generic staff detail has no member context; explicit staff eligibility uses a validated member. */
public record SessionDetailResponse(SessionResponse session, boolean memberContext,
        BookingStatus bookingStatus, Boolean alreadyBooked, Boolean eligible,
        EligibilityReason reason, List<SuitableRegistration> suitableRegistrations) {

    public enum EligibilityReason {
        NO_PACKAGE, NO_SESSIONS_LEFT, PACKAGE_EXPIRED_BY_THEN, PACKAGE_NOT_STARTED_BY_THEN,
        FULL, ALREADY_BOOKED, CAPACITY_UNAVAILABLE
    }

    public record SuitableRegistration(Long registrationId, String registrationCode,
            Long packageId, String packageName, Long sportId, TrainingFormat trainingFormat,
            LocalDate startDate, LocalDate endDate, int remainingSessions) { }
}
