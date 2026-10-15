package com.sportify.core.config;

import com.sportify.core.common.CodeFormatter;
import com.sportify.core.validation.StrongPasswordValidator;
import com.sportify.identity.security.ApplicationConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sportify.core.config.DemoSeedSource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAccountsMigrationTest {
    @Test
    void documentedPasswordSatisfiesRealValidator() {
        assertTrue(new StrongPasswordValidator().isValid(PASSWORD, null), "Demo password must satisfy validation");
    }

    @Test
    void actualConfiguredEncoderMatchesMigrationHash() {
        var encoder = new ApplicationConfig(null).passwordEncoder();
        assertTrue(encoder.matches(PASSWORD, HASH), "Demo migration hash must match the documented credential");
        assertTrue(!encoder.matches("invalid-demo-password", HASH), "Wrong demo password must fail");
        assertTrue(!PASSWORD.equals(HASH), "SQL must store a hash");
        assertThat(HASH).matches("\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}");
        assertTrue(!SQL.contains(PASSWORD), "Plaintext credential must not be embedded in SQL");
    }

    @Test
    void specificationHasExactlySevenDistinctIdentitiesAndExpectedRoles() {
        assertThat(ACCOUNTS).hasSize(7);
        assertThat(ACCOUNTS).extracting(Account::email).doesNotHaveDuplicates()
                .allMatch(email -> email.endsWith("@example.com"));
        assertThat(ACCOUNTS).extracting(Account::ordinal).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(ACCOUNTS.stream().collect(Collectors.groupingBy(Account::role, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of("RECEPTIONIST", 1L, "COACH", 3L, "MEMBER", 3L));
        assertThat(ACCOUNTS).allMatch(account -> account.name().matches("[A-Za-z ]+"));
    }

    @Test
    void coachSportsAreDistinctAndExistInTheActualCatalogSeed() {
        var sports = ACCOUNTS.stream().filter(a -> a.role().equals("COACH")).map(Account::sport).toList();
        assertThat(sports).containsExactly("BADMINTON", "SWIMMING", "BASKETBALL");
        String catalogSeed = resource("db/migration/catalog/V6__seed_catalog_data.sql");
        sports.forEach(sport -> assertThat(catalogSeed).contains("VALUES ('" + sport + "'"));
    }

    @Test
    void membersCoverGoldPhysicalStandardAndNoCard() {
        var members = ACCOUNTS.stream().filter(a -> a.role().equals("MEMBER")).toList();
        assertThat(members).extracting(Account::tier).containsExactly("GOLD", "STANDARD", null);
        assertThat(SQL).contains("'DEMO-CARD-GOLD', 'GOLD', @GoldEndDate, 300000.00",
                "'DEMO-CARD-STANDARD', 'STANDARD', NULL, 0.00", "c.payment_id IS NOT NULL",
                "WHERE expected.email IS NULL OR c.card_code <> expected.card_code");
        assertThat(SQL).doesNotContain("INSERT INTO payment", "UPDATE user_account", "DELETE FROM");
    }

    @Test
    void memberCodesUseTheExistingSequenceAndFormatterWithoutReseeding() {
        assertThat(SQL).contains("NEXT VALUE FOR seq_member_code", "LEN(@Digits) < 4",
                "RIGHT('0000' + @Digits, 4)", "WHERE user_id = @MemberId");
        assertThat(SQL).doesNotContain("ALTER SEQUENCE", "RESTART WITH");
        var formatter = new CodeFormatter();
        var codes = List.of(1L, 2L, 3L, 9999L, 10000L).stream().map(formatter::formatMemberCode).toList();
        assertThat(codes).containsExactly("MEM-0001", "MEM-0002", "MEM-0003", "MEM-9999", "MEM-10000");
    }

    @Test
    void fixedGoldTermIsTwelveMonthsWithInclusiveEnd() {
        var start = LocalDate.parse(capture(SQL, "DECLARE @StartDate DATE = '(\\d+)'") , DateTimeFormatter.BASIC_ISO_DATE);
        var end = LocalDate.parse(capture(SQL, "DECLARE @GoldEndDate DATE = '(\\d+)'") , DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(end).isEqualTo(start.plusMonths(12).minusDays(1));
        assertThat(README).contains(start.toString(), end.toString());
    }

    @Test
    void everyReadmeTableRowMatchesTheActualMigrationAndCommonPassword() {
        for (Account account : ACCOUNTS) {
            var rows = README.lines().filter(line -> line.startsWith("| " + account.name() + " | ")).toList();
            assertThat(rows).hasSize(1);
            var columns = rows.getFirst().split("\\|", -1);
            assertThat(columns[2].trim()).isEqualTo(account.email());
            assertThat(columns[3].trim()).isEqualTo(account.role());
            assertTrue(columns[4].trim().equals(PASSWORD), "README demo credentials must be consistent");
            if (account.sport() != null) {
                assertThat(columns[5]).contains(account.sport());
            }
        }
        assertThat(README).contains("## Demo Accounts", "SPRING_PROFILES_ACTIVE", "dev,demo", "isolated");
    }

    @Test
    void migrationVersionsAreUniqueAcrossAllResourceLocations() throws Exception {
        var versions = new HashSet<String>();
        try (var paths = Files.walk(ROOT.resolve("backend/src/main/resources/db"))) {
            var migrations = paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches("V.+__.+\\.sql"))
                    .toList();
            assertThat(migrations).anyMatch(path -> path.endsWith(RESOURCE.replace('/', java.io.File.separatorChar)));
            for (var path : migrations) {
                String version = path.getFileName().toString().split("__")[0].substring(1);
                assertTrue(versions.add(version), "Duplicate Flyway version: " + version);
            }
        }
        assertThat(versions).contains("27", "28");
        assertThat(SQL).contains("IF @@TRANCOUNT = 0", "BEGIN TRANSACTION", "COMMIT TRANSACTION", "ROLLBACK TRANSACTION",
                "sp_getapplock", "@LockOwner = 'Transaction'",
                "WITH (UPDLOCK, HOLDLOCK)");
    }
}
