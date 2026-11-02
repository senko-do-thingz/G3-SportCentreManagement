package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public final class TrainingGuidanceRequest {
    private TrainingGuidanceRequest() {}
    public record Preferences(Long sportId, String level, String goal, LocalDate from, LocalDate to,
                              LocalTime earliest, LocalTime latest, int page, int size) {}
}
