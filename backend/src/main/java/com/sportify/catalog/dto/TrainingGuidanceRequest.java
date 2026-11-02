package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TrainingGuidanceRequest {
    private TrainingGuidanceRequest() {}
    public record Preferences(Long sportId, String level, String goal, LocalDate from, LocalDate to,
                              LocalTime earliest, LocalTime latest, int page, int size) {}
    public record Consultation(@NotBlank @Size(max=500) String question, boolean optIn) {}
}
