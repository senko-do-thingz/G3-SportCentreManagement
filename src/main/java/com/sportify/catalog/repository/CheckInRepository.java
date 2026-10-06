package com.sportify.catalog.repository;

import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.CheckInResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    List<CheckIn> findByMemberIdOrderByCheckedInAtDesc(Long memberId);
    boolean existsByBookingIdAndResult(Long bookingId, CheckInResult result);
}
