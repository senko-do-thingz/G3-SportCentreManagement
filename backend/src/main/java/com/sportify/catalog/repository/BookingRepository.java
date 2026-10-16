package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    String MY_FROM = " FROM Booking b JOIN b.session s ";
    String MY_WHERE = " WHERE b.member.id = :memberId AND (:period IS NULL "
            + "OR (:period = 'UPCOMING' AND b.status = com.sportify.catalog.entity.BookingStatus.CONFIRMED "
            + "AND s.status IN (com.sportify.catalog.entity.SessionStatus.DRAFT, com.sportify.catalog.entity.SessionStatus.PUBLISHED) "
            + "AND (s.sessionDate > :today OR (s.sessionDate = :today AND s.startTime > cast(:now as time)))) "
            + "OR (:period = 'PAST' AND (b.status = com.sportify.catalog.entity.BookingStatus.CANCELLED "
            + "OR s.status IN (com.sportify.catalog.entity.SessionStatus.CANCELLED, com.sportify.catalog.entity.SessionStatus.COMPLETED) "
            + "OR s.sessionDate < :today OR (s.sessionDate = :today AND s.startTime <= cast(:now as time)))))";

    @EntityGraph(attributePaths = {"session.sport", "session.sportClass", "session.coach.userAccount", "session.facility",
            "member.userAccount", "packageRegistration.sportPackage", "cancelledBy.role"})
    @Query(value = "SELECT b" + MY_FROM + MY_WHERE, countQuery = "SELECT COUNT(b)" + MY_FROM + MY_WHERE)
    Page<Booking> findMyBookings(@Param("memberId") Long memberId, @Param("period") String period,
            @Param("today") LocalDate today, @Param("now") LocalTime now, Pageable pageable);

    String STAFF_WHERE = MY_WHERE + " AND (:status IS NULL OR b.status = :status)";

    @EntityGraph(attributePaths = {"session.sport", "session.sportClass", "session.coach.userAccount", "session.facility",
            "member.userAccount", "packageRegistration.sportPackage", "cancelledBy.role"})
    @Query(value = "SELECT b" + MY_FROM + STAFF_WHERE, countQuery = "SELECT COUNT(b)" + MY_FROM + STAFF_WHERE)
    Page<Booking> findMemberBookings(@Param("memberId") Long memberId, @Param("period") String period,
            @Param("status") BookingStatus status, @Param("today") LocalDate today,
            @Param("now") LocalTime now, Pageable pageable);

    long countBySessionIdAndStatus(Long sessionId, BookingStatus status);

    /** Scalar lookup keeps Booking out of the persistence context until its session is locked. */
    @Query("SELECT b.session.id FROM Booking b WHERE b.id = :bookingId")
    Optional<Long> findSessionIdByBookingId(@Param("bookingId") Long bookingId);

    @Query("SELECT COUNT(b) FROM Booking b JOIN b.packageRegistration r "
            + "WHERE b.session.id = :sessionId AND b.status = :status "
            + "AND (r.startDate > :date OR r.endDate < :date)")
    long countOutsidePackageValidity(@Param("sessionId") Long sessionId,
            @Param("status") BookingStatus status, @Param("date") LocalDate date);

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
    @EntityGraph(attributePaths = {"session.sport", "session.sportClass", "session.coach.userAccount", "session.facility",
            "member.userAccount", "packageRegistration.sportPackage", "cancelledBy.role"})
    @Query("SELECT b FROM Booking b WHERE b.member.id = :memberId ORDER BY b.bookedAt DESC, b.id DESC")
    List<Booking> findByMemberIdOrderByBookedAtDesc(@Param("memberId") Long memberId);
    
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

    /** One booking a refund would cancel: the booking id and the id of its session. */
    interface RefundableBookingRow {
        Long getBookingId();
        Long getSessionId();
    }

    /**
     * Bookings of a package registration with the given status, on sessions that have not started yet (session date
     * after today, or today with a start time after now), without a check-in with the given result. One set-based
     * query (BE-21 P2-01): the check-in filter is a NOT EXISTS, not one query per booking. Scalar rows keep Booking
     * and ClassSession out of the persistence context until the refund has locked the sessions. Ordered by session id.
     * Used by the refund quote (they count as unused sessions) and by refund completion (they are cancelled).
     */
    @Query("SELECT b.id AS bookingId, s.id AS sessionId FROM Booking b JOIN b.session s "
            + "WHERE b.packageRegistration.id = :registrationId "
            + "AND b.status = :bookingStatus "
            + "AND (s.sessionDate > :today OR (s.sessionDate = :today AND s.startTime > cast(:now as time))) "
            + "AND NOT EXISTS (SELECT c.id FROM CheckIn c WHERE c.booking.id = b.id AND c.result = :checkInResult) "
            + "ORDER BY s.id, b.id")
    List<RefundableBookingRow> findRefundableUpcomingBookings(@Param("registrationId") Long registrationId,
                                                             @Param("bookingStatus") BookingStatus bookingStatus,
                                                             @Param("checkInResult") CheckInResult checkInResult,
                                                             @Param("today") LocalDate today,
                                                             @Param("now") LocalTime now);

    /**
     * Cancels the listed bookings of one session that are still CONFIRMED and have no ALLOWED check-in. The caller
     * holds the session row lock (BE-08 and BE-10 lock order: session, then bookings), so the returned count is exact
     * and is the number of seats to release on that session. Native SQL, like SessionCancellationRepository (BE-10).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE booking SET status = 'CANCELLED', cancelled_by_user_id = :actorId, cancelled_at = :now, "
            + "cancel_reason = :reason, updated_at = :now "
            + "WHERE id IN (:bookingIds) AND session_id = :sessionId AND status = 'CONFIRMED' "
            + "AND NOT EXISTS (SELECT 1 FROM check_in c WHERE c.booking_id = booking.id AND c.result = 'ALLOWED')",
            nativeQuery = true)
    int cancelConfirmedSessionBookings(@Param("bookingIds") Collection<Long> bookingIds,
                                       @Param("sessionId") Long sessionId,
                                       @Param("actorId") Long actorId,
                                       @Param("reason") String reason,
                                       @Param("now") LocalDateTime now);
}
