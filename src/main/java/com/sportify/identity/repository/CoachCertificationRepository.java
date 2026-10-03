package com.sportify.identity.repository;

import com.sportify.identity.entity.CoachCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachCertificationRepository extends JpaRepository<CoachCertification, Long> {
}
