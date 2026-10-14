package com.sportify.catalog.repository;

import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SportPackageRegistrationRepository extends JpaRepository<SportPackageRegistration, Long> {
    Optional<SportPackageRegistration> findByRegistrationCode(String registrationCode);
    List<SportPackageRegistration> findByMemberIdOrderByStartDateDesc(Long memberId);
    Optional<SportPackageRegistration> findByPaymentId(Long paymentId);

    /** Registrations paid by the given payments, with their package, for the front desk payment list. */
    @Query("SELECT r FROM SportPackageRegistration r JOIN FETCH r.sportPackage WHERE r.payment.id IN :paymentIds")
    List<SportPackageRegistration> findByPaymentIdIn(@Param("paymentIds") Collection<Long> paymentIds);

    @Query("SELECT spr FROM SportPackageRegistration spr JOIN FETCH spr.sportPackage p WHERE spr.member.id = :memberId AND spr.status = :status AND spr.startDate <= :today AND spr.endDate >= :today AND spr.remainingSessions > 0")
    List<SportPackageRegistration> findActiveRegistrations(@Param("memberId") Long memberId, @Param("status") PackageRegistrationStatus status, @Param("today") LocalDate today);

    @Query("SELECT spr FROM SportPackageRegistration spr JOIN FETCH spr.sportPackage p WHERE spr.member.id = :memberId AND p.sport.id = :sportId AND spr.status = :status AND spr.startDate <= :today AND spr.endDate >= :today AND spr.remainingSessions > 0")
    List<SportPackageRegistration> findActiveRegistrationsForSport(@Param("memberId") Long memberId, @Param("sportId") Long sportId, @Param("status") PackageRegistrationStatus status, @Param("today") LocalDate today);

    @Query(value = "SELECT NEXT VALUE FOR seq_package_reg_code", nativeQuery = true)
    Long getNextRegistrationCodeSequence();
}
