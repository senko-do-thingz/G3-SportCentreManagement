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
                                 LocalDate date, LocalTime startTime, LocalTime endTime, String trainingType) {}
    public record SessionMember(long bookingId, long memberId, String memberName, Long attendanceId, String status, String notes) {}
    public record CoachPlan(long id, String title, String description, long classId, String className,
                            long memberId, String memberName, String coachName) {}
    public record PlanStep(long id, int stepOrder, String title, int durationMinutes, String instructions) {}
    public record CoachPlanDetail(CoachPlan plan, List<PlanStep> steps) {}
    public record Metric(long id, String name, int maxScore) {}
}
