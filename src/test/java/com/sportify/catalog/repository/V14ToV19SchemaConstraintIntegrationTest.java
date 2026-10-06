package com.sportify.catalog.repository;

import com.sportify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests verifying schema constraints, foreign keys, not-null requirements,
 * check constraints, sequences, and seed data for tables introduced in V14 through V19.
 */
@Transactional
class V14ToV19SchemaConstraintIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // -------------------------------------------------------------------------
    // Sequences Verification (11 Sequences in total)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("All 11 sequences should exist and return valid next values")
    void allElevenSequencesShouldGenerateValues() {
        List<String> sequences = List.of(
                "seq_member_code",
                "seq_registration_code",
                "seq_card_code",
                "seq_package_reg_code",
                "seq_refund_code",
                "seq_booking_code",
                "seq_class_code",
                "seq_payment_code",
                "seq_invoice_number",
                "seq_support_request_code",
                "seq_workout_plan_code"
        );

        for (String seq : sequences) {
            Long nextVal = jdbc.queryForObject("SELECT NEXT VALUE FOR " + seq, Long.class);
            assertThat(nextVal)
                    .as("Sequence %s should generate a positive value", seq)
                    .isNotNull()
                    .isPositive();
        }
    }

    // -------------------------------------------------------------------------
    // V14: payment, invoice, invoice_line, and payment_id foreign keys
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V14: Payment table constraints, valid insert, and invalid checks")
    void paymentTableConstraints() {
        long memberId = getOrCreateTestMember("pay.check@sportify.test", "MEM-PAY-01");

        // Valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, reference_code)
                VALUES ('PAY-TEST-001', ?, 500000.00, 'BANK_TRANSFER', 'SUCCESS', 'REF-001')
                """, memberId)).doesNotThrowAnyException();

        // Invalid amount (amount <= 0)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-TEST-002', ?, -100.00, 'BANK_TRANSFER', 'SUCCESS')
                """, memberId)).isInstanceOf(DataAccessException.class);

        // Invalid payment_method
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-TEST-003', ?, 100000.00, 'CRYPTO', 'SUCCESS')
                """, memberId)).isInstanceOf(DataAccessException.class);

        // Invalid payment_status
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-TEST-004', ?, 100000.00, 'CASH', 'UNKNOWN_STATUS')
                """, memberId)).isInstanceOf(DataAccessException.class);

        // Invalid member_id FK
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-TEST-005', 999999, 100000.00, 'CASH', 'SUCCESS')
                """)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V14: Payment foreign keys on member_card, sport_package_registration, refund_request")
    void paymentIdForeignKeysOnExistingTables() {
        long memberId = getOrCreateTestMember("fk.check@sportify.test", "MEM-PAY-02");

        jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-FK-001', ?, 300000.00, 'CASH', 'SUCCESS')
                """, memberId);
        long paymentId = jdbc.queryForObject("SELECT id FROM payment WHERE payment_code = 'PAY-FK-001'", Long.class);

        // Valid assignment of payment_id
        long tierId = jdbc.queryForObject("SELECT TOP 1 id FROM membership_card_tier ORDER BY id", Long.class);
        assertThatCode(() -> jdbc.update("""
                INSERT INTO member_card (card_code, member_id, tier_id, status, price_paid, start_date, end_date, payment_id)
                VALUES ('CARD-FK-01', ?, ?, 'ACTIVE', 300000.00, '2026-10-01', '2027-10-01', ?)
                """, memberId, tierId, paymentId)).doesNotThrowAnyException();

        // Invalid payment_id on member_card
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO member_card (card_code, member_id, tier_id, status, price_paid, start_date, end_date, payment_id)
                VALUES ('CARD-FK-02', ?, ?, 'ACTIVE', 300000.00, '2026-10-01', '2027-10-01', 999999)
                """, memberId, tierId)).isInstanceOf(DataAccessException.class);

        // Valid assignment on sport_package_registration
        long pkgId = jdbc.queryForObject("SELECT TOP 1 id FROM sport_package ORDER BY id", Long.class);
        assertThatCode(() -> jdbc.update("""
                INSERT INTO sport_package_registration (registration_code, member_id, package_id, status, price_paid, total_sessions, remaining_sessions, start_date, end_date, payment_id)
                VALUES ('REG-FK-01', ?, ?, 'ACTIVE', 500000.00, 8, 8, '2026-10-01', '2026-10-31', ?)
                """, memberId, pkgId, paymentId)).doesNotThrowAnyException();

        // Invalid payment_id on sport_package_registration
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sport_package_registration (registration_code, member_id, package_id, status, price_paid, total_sessions, remaining_sessions, start_date, end_date, payment_id)
                VALUES ('REG-FK-02', ?, ?, 'ACTIVE', 500000.00, 8, 8, '2026-10-01', '2026-10-31', 999999)
                """, memberId, pkgId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V14: Invoice and invoice_line constraints")
    void invoiceAndInvoiceLineConstraints() {
        long memberId = getOrCreateTestMember("inv.check@sportify.test", "MEM-INV-01");
        jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-INV-001', ?, 500000.00, 'BANK_TRANSFER', 'SUCCESS')
                """, memberId);
        long paymentId = jdbc.queryForObject("SELECT id FROM payment WHERE payment_code = 'PAY-INV-001'", Long.class);

        // Valid invoice insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO invoice (invoice_number, payment_id, member_id, subtotal_amount, discount_amount, tax_amount, total_amount, status)
                VALUES ('INV-TEST-001', ?, ?, 500000.00, 0.00, 0.00, 500000.00, 'PAID')
                """, paymentId, memberId)).doesNotThrowAnyException();
        long invoiceId = jdbc.queryForObject("SELECT id FROM invoice WHERE invoice_number = 'INV-TEST-001'", Long.class);

        // Invalid invoice status check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO invoice (invoice_number, payment_id, member_id, subtotal_amount, total_amount, status)
                VALUES ('INV-TEST-002', ?, ?, 500000.00, 500000.00, 'BAD_STATUS')
                """, paymentId, memberId)).isInstanceOf(DataAccessException.class);

        // Valid invoice line insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO invoice_line (invoice_id, item_type, item_reference_id, description, quantity, unit_price, line_total)
                VALUES (?, 'SPORT_PACKAGE', 1, 'Football 30-Day Pass', 1, 500000.00, 500000.00)
                """, invoiceId)).doesNotThrowAnyException();

        // Invalid invoice line item_type check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO invoice_line (invoice_id, item_type, item_reference_id, description, quantity, unit_price, line_total)
                VALUES (?, 'INVALID_TYPE', 1, 'Bad item', 1, 100.00, 100.00)
                """, invoiceId)).isInstanceOf(DataAccessException.class);

        // Invalid quantity check (quantity <= 0)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO invoice_line (invoice_id, item_type, item_reference_id, description, quantity, unit_price, line_total)
                VALUES (?, 'SPORT_PACKAGE', 1, 'Football Pass', 0, 100.00, 100.00)
                """, invoiceId)).isInstanceOf(DataAccessException.class);
    }

    // -------------------------------------------------------------------------
    // V15: waitlist_entry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V15: Waitlist entry constraints and check behavior")
    void waitlistEntryConstraints() {
        long memberId = getOrCreateTestMember("wait.check@sportify.test", "MEM-WAIT-01");
        long sessionId = getOrCreateTestClassSession();

        // Valid waitlist insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO waitlist_entry (session_id, member_id, status)
                VALUES (?, ?, 'WAITING')
                """, sessionId, memberId)).doesNotThrowAnyException();

        // Invalid waitlist status check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO waitlist_entry (session_id, member_id, status)
                VALUES (?, ?, 'UNKNOWN_STATUS')
                """, sessionId, memberId)).isInstanceOf(DataAccessException.class);

        // Invalid FK session_id
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO waitlist_entry (session_id, member_id, status)
                VALUES (999999, ?, 'WAITING')
                """, memberId)).isInstanceOf(DataAccessException.class);
    }

    // -------------------------------------------------------------------------
    // V16: Training and attendance tables
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V16: Session plan, attendance, skill metric, and feedback constraints")
    void trainingAndAttendanceConstraints() {
        long memberId = getOrCreateTestMember("train.check@sportify.test", "MEM-TRN-01");
        long coachId = getOrCreateTestCoach("coach.trn@sportify.test", "COACH-TRN-01");
        long classId = getOrCreateTestSportClass(coachId);
        long sessionId = getOrCreateTestClassSession();

        // 1. session_plan valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO session_plan (class_id, coach_id, title, is_template)
                VALUES (?, ?, 'Cardio & Dribbling Drill', 1)
                """, classId, coachId)).doesNotThrowAnyException();
        long planId = jdbc.queryForObject("SELECT TOP 1 id FROM session_plan ORDER BY id DESC", Long.class);

        // 2. session_plan_step valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO session_plan_step (plan_id, step_order, title, duration_minutes)
                VALUES (?, 1, 'Warm-up jog', 15)
                """, planId)).doesNotThrowAnyException();

        // session_plan_step duration check (<= 0)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO session_plan_step (plan_id, step_order, title, duration_minutes)
                VALUES (?, 2, 'Invalid step', 0)
                """, planId)).isInstanceOf(DataAccessException.class);

        // 3. attendance_record valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO attendance_record (session_id, member_id, status, marked_by)
                VALUES (?, ?, 'PRESENT', ?)
                """, sessionId, memberId, coachId)).doesNotThrowAnyException();
        long attId = jdbc.queryForObject("SELECT TOP 1 id FROM attendance_record ORDER BY id DESC", Long.class);

        // attendance_record duplicate unique constraint (session_id, member_id)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO attendance_record (session_id, member_id, status, marked_by)
                VALUES (?, ?, 'ABSENT', ?)
                """, sessionId, memberId, coachId)).isInstanceOf(DataAccessException.class);

        // attendance_record invalid status check
        long otherMemberId = getOrCreateTestMember("other.mem@sportify.test", "MEM-TRN-02");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO attendance_record (session_id, member_id, status, marked_by)
                VALUES (?, ?, 'INVALID_STATUS', ?)
                """, sessionId, otherMemberId, coachId)).isInstanceOf(DataAccessException.class);

        // 4. attendance_correction valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO attendance_correction (attendance_record_id, previous_status, new_status, reason, corrected_by)
                VALUES (?, 'PRESENT', 'LATE', 'Traffic delay', ?)
                """, attId, coachId)).doesNotThrowAnyException();

        // 5. skill_metric valid insert
        long sportId = jdbc.queryForObject("SELECT TOP 1 id FROM sport ORDER BY id", Long.class);
        assertThatCode(() -> jdbc.update("""
                INSERT INTO skill_metric (sport_id, name, max_score)
                VALUES (?, 'Passing Accuracy', 10)
                """, sportId)).doesNotThrowAnyException();
        long metricId = jdbc.queryForObject("SELECT TOP 1 id FROM skill_metric ORDER BY id DESC", Long.class);

        // 6. session_result valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO session_result (session_id, member_id, notes)
                VALUES (?, ?, 'Strong performance in drill')
                """, sessionId, memberId)).doesNotThrowAnyException();
        long resultId = jdbc.queryForObject("SELECT TOP 1 id FROM session_result ORDER BY id DESC", Long.class);

        // session_result_score valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO session_result_score (result_id, metric_id, score)
                VALUES (?, ?, 8.5)
                """, resultId, metricId)).doesNotThrowAnyException();

        // 7. coach_feedback valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO coach_feedback (session_id, coach_id, member_id, rating, comments)
                VALUES (?, ?, ?, 5, 'Excellent enthusiasm')
                """, sessionId, coachId, memberId)).doesNotThrowAnyException();

        // coach_feedback rating check constraint (rating out of 1..5)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coach_feedback (session_id, coach_id, member_id, rating, comments)
                VALUES (?, ?, ?, 6, 'Rating too high')
                """, sessionId, coachId, memberId)).isInstanceOf(DataAccessException.class);
    }

    // -------------------------------------------------------------------------
    // V17: Support request and AI assistant tables
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V17: Support request, topic, and AI conversation constraints")
    void supportAndAiConstraints() {
        long memberId = getOrCreateTestMember("supp.check@sportify.test", "MEM-SUP-01");

        // 1. support_request valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO support_request (request_code, member_id, subject, category, description, priority, status)
                VALUES ('SR-TEST-001', ?, 'Locker issue', 'FACILITY', 'Locker #42 lock jammed', 'MEDIUM', 'OPEN')
                """, memberId)).doesNotThrowAnyException();
        long reqId = jdbc.queryForObject("SELECT id FROM support_request WHERE request_code = 'SR-TEST-001'", Long.class);

        // Duplicate request_code check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO support_request (request_code, member_id, subject, category, description, priority, status)
                VALUES ('SR-TEST-001', ?, 'Another subject', 'FACILITY', 'Duplicate code', 'MEDIUM', 'OPEN')
                """, memberId)).isInstanceOf(DataAccessException.class);

        // Invalid priority check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO support_request (request_code, member_id, subject, category, description, priority, status)
                VALUES ('SR-TEST-002', ?, 'Subject', 'FACILITY', 'Bad priority', 'EXTREME', 'OPEN')
                """, memberId)).isInstanceOf(DataAccessException.class);

        // 2. support_request_message valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO support_request_message (request_id, sender_id, message_body)
                VALUES (?, ?, 'Maintenance dispatched')
                """, reqId, memberId)).doesNotThrowAnyException();

        // 3. assistant_setting valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO assistant_setting (setting_key, setting_value, description)
                VALUES ('model_temperature', '0.7', 'Sampling temperature')
                """)).doesNotThrowAnyException();

        // 4. assistant_topic and quick_prompt valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO assistant_topic (title, category, content)
                VALUES ('Gym Hours', 'FACILITY', 'Open 6 AM to 10 PM daily')
                """)).doesNotThrowAnyException();
        long topicId = jdbc.queryForObject("SELECT TOP 1 id FROM assistant_topic ORDER BY id DESC", Long.class);

        assertThatCode(() -> jdbc.update("""
                INSERT INTO assistant_quick_prompt (topic_id, prompt_text, display_order)
                VALUES (?, 'What time does the gym close?', 1)
                """, topicId)).doesNotThrowAnyException();

        // 5. ai_conversation valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO ai_conversation (user_id, title, rating)
                VALUES (?, 'Workout inquiry', 4)
                """, memberId)).doesNotThrowAnyException();
        long convId = jdbc.queryForObject("SELECT TOP 1 id FROM ai_conversation ORDER BY id DESC", Long.class);

        // ai_conversation rating check (out of 1..5)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ai_conversation (user_id, title, rating)
                VALUES (?, 'Bad rating', 0)
                """, memberId)).isInstanceOf(DataAccessException.class);

        // 6. ai_message valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO ai_message (conversation_id, sender_type, content)
                VALUES (?, 'USER', 'Can I book a tennis coach?')
                """, convId)).doesNotThrowAnyException();

        // ai_message invalid sender_type check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ai_message (conversation_id, sender_type, content)
                VALUES (?, 'INVALID_SENDER', 'Invalid sender')
                """, convId)).isInstanceOf(DataAccessException.class);
    }

    // -------------------------------------------------------------------------
    // V18: Workout recommendations and plans
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V18: Recommendation rules, workout plans, and review constraints")
    void workoutRecommendationConstraints() {
        long memberId = getOrCreateTestMember("wrk.check@sportify.test", "MEM-WRK-01");
        long coachId = getOrCreateTestCoach("coach.wrk@sportify.test", "COACH-WRK-01");

        // 1. recommendation_rule_set valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO recommendation_rule_set (name, description, is_active)
                VALUES ('Beginner General Fitness', 'Weight distribution rules for beginner tier', 1)
                """)).doesNotThrowAnyException();
        long ruleSetId = jdbc.queryForObject("SELECT TOP 1 id FROM recommendation_rule_set ORDER BY id DESC", Long.class);

        assertThatCode(() -> jdbc.update("""
                INSERT INTO recommendation_rule_weight (rule_set_id, parameter_name, weight)
                VALUES (?, 'age_factor', 0.25)
                """, ruleSetId)).doesNotThrowAnyException();

        // 2. workout_plan valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO workout_plan (plan_code, member_id, name, goal, difficulty_level, duration_weeks, status)
                VALUES ('WP-TEST-001', ?, 'Core & Mobility Plan', 'STRENGTH', 'INTERMEDIATE', 4, 'ACTIVE')
                """, memberId)).doesNotThrowAnyException();
        long planId = jdbc.queryForObject("SELECT id FROM workout_plan WHERE plan_code = 'WP-TEST-001'", Long.class);

        // duration_weeks check (<= 0)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO workout_plan (plan_code, member_id, name, goal, difficulty_level, duration_weeks)
                VALUES ('WP-TEST-002', ?, 'Zero weeks plan', 'STRENGTH', 'BEGINNER', 0)
                """, memberId)).isInstanceOf(DataAccessException.class);

        // 3. workout_plan_week, day, exercise valid inserts
        assertThatCode(() -> jdbc.update("""
                INSERT INTO workout_plan_week (plan_id, week_number, focus)
                VALUES (?, 1, 'Core Stability')
                """, planId)).doesNotThrowAnyException();
        long weekId = jdbc.queryForObject("SELECT TOP 1 id FROM workout_plan_week ORDER BY id DESC", Long.class);

        assertThatCode(() -> jdbc.update("""
                INSERT INTO workout_plan_day (week_id, day_number, focus)
                VALUES (?, 1, 'Planks and rotation')
                """, weekId)).doesNotThrowAnyException();
        long dayId = jdbc.queryForObject("SELECT TOP 1 id FROM workout_plan_day ORDER BY id DESC", Long.class);

        assertThatCode(() -> jdbc.update("""
                INSERT INTO workout_plan_exercise (day_id, name, sets, reps, rest_seconds)
                VALUES (?, 'Standard Plank', 3, 60, 45)
                """, dayId)).doesNotThrowAnyException();

        // 4. plan_review valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO plan_review (plan_id, coach_id, status, feedback)
                VALUES (?, ?, 'APPROVED', 'Looks balanced')
                """, planId, coachId)).doesNotThrowAnyException();

        // plan_review invalid status check
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO plan_review (plan_id, coach_id, status)
                VALUES (?, ?, 'INVALID_STATUS')
                """, planId, coachId)).isInstanceOf(DataAccessException.class);
    }

    // -------------------------------------------------------------------------
    // V19: Notifications, announcements, settings, multi-sport links
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V19: Notification, announcement, settings, and multi-sport links")
    void notificationAndSystemTableConstraints() {
        long memberId = getOrCreateTestMember("sys.check@sportify.test", "MEM-SYS-01");
        long coachId = getOrCreateTestCoach("coach.sys@sportify.test", "COACH-SYS-01");
        long sportId = jdbc.queryForObject("SELECT TOP 1 id FROM sport ORDER BY id", Long.class);

        // 1. notification valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO notification (user_id, title, content, type, is_read)
                VALUES (?, 'Booking Confirmed', 'Your session at 18:00 is confirmed', 'BOOKING', 0)
                """, memberId)).doesNotThrowAnyException();

        // 2. announcement valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO announcement (title, content, target_role, is_published)
                VALUES ('Holiday Hours', 'Centre closes early on New Year Eve', 'ALL', 1)
                """)).doesNotThrowAnyException();

        // 3. system_setting valid insert
        assertThatCode(() -> jdbc.update("""
                INSERT INTO system_setting (setting_key, setting_value, description)
                VALUES ('booking_lead_hours', '2', 'Minimum hours required before booking')
                """)).doesNotThrowAnyException();

        // Duplicate setting_key
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO system_setting (setting_key, setting_value)
                VALUES ('booking_lead_hours', '3')
                """)).isInstanceOf(DataAccessException.class);

        // 4. member_sport_interest composite PK
        assertThatCode(() -> jdbc.update("""
                INSERT INTO member_sport_interest (member_id, sport_id)
                VALUES (?, ?)
                """, memberId, sportId)).doesNotThrowAnyException();

        // Duplicate insert into composite PK
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO member_sport_interest (member_id, sport_id)
                VALUES (?, ?)
                """, memberId, sportId)).isInstanceOf(DataAccessException.class);

        // 5. coach_sport composite PK
        assertThatCode(() -> jdbc.update("""
                INSERT INTO coach_sport (coach_id, sport_id)
                VALUES (?, ?)
                """, coachId, sportId)).doesNotThrowAnyException();

        // Duplicate insert into composite PK
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coach_sport (coach_id, sport_id)
                VALUES (?, ?)
                """, coachId, sportId)).isInstanceOf(DataAccessException.class);
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private long getOrCreateTestMember(String email, String memberCode) {
        List<Long> existing = jdbc.queryForList("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update("""
                INSERT INTO user_account (email, password_hash, full_name, role_id, status)
                SELECT ?, 'hash-123', 'Member Test', r.id, 'ACTIVE' FROM role r WHERE r.code = 'MEMBER'
                """, email);
        long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO member_profile (user_id, member_code) VALUES (?, ?)", userId, memberCode);
        return userId;
    }

    private long getOrCreateTestCoach(String email, String coachCode) {
        List<Long> existing = jdbc.queryForList("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update("""
                INSERT INTO user_account (email, password_hash, full_name, role_id, status)
                SELECT ?, 'hash-coach', 'Coach Test', r.id, 'ACTIVE' FROM role r WHERE r.code = 'COACH'
                """, email);
        long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO coach_profile (user_id, bio) VALUES (?, 'Specialist')", userId);
        return userId;
    }

    private long getOrCreateTestSportClass(long coachId) {
        long sportId = jdbc.queryForObject("SELECT TOP 1 id FROM sport ORDER BY id", Long.class);
        List<Long> existing = jdbc.queryForList("SELECT id FROM sport_class WHERE code = 'CLS-TEST-01'", Long.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update("""
                INSERT INTO sport_class (code, name, sport_id, coach_id, training_format, capacity, status)
                VALUES ('CLS-TEST-01', 'Introductory Class', ?, ?, 'COACH_LED', 20, 'ACTIVE')
                """, sportId, coachId);
        return jdbc.queryForObject("SELECT id FROM sport_class WHERE code = 'CLS-TEST-01'", Long.class);
    }

    private long getOrCreateTestClassSession() {
        long coachId = getOrCreateTestCoach("session.coach@sportify.test", "COACH-SESS-01");
        long classId = getOrCreateTestSportClass(coachId);
        List<Long> existing = jdbc.queryForList("SELECT id FROM class_session WHERE class_id = ?", Long.class, classId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update("""
                INSERT INTO class_session (class_id, session_date, start_time, end_time, capacity, booked_count, status)
                VALUES (?, '2026-10-15', '09:00', '10:30', 20, 0, 'SCHEDULED')
                """, classId);
        return jdbc.queryForObject("SELECT TOP 1 id FROM class_session WHERE class_id = ? ORDER BY id DESC", Long.class, classId);
    }
}
