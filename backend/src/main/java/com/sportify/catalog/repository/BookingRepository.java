package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingCode(String bookingCode);
    List<Booking> findByMemberIdOrderByBookedAtDesc(Long memberId);
    
    @Query("SELECT b FROM Booking b JOIN FETCH b.session s WHERE b.member.id = :memberId AND s.sessionDate = :today AND b.status = :status")
    List<Booking> findTodayBookingsForMember(@Param("memberId") Long memberId, @Param("today") LocalDate today, @Param("status") BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.session.id = :sessionId AND b.member.id = :memberId AND b.status = :status")
    Optional<Booking> findBySessionIdAndMemberIdAndStatus(@Param("sessionId") Long sessionId, @Param("memberId") Long memberId, @Param("status") BookingStatus status);

    @Query(value = "SELECT NEXT VALUE FOR seq_booking_code", nativeQuery = true)
    Long getNextBookingCodeSequence();
}
