package com.sportify.catalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;

@Component
public class AttendanceBackfillPolicy {
    private final boolean enabled;
    private final String window;

    public AttendanceBackfillPolicy(
        @Value("${app.training.attendance-backfill.enabled:false}") boolean enabled,
        @Value("${app.training.attendance-backfill.window:}") String window) {
        this.enabled = enabled;
        this.window = window;
    }

    public Optional<Duration> activeWindow() {
        if (!enabled || window == null || window.isBlank()) return Optional.empty();
        try {
            Duration value = Duration.parse(window.trim());
            return value.isPositive() ? Optional.of(value) : Optional.empty();
        } catch (java.time.DateTimeException | ArithmeticException invalid) {
            return Optional.empty();
        }
    }
}
