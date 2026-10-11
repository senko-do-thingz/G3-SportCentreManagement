package com.sportify.identity.repository;

import com.sportify.identity.entity.CoachProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoachProfileRepository extends JpaRepository<CoachProfile, Long> {

    /**
     * Loads a coach profile with its account and role, used to validate a coach assignment.
     */
    @EntityGraph(attributePaths = {"userAccount", "userAccount.role"})
    Optional<CoachProfile> findWithAccountById(Long id);

    /**
     * Coaches whose account has the COACH role and is ACTIVE, ordered by name.
     */
    @Query("SELECT cp FROM CoachProfile cp JOIN FETCH cp.userAccount u JOIN u.role r "
            + "WHERE r.code = 'COACH' AND u.status = 'ACTIVE' "
            + "ORDER BY u.fullName ASC, cp.id ASC")
    List<CoachProfile> findActiveCoaches();

    /**
     * Active coaches (see {@link #findActiveCoaches()}) who are able to teach the given sport
     * (a row exists in coach_sport).
     */
    @Query("SELECT cp FROM CoachProfile cp JOIN FETCH cp.userAccount u JOIN u.role r "
            + "WHERE r.code = 'COACH' AND u.status = 'ACTIVE' "
            + "AND EXISTS (SELECT cs.id.coachId FROM CoachSport cs "
            + "            WHERE cs.id.coachId = cp.id AND cs.id.sportId = :sportId) "
            + "ORDER BY u.fullName ASC, cp.id ASC")
    List<CoachProfile> findActiveCoachesBySport(@Param("sportId") Long sportId);
}
