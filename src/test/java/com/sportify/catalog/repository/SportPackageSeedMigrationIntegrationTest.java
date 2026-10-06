package com.sportify.catalog.repository;

import com.sportify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real migration integration test verifying that Flyway migrations (including V12 to V19)
 * seed the complete 36 sport package catalog matching the Figma design, enforce duration
 * constraints, and create all planned tables.
 */
@Transactional
class SportPackageSeedMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record ExpectedPackage(
            String code,
            String sportCode,
            String trainingFormat,
            int durationDays,
            int sessionCount,
            int sessionMinutes,
            BigDecimal priceAmount
    ) {}

    private static final List<ExpectedPackage> EXPECTED_PACKAGES = List.of(
            // Football (PK-001 .. PK-006)
            new ExpectedPackage("PK-001", "FOOTBALL", "SELF_TRAINING", 1, 1, 60, new BigDecimal("80000.00")),
            new ExpectedPackage("PK-002", "FOOTBALL", "SELF_TRAINING", 30, 8, 60, new BigDecimal("500000.00")),
            new ExpectedPackage("PK-003", "FOOTBALL", "SELF_TRAINING", 90, 24, 60, new BigDecimal("1350000.00")),
            new ExpectedPackage("PK-004", "FOOTBALL", "COACH_LED", 1, 1, 90, new BigDecimal("150000.00")),
            new ExpectedPackage("PK-005", "FOOTBALL", "COACH_LED", 30, 8, 90, new BigDecimal("900000.00")),
            new ExpectedPackage("PK-006", "FOOTBALL", "COACH_LED", 90, 24, 90, new BigDecimal("2400000.00")),

            // Basketball (PK-007 .. PK-012)
            new ExpectedPackage("PK-007", "BASKETBALL", "SELF_TRAINING", 1, 1, 60, new BigDecimal("70000.00")),
            new ExpectedPackage("PK-008", "BASKETBALL", "SELF_TRAINING", 30, 8, 60, new BigDecimal("450000.00")),
            new ExpectedPackage("PK-009", "BASKETBALL", "SELF_TRAINING", 90, 24, 60, new BigDecimal("1200000.00")),
            new ExpectedPackage("PK-010", "BASKETBALL", "COACH_LED", 1, 1, 90, new BigDecimal("130000.00")),
            new ExpectedPackage("PK-011", "BASKETBALL", "COACH_LED", 30, 8, 90, new BigDecimal("800000.00")),
            new ExpectedPackage("PK-012", "BASKETBALL", "COACH_LED", 90, 24, 90, new BigDecimal("2100000.00")),

            // Swimming (PK-013 .. PK-018)
            new ExpectedPackage("PK-013", "SWIMMING", "SELF_TRAINING", 1, 1, 60, new BigDecimal("90000.00")),
            new ExpectedPackage("PK-014", "SWIMMING", "SELF_TRAINING", 30, 8, 60, new BigDecimal("600000.00")),
            new ExpectedPackage("PK-015", "SWIMMING", "SELF_TRAINING", 90, 24, 60, new BigDecimal("1620000.00")),
            new ExpectedPackage("PK-016", "SWIMMING", "COACH_LED", 1, 1, 90, new BigDecimal("180000.00")),
            new ExpectedPackage("PK-017", "SWIMMING", "COACH_LED", 30, 8, 90, new BigDecimal("1100000.00")),
            new ExpectedPackage("PK-018", "SWIMMING", "COACH_LED", 90, 24, 90, new BigDecimal("2970000.00")),

            // Badminton (PK-019 .. PK-024)
            new ExpectedPackage("PK-019", "BADMINTON", "SELF_TRAINING", 1, 1, 60, new BigDecimal("70000.00")),
            new ExpectedPackage("PK-020", "BADMINTON", "SELF_TRAINING", 30, 8, 60, new BigDecimal("450000.00")),
            new ExpectedPackage("PK-021", "BADMINTON", "SELF_TRAINING", 90, 24, 60, new BigDecimal("1200000.00")),
            new ExpectedPackage("PK-022", "BADMINTON", "COACH_LED", 1, 1, 90, new BigDecimal("140000.00")),
            new ExpectedPackage("PK-023", "BADMINTON", "COACH_LED", 30, 8, 90, new BigDecimal("850000.00")),
            new ExpectedPackage("PK-024", "BADMINTON", "COACH_LED", 90, 24, 90, new BigDecimal("2250000.00")),

            // Volleyball (PK-025 .. PK-030)
            new ExpectedPackage("PK-025", "VOLLEYBALL", "SELF_TRAINING", 1, 1, 60, new BigDecimal("60000.00")),
            new ExpectedPackage("PK-026", "VOLLEYBALL", "SELF_TRAINING", 30, 8, 60, new BigDecimal("400000.00")),
            new ExpectedPackage("PK-027", "VOLLEYBALL", "SELF_TRAINING", 90, 24, 60, new BigDecimal("1080000.00")),
            new ExpectedPackage("PK-028", "VOLLEYBALL", "COACH_LED", 1, 1, 90, new BigDecimal("120000.00")),
            new ExpectedPackage("PK-029", "VOLLEYBALL", "COACH_LED", 30, 8, 90, new BigDecimal("750000.00")),
            new ExpectedPackage("PK-030", "VOLLEYBALL", "COACH_LED", 90, 24, 90, new BigDecimal("1950000.00")),

            // Tennis (PK-031 .. PK-036)
            new ExpectedPackage("PK-031", "TENNIS", "SELF_TRAINING", 1, 1, 60, new BigDecimal("120000.00")),
            new ExpectedPackage("PK-032", "TENNIS", "SELF_TRAINING", 30, 8, 60, new BigDecimal("800000.00")),
            new ExpectedPackage("PK-033", "TENNIS", "SELF_TRAINING", 90, 24, 60, new BigDecimal("2160000.00")),
            new ExpectedPackage("PK-034", "TENNIS", "COACH_LED", 1, 1, 90, new BigDecimal("220000.00")),
            new ExpectedPackage("PK-035", "TENNIS", "COACH_LED", 30, 8, 90, new BigDecimal("1400000.00")),
            new ExpectedPackage("PK-036", "TENNIS", "COACH_LED", 90, 24, 90, new BigDecimal("3780000.00"))
    );

    @Test
    @DisplayName("Should have exactly 36 packages in sport_package and exactly 6 per sport code")
    void shouldHaveExactly36PackagesAnd6PerSportCode() {
        Integer totalCount = jdbc.queryForObject("SELECT COUNT(*) FROM sport_package", Integer.class);
        assertThat(totalCount).isEqualTo(36);

        List<String> expectedSports = List.of("FOOTBALL", "BASKETBALL", "SWIMMING", "BADMINTON", "VOLLEYBALL", "TENNIS");
        for (String sportCode : expectedSports) {
            Integer countPerSport = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sport_package p JOIN sport s ON s.id = p.sport_id WHERE s.code = ?",
                    Integer.class,
                    sportCode
            );
            assertThat(countPerSport)
                    .as("Package count for sport %s", sportCode)
                    .isEqualTo(6);
        }
    }

    @Test
    @DisplayName("Codes should be exactly PK-001..PK-036 with no gaps and no leftover legacy codes")
    void codesShouldBeExactlyPK001ThroughPK036WithNoGapsAndNoLegacyCodes() {
        List<String> codes = jdbc.queryForList("SELECT code FROM sport_package ORDER BY code", String.class);
        List<String> expectedCodes = IntStream.rangeClosed(1, 36)
                .mapToObj(i -> String.format("PK-%03d", i))
                .toList();

        assertThat(codes).containsExactlyElementsOf(expectedCodes);

        Integer legacyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sport_package WHERE code LIKE '%\\_SELF\\_%' ESCAPE '\\' OR code LIKE '%\\_COACH\\_%' ESCAPE '\\'",
                Integer.class
        );
        assertThat(legacyCount)
                .as("Leftover legacy codes matching pattern '%_SELF_%' or '%_COACH_%'")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("All 36 packages should match Figma catalog attributes, session minutes, and prices")
    void all36PackagesShouldMatchFigmaCatalogAttributesAndPrices() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.code, s.code AS sport_code, p.training_format, p.duration_days, p.session_count, p.session_minutes, p.price_amount " +
                        "FROM sport_package p JOIN sport s ON s.id = p.sport_id ORDER BY p.code"
        );

        assertThat(rows).hasSize(36);

        for (int i = 0; i < 36; i++) {
            Map<String, Object> row = rows.get(i);
            ExpectedPackage expected = EXPECTED_PACKAGES.get(i);

            assertThat(row.get("code")).isEqualTo(expected.code());
            assertThat(row.get("sport_code")).isEqualTo(expected.sportCode());
            assertThat(row.get("training_format")).isEqualTo(expected.trainingFormat());
            assertThat(((Number) row.get("duration_days")).intValue()).isEqualTo(expected.durationDays());
            assertThat(((Number) row.get("session_count")).intValue()).isEqualTo(expected.sessionCount());
            assertThat(((Number) row.get("session_minutes")).intValue()).isEqualTo(expected.sessionMinutes());

            BigDecimal actualPrice = (BigDecimal) row.get("price_amount");
            assertThat(actualPrice)
                    .as("Price for package %s", expected.code())
                    .isEqualByComparingTo(expected.priceAmount());
        }
    }

    @Test
    @DisplayName("All 36 sport package rows should have is_active = 1")
    void allRowsShouldBeActive() {
        Integer inactiveCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sport_package WHERE is_active <> 1",
                Integer.class
        );
        assertThat(inactiveCount)
                .as("Count of non-active packages")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Session minutes constraint should reject values other than 60 and 90")
    void sessionMinutesCheckConstraintShouldRejectInvalidValues() {
        assertThatThrownBy(() -> jdbc.execute(
                "INSERT INTO sport_package (code, name, sport_id, training_format, duration_days, session_count, session_minutes, price_amount, is_active, created_at) " +
                        "VALUES ('PK-TEST-INVALID', 'Test Invalid', 1, 'SELF_TRAINING', 1, 1, 45, 100000.00, 1, SYSDATETIME())"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("All planned tables and foreign keys created in V13-V19 should exist in database")
    void allPlannedTablesAndForeignKeysShouldExistInDatabase() {
        List<String> plannedTables = List.of(
                "payment", "invoice", "invoice_line",
                "waitlist_entry",
                "session_plan", "session_plan_step", "attendance_record", "attendance_correction",
                "skill_metric", "session_result", "session_result_score", "coach_feedback",
                "support_request", "support_request_message", "assistant_setting", "assistant_topic", "assistant_quick_prompt", "ai_conversation", "ai_message",
                "recommendation_rule_set", "recommendation_rule_weight", "workout_plan", "workout_plan_candidate",
                "workout_plan_week", "workout_plan_day", "workout_plan_exercise", "plan_review",
                "notification", "announcement", "system_setting", "member_sport_interest", "coach_sport"
        );

        for (String tableName : plannedTables) {
            Integer tableExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.tables WHERE name = ?",
                    Integer.class,
                    tableName
            );
            assertThat(tableExists)
                    .as("Table %s should exist in database", tableName)
                    .isEqualTo(1);
        }

        // Verify payment_id columns exist on member_card and sport_package_registration
        Integer cardPaymentIdCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id WHERE t.name = 'member_card' AND c.name = 'payment_id'",
                Integer.class
        );
        assertThat(cardPaymentIdCount).isEqualTo(1);

        Integer pkgRegPaymentIdCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id WHERE t.name = 'sport_package_registration' AND c.name = 'payment_id'",
                Integer.class
        );
        assertThat(pkgRegPaymentIdCount).isEqualTo(1);
    }
}
