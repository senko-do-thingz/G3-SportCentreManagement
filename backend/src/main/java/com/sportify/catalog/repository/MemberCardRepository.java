package com.sportify.catalog.repository;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberCardRepository extends JpaRepository<MemberCard, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"tier", "member", "member.userAccount"})
    org.springframework.data.domain.Page<MemberCard> findByStatus(CardStatus status, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT c FROM MemberCard c JOIN FETCH c.tier JOIN FETCH c.member m JOIN FETCH m.userAccount "
            + "WHERE m.id = :memberId AND c.status = com.sportify.catalog.entity.CardStatus.ACTIVE "
            + "AND c.startDate <= :today AND (c.endDate IS NULL OR c.endDate >= :today) "
            + "ORDER BY c.tier.tierRank DESC, c.id DESC")
    List<MemberCard> findCurrentForOverview(@Param("memberId") Long memberId, @Param("today") LocalDate today,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"tier", "member", "member.userAccount"})
    Optional<MemberCard> findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc(Long memberId, CardStatus status);

    Optional<MemberCard> findByCardCode(String cardCode);
    List<MemberCard> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<MemberCard> findByMemberIdAndStatus(Long memberId, CardStatus status);

    Optional<MemberCard> findByPaymentId(Long paymentId);

    @Query("SELECT mc FROM MemberCard mc JOIN FETCH mc.tier WHERE mc.member.id = :memberId AND mc.status = :status AND (mc.endDate IS NULL OR mc.endDate >= :today) ORDER BY mc.tier.discountPercentage DESC")
    List<MemberCard> findActiveCardsForMember(@Param("memberId") Long memberId, @Param("status") CardStatus status, @Param("today") LocalDate today);

    @Query(value = "SELECT NEXT VALUE FOR seq_card_code", nativeQuery = true)
    Long getNextCardCodeSequence();
}
