package com.sportify.core.config;

import com.sportify.catalog.entity.ClassLevel;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.sportify.core.config.DemoClassesSeedSource.*;
import static org.assertj.core.api.Assertions.assertThat;

class DemoClassesMigrationTest {
    record Slot(LocalDate date, LocalTime start, LocalTime end, String coach, String facility, String format) { }

    @Test
    void unchangedMigrationsThroughV28RetainTheirApprovedContent() throws Exception {
        var root = DemoSeedSource.ROOT.resolve("backend/src/main/resources");
        var digest = MessageDigest.getInstance("SHA-256");
        try (var walk = Files.walk(root.resolve("db"))) {
            var files = walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches("V[0-9]+__.+\\.sql"))
                    .filter(p -> Integer.parseInt(p.getFileName().toString().split("__")[0].substring(1)) <= 28)
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().replace('\\', '/'))).toList();
            assertThat(files).hasSize(24);
            for (var path : files) {
                digest.update((root.relativize(path).toString().replace('\\', '/') + "\n").getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readString(path).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        assertThat(HexFormat.of().formatHex(digest.digest())).isEqualTo("50bd5a7b9e676e5f181f757681416d7e21e899cf34eda8a4c7f19713e4cdd240");
    }

    @Test
    void installedFlywaySqlServerParserReadsOneCompleteTransactionalBatch() {
        var parser = new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure(),
                new org.flywaydb.core.internal.parser.ParsingContext());
        try (var statements = parser.parse(new org.flywaydb.core.internal.resource.StringResource(SQL))) {
            assertThat(statements.hasNext()).isTrue();
            assertThat(statements.next().getSql()).contains("BEGIN TRY", "COMMIT TRANSACTION", "ROLLBACK TRANSACTION", "END CATCH;");
            assertThat(statements.hasNext()).isFalse();
        }
    }

    @Test
    void migrationIsNextUniqueVersionInExistingOptInLocation() throws Exception {
        var files = new ArrayList<java.nio.file.Path>();
        try (var paths = Files.walk(DemoSeedSource.ROOT.resolve("backend/src/main/resources/db"))) {
            files.addAll(paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches("V.+__.+\\.sql")).toList());
        }
        var versions = files.stream().map(p -> MigrationVersion.fromVersion(p.getFileName().toString().split("__")[0].substring(1))).toList();
        assertThat(versions).doesNotHaveDuplicates();
        assertThat(versions.stream().filter(v -> v.compareTo(MigrationVersion.fromVersion("29")) < 0).max(Comparator.naturalOrder()).orElseThrow())
                .isEqualTo(MigrationVersion.fromVersion("28"));
        assertThat(files).anyMatch(p -> p.toString().replace('\\', '/').endsWith(RESOURCE));
        assertThat(DemoSeedSource.resource("application-demo.yml")).contains("classpath:db/demo");
        assertThat(DemoSeedSource.resource("application.yml")).contains("out-of-order: false", "validate-on-migrate: true")
                .doesNotContain("classpath:db/demo");
        assertThat(DemoSeedSource.resource("application-prod.yml")).doesNotContain("classpath:db/demo");
    }

    @Test
    void shippedClassSpecHasSixUniqueClassesThreeActualSportsAndAllSupportedLevels() {
        assertThat(CLASSES).hasSize(6);
        assertThat(CLASSES).extracting(ClassSpec::code).doesNotHaveDuplicates();
        assertThat(CLASSES).extracting(ClassSpec::ordinal).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(CLASSES).extracting(ClassSpec::sport).containsOnly("BADMINTON", "SWIMMING", "BASKETBALL");
        assertThat(CLASSES.stream().map(ClassSpec::sport).distinct()).hasSize(3);
        assertThat(CLASSES.stream().map(s -> ClassLevel.valueOf(s.level())).distinct()).containsExactlyInAnyOrder(ClassLevel.values());
        String catalog = DemoSeedSource.resource("db/migration/catalog/V6__seed_catalog_data.sql");
        CLASSES.forEach(spec -> assertThat(catalog).contains("VALUES ('" + spec.sport() + "'"));
    }

    @Test
    void eachCoachMatchesBe07SportAndEveryFacilityIsAppropriate() {
        for (var spec : CLASSES) {
            assertThat(DemoSeedSource.ACCOUNTS).anySatisfy(account -> {
                assertThat(account.email()).isEqualTo(spec.coach());
                assertThat(account.role()).isEqualTo("COACH");
                assertThat(account.sport()).isEqualTo(spec.sport());
            });
            assertThat(spec.facility()).isEqualTo(switch (spec.sport()) {
                case "BADMINTON" -> "DEMO-BADMINTON";
                case "SWIMMING" -> "INDOOR_POOL";
                default -> spec.ordinal() == 5 ? "BB_COURT_A" : "BB_COURT_B";
            });
        }
    }

    List<Slot> plan(LocalDate anchor) {
        var result = new ArrayList<Slot>();
        for (int week = 0; week < 4; week++) {
            for (var spec : CLASSES) {
                var date = anchor.plusDays(spec.offset() + 7L * week);
                result.add(new Slot(date, spec.coachStart(), spec.coachEnd(), spec.coach(), spec.facility(), "COACH_LED"));
                result.add(new Slot(date, spec.selfStart(), spec.selfEnd(), null, spec.facility(), "SELF_TRAINING"));
            }
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-16", "2026-12-31", "2028-02-28", "2028-02-29"})
    void actualSpecificationProduces48FutureNonOverlappingSlotsAcrossFourWeeks(LocalDate anchor) {
        var slots = plan(anchor);
        assertThat(slots).hasSize(48).doesNotHaveDuplicates();
        assertThat(slots.stream().filter(s -> s.format().equals("COACH_LED"))).hasSize(24);
        assertThat(slots.stream().filter(s -> s.format().equals("SELF_TRAINING"))).hasSize(24).allMatch(s -> s.coach() == null);
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.date()).isBetween(anchor.plusDays(1), anchor.plusDays(28));
            assertThat(slot.start()).isBefore(slot.end());
            assertThat(ChronoUnit.MINUTES.between(slot.start(), slot.end())).isEqualTo(slot.coach() == null ? 60 : 90);
            assertThat(slot.start()).isAfterOrEqualTo(LocalTime.of(7, 0));
            assertThat(slot.end()).isBeforeOrEqualTo(LocalTime.of(21, 0));
        });
        assertThat(slots.stream().map(s -> (ChronoUnit.DAYS.between(anchor, s.date()) - 1) / 7).distinct()).containsExactly(0L, 1L, 2L, 3L);
        assertThat(slots).anyMatch(s -> s.date().getDayOfWeek().getValue() >= 6)
                .anyMatch(s -> s.date().getDayOfWeek().getValue() < 6);
        for (int first = 0; first < slots.size(); first++) {
            for (int second = first + 1; second < slots.size(); second++) {
                var a = slots.get(first); var b = slots.get(second);
                boolean shares = a.facility().equals(b.facility()) || a.coach() != null && a.coach().equals(b.coach());
                boolean overlap = a.date().equals(b.date()) && a.start().isBefore(b.end()) && b.start().isBefore(a.end());
                assertThat(shares && overlap).as("specification has no shared-resource overlap").isFalse();
            }
        }
        assertThat(SQL).contains("e.day_offset + 7 * w.week_no", "(VALUES (0), (1), (2), (3))", "CAST(GETDATE() AS DATE)");
    }

    @Test
    void smallCapacityFixturesNeedOnlyThreeMembersAndFiveConsumedCredits() {
        assertThat(CLASSES.get(0).capacity()).isEqualTo(3);
        assertThat(CLASSES.get(1).capacity()).isEqualTo(3);
        var pattern = java.util.regex.Pattern.compile("\\('(DEMO-BK-[^']+)', (\\d), '(DEMO-REG-[^']+)'\\)");
        var bookings = pattern.matcher(SQL).results().toList();
        assertThat(bookings).hasSize(5);
        assertThat(bookings.stream().map(m -> m.group(1))).doesNotHaveDuplicates();
        assertThat(bookings.stream().filter(m -> m.group(2).equals("1"))).hasSize(3);
        assertThat(bookings.stream().filter(m -> m.group(2).equals("2"))).hasSize(2);
        var consumed = bookings.stream().collect(java.util.stream.Collectors.groupingBy(m -> m.group(3), java.util.stream.Collectors.counting()));
        assertThat(consumed).containsExactlyInAnyOrderEntriesOf(Map.of("DEMO-REG-BAD-AN", 2L, "DEMO-REG-BAD-MAI", 2L, "DEMO-REG-BAD-BAO", 1L));
        assertThat(SQL).contains("'RECEPTION', 0, 0, 0, 8, 8", "remaining_sessions = e.final_remaining", "SET booked_count = used.n");
        assertThat(SQL).doesNotContain("INSERT INTO payment", "INSERT INTO invoice", "INSERT INTO activity_log");
    }

    @Test
    void seedDoesNotMutateAccountsSharedMembershipOrAssumeGeneratedIds() {
        assertThat(SQL).doesNotContain("UPDATE user_account", "INSERT INTO user_account", "UPDATE coach_profile",
                "UPDATE member_profile", "INSERT INTO member_card", "IDENTITY_INSERT", "ALTER TABLE", "ALTER SEQUENCE", "DELETE FROM");
        assertThat(SQL).doesNotMatch("(?s).*(?:coach_id|sport_id|facility_id|member_id|package_id)\\s*=\\s*[0-9]+.*");
        assertThat(SQL).contains("u.email = e.coach_email", "f.code = e.facility_code", "p.code = e.package_code");
    }

    @Test
    void replayGuardsAreAtomicAnchoredAndResourceScoped() {
        assertThat(SQL).contains("IF @@TRANCOUNT = 0", "SET XACT_ABORT ON", "COMMIT TRANSACTION", "ROLLBACK TRANSACTION",
                "sp_getapplock", "@LockOwner = 'Transaction'", "Sportify:BE-15:demo-classes", "@Anchors",
                "WITH (UPDLOCK, HOLDLOCK)", "s.status IN ('DRAFT', 'PUBLISHED')", "s.start_time < e.end_time",
                "e.start_time < s.end_time", "e.coach_id IS NOT NULL", "WHERE missing = 1", "e.missing_count > 0",
                "THROW 51503", "THROW 51504", "THROW 51505");
        assertThat(SQL).doesNotContain("TABLOCK", "MERGE ", "RAND(");
    }

    @Test
    void technicalSourceIsAsciiAndReadmeDocumentsActualLifecycleAndCounts() {
        assertThat(SQL.chars().allMatch(c -> c < 128)).isTrue();
        var readme = DemoSeedSource.file(DemoSeedSource.ROOT.resolve("README.md"));
        assertThat(readme).contains("V29__seed_demo_classes_and_sessions.sql", "48", "24", "DEMO-CL-BAD-BEG",
                "DEMO-CL-BAD-INT", "fresh isolated", "not move", "complimentary");
        CLASSES.forEach(spec -> assertThat(readme).contains(spec.code(), spec.name(), spec.coach(), spec.facility()));
    }
}
