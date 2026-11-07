package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TrainingGuidanceRequest {
    private TrainingGuidanceRequest() {}
    /**
     * Recommendation filters. {@code format} is COACH_LED or SELF_TRAINING (null = both); {@code days} are the
     * weekdays the Member is available (null or empty = any day).
     */
    public record Preferences(Long sportId, String level, String goal, LocalDate from, LocalDate to,
                              LocalTime earliest, LocalTime latest, int page, int size,
                              String format, java.util.Set<java.time.DayOfWeek> days) {
        public Preferences(Long sportId, String level, String goal, LocalDate from, LocalDate to,
                           LocalTime earliest, LocalTime latest, int page, int size) {
            this(sportId, level, goal, from, to, earliest, latest, page, size, null, null);
        }
    }
    public record Consultation(@NotBlank @Size(max=500) String question, boolean optIn) {}
}
