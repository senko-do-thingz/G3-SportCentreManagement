package com.sportify.catalog.dto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
public final class ClassLearningResponse {
    private ClassLearningResponse() {}
    public record LearningClass(long id, String name, String sportName, String coachName, String level, int capacity, boolean active) {}
    public record Participation(long bookingId, long sessionId, LocalDate date, LocalTime startTime, LocalTime endTime,
        String bookingStatus, String attendanceStatus, String notes, Long resultId,
        String sessionStatus, String trainingFormat, int capacity, int freeSeats) {}
    public record ClassDetail(LearningClass learningClass, List<Participation> sessions) {}
    public record Question(long id, long classId, String className, String memberName, String subject, String description,
        String status, LocalDateTime createdAt) {}
    public record Message(long id, String senderName, String body, LocalDateTime createdAt) {}
    public record Discussion(Question question, List<Message> messages) {}
    public record Guidance(String source, String message, List<LearningClass> classes) {}
}
