package com.sportify.catalog.repository;

import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SportPackageRegistrationRepository extends JpaRepository<SportPackageRegistration, Long> {
    Optional<SportPackageRegistration> findByRegistrationCode(String registrationCode);
    List<SportPackageRegistration> findByMemberIdOrderByStartDateDesc(Long memberId);
    Optional<SportPackageRegistration> findByPaymentId(Long paymentId);

    /**
     * The payment id of a registration, read as a single value so that no registration entity is loaded before the
     * payment row is locked (lock order: payment first, then the registration). Empty when there is none.
     */
    @Query("SELECT r.payment.id FROM SportPackageRegistration r WHERE r.id = :registrationId")
    Optional<Long> findPaymentIdById(@Param("registrationId") Long registrationId);

    /** Registrations paid by the given payments, with their package, for the front desk payment list. */
    @Query("SELECT r FROM SportPackageRegistration r JOIN FETCH r.sportPackage WHERE r.payment.id IN :paymentIds")
    List<SportPackageRegistration> findByPaymentIdIn(@Param("paymentIds") Collection<Long> paymentIds);

    /** Call only after locking the session and checking its booking state. */
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            UPDATE sport_package_registration
            SET remaining_sessions = remaining_sessions - 1, updated_at = :now
            WHERE id = :id AND status = :status AND remaining_sessions > 0
                AND start_date <= :date AND end_date >= :date
            """, nativeQuery = true)
    int consumeSession(@Param("id") Long id, @Param("status") String status,
            @Param("date") LocalDate date, @Param("now") LocalDateTime now);

    /** Database eligibility and arithmetic serialize with consumption and manager refunds. */
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            UPDATE sport_package_registration
            SET remaining_sessions = CASE WHEN remaining_sessions < total_sessions
                    THEN remaining_sessions + 1 ELSE total_sessions END, updated_at = :now
            WHERE id = :id AND status = :status
            """, nativeQuery = true)
    int restoreSession(@Param("id") Long id, @Param("status") String status, @Param("now") LocalDateTime now);

    /** Scalar projection bypasses a stale managed registration after a failed conditional debit. */
    @Query("SELECT r.status AS status, r.startDate AS startDate, r.endDate AS endDate "
            + "FROM SportPackageRegistration r WHERE r.id = :id")
    Optional<BookingState> findBookingStateById(@Param("id") Long id);

    interface BookingState {
        PackageRegistrationStatus getStatus();
        LocalDate getStartDate();
        LocalDate getEndDate();
    }

    @Query("SELECT spr FROM SportPackageRegistration spr JOIN FETCH spr.sportPackage p WHERE spr.member.id = :memberId AND spr.status = :status AND spr.startDate <= :today AND spr.endDate >= :today AND spr.remainingSessions > 0")
    List<SportPackageRegistration> findActiveRegistrations(@Param("memberId") Long memberId, @Param("status") PackageRegistrationStatus status, @Param("today") LocalDate today);

    @Query("SELECT spr FROM SportPackageRegistration spr JOIN FETCH spr.sportPackage p WHERE spr.member.id = :memberId AND p.sport.id = :sportId AND spr.status = :status AND spr.startDate <= :today AND spr.endDate >= :today AND spr.remainingSessions > 0")
    List<SportPackageRegistration> findActiveRegistrationsForSport(@Param("memberId") Long memberId, @Param("sportId") Long sportId, @Param("status") PackageRegistrationStatus status, @Param("today") LocalDate today);

    @Query(value = "SELECT NEXT VALUE FOR seq_package_reg_code", nativeQuery = true)
    Long getNextRegistrationCodeSequence();
}
