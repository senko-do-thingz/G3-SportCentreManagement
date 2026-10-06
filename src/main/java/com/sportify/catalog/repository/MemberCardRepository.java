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
    Optional<MemberCard> findByCardCode(String cardCode);
    List<MemberCard> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    
    @Query("SELECT mc FROM MemberCard mc JOIN FETCH mc.tier WHERE mc.member.id = :memberId AND mc.status = :status AND (mc.endDate IS NULL OR mc.endDate >= :today) ORDER BY mc.tier.discountPercentage DESC")
    List<MemberCard> findActiveCardsForMember(@Param("memberId") Long memberId, @Param("status") CardStatus status, @Param("today") LocalDate today);

    @Query(value = "SELECT NEXT VALUE FOR seq_card_code", nativeQuery = true)
    Long getNextCardCodeSequence();
}
