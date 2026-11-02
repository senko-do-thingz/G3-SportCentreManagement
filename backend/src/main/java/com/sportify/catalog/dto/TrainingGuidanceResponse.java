package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TrainingGuidanceResponse {
    private TrainingGuidanceResponse() {}
    public record ClassRecommendation(long classId, long sessionId, long sportId, String className,
        String sportName, String level, String coachName, String trainingFormat, LocalDate date,
        LocalTime startTime, LocalTime endTime, int freeSeats, int score, List<String> reasons) {}
    public record Recommendations(String mode, String ruleVersion, boolean bookingEligibilityChecked,
        String message, int page, int size, int totalMatches, List<ClassRecommendation> items) {}
}
