package com.sportify.catalog.repository;

import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    List<ClassSession> findBySessionDateAndStatus(LocalDate sessionDate, SessionStatus status);
    List<ClassSession> findBySportIdAndSessionDateAndStatus(Long sportId, LocalDate sessionDate, SessionStatus status);

    /**
     * Number of sessions per class that have not started yet (session date after today, or today
     * with a start time after now) and whose status is one of the given statuses.
     * Classes without such sessions are absent from the result.
     */
    @Query("SELECT s.sportClass.id AS classId, COUNT(s) AS sessionCount FROM ClassSession s "
            + "WHERE s.sportClass.id IN :classIds "
            + "AND s.status IN :statuses "
            + "AND (s.sessionDate > :today OR (s.sessionDate = :today AND s.startTime > :now)) "
            + "GROUP BY s.sportClass.id")
    List<ClassSessionCount> countUpcomingByClassIds(@Param("classIds") Collection<Long> classIds,
                                                    @Param("statuses") Collection<SessionStatus> statuses,
                                                    @Param("today") LocalDate today,
                                                    @Param("now") LocalTime now);

    /** Projection row of {@link #countUpcomingByClassIds}. */
    interface ClassSessionCount {
        Long getClassId();

        Long getSessionCount();
    }
}
