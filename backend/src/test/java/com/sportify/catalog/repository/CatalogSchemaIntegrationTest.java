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
 * Verifies V5 (schema) and V6 (seed) against a real SQL Server (Testcontainers).
 * Every test runs in a transaction that is rolled back, so inserted rows never leak into other tests.
 */
@Transactional
class CatalogSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------
    // Flyway and seed
    // ---------------------------------------------------------------------

    @Test
    void flyway_shouldHaveAppliedCatalogMigrationsV5AndV6() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IN ('5', '6') ORDER BY installed_rank");

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(r -> r.get("version")).containsExactly("5", "6");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("success")).isEqualTo(true));
    }

    @Test
    void seed_shouldContainSixSports() {
        List<String> codes = jdbc.queryForList("SELECT code FROM sport ORDER BY id", String.class);

        assertThat(codes).containsExactly("FOOTBALL", "BADMINTON", "BASKETBALL", "VOLLEYBALL", "SWIMMING", "TENNIS");
    }

    @Test
    void seed_shouldContainActivePlansWithPoolsAndFeatures() {
        List<String> activeCodes = jdbc.queryForList(
                "SELECT code FROM membership_plan WHERE status = 'ACTIVE'", String.class);
        assertThat(activeCodes).containsExactlyInAnyOrder(
                "STARTER", "MULTI_SPORT", "ALL_ACCESS", "SWIM_STARTER", "BASKETBALL_PASS");

        assertThat(poolSize("STARTER")).isEqualTo(6);
        assertThat(poolSize("MULTI_SPORT")).isEqualTo(6);
        assertThat(poolSize("ALL_ACCESS")).isEqualTo(6);
        assertThat(poolSize("SWIM_STARTER")).isEqualTo(1);
        assertThat(poolSize("BASKETBALL_PASS")).isEqualTo(1);

        String swimSport = jdbc.queryForObject("""
                SELECT s.code FROM plan_eligible_sport pes
                JOIN membership_plan p ON p.id = pes.plan_id
                JOIN sport s ON s.id = pes.sport_id
                WHERE p.code = 'SWIM_STARTER'
                """, String.class);
        assertThat(swimSport).isEqualTo("SWIMMING");

        Integer featureCount = jdbc.queryForObject("SELECT COUNT(*) FROM plan_feature", Integer.class);
        assertThat(featureCount).isEqualTo(10);
    }

    @Test
    void seed_shouldContainAgeGroupsAndFacilities() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM age_group", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT code FROM facility", String.class))
                .containsExactlyInAnyOrder("BB_COURT_A", "BB_COURT_B", "INDOOR_POOL");
    }

    private int poolSize(String planCode) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM plan_eligible_sport pes
                JOIN membership_plan p ON p.id = pes.plan_id
                WHERE p.code = ?
                """, Integer.class, planCode);
    }

    // ---------------------------------------------------------------------
    // Membership constraints
    // ---------------------------------------------------------------------

    @Test
    void uxMembershipOnePending_shouldRejectSecondPendingRegistrationForSameMember() {
        long memberId = createMember("pending.check@sportify.test", "MEM-T001");
        long planId = planId("STARTER");

        insertMembership("REG-T001", memberId, planId, "PENDING_PAYMENT", null, null);

        assertThatThrownBy(() -> insertMembership("REG-T002", memberId, planId, "PENDING_PAYMENT", null, null))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause().getMessage())
                        .contains("ux_membership_one_pending"));
    }

    @Test
    void uxMembershipOnePending_shouldAllowPendingNextToNonPendingRows() {
        long memberId = createMember("pending.ok@sportify.test", "MEM-T002");
        long planId = planId("STARTER");

        insertMembership("REG-T010", memberId, planId, "CANCELLED", null, null);
        insertMembership("REG-T011", memberId, planId, "ACTIVE", "2026-10-01", "2026-10-31");

        assertThatCode(() -> insertMembership("REG-T012", memberId, planId, "PENDING_PAYMENT", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void ckMembershipActiveDates_shouldRejectActiveMembershipWithoutDates() {
        long memberId = createMember("dates.check@sportify.test", "MEM-T003");

        assertThatThrownBy(() -> insertMembership("REG-T020", memberId, planId("STARTER"), "ACTIVE", null, null))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause().getMessage())
                        .contains("ck_membership_active_dates"));
    }

    @Test
    void ckMembershipDates_shouldRejectEndDateBeforeStartDate() {
        long memberId = createMember("range.check@sportify.test", "MEM-T004");

        assertThatThrownBy(() -> insertMembership("REG-T030", memberId, planId("STARTER"), "ACTIVE", "2026-10-31", "2026-10-01"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause().getMessage())
                        .contains("ck_membership_dates"));
    }

    // ---------------------------------------------------------------------
    // Plan and lookup constraints
    // ---------------------------------------------------------------------

    @Test
    void ckMembershipPlanPrice_shouldRejectNegativePrice() {
        assertConstraintViolation(() -> insertPlan("NEG_PRICE", "-1", 30, 1, "DRAFT"), "ck_membership_plan_price");
    }

    @Test
    void ckMembershipPlanDuration_shouldRejectZeroDuration() {
        assertConstraintViolation(() -> insertPlan("ZERO_DAYS", "1", 0, 1, "DRAFT"), "ck_membership_plan_duration");
    }

    @Test
    void ckMembershipPlanMaxSports_shouldRejectZeroMaxSports() {
        assertConstraintViolation(() -> insertPlan("ZERO_SPORTS", "1", 30, 0, "DRAFT"), "ck_membership_plan_max_sports");
    }

    @Test
    void ckMembershipPlanStatus_shouldRejectUnknownStatus() {
        assertConstraintViolation(() -> insertPlan("BAD_STATUS", "1", 30, 1, "PUBLISHED"), "ck_membership_plan_status");
    }

    @Test
    void uqMembershipPlanCode_shouldRejectDuplicateCode() {
        assertConstraintViolation(() -> insertPlan("STARTER", "1", 30, 1, "DRAFT"), "uq_membership_plan_code");
    }

    @Test
    void ckAgeGroupMaxAge_shouldRejectMaxBelowMin() {
        assertConstraintViolation(() -> jdbc.update(
                "INSERT INTO age_group (code, label, min_age, max_age) VALUES ('BAD_RANGE', 'Bad', 10, 5)"),
                "ck_age_group_max_age");
    }

    @Test
    void ckFacilityCapacity_shouldRejectNonPositiveCapacity() {
        assertConstraintViolation(() -> jdbc.update(
                "INSERT INTO facility (code, name, facility_type, capacity) VALUES ('BAD_CAP', 'Bad', 'HALL', 0)"),
                "ck_facility_capacity");
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

    private long planId(String code) {
        return jdbc.queryForObject("SELECT id FROM membership_plan WHERE code = ?", Long.class, code);
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

    private void insertMembership(String code, long memberId, long planId, String status, String startDate, String endDate) {
        jdbc.update("""
                INSERT INTO membership (registration_code, member_id, plan_id, registration_type, channel, status,
                                        price_amount, duration_days, start_date, end_date, created_by_user_id)
                VALUES (?, ?, ?, 'NEW', 'ONLINE', ?, 300000.00, 30, CAST(? AS DATE), CAST(? AS DATE), ?)
                """, code, memberId, planId, status, startDate, endDate, memberId);
    }

    private void insertPlan(String code, String price, int durationDays, int maxSports, String status) {
        jdbc.update("""
                INSERT INTO membership_plan (code, name, price, duration_days, max_sports, status)
                VALUES (?, 'Test plan', CAST(? AS DECIMAL(14,2)), ?, ?, ?)
                """, code, price, durationDays, maxSports, status);
    }
}
