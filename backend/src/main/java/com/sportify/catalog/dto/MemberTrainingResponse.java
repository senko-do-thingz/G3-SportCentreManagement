package com.sportify.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

/** Read models for the signed-in member; no member id is accepted from the browser. */
public final class MemberTrainingResponse {
    private MemberTrainingResponse() {}
    public record Plan(Long id, String title, String description, String className, String coachName) {}
    public record Step(Long id, int stepOrder, String title, int durationMinutes, String instructions) {}
    public record PlanDetail(Plan plan, List<Step> steps, List<Feedback> feedback) {}
    public record Session(Long id, String className, LocalDate date, LocalTime startTime, LocalTime endTime) {}
    public record Attendance(Long id, Session session, String status, String notes,
                             Long bookingId, String bookingStatus, String sessionStatus, String recordedStatus) {}
    public record Result(Long id, Session session, String notes) {}
    public record Score(Long id, String metricName, String description, BigDecimal score, int maxScore) {}
    public record Feedback(Long id, Long planId, String planTitle, String coachName, int rating, String comments,
                           String strengths, String areasForImprovement, LocalDateTime createdAt) {}
    public record ResultDetail(Result result, List<Score> scores) {}
}
