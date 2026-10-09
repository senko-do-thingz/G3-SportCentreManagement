package com.sportify.catalog.repository;

import com.sportify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies V20 against a real SQL Server (Testcontainers): card statuses, the new columns and
 * the index that blocks double booking. Every test is rolled back.
 */
@Transactional
class V20SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flyway_shouldHaveAppliedV20() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version = '20'");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("success")).isEqualTo(true);
    }

    @Test
    void memberCard_shouldAcceptPendingPaymentAndReplaced() {
        long memberId = createMember("v20_card@test.com", "MEM-V20A");

        assertThatCode(() -> insertCard("CARD-V20-1", memberId, "PENDING_PAYMENT")).doesNotThrowAnyException();
        assertThatCode(() -> insertCard("CARD-V20-2", memberId, "REPLACED")).doesNotThrowAnyException();
    }

    @Test
    void memberCard_shouldRejectUnknownStatus() {
        long memberId = createMember("v20_card2@test.com", "MEM-V20B");

        assertConstraintViolation(() -> insertCard("CARD-V20-3", memberId, "LOST"), "ck_member_card_status");
    }

    @Test
    void newColumns_shouldExist() {
        Integer coach = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'sport_class' AND COLUMN_NAME = 'coach_id'", Integer.class);
        Integer cancelledBy = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'booking' AND COLUMN_NAME = 'cancelled_by_user_id'", Integer.class);

        assertThat(coach).isEqualTo(1);
        assertThat(cancelledBy).isEqualTo(1);
    }

    @Test
    void booking_shouldRejectASecondConfirmedBookingForTheSameSession() {
        long memberId = createMember("v20_booking@test.com", "MEM-V20C");
        long sessionId = createSession();
        insertBooking("BK-V20-1", sessionId, memberId, "CONFIRMED");

        assertConstraintViolation(() -> insertBooking("BK-V20-2", sessionId, memberId, "CONFIRMED"), "ux_booking_active");
    }

    @Test
    void booking_shouldAllowBookingAgainAfterCancelling() {
        long memberId = createMember("v20_booking2@test.com", "MEM-V20D");
        long sessionId = createSession();
        insertBooking("BK-V20-3", sessionId, memberId, "CANCELLED");

        assertThatCode(() -> insertBooking("BK-V20-4", sessionId, memberId, "CONFIRMED")).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void assertConstraintViolation(Runnable statement, String constraintName) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause().getMessage())
                        .contains(constraintName));
    }

    private long createMember(String email, String memberCode) {
        jdbc.update("""
                INSERT INTO user_account (email, password_hash, full_name, role_id, status)
                SELECT ?, 'not-a-real-hash', 'Test Member', r.id, 'ACTIVE' FROM role r WHERE r.code = 'MEMBER'
                """, email);
        long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO member_profile (user_id, member_code) VALUES (?, ?)", userId, memberCode);
        return userId;
    }

    private void insertCard(String code, long memberId, String status) {
        jdbc.update("""
                INSERT INTO member_card (card_code, member_id, tier_id, start_date, end_date, status, price_paid)
                SELECT ?, ?, t.id, CAST('2026-10-06' AS DATE), CAST('2027-10-05' AS DATE), ?, t.price
                FROM membership_card_tier t WHERE t.code = 'GOLD'
                """, code, memberId, status);
    }

    private long createSession() {
        jdbc.update("""
                INSERT INTO class_session (sport_id, session_date, start_time, end_time, training_type, capacity)
                SELECT s.id, CAST('2026-10-20' AS DATE), CAST('18:00' AS TIME), CAST('19:30' AS TIME), 'SELF_TRAINING', 10
                FROM sport s WHERE s.code = 'BADMINTON'
                """);
        return jdbc.queryForObject("SELECT MAX(id) FROM class_session", Long.class);
    }

    private void insertBooking(String code, long sessionId, long memberId, String status) {
        jdbc.update("INSERT INTO booking (booking_code, session_id, member_id, status) VALUES (?, ?, ?, ?)",
                code, sessionId, memberId, status);
    }
}
