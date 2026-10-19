package com.sportify.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.AuthenticationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.sportify.core.config.DemoClassesSeedSource.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Full demo-profile migration, actual HTTP discovery and transactional replay on an isolated CI catalog. */
@ActiveProfiles(value = {"dev", "demo", "test"}, inheritProfiles = false)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {"spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.format_sql=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoClassesIntegrationTest extends AbstractIntegrationTest {
    private static final String DATABASE = "be15_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean databaseCreated;
    @DynamicPropertySource
    static synchronized void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        if (!databaseCreated) {
            try (var connection = DriverManager.getConnection(MSSQL_SERVER_CONTAINER.getJdbcUrl(),
                    MSSQL_SERVER_CONTAINER.getUsername(), MSSQL_SERVER_CONTAINER.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE [" + DATABASE + "]");
            }
            databaseCreated = true;
        }
        registry.add("spring.datasource.hikari.catalog", () -> DATABASE);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthenticationService authentication;
    @Autowired UserDetailsService users;

    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    long id(String table, String key, String value) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE " + key + " = ?", Long.class, value);
    }
    LocalDate anchor() {
        return jdbc.queryForObject("SELECT CAST(created_at AS DATE) FROM sport_class WHERE code = 'DEMO-CL-BAD-BEG'", LocalDate.class);
    }
    long session(String code, int week, String format) {
        int offset = CLASSES.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow().offset();
        return jdbc.queryForObject("SELECT s.id FROM class_session s JOIN sport_class c ON c.id = s.class_id WHERE c.code = ? AND s.session_date = ? AND s.training_type = ?",
                Long.class, code, anchor().plusDays(offset + week * 7L), format);
    }
    CustomUserDetails actor(String email) { return (CustomUserDetails) users.loadUserByUsername(email); }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return request.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
    }
    JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }
    JsonNode search(Map<String, String> filters) throws Exception {
        var request = get("/api/v1/sessions").with(user(actor("bao.nocard@example.com"))).param("size", "100");
        filters.forEach(request::param);
        return call(request, 200);
    }
    void rollback(Runnable action) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> { status.setRollbackOnly(); action.run(); });
    }
    void replay() { SqlServerScriptReplay.execute(jdbc, SQL); }
    void failure(int error, Runnable action) {
        String before = snapshot(jdbc);
        assertThatThrownBy(() -> rollback(action)).isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                        sql -> assertThat(sql.getErrorCode()).isEqualTo(error)));
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    @Test
    void freshFlywayAppliesV29AfterV28InItsOwnCatalog() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo(DATABASE);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class))
                .doesNotHaveDuplicates().containsSubsequence("27", "28", "29", "30");
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '29' AND script = 'V29__seed_demo_classes_and_sessions.sql' AND success = 1")).isEqualTo(1);
        assertShape();
    }

    @Test
    void classesCoverThreeSportsLevelsAndQualifiedBe07Coaches() {
        assertThat(count("SELECT COUNT(DISTINCT sport_id) FROM sport_class WHERE code LIKE 'DEMO-CL-%'")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT DISTINCT level FROM sport_class WHERE code LIKE 'DEMO-CL-%'", String.class))
                .containsExactlyInAnyOrder("BEGINNER", "INTERMEDIATE", "ADVANCED");
        assertThat(count("""
                SELECT COUNT(*) FROM sport_class c JOIN user_account u ON u.id = c.coach_id
                JOIN role r ON r.id = u.role_id JOIN coach_sport cs ON cs.coach_id = c.coach_id AND cs.sport_id = c.sport_id
                WHERE c.code LIKE 'DEMO-CL-%' AND r.code = 'COACH' AND u.status = 'ACTIVE'
                """)).isEqualTo(6);
        for (var spec : CLASSES) {
            assertThat(jdbc.queryForObject("SELECT u.email FROM sport_class c JOIN user_account u ON u.id = c.coach_id WHERE c.code = ?", String.class, spec.code()))
                    .isEqualTo(spec.coach());
        }
    }

    @Test
    void allSessionsArePublishedFutureValidAndSpanAllFourWeeks() {
        var dates = jdbc.queryForList("SELECT session_date FROM class_session WHERE class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%')", LocalDate.class);
        assertThat(dates).hasSize(48).allMatch(d -> d.isAfter(anchor()) && !d.isAfter(anchor().plusDays(28)));
        assertThat(dates.stream().map(d -> (java.time.temporal.ChronoUnit.DAYS.between(anchor(), d) - 1) / 7).distinct())
                .containsExactlyInAnyOrder(0L, 1L, 2L, 3L);
        assertThat(count("SELECT COUNT(*) FROM class_session s JOIN sport_class c ON c.id = s.class_id WHERE c.code LIKE 'DEMO-CL-%' AND (s.session_date < CAST(GETDATE() AS DATE) OR (s.session_date = CAST(GETDATE() AS DATE) AND s.start_time <= CAST(GETDATE() AS TIME)))")).isZero();
        assertThat(count("SELECT COUNT(*) FROM class_session s JOIN sport_class c ON c.id = s.class_id WHERE c.code LIKE 'DEMO-CL-%' AND (s.status <> 'PUBLISHED' OR s.start_time >= s.end_time OR s.capacity < 1 OR s.capacity > c.max_members)")).isZero();
        assertThat(count("SELECT COUNT(DISTINCT facility_id) FROM class_session s JOIN sport_class c ON c.id = s.class_id WHERE c.code LIKE 'DEMO-CL-%'")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM class_session s JOIN facility f ON f.id = s.facility_id JOIN sport_class c ON c.id = s.class_id WHERE c.code LIKE 'DEMO-CL-%' AND (f.is_active <> 1 OR f.sport_id <> s.sport_id)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM class_session WHERE training_type = 'COACH_LED' AND coach_id IS NOT NULL AND class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%')")).isEqualTo(24);
        assertThat(count("SELECT COUNT(*) FROM class_session WHERE training_type = 'SELF_TRAINING' AND coach_id IS NULL AND class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%')")).isEqualTo(24);
    }

    @ParameterizedTest
    @ValueSource(strings = {"coach_id", "facility_id"})
    void noSharedResourceOverlapExistsWithinSeedOrAgainstOtherBlockingSessions(String resource) {
        assertThat(count("SELECT COUNT(*) FROM class_session a JOIN class_session b ON a.id < b.id AND a.session_date = b.session_date "
                + "AND a.start_time < b.end_time AND b.start_time < a.end_time AND a." + resource + " = b." + resource
                + " WHERE a.status IN ('DRAFT','PUBLISHED') AND b.status IN ('DRAFT','PUBLISHED') "
                + "AND (a.class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%') "
                + "OR b.class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%'))")).isZero();
    }

    @Test
    void fullNearlyFullAndFreeSessionsHaveRealBookingsAndConsistentCredits() {
        long full = session("DEMO-CL-BAD-BEG", 0, "COACH_LED");
        long near = session("DEMO-CL-BAD-INT", 0, "COACH_LED");
        assertThat(count("SELECT capacity - booked_count FROM class_session WHERE id = ?", full)).isZero();
        assertThat(count("SELECT capacity - booked_count FROM class_session WHERE id = ?", near)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CONFIRMED'", full)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CONFIRMED'", near)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM class_session WHERE class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%') AND booked_count = 0")).isEqualTo(46);
        assertThat(jdbc.queryForList("SELECT remaining_sessions FROM sport_package_registration WHERE registration_code IN ('DEMO-REG-BAD-AN','DEMO-REG-BAD-MAI','DEMO-REG-BAD-BAO') ORDER BY registration_code", Integer.class))
                .containsExactly(6, 7, 6);
        assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE registration_code = 'DEMO-REG-SELF-BAO'")).isEqualTo(8);
        assertIntegrity();
    }

    @Test
    void complimentaryEntitlementsCreateNoFakePaymentsReceiptsOrAudits() {
        assertThat(count("SELECT COUNT(*) FROM sport_package WHERE code LIKE 'DEMO-PKG-%' AND price_amount = 0 AND session_count = 8 AND duration_days = 30")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM sport_package_registration WHERE registration_code LIKE 'DEMO-REG-%' AND paid_amount = 0 AND original_price = 0 AND payment_id IS NULL AND channel = 'RECEPTION' AND status = 'ACTIVE'")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM payment WHERE member_id IN (SELECT id FROM user_account WHERE email IN ('an.gold@example.com','mai.standard@example.com','bao.nocard@example.com'))")).isZero();
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE member_id IN (SELECT id FROM user_account WHERE email IN ('an.gold@example.com','mai.standard@example.com','bao.nocard@example.com'))")).isZero();
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE entity_code LIKE 'DEMO-%'")).isZero();
    }

    @Test
    void repeatFlywayStartupAndStandaloneSqlReplayDoNotChangeRowsOrCredits() {
        String before = snapshot(jdbc);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        replay(); replay();
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertShape(); assertIntegrity();
    }

    @Test
    void seedReplaysSerializeWithoutDuplicateRowsOrDoubleConsumption() throws Exception {
        String before = snapshot(jdbc);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(this::replay); var two = pool.submit(this::replay);
            one.get(30, TimeUnit.SECONDS); two.get(30, TimeUnit.SECONDS);
        }
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"class", "session", "class-coach", "facility", "package", "registration", "booking-debited", "booking-refunded"})
    void reconstructablePartialStatesRepairOnlyMissingFixtures(String missing) {
        rollback(() -> {
            switch (missing) {
                case "class" -> {
                    jdbc.update("DELETE FROM class_session WHERE class_id = (SELECT id FROM sport_class WHERE code = 'DEMO-CL-BB-ADV')");
                    jdbc.update("DELETE FROM sport_class WHERE code = 'DEMO-CL-BB-ADV'");
                }
                case "session" -> jdbc.update("DELETE FROM class_session WHERE id = ?", session("DEMO-CL-SWIM-ADV", 3, "SELF_TRAINING"));
                case "class-coach" -> jdbc.update("UPDATE sport_class SET coach_id = NULL WHERE code = 'DEMO-CL-BB-ADV'");
                case "facility" -> {
                    jdbc.update("DELETE FROM booking WHERE booking_code LIKE 'DEMO-BK-%'");
                    jdbc.update("UPDATE sport_package_registration SET remaining_sessions = total_sessions WHERE registration_code LIKE 'DEMO-REG-%'");
                    jdbc.update("DELETE FROM class_session WHERE facility_id = (SELECT id FROM facility WHERE code = 'DEMO-BADMINTON')");
                    jdbc.update("DELETE FROM facility WHERE code = 'DEMO-BADMINTON'");
                }
                case "package" -> {
                    jdbc.update("DELETE FROM sport_package_registration WHERE registration_code = 'DEMO-REG-SELF-BAO'");
                    jdbc.update("DELETE FROM sport_package WHERE code = 'DEMO-PKG-BAD-SELF'");
                }
                case "registration" -> jdbc.update("DELETE FROM sport_package_registration WHERE registration_code = 'DEMO-REG-SELF-BAO'");
                case "booking-debited", "booking-refunded" -> {
                    jdbc.update("DELETE FROM booking WHERE booking_code = 'DEMO-BK-FULL-AN'");
                    if (missing.equals("booking-refunded")) {
                        jdbc.update("UPDATE class_session SET booked_count = booked_count - 1 WHERE id = ?", session("DEMO-CL-BAD-BEG", 0, "COACH_LED"));
                        jdbc.update("UPDATE sport_package_registration SET remaining_sessions = remaining_sessions + 1 WHERE registration_code = 'DEMO-REG-BAD-AN'");
                    }
                }
                default -> throw new AssertionError(missing);
            }
            replay(); assertShape(); assertIntegrity();
            assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE registration_code = 'DEMO-REG-BAD-AN'")).isEqualTo(6);
            String repaired = snapshot(jdbc);
            replay(); assertThat(snapshot(jdbc)).isEqualTo(repaired);
        });
        assertShape(); assertIntegrity();
    }

    @ParameterizedTest
    @ValueSource(strings = {"class-name", "class-coach", "inactive-class", "session-capacity", "session-status", "duplicate-session",
            "package-price", "registration-owner", "credit-corruption", "counter-corruption", "coach-link", "facility-inactive", "booking-owner"})
    void conflictingOrAmbiguousStatesFailAtomically(String conflict) {
        int expected = switch (conflict) {
            case "coach-link", "facility-inactive" -> 51501;
            case "credit-corruption", "counter-corruption", "booking-owner" -> 51504;
            default -> 51502;
        };
        failure(expected, () -> {
            switch (conflict) {
                case "class-name" -> jdbc.update("UPDATE sport_class SET name = 'Unrelated class' WHERE code = 'DEMO-CL-BB-ADV'");
                case "class-coach" -> jdbc.update("UPDATE sport_class SET coach_id = ? WHERE code = 'DEMO-CL-BB-ADV'", id("user_account", "email", "quan.coach@example.com"));
                case "inactive-class" -> jdbc.update("UPDATE sport_class SET is_active = 0 WHERE code = 'DEMO-CL-BB-ADV'");
                case "session-capacity" -> jdbc.update("UPDATE class_session SET capacity = 9 WHERE id = ?", session("DEMO-CL-SWIM-ADV", 3, "SELF_TRAINING"));
                case "session-status" -> jdbc.update("UPDATE class_session SET status = 'CANCELLED' WHERE id = ?", session("DEMO-CL-SWIM-ADV", 3, "SELF_TRAINING"));
                case "duplicate-session" -> jdbc.update("INSERT INTO class_session(class_id,sport_id,session_date,start_time,end_time,facility_id,coach_id,training_type,capacity,booked_count,status) SELECT class_id,sport_id,session_date,start_time,end_time,facility_id,coach_id,training_type,capacity,booked_count,status FROM class_session WHERE id = ?", session("DEMO-CL-SWIM-ADV", 3, "SELF_TRAINING"));
                case "package-price" -> jdbc.update("UPDATE sport_package SET price_amount = 1 WHERE code = 'DEMO-PKG-BAD-SELF'");
                case "registration-owner" -> jdbc.update("UPDATE sport_package_registration SET member_id = ? WHERE registration_code = 'DEMO-REG-SELF-BAO'", id("user_account", "email", "an.gold@example.com"));
                case "credit-corruption" -> jdbc.update("UPDATE sport_package_registration SET remaining_sessions = remaining_sessions - 1 WHERE registration_code = 'DEMO-REG-BAD-AN'");
                case "counter-corruption" -> jdbc.update("UPDATE class_session SET booked_count = booked_count + 1 WHERE id = ?", session("DEMO-CL-SWIM-ADV", 3, "SELF_TRAINING"));
                case "coach-link" -> jdbc.update("DELETE FROM coach_sport WHERE coach_id = ?", id("user_account", "email", "quan.coach@example.com"));
                case "facility-inactive" -> jdbc.update("UPDATE facility SET is_active = 0 WHERE code = 'BB_COURT_A'");
                case "booking-owner" -> jdbc.update("UPDATE booking SET member_id = ? WHERE booking_code = 'DEMO-BK-NEAR-AN'", id("user_account", "email", "bao.nocard@example.com"));
                default -> throw new AssertionError(conflict);
            }
            replay();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"coach-DRAFT", "coach-PUBLISHED", "facility-DRAFT", "facility-PUBLISHED"})
    void existingBlockingSessionsRejectWholeSeedRepair(String scenario) {
        failure(51503, () -> {
            boolean coachConflict = scenario.startsWith("coach-");
            long target = coachConflict ? session("DEMO-CL-BB-ADV", 3, "COACH_LED") : session("DEMO-CL-SWIM-ADV", 3, "SELF_TRAINING");
            jdbc.update("""
                    INSERT INTO class_session(sport_id,session_date,start_time,end_time,facility_id,coach_id,training_type,capacity,status)
                    SELECT sport_id,session_date,start_time,end_time,?,coach_id,training_type,capacity,? FROM class_session WHERE id = ?
                    """, coachConflict ? id("facility", "code", "BB_COURT_A") : id("facility", "code", "INDOOR_POOL"),
                    scenario.substring(scenario.indexOf('-') + 1), target);
            jdbc.update("DELETE FROM class_session WHERE id = ?", target);
            replay();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED", "ADJACENT"})
    void nonBlockingOrAdjacentExistingSessionsRemainUntouched(String scenario) {
        rollback(() -> {
            long target = session("DEMO-CL-BAD-BEG", 0, "COACH_LED");
            if (scenario.equals("ADJACENT")) {
                jdbc.update("INSERT INTO class_session(sport_id,session_date,start_time,end_time,facility_id,training_type,capacity,status) SELECT sport_id,session_date,'10:30','11:00',facility_id,'SELF_TRAINING',3,'PUBLISHED' FROM class_session WHERE id = ?", target);
            } else {
                jdbc.update("INSERT INTO class_session(sport_id,session_date,start_time,end_time,facility_id,coach_id,training_type,capacity,status) SELECT sport_id,session_date,start_time,end_time,facility_id,coach_id,training_type,capacity,? FROM class_session WHERE id = ?", scenario, target);
            }
            String before = snapshot(jdbc); replay();
            assertThat(snapshot(jdbc)).isEqualTo(before);
        });
    }

    @Test
    void missingPastSessionIsRejectedRatherThanMovingScheduleForward() {
        failure(51505, () -> {
            // Move the complete empty fixture backward while preserving the shared persisted anchor.
            jdbc.update("DELETE FROM booking WHERE booking_code LIKE 'DEMO-BK-%'");
            jdbc.update("DELETE FROM class_session WHERE class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%')");
            jdbc.update("UPDATE sport_class SET created_at = DATEADD(DAY,-40,created_at) WHERE code LIKE 'DEMO-CL-%'");
            jdbc.update("UPDATE sport_package_registration SET start_date = DATEADD(DAY,-40,start_date), end_date = DATEADD(DAY,-40,end_date), remaining_sessions = 8 WHERE registration_code LIKE 'DEMO-REG-%'");
            replay();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"package-conflict", "late-credit-write"})
    void standaloneLateFailureRollsBackAllEarlierClassSessionAndFacilityInserts(String mode) throws Exception {
        String catalog = "be15_rollback_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(MSSQL_SERVER_CONTAINER.getJdbcUrl(),
                MSSQL_SERVER_CONTAINER.getUsername(), MSSQL_SERVER_CONTAINER.getPassword())) {
            try (var statement = connection.createStatement()) { statement.execute("CREATE DATABASE [" + catalog + "]"); }
            connection.setCatalog(catalog);
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).locations("classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/demo")
                    .target("28").outOfOrder(false).cleanDisabled(true).load().migrate();
            var isolated = new JdbcTemplate(source);
            if (mode.equals("package-conflict")) {
                isolated.update("INSERT INTO sport_package(code,name,sport_id,training_format,duration_days,session_count,session_minutes,price_amount,is_active) SELECT 'DEMO-PKG-BAD-COACH','Conflicting package',id,'COACH_LED',30,8,90,1,1 FROM sport WHERE code = 'BADMINTON'");
            } else {
                SqlServerScriptReplay.execute(isolated, """
                        CREATE TRIGGER be15_test_late_failure ON sport_package_registration AFTER UPDATE AS
                        BEGIN
                            IF EXISTS (SELECT 1 FROM inserted WHERE registration_code LIKE 'DEMO-REG-%')
                                THROW 51506, 'BE-15 test-only failure after booking insertion.', 1;
                        END;
                        """);
            }
            String before = snapshot(isolated);
            assertThatThrownBy(() -> SqlServerScriptReplay.execute(isolated, SQL)).isInstanceOf(DataAccessException.class)
                    .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                            sql -> assertThat(sql.getErrorCode()).isEqualTo(mode.equals("package-conflict") ? 51502 : 51506)));
            assertThat(snapshot(isolated)).isEqualTo(before);
            assertThat(isolated.queryForObject("SELECT @@TRANCOUNT", Integer.class)).isZero();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM sport_class WHERE code LIKE 'DEMO-CL-%'", Integer.class)).isZero();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM facility WHERE code = 'DEMO-BADMINTON'", Integer.class)).isZero();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM booking WHERE booking_code LIKE 'DEMO-BK-%'", Integer.class)).isZero();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM sport_package_registration WHERE registration_code LIKE 'DEMO-REG-%'", Integer.class)).isZero();
        }
    }

    @Test
    void ordinaryMigrationLocationsActuallyApplyNoDemoRows() throws Exception {
        String catalog = "be15_ordinary_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(MSSQL_SERVER_CONTAINER.getJdbcUrl(),
                MSSQL_SERVER_CONTAINER.getUsername(), MSSQL_SERVER_CONTAINER.getPassword())) {
            try (var statement = connection.createStatement()) { statement.execute("CREATE DATABASE [" + catalog + "]"); }
            connection.setCatalog(catalog);
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).locations("classpath:db/migration/identity", "classpath:db/migration/catalog")
                    .outOfOrder(false).cleanDisabled(true).load().migrate();
            var isolated = new JdbcTemplate(source);
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('28','29')", Integer.class)).isZero();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM sport_class WHERE code LIKE 'DEMO-CL-%'", Integer.class)).isZero();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM user_account WHERE email = 'quan.coach@example.com'", Integer.class)).isZero();
        }
    }

    @Test
    void realDemoLoginDefaultSearchAndAllSevenBe07AccountsRemainUsable() throws Exception {
        for (var account : DemoSeedSource.ACCOUNTS) {
            var tokens = authentication.authenticate(new AuthRequest(account.email(), DemoSeedSource.PASSWORD));
            assertTrue(tokens.getAccessToken() != null && !tokens.getAccessToken().isBlank(), "Demo authentication failed");
            if (account.email().equals("bao.nocard@example.com")) {
                var defaults = call(get("/api/v1/sessions").header("Authorization", "Bearer " + tokens.getAccessToken()), 200);
                assertThat(defaults.path("totalElements").asInt()).isEqualTo(48);
                assertThat(defaults.path("content").size()).isEqualTo(20);
                assertThat(defaults.path("page").asInt()).isZero();
                var response = call(get("/api/v1/sessions").header("Authorization", "Bearer " + tokens.getAccessToken()).param("size", "100"), 200);
                assertThat(response.path("totalElements").asInt()).isEqualTo(48);
                assertThat(response.path("content").size()).isEqualTo(48);
            }
        }
        assertThat(count("SELECT COUNT(*) FROM member_card WHERE card_code IN ('DEMO-CARD-GOLD','DEMO-CARD-STANDARD')")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM member_card WHERE member_id = ?", id("user_account", "email", "bao.nocard@example.com"))).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BADMINTON", "SWIMMING", "BASKETBALL"})
    void sportFilterReturnsExactlyItsSixteenSessions(String sport) throws Exception {
        long sportId = id("sport", "code", sport);
        var result = search(Map.of("sportId", String.valueOf(sportId)));
        assertThat(result.path("totalElements").asInt()).isEqualTo(16);
        result.path("content").forEach(row -> assertThat(row.path("sportId").asLong()).isEqualTo(sportId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
    void levelFilterReturnsExactlyItsSixteenSessions(String level) throws Exception {
        var result = search(Map.of("level", level));
        assertThat(result.path("totalElements").asInt()).isEqualTo(16);
        result.path("content").forEach(row -> assertThat(row.path("level").asText()).isEqualTo(level));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH_LED", "SELF_TRAINING"})
    void formatFilterReturns24SessionsAndSelfTrainingHasNullCoach(String format) throws Exception {
        var result = search(Map.of("format", format));
        assertThat(result.path("totalElements").asInt()).isEqualTo(24);
        result.path("content").forEach(row -> {
            assertThat(row.path("trainingFormat").asText()).isEqualTo(format);
            if (format.equals("SELF_TRAINING")) {
                assertThat(row.path("coachId").isNull()).isTrue();
                assertThat(row.path("coachName").isNull()).isTrue();
            }
        });
    }

    @Test
    void coachDateAndCombinedFiltersUseActualSeedReferences() throws Exception {
        String coach = String.valueOf(id("user_account", "email", "quan.coach@example.com"));
        assertThat(search(Map.of("coachId", coach)).path("totalElements").asInt()).isEqualTo(8);
        assertThat(search(Map.of("from", anchor().plusDays(1).toString(), "to", anchor().plusDays(7).toString())).path("totalElements").asInt()).isEqualTo(12);
        var combined = search(Map.of("sportId", String.valueOf(id("sport", "code", "BADMINTON")), "level", "BEGINNER",
                "format", "COACH_LED", "coachId", coach, "from", anchor().plusDays(2).toString(), "to", anchor().plusDays(9).toString()));
        assertThat(combined.path("totalElements").asInt()).isEqualTo(2);
        assertThat(combined.path("content").get(0).path("sessionDate").asText()).isEqualTo(anchor().plusDays(2).toString());
        assertThat(combined.path("content").get(1).path("sessionDate").asText()).isEqualTo(anchor().plusDays(9).toString());
    }

    @Test
    void paginationTraverses48DistinctChronologicallyOrderedSessions() throws Exception {
        var seen = new HashSet<Long>();
        String previous = "";
        for (int page = 0; page < 10; page++) {
            var response = call(get("/api/v1/sessions").with(user(actor("bao.nocard@example.com")))
                    .param("page", String.valueOf(page)).param("size", "5"), 200);
            assertThat(response.path("totalElements").asInt()).isEqualTo(48);
            assertThat(response.path("totalPages").asInt()).isEqualTo(10);
            assertThat(response.path("content").size()).isEqualTo(page == 9 ? 3 : 5);
            for (var row : response.path("content")) {
                assertThat(seen.add(row.path("id").asLong())).isTrue();
                String dateTime = row.path("sessionDate").asText() + "T" + row.path("startTime").asText();
                assertThat(dateTime.compareTo(previous)).isGreaterThanOrEqualTo(0);
                previous = dateTime;
            }
        }
        assertThat(seen).hasSize(48);
    }

    @Test
    void apiReportsFullNearlyFullAndExistingMemberEligibilityCorrectly() throws Exception {
        var full = call(get("/api/v1/sessions/" + session("DEMO-CL-BAD-BEG", 0, "COACH_LED")).with(user(actor("bao.nocard@example.com"))), 200);
        assertThat(full.path("session").path("full").asBoolean()).isTrue();
        assertThat(full.path("session").path("freeSeats").asInt()).isZero();
        assertThat(full.path("reason").asText()).isEqualTo("ALREADY_BOOKED");
        var near = call(get("/api/v1/sessions/" + session("DEMO-CL-BAD-INT", 0, "COACH_LED")).with(user(actor("bao.nocard@example.com"))), 200);
        assertThat(near.path("session").path("full").asBoolean()).isFalse();
        assertThat(near.path("session").path("freeSeats").asInt()).isEqualTo(1);
        assertThat(near.path("session").path("bookedCount").asInt()).isEqualTo(2);
        assertThat(near.path("eligible").asBoolean()).isTrue();
        assertThat(near.path("suitableRegistrations").size()).isEqualTo(1);
    }

    @Test
    void realBookingAndCancellationConsumeAndRestoreExactlyOnceWithoutSeedReset() {
        rollback(() -> {
            try {
                long near = session("DEMO-CL-BAD-INT", 0, "COACH_LED");
                long registration = id("sport_package_registration", "registration_code", "DEMO-REG-BAD-BAO");
                var result = call(body(post("/api/v1/bookings").with(user(actor("bao.nocard@example.com"))),
                        Map.of("sessionId", near, "packageRegistrationId", registration)), 201);
                long booking = result.path("id").asLong();
                assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration)).isEqualTo(6);
                assertThat(count("SELECT booked_count FROM class_session WHERE id = ?", near)).isEqualTo(3);
                String beforeReplay = snapshot(jdbc); replay(); assertThat(snapshot(jdbc)).isEqualTo(beforeReplay);
                call(delete("/api/v1/bookings/" + booking).with(user(actor("bao.nocard@example.com"))), 200);
                assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration)).isEqualTo(7);
                assertThat(count("SELECT booked_count FROM class_session WHERE id = ?", near)).isEqualTo(2);
                call(delete("/api/v1/bookings/" + booking).with(user(actor("bao.nocard@example.com"))), 400);
                assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration)).isEqualTo(7);
                assertIntegrity();
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    @Test
    void selfTrainingBookingUsesMatchingFreePackageAndAnonymousIsDenied() throws Exception {
        call(get("/api/v1/sessions"), 401);
        rollback(() -> {
            try {
                long session = session("DEMO-CL-BAD-BEG", 0, "SELF_TRAINING");
                long registration = id("sport_package_registration", "registration_code", "DEMO-REG-SELF-BAO");
                call(body(post("/api/v1/bookings").with(user(actor("bao.nocard@example.com"))),
                        Map.of("sessionId", session, "packageRegistrationId", registration)), 201);
                assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration)).isEqualTo(7);
                assertThat(count("SELECT booked_count FROM class_session WHERE id = ?", session)).isEqualTo(1);
                assertIntegrity();
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH_LED", "SELF_TRAINING"})
    void be10ManagerCannotDoubleBookASeededCoachOrFacility(String format) throws Exception {
        String before = snapshot(jdbc);
        var spec = CLASSES.get(5);
        Map<String, Object> payload = new HashMap<>(Map.of("date", anchor().plusDays(spec.offset()),
                "startTime", format.equals("COACH_LED") ? spec.coachStart() : spec.selfStart(),
                "endTime", format.equals("COACH_LED") ? spec.coachEnd() : spec.selfEnd(),
                "facilityId", id("facility", "code", spec.facility()), "capacity", 3, "trainingType", format));
        if (format.equals("COACH_LED")) payload.put("coachId", id("user_account", "email", spec.coach()));
        var response = call(body(post("/api/v1/manager/classes/" + id("sport_class", "code", spec.code()) + "/sessions")
                .with(user(actor("manager@sportify.com"))), payload), 409);
        assertThat(response.path("details").path("conflicts").size()).isGreaterThan(0);
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    @Test
    void legitimateAdditionalManagerSessionSurvivesSeedReplay() {
        rollback(() -> {
            try {
                var spec = CLASSES.get(5);
                call(body(post("/api/v1/manager/classes/" + id("sport_class", "code", spec.code()) + "/sessions")
                        .with(user(actor("manager@sportify.com"))), Map.of("date", anchor().plusDays(spec.offset()),
                        "startTime", "14:00:00", "endTime", "15:30:00", "facilityId", id("facility", "code", spec.facility()),
                        "coachId", id("user_account", "email", spec.coach()), "capacity", 3, "trainingType", "COACH_LED")), 201);
                assertThat(count("SELECT COUNT(*) FROM class_session WHERE class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%')")).isEqualTo(49);
                String before = snapshot(jdbc); replay(); assertThat(snapshot(jdbc)).isEqualTo(before);
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    @Test
    void cancellingAnOriginalFixtureBookingIsPreservedRatherThanRefilled() {
        rollback(() -> {
            try {
                long booking = id("booking", "booking_code", "DEMO-BK-FULL-AN");
                call(delete("/api/v1/bookings/" + booking).with(user(actor("an.gold@example.com"))), 200);
                assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE registration_code = 'DEMO-REG-BAD-AN'")).isEqualTo(7);
                assertThat(count("SELECT booked_count FROM class_session WHERE id = ?", session("DEMO-CL-BAD-BEG", 0, "COACH_LED"))).isEqualTo(2);
                String before = snapshot(jdbc); replay(); assertThat(snapshot(jdbc)).isEqualTo(before);
                assertIntegrity();
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    private void assertShape() {
        assertThat(count("SELECT COUNT(*) FROM sport_class WHERE code LIKE 'DEMO-CL-%'")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM class_session WHERE class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%')")).isEqualTo(48);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE booking_code LIKE 'DEMO-BK-%'")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM sport_package WHERE code LIKE 'DEMO-PKG-%'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM sport_package_registration WHERE registration_code LIKE 'DEMO-REG-%'")).isEqualTo(4);
    }

    private void assertIntegrity() {
        assertThat(count("SELECT COUNT(*) FROM class_session s CROSS APPLY (SELECT COUNT(*) n FROM booking b WHERE b.session_id = s.id AND b.status = 'CONFIRMED') used WHERE s.class_id IN (SELECT id FROM sport_class WHERE code LIKE 'DEMO-CL-%') AND (s.booked_count <> used.n OR s.booked_count < 0 OR s.booked_count > s.capacity)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sport_package_registration r CROSS APPLY (SELECT COUNT(*) n FROM booking b WHERE b.package_registration_id = r.id AND b.status = 'CONFIRMED') used WHERE r.registration_code LIKE 'DEMO-REG-%' AND (r.remaining_sessions + used.n <> r.total_sessions OR r.remaining_sessions < 0 OR r.remaining_sessions > r.total_sessions)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM booking b JOIN sport_package_registration r ON r.id = b.package_registration_id JOIN sport_package p ON p.id = r.package_id JOIN class_session s ON s.id = b.session_id WHERE b.booking_code LIKE 'DEMO-BK-%' AND (b.member_id <> r.member_id OR p.sport_id <> s.sport_id OR p.training_format <> s.training_type OR s.session_date < r.start_date OR s.session_date > r.end_date)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sys.foreign_keys WHERE is_disabled = 1 OR is_not_trusted = 1")).isZero();
    }

    private String snapshot(JdbcTemplate database) {
        StringBuilder data = new StringBuilder();
        for (String table : List.of("user_account", "member_profile", "coach_profile", "coach_sport", "member_card",
                "facility", "sport_class", "class_session", "sport_package", "sport_package_registration", "booking", "payment", "invoice", "invoice_line", "activity_log")) {
            String order = switch (table) { case "member_profile", "coach_profile" -> "user_id"; case "coach_sport" -> "coach_id,sport_id"; default -> "id"; };
            data.append(table).append(database.queryForList("SELECT * FROM [" + table + "] ORDER BY " + order));
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
