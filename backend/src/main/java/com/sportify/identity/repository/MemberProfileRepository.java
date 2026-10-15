package com.sportify.identity.repository;

import com.sportify.identity.entity.MemberProfile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
    @Query("SELECT m FROM MemberProfile m JOIN FETCH m.userAccount WHERE m.id = :id")
    Optional<MemberProfile> findWithUserById(@Param("id") Long id);

    // SQL Server's default case-insensitive collation handles casing without LOWER on indexed columns.
    String FRONT_DESK_FROM = """
            FROM MemberProfile m JOIN m.userAccount u
            LEFT JOIN MemberCard c ON c.member = m AND c.status = com.sportify.catalog.entity.CardStatus.ACTIVE
                AND c.startDate <= :today AND (c.endDate IS NULL OR c.endDate >= :today)
            LEFT JOIN c.tier t
            WHERE (c.id IS NULL OR NOT EXISTS (
                SELECT 1 FROM MemberCard better WHERE better.member = m
                    AND better.status = com.sportify.catalog.entity.CardStatus.ACTIVE
                    AND better.startDate <= :today AND (better.endDate IS NULL OR better.endDate >= :today)
                    AND (better.tier.tierRank > c.tier.tierRank
                        OR (better.tier.tierRank = c.tier.tierRank AND better.id > c.id))))
                AND (:pattern IS NULL OR u.fullName LIKE :pattern ESCAPE '\\'
                    OR u.email LIKE :pattern ESCAPE '\\' OR u.phone LIKE :pattern ESCAPE '\\'
                    OR m.memberCode LIKE :pattern ESCAPE '\\')
            """;

    @Query(value = """
            SELECT m.id AS userId, m.memberCode AS memberCode, u.fullName AS fullName,
                u.email AS email, u.phone AS phone, m.currentLevel AS currentLevel,
                t.code AS cardTier, u.status AS status
            """ + FRONT_DESK_FROM, countQuery = "SELECT count(m) " + FRONT_DESK_FROM)
    Page<MemberSearchRow> searchFrontDesk(
            @Param("pattern") String pattern,
            @Param("today") LocalDate today,
            Pageable pageable);

    Optional<MemberProfile> findByMemberCode(String memberCode);

    @Query(value = "SELECT NEXT VALUE FOR seq_member_code", nativeQuery = true)
    Long getNextMemberCode();

}
