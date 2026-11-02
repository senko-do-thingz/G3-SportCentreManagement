package com.sportify.identity.repository;

import com.sportify.identity.entity.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.sessionId = :sessionId")
    Optional<UserSession> findForUpdateBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now, s.revokedReason = :reason WHERE s.sessionId = :sessionId AND s.revokedAt IS NULL")
    int revokeSession(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now, s.revokedReason = :reason WHERE s.userAccount.id = :userId AND s.revokedAt IS NULL")
    int revokeAllUserSessions(@Param("userId") Long userId, @Param("now") LocalDateTime now, @Param("reason") String reason);
}
