package com.sportify.catalog.repository;

import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    List<ClassSession> findBySessionDateAndStatus(LocalDate sessionDate, SessionStatus status);
    List<ClassSession> findBySportIdAndSessionDateAndStatus(Long sportId, LocalDate sessionDate, SessionStatus status);
}
