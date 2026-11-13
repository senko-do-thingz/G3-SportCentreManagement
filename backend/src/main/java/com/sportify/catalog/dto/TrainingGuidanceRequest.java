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
    /** One earlier chat turn sent back by the browser; role is "user" or "assistant". Never stored. */
    public record ChatTurn(@Size(max=10) String role, @Size(max=2000) String text) {}
    /** {@code history} holds the earlier turns of the open chat, oldest first, so follow-up questions make sense. */
    public record Consultation(@NotBlank @Size(max=500) String question, boolean optIn,
                               @jakarta.validation.Valid @Size(max=12) java.util.List<ChatTurn> history) {
        public Consultation(String question, boolean optIn) { this(question, optIn, null); }
    }
}
