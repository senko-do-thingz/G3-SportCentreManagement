package com.sportify.catalog.repository;

import com.sportify.catalog.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Step 2A: schema only. Check-in queries are added in Step 2B.
 */
import java.util.List;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    List<CheckIn> findByMemberIdOrderByCheckedInAtDesc(Long memberId);
}
