package com.sportify.identity.repository;

import com.sportify.catalog.dto.SportSummaryResponse;
import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.entity.CoachSportId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CoachSportRepository extends JpaRepository<CoachSport, CoachSportId> {

    @Query("SELECT new com.sportify.catalog.dto.SportSummaryResponse(s.id, s.code, s.name) "
            + "FROM CoachSport cs JOIN Sport s ON s.id = cs.id.sportId "
            + "WHERE cs.id.coachId = :coachId ORDER BY s.id ASC")
    List<SportSummaryResponse> findProfileSports(@Param("coachId") Long coachId);

    long countByIdCoachId(Long coachId);

    List<CoachSport> findByIdCoachIdIn(Collection<Long> coachIds);
}
