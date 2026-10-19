package com.sportify.payment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.sqlserver.SQLServerParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline safety checks supplement, but never replace, the SQL Server migration suites. */
class FinanceMigrationSourceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path FINANCE = RESOURCES.resolve("db/migration/catalog/V30__create_finance_report_views.sql");

    @Test
    void financeUsesANewGloballyUniqueForwardVersion() throws Exception {
        assertThat(FINANCE).exists();
        try (var walk = Files.walk(RESOURCES.resolve("db"))) {
            var versions = walk.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString()).filter(n -> n.matches("V[0-9]+__.*\\.sql"))
                    .map(n -> MigrationVersion.fromVersion(n.substring(1, n.indexOf("__")))).toList();
            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions.stream().filter(v -> v.compareTo(MigrationVersion.fromVersion("30")) < 0)
                    .max(Comparator.naturalOrder()).orElseThrow()).isEqualTo(MigrationVersion.fromVersion("29"));
        }
    }

    @Test
    void allPreFinanceMigrationsRetainTheirExactApprovedContent() throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var walk = Files.walk(RESOURCES.resolve("db"))) {
            var files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("V[0-9]+__.*\\.sql"))
                    .filter(p -> Integer.parseInt(p.getFileName().toString().split("__")[0].substring(1)) <= 29)
                    .sorted(Comparator.comparing(p -> RESOURCES.relativize(p).toString().replace('\\', '/'))).toList();
            assertThat(files).hasSize(25);
            for (var file : files) {
                digest.update((RESOURCES.relativize(file).toString().replace('\\', '/') + "\n").getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readString(file).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        assertThat(HexFormat.of().formatHex(digest.digest()))
                .isEqualTo("3afcd9be651dcc3e508d2c880879753ee5f8e25992a8aa39ee64a0029577ce4d");
    }

    @Test
    void installedSqlServerParserRecognizesSeparateViewBatchesAndConflictGuards() throws Exception {
        String sql = Files.readString(FINANCE);
        assertThat(sql.chars().allMatch(c -> c < 128)).isTrue();
        assertThat(sql).contains("THROW 51030", "OBJECT_ID(N'dbo.vw_payment_ledger')", "ix_payment_status_time")
                .doesNotContain("DROP ", "CREATE OR ALTER", "ALTER VIEW", "flyway_schema_history");
        var parser = new SQLServerParser(Flyway.configure(), new ParsingContext());
        var statements = new ArrayList<String>();
        try (var parsed = parser.parse(new StringResource(sql))) {
            while (parsed.hasNext()) statements.add(parsed.next().getSql());
        }
        assertThat(statements).filteredOn(s -> s.startsWith("CREATE VIEW [dbo].")).hasSize(2);
        assertThat(statements).anyMatch(s -> s.startsWith("CREATE NONCLUSTERED INDEX"));
    }
}
