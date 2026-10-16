package com.sportify.catalog.dto;

import java.time.LocalDateTime;

/** Operational member contact information is returned only after session authorization. */
public record CoachRosterResponse(Long bookingId, String memberCode, String memberName,
        String memberPhone, Long packageRegistrationId, Long packageId, String packageName,
        LocalDateTime bookedAt) { }
