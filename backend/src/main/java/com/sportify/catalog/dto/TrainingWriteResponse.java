package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TrainingWriteResponse {
    private TrainingWriteResponse() {}
    public record Created(long id) {}
    public record ClassSummary(long id, String name, long sportId) {}
    public record BookingCandidate(long bookingId, long memberId, String memberName, LocalDate sessionDate) {}
    public record SessionSummary(long id, Long classId, String className, long sportId,
                                 LocalDate date, LocalTime startTime, LocalTime endTime, String trainingType,
                                 boolean attendanceDateOpen, String sessionId, String sportName, String coachName, String facilityName, String status, long bookedCount, long markedCount, String attendanceState) {
        /** Transitional Java constructor; live mappers supply the persisted public identity. */
        public SessionSummary(long id, Long classId, String className, long sportId,
                                     LocalDate date, LocalTime startTime, LocalTime endTime, String trainingType,
                                     boolean attendanceDateOpen) {
            this(id, classId, className, sportId, date, startTime, endTime, trainingType, attendanceDateOpen, null, null, null, null, null, 0, 0, attendanceDateOpen ? "OPEN" : "CLOSED");
        }
    }
    public record SessionMember(long bookingId, long memberId, String memberName, Long attendanceId, String status, String notes, Long resultId) {}
    public record CoachPlan(long id, String title, String description, long classId, String className,
                            long memberId, String memberName, String coachName) {}
    public record PlanStep(long id, int stepOrder, String title, int durationMinutes, String instructions) {}
    public record CoachPlanDetail(CoachPlan plan, List<PlanStep> steps) {}
    public record Metric(long id, String name, int maxScore) {}
    public record DrillDraft(String title, String goal, int durationMinutes, String instructions, String reason, String level) {}
    public record DrillSuggestions(String mode, String ruleVersion, boolean requiresCoachReview, boolean persisted,
        long bookingId, long memberId, long classId, String sportName, String level, List<DrillDraft> drafts) {}
}
