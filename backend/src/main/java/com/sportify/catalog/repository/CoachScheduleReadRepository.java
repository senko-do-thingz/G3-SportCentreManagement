package com.sportify.catalog.repository;

import com.sportify.catalog.dto.CoachRosterResponse;
import com.sportify.catalog.dto.CoachSessionResponse;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassSession;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-only scalar queries; existing write repositories and locks remain unchanged. */
public interface CoachScheduleReadRepository extends Repository<ClassSession, Long> {
    @Query("""
            SELECT new com.sportify.catalog.dto.CoachSessionResponse(
                s.id, c.id, c.name, sport.id, sport.name, co.id, u.fullName,
                f.id, f.name, s.sessionDate, s.startTime, s.endTime, s.capacity, s.status)
            FROM ClassSession s JOIN s.coach co LEFT JOIN co.userAccount u
            LEFT JOIN s.sportClass c JOIN s.sport sport LEFT JOIN s.facility f
            WHERE s.sessionDate BETWEEN :from AND :to AND (:coachId IS NULL OR co.id = :coachId)
            ORDER BY s.sessionDate ASC, s.startTime ASC, s.id ASC
            """)
    List<CoachSessionResponse> findTeachingSessions(@Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("coachId") Long coachId);

    @Query("SELECT s.id AS id, co.id AS coachId FROM ClassSession s LEFT JOIN s.coach co WHERE s.id = :id")
    Optional<SessionOwnership> findOwnership(@Param("id") Long id);

    interface SessionOwnership {
        Long getId();
        Long getCoachId();
    }

    @Query("""
            SELECT new com.sportify.catalog.dto.CoachRosterResponse(
                b.id, m.memberCode, u.fullName, u.phone, r.id, p.id, p.name, b.bookedAt)
            FROM Booking b JOIN b.session s LEFT JOIN s.coach assigned
            LEFT JOIN b.member m LEFT JOIN m.userAccount u
            LEFT JOIN b.packageRegistration r ON r.member.id = m.id
            LEFT JOIN r.sportPackage p
            WHERE s.id = :sessionId AND b.status = :status AND (:manager = true OR assigned.id = :coachId)
            ORDER BY b.bookedAt ASC, b.id ASC
            """)
    List<CoachRosterResponse> findRoster(@Param("sessionId") Long sessionId,
            @Param("status") BookingStatus status, @Param("manager") boolean manager,
            @Param("coachId") Long coachId);
}
