package com.sportify.catalog.exception;

import com.sportify.catalog.dto.SessionConflictResponse;
import com.sportify.core.exception.ConflictException;

import java.util.List;
import java.util.Map;

public class ScheduleConflictException extends ConflictException {
    public ScheduleConflictException(List<SessionConflictResponse> conflicts) {
        super("The selected coach, facility or time conflicts with existing sessions. Choose another coach, facility or time.",
                Map.of("conflicts", List.copyOf(conflicts)));
    }
}
