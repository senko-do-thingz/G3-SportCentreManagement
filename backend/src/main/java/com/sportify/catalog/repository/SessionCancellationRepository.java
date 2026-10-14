package com.sportify.catalog.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Set-based cancellation shares the caller's JPA transaction and datasource connection. */
@Repository
@RequiredArgsConstructor
public class SessionCancellationRepository {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelConfirmedBookings(Long sessionId, Long managerId, String reason, LocalDateTime now) {
        // Hold update and range locks until commit so the refund set cannot change between statements.
        jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking WITH (UPDLOCK, HOLDLOCK)
                WHERE session_id = ? AND status = 'CONFIRMED'
                """, Integer.class, sessionId);

        // total_sessions is the purchased snapshot, not the current package catalog session_count.
        jdbcTemplate.update("""
                UPDATE r SET remaining_sessions = CASE
                    WHEN CAST(r.remaining_sessions AS BIGINT) + refund.sessions > r.total_sessions
                        THEN r.total_sessions
                    ELSE r.remaining_sessions + refund.sessions END,
                    updated_at = ?
                FROM sport_package_registration r
                JOIN (SELECT package_registration_id, COUNT(*) AS sessions FROM booking
                      WHERE session_id = ? AND status = 'CONFIRMED' AND package_registration_id IS NOT NULL
                      GROUP BY package_registration_id) refund ON refund.package_registration_id = r.id
                """, now, sessionId);

        return jdbcTemplate.update("""
                UPDATE booking SET status = 'CANCELLED', cancelled_by_user_id = ?, cancelled_at = ?,
                    cancel_reason = ?, updated_at = ?
                WHERE session_id = ? AND status = 'CONFIRMED'
                """, managerId, now, reason, now, sessionId);
    }
}
