package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Scalar lookup keeps Booking out of the persistence context until its session is locked. */
    @Query("SELECT b.session.id FROM Booking b WHERE b.id = :bookingId")
    Optional<Long> findSessionIdByBookingId(@Param("bookingId") Long bookingId);

    @Query("SELECT b.session.id AS sessionId, COUNT(b) AS bookingCount FROM Booking b "
            + "WHERE b.session.id IN :sessionIds AND b.status = :status GROUP BY b.session.id")
    List<SessionBookingCount> countBySessionIds(@Param("sessionIds") Collection<Long> sessionIds,
            @Param("status") BookingStatus status);

    /** Calendar filters avoid an unbounded IN list and SQL Server's parameter limit. */
    @Query("SELECT s.id AS sessionId, COUNT(b) AS bookingCount FROM Booking b JOIN b.session s "
            + "WHERE s.sessionDate BETWEEN :from AND :to AND b.status = :status "
            + "AND (:classId IS NULL OR s.sportClass.id = :classId) "
            + "AND (:coachId IS NULL OR s.coach.id = :coachId) "
            + "AND (:facilityId IS NULL OR s.facility.id = :facilityId) GROUP BY s.id")
    List<SessionBookingCount> countForSessionCalendar(@Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("classId") Long classId, @Param("coachId") Long coachId, @Param("facilityId") Long facilityId,
            @Param("status") BookingStatus status);

    interface SessionBookingCount {
        Long getSessionId();
        long getBookingCount();
    }
    Optional<Booking> findByBookingCode(String bookingCode);
    List<Booking> findByMemberIdOrderByBookedAtDesc(Long memberId);
    
    @Query("SELECT b FROM Booking b JOIN FETCH b.session s WHERE b.member.id = :memberId AND s.sessionDate = :today AND b.status = :status")
    List<Booking> findTodayBookingsForMember(@Param("memberId") Long memberId, @Param("today") LocalDate today, @Param("status") BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.session.id = :sessionId AND b.member.id = :memberId AND b.status = :status")
    Optional<Booking> findBySessionIdAndMemberIdAndStatus(@Param("sessionId") Long sessionId, @Param("memberId") Long memberId, @Param("status") BookingStatus status);

    @Query(value = "SELECT NEXT VALUE FOR seq_booking_code", nativeQuery = true)
    Long getNextBookingCodeSequence();

    /**
     * Counts bookings with the given status on sessions of a class that have not started yet
     * (session date after today, or today with a start time after now) and whose session status
     * is one of the given statuses.
     */
    @Query("SELECT COUNT(b) FROM Booking b JOIN b.session s "
            + "WHERE s.sportClass.id = :classId "
            + "AND b.status = :bookingStatus "
            + "AND s.status IN :sessionStatuses "
            + "AND (s.sessionDate > :today OR (s.sessionDate = :today AND s.startTime > cast(:now as time)))")
    long countUpcomingByClass(@Param("classId") Long classId,
                              @Param("bookingStatus") BookingStatus bookingStatus,
                              @Param("sessionStatuses") Collection<SessionStatus> sessionStatuses,
                              @Param("today") LocalDate today,
                              @Param("now") LocalTime now);
}
