package com.sportify.identity.repository;

import com.sportify.identity.entity.CoachCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoachCertificationRepository extends JpaRepository<CoachCertification, Long> {
    List<CoachCertification> findByCoachIdOrderByIdAsc(Long coachId);

    Optional<CoachCertification> findByIdAndCoachId(Long id, Long coachId);

    // Lay tat ca chung chi cua cac huan luyen vien trong trung tam de tong hop danh sach lua chon
    @Query("SELECT c FROM CoachCertification c ORDER BY c.name ASC, c.issuer ASC, c.id ASC")
    List<CoachCertification> findAllOrdered();
}
