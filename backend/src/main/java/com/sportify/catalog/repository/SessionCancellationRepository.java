package com.sportify.catalog.repository;

import com.sportify.catalog.entity.PackageRegistrationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Set-based cancellation shares the caller's JPA transaction and datasource connection. */
@Repository
@RequiredArgsConstructor
public class SessionCancellationRepository {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public CancellationResult cancelConfirmedBookings(Long sessionId, Long managerId, String reason, LocalDateTime now) {
        // Lock order: caller locks session first, then this booking range, then package registrations.
        List<PackageBookings> registrations = jdbcTemplate.query("""
                SELECT package_registration_id, COUNT(*) AS package_bookings FROM booking WITH (UPDLOCK, HOLDLOCK)
                WHERE session_id = ? AND status = 'CONFIRMED' AND package_registration_id IS NOT NULL
                GROUP BY package_registration_id ORDER BY package_registration_id
                """, (rs, rowNum) -> new PackageBookings(rs.getLong("package_registration_id"),
                        rs.getInt("package_bookings")), sessionId);

        // Match BE-08: only ACTIVE registrations receive credits, capped at the purchased snapshot.
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
                WHERE r.status = ?
                """, now, sessionId, PackageRegistrationStatus.ACTIVE.name());

        int cancelled = jdbcTemplate.update("""
                UPDATE booking SET status = 'CANCELLED', cancelled_by_user_id = ?, cancelled_at = ?,
                    cancel_reason = ?, updated_at = ?
                WHERE session_id = ? AND status = 'CONFIRMED'
                """, managerId, now, reason, now, sessionId);
        return new CancellationResult(cancelled, registrations);
    }

    /**
     * E2E-BUG-02 / E2E-D5 (PROVISIONAL, policy pending): confirmed bookings of the session with participation
     * evidence, meaning PRESENT or LATE attendance or an ALLOWED front-desk check-in. The caller holds the session row
     * lock, which attendance and check-in writes for this session also take.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int countBookingsWithParticipation(Long sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking b WHERE b.session_id = ? AND b.status = 'CONFIRMED' AND (
                    EXISTS (SELECT 1 FROM attendance_record a WHERE a.session_id = b.session_id
                            AND a.member_id = b.member_id AND a.status IN ('PRESENT', 'LATE'))
                    OR EXISTS (SELECT 1 FROM check_in c WHERE c.booking_id = b.id AND c.result = 'ALLOWED'))
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    /** Counts linked bookings before cancellation, not the credits actually restored. */
    public record PackageBookings(Long registrationId, int packageBookings) {
    }

    public record CancellationResult(int cancelledCount, List<PackageBookings> registrations) {
        public CancellationResult {
            registrations = List.copyOf(registrations);
        }
    }
}
