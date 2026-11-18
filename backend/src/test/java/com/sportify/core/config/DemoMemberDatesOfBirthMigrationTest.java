package com.sportify.core.config;

import org.junit.jupiter.api.Test;

import static com.sportify.core.config.DemoSeedSource.resource;
import static org.assertj.core.api.Assertions.assertThat;

class DemoMemberDatesOfBirthMigrationTest {
    private static final String MIGRATION = "db/demo/V67__assign_demo_member_dates_of_birth.sql";
    private static final String SQL = resource(MIGRATION);

    @Test
    void migrationTargetsOnlyTheThreeDemoMembersWithAdultDates() {
        assertThat(SQL).contains(
                "('an.gold@example.com', CAST('19950412' AS DATE))",
                "('mai.standard@example.com', CAST('19980903' AS DATE))",
                "('bao.nocard@example.com', CAST('20010115' AS DATE))");
        assertThat(SQL.lines().filter(line -> line.contains("@example.com")).toList()).hasSize(3);
        assertThat(SQL).contains("r.code = 'MEMBER'");
    }

    @Test
    void migrationPreservesEveryDateOfBirthAlreadyEnteredByAMember() {
        assertThat(SQL).contains("WHERE u.date_of_birth IS NULL");
        assertThat(SQL).doesNotContain("ELSE", "DELETE FROM", "INSERT INTO");
    }

    @Test
    void migrationIsAvailableOnlyThroughTheExplicitDemoProfile() {
        assertThat(resource("application-demo.yml")).contains("classpath:db/demo");
        assertThat(resource("application.yml")).doesNotContain("classpath:db/demo");
        assertThat(resource("application-prod.yml")).doesNotContain("classpath:db/demo");
    }
}
