package com.sportify.catalog.repository;

import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long>, JpaSpecificationExecutor<ClassSession> {
    List<ClassSession> findBySessionDateAndStatus(LocalDate sessionDate, SessionStatus status);
    List<ClassSession> findBySportIdAndSessionDateAndStatus(Long sportId, LocalDate sessionDate, SessionStatus status);

    @Override
    @EntityGraph(attributePaths = {"sportClass", "coach", "coach.userAccount", "facility"})
    List<ClassSession> findAll(Specification<ClassSession> specification, Sort sort);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClassSession s WHERE s.id = :id")
    Optional<ClassSession> findForUpdate(@Param("id") Long id);

    /** Separate resource queries keep both resource/date indexes usable without an OR predicate. */
    @EntityGraph(attributePaths = {"sportClass", "coach", "coach.userAccount", "facility"})
    @Query("SELECT s FROM ClassSession s WHERE s.coach.id = :coachId AND s.sessionDate IN :dates "
            + "AND s.status IN :statuses AND s.startTime < cast(:endTime as time) "
            + "AND s.endTime > cast(:startTime as time) AND (:excludedId IS NULL OR s.id <> :excludedId)")
    List<ClassSession> findCoachConflicts(@Param("coachId") Long coachId, @Param("dates") Collection<LocalDate> dates,
            @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
            @Param("statuses") Collection<SessionStatus> statuses, @Param("excludedId") Long excludedId);

    @EntityGraph(attributePaths = {"sportClass", "coach", "coach.userAccount", "facility"})
    @Query("SELECT s FROM ClassSession s WHERE s.facility.id = :facilityId AND s.sessionDate IN :dates "
            + "AND s.status IN :statuses AND s.startTime < cast(:endTime as time) "
            + "AND s.endTime > cast(:startTime as time) AND (:excludedId IS NULL OR s.id <> :excludedId)")
    List<ClassSession> findFacilityConflicts(@Param("facilityId") Long facilityId, @Param("dates") Collection<LocalDate> dates,
            @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
            @Param("statuses") Collection<SessionStatus> statuses, @Param("excludedId") Long excludedId);

    /**
     * Number of sessions per class that have not started yet (session date after today, or today
     * with a start time after now) and whose status is one of the given statuses.
     * Classes without such sessions are absent from the result.
     */
    @Query("SELECT s.sportClass.id AS classId, COUNT(s) AS sessionCount FROM ClassSession s "
            + "WHERE s.sportClass.id IN :classIds "
            + "AND s.status IN :statuses "
            + "AND (s.sessionDate > :today OR (s.sessionDate = :today AND s.startTime > cast(:now as time))) "
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
