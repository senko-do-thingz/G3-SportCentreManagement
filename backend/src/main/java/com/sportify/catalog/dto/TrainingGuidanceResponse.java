package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TrainingGuidanceResponse {
    private TrainingGuidanceResponse() {}
    public record ClassRecommendation(long classId, long sessionId, long sportId, String className,
        String sportName, String level, String coachName, String trainingFormat, LocalDate date,
        LocalTime startTime, LocalTime endTime, int freeSeats, int score, List<String> reasons, String sessionCode) {
        /** Transitional Java constructor; live mappers supply the persisted public identity. */
        public ClassRecommendation(long classId, long sessionId, long sportId, String className,
            String sportName, String level, String coachName, String trainingFormat, LocalDate date,
            LocalTime startTime, LocalTime endTime, int freeSeats, int score, List<String> reasons) {
            this(classId, sessionId, sportId, className, sportName, level, coachName, trainingFormat, date, startTime, endTime, freeSeats, score, reasons, null);
        }
    }
    public record Recommendations(String mode, String ruleVersion, boolean bookingEligibilityChecked,
        String message, int page, int size, int totalMatches, List<ClassRecommendation> items) {}
    public record ConsultationSource(String kind, long id, String title, String detail, String path) {}
    public record Consultation(String mode, String status, String answer, boolean historyStored,
                               List<ConsultationSource> sources) {}
}
