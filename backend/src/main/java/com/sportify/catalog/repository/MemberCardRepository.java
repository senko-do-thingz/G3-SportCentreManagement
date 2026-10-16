package com.sportify.catalog.repository;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
public interface MemberCardRepository extends JpaRepository<MemberCard, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("UPDATE MemberCard c SET c.status = com.sportify.catalog.entity.CardStatus.CANCELLED, "
            + "c.updatedAt = :now WHERE c.status = com.sportify.catalog.entity.CardStatus.PENDING_PAYMENT "
            + "AND c.createdAt < :cutoff")
    int cancelOverduePending(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("UPDATE MemberCard c SET c.status = com.sportify.catalog.entity.CardStatus.EXPIRED, "
            + "c.updatedAt = :now WHERE c.status = com.sportify.catalog.entity.CardStatus.ACTIVE "
            + "AND c.endDate < :today")
    int expireActive(@Param("today") LocalDate today, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"tier", "member", "member.userAccount"})
    Page<MemberCard> findByStatus(CardStatus status, Pageable pageable);

    @Query("SELECT c FROM MemberCard c JOIN FETCH c.tier JOIN FETCH c.member m JOIN FETCH m.userAccount "
            + "WHERE m.id = :memberId AND c.status = com.sportify.catalog.entity.CardStatus.ACTIVE "
            + "AND c.startDate <= :today AND (c.endDate IS NULL OR c.endDate >= :today) "
            + "ORDER BY c.tier.tierRank DESC, c.id DESC")
    List<MemberCard> findCurrentForOverview(@Param("memberId") Long memberId, @Param("today") LocalDate today,
            Pageable pageable);

    @EntityGraph(attributePaths = {"tier", "member", "member.userAccount"})
    Optional<MemberCard> findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc(Long memberId, CardStatus status);

    Optional<MemberCard> findByCardCode(String cardCode);
    List<MemberCard> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<MemberCard> findByMemberIdAndStatus(Long memberId, CardStatus status);

    Optional<MemberCard> findByPaymentId(Long paymentId);

    /** True when a membership card is paid by this payment (BE-21: such a payment is not a package payment). */
    boolean existsByPaymentId(Long paymentId);

    /**
     * The payment id of a card, read as a single value so that no card entity is loaded before the payment row
     * is locked (lock order: payment first, then the card). Empty when the card does not exist or has no payment.
     */
    @Query("SELECT mc.payment.id FROM MemberCard mc WHERE mc.id = :cardId")
    Optional<Long> findPaymentIdById(@Param("cardId") Long cardId);

    /** Cards paid by the given payments, with their tier, for the front desk payment list. */
    @Query("SELECT mc FROM MemberCard mc JOIN FETCH mc.tier WHERE mc.payment.id IN :paymentIds")
    List<MemberCard> findByPaymentIdIn(@Param("paymentIds") Collection<Long> paymentIds);

    @Query("SELECT mc FROM MemberCard mc JOIN FETCH mc.tier WHERE mc.member.id = :memberId AND mc.status = :status AND (mc.endDate IS NULL OR mc.endDate >= :today) ORDER BY mc.tier.discountPercentage DESC")
    List<MemberCard> findActiveCardsForMember(@Param("memberId") Long memberId, @Param("status") CardStatus status, @Param("today") LocalDate today);

    @Query(value = "SELECT NEXT VALUE FOR seq_card_code", nativeQuery = true)
    Long getNextCardCodeSequence();
}
