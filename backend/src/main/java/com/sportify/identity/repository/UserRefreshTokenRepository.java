package com.sportify.identity.repository;

import com.sportify.identity.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    Optional<UserRefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE UserRefreshToken t SET t.consumedAt = :now WHERE t.tokenHash = :tokenHash AND t.consumedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int consumeToken(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserRefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserRefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);
}
