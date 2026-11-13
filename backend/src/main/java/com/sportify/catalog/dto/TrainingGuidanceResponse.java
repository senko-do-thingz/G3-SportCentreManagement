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
    /**
     * language is "vi" when the question was asked in Vietnamese and the answer text is Vietnamese, otherwise "en".
     * handoff names the human role that owns the topic ("RECEPTIONIST" or "COACH"), or null.
     */
    public record Consultation(String mode, String status, String answer, boolean historyStored,
                               List<ConsultationSource> sources, String language, String handoff) {
        public Consultation(String mode, String status, String answer, boolean historyStored, List<ConsultationSource> sources) {
            this(mode, status, answer, historyStored, sources, "en", null);
        }
        public Consultation(String mode, String status, String answer, boolean historyStored,
                            List<ConsultationSource> sources, String language) {
            this(mode, status, answer, historyStored, sources, language, null);
        }
        public Consultation withHandoff(String role) {
            return new Consultation(mode, status, answer, historyStored, sources, language, role);
        }
    }
}
