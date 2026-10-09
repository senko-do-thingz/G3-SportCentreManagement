package com.sportify.identity.repository;

import com.sportify.identity.entity.CoachProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachProfileRepository extends JpaRepository<CoachProfile, Long> {
}
