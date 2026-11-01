package com.sportify.catalog.repository;

import com.sportify.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Executes unmodified Flyway migrations on SQL Server; skipped explicitly when Docker is absent. */
@Transactional
class V37TrainingSchemaIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    private long coach;
    private long member;
    private long other;
    private long classId;

    @BeforeEach void fixtures() {
        coach = account("COACH");
        member = account("MEMBER");
        other = account("MEMBER");
        String code = "V37-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO sport_class(code, name, sport_id, coach_id) SELECT ?, 'V37 Class', MIN(id), ? FROM sport", code, coach);
        classId = jdbc.queryForObject("SELECT id FROM sport_class WHERE code = ?", Long.class, code);
    }

    @Test void flywayAppliedV37AndRetainsLegacyUnassignedPlans() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '37' AND success = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'member_class_enrollment'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'session_plan' AND COLUMN_NAME = 'booking_id'", Integer.class)).isEqualTo(1);
        long legacy = plan(null);
        assertThat(jdbc.queryForObject("SELECT member_id FROM session_plan WHERE id = ?", Long.class, legacy)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE name = 'ix_coach_feedback_session'", Integer.class)).isEqualTo(1);
    }

    @Test void personalFeedbackAllowsNoSessionAndRejectsAnotherMembersPlan() {
        long id = plan(member);
        jdbc.update("INSERT INTO coach_feedback(plan_id, member_id, coach_id, rating, comments) VALUES (?, ?, ?, 4, 'Immediate feedback')", id, member, coach);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coach_feedback WHERE plan_id = ? AND session_id IS NULL", Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO coach_feedback(plan_id, member_id, coach_id, rating, comments) VALUES (?, ?, ?, 4, 'Wrong owner')", id, other, coach))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void feedbackRequiresPlanOrLegacySessionTarget() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO coach_feedback(member_id, coach_id, rating, comments) VALUES (?, ?, 4, 'No target')", member, coach))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long account(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String email = "v37-" + suffix + "@sportify.test";
        jdbc.update("INSERT INTO user_account(email, password_hash, full_name, role_id, status) SELECT ?, 'fixture', 'V37 User', id, 'ACTIVE' FROM role WHERE code = ?", email, role);
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        if (role.equals("COACH")) jdbc.update("INSERT INTO coach_profile(user_id) VALUES (?)", id);
        else jdbc.update("INSERT INTO member_profile(user_id, member_code) VALUES (?, ?)", id, "M-V37-" + suffix);
        return id;
    }

    private long plan(Long owner) {
        String title = "V37 " + UUID.randomUUID();
        jdbc.update("INSERT INTO session_plan(class_id, coach_id, member_id, title) VALUES (?, ?, ?, ?)", classId, coach, owner, title);
        return jdbc.queryForObject("SELECT id FROM session_plan WHERE title = ?", Long.class, title);
    }
}

