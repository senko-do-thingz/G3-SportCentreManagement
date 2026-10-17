package com.sportify.catalog.repository;

import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.CheckInResult;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    @Query("""
            select c from CheckIn c
            where c.checkedInAt >= :start and (:end is null or c.checkedInAt < :end)
              and (:memberId is null or c.member.id = :memberId)
            order by c.checkedInAt desc, c.id desc
            """)
    List<CheckIn> findForDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
            @Param("memberId") Long memberId);
    List<CheckIn> findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(Long memberId);
    List<CheckIn> findByMemberIdOrderByCheckedInAtDesc(Long memberId);
    boolean existsByBookingIdAndResult(Long bookingId, CheckInResult result);
}
