package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sportify.catalog.entity.MembershipStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Step 2B: Registration / renewal queries.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query(value = "SELECT NEXT VALUE FOR seq_registration_code", nativeQuery = true)
    Long getNextRegistrationCode();

    boolean existsByMemberIdAndStatus(Long memberId, MembershipStatus status);

    @Query("SELECT m FROM Membership m " +
           "WHERE m.member.id = :memberId " +
           "  AND m.status IN (:statuses) " +
           "  AND m.endDate >= :today " +
           "ORDER BY m.startDate ASC")
    List<Membership> findActiveOrScheduled(@Param("memberId") Long memberId,
                                           @Param("statuses") List<MembershipStatus> statuses,
                                           @Param("today") LocalDate today);

    List<Membership> findAllByMemberIdOrderByStartDateDesc(Long memberId);

    List<Membership> findByStatusAndStartDateLessThanEqual(MembershipStatus status, LocalDate date);

    List<Membership> findByStatusAndEndDateLessThan(MembershipStatus status, LocalDate date);
}
