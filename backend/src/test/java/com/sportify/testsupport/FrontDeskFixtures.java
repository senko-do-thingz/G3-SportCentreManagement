package com.sportify.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Isolated SQL fixtures shared by the front desk feature suites. */
public final class FrontDeskFixtures {
    // Use secure randomness for fixture values persisted as account identifiers.
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private int serial;
    public final String marker = "be05" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    public final LocalDate today;
    public final String phone;
    public final long memberId;

    public FrontDeskFixtures(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        today = LocalDate.now(clock);
        phone = randomPhone();
        memberId = account(marker + "@example.test", phone, marker + " Alice Smith");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                memberId, marker, today);
    }

    public String randomPhone() {
        return Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L));
    }

    public long account(String email, String accountPhone, String name) {
        return jdbc.queryForObject("""
                INSERT INTO user_account(email, phone, full_name, password_hash, role_id, status)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, 'integration-test-hash', (SELECT id FROM role WHERE code = 'MEMBER'), 'ACTIVE')
                """, Long.class, email, accountPhone, name);
    }

    public long card(String tier, String status, LocalDate start, LocalDate end, LocalDateTime requestedAt) {
        return jdbc.queryForObject("""
                INSERT INTO member_card(card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at)
                OUTPUT INSERTED.id VALUES (?, ?, (SELECT id FROM membership_card_tier WHERE code = ?), ?, ?, ?, 250000, ?)
                """, Long.class, marker + ++serial, memberId, tier, start, end, status, requestedAt);
    }

    public long registration(String status, LocalDateTime requestedAt) {
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel,
                    original_price, discount_percentage, paid_amount, total_sessions, remaining_sessions,
                    start_date, end_date, status, created_by_user_id, created_at)
                OUTPUT INSERTED.id VALUES (?, ?, (SELECT TOP (1) id FROM sport_package ORDER BY id),
                    'RECEPTION', 100000, 0, 100000, 8, 8, ?, ?, ?, ?, ?)
                """, Long.class, marker + ++serial, memberId, today.minusDays(1), today.plusDays(30),
                status, memberId, requestedAt);
    }

    public void deleteCreatedMembers() {
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (SELECT id FROM user_account WHERE email LIKE ?)", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
    }
}
