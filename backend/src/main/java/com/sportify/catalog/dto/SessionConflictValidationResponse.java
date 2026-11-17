package com.sportify.catalog.dto;

import java.util.List;

public record SessionConflictValidationResponse(
        boolean hasConflict,
        List<SessionConflictResponse> conflicts,
        String message
) {
    public static SessionConflictValidationResponse ok() {
        return new SessionConflictValidationResponse(false, List.of(), null);
    }

    public static SessionConflictValidationResponse conflict(List<SessionConflictResponse> conflicts, String message) {
        return new SessionConflictValidationResponse(true, conflicts == null ? List.of() : conflicts, message);
    }
}
