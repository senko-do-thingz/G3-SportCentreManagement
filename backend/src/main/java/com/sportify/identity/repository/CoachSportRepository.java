package com.sportify.identity.repository;

import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.entity.CoachSportId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachSportRepository extends JpaRepository<CoachSport, CoachSportId> {

    long countByIdCoachId(Long coachId);
}
