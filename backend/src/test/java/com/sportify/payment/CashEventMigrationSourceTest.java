package com.sportify.payment;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class CashEventMigrationSourceTest {
    @Test
    void collisionGuardIsAnIndependentTransactionalSqlServerBatch() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql"));
        var configuration = org.flywaydb.core.Flyway.configure();
        assertThat(configuration.isExecuteInTransaction()).isTrue();
        var parser = new org.flywaydb.database.sqlserver.SQLServerParser(configuration,
                new org.flywaydb.core.internal.parser.ParsingContext());
        var batches = new java.util.ArrayList<org.flywaydb.core.internal.sqlscript.SqlStatement>();
        try (var statements = parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while (statements.hasNext()) batches.add(statements.next());
        }
        assertThat(batches).hasSize(7).allMatch(org.flywaydb.core.internal.sqlscript.SqlStatement::canExecuteInTransaction);
        assertThat(batches.getFirst().getSql()).contains("IF OBJECT_ID", "51031",
                "Unexpected cash reporting objects; investigate migration cohort")
                .doesNotContain("CREATE TABLE", "CREATE INDEX", "CREATE TRIGGER", "COMMIT");
        assertThat(batches.getFirst().getDelimiter().strip()).isEqualTo("GO");
        assertThat(batches.get(1).getSql()).contains("CREATE TABLE dbo.cash_coverage", "CREATE TABLE dbo.cash_event",
                "CREATE TABLE dbo.cash_capture_ticket", "CREATE UNIQUE INDEX ux_cash_collection");
    }

    @Test
    void prospectiveLedgerHasAtomicGuardsAndDoesNotInventHistory() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("cash_event", "cash_coverage", "cash_capture_ticket", "CURRENT_TRANSACTION_ID()",
                "COLLECTION", "REFUND_PAYOUT", "DECIMAL(14,2)", "DATETIME2(7)", "CREATE TRIGGER");
        assertThat(sql).doesNotContain("UPDATE [payment]", "UPDATE [refund_request]", "flyway_schema_history",
                "FROM dbo.activity_log", "CREATE OR ALTER", "DROP TABLE");
        assertThat(sql.chars().allMatch(c -> c < 128)).isTrue();
        var parser = new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure(),
                new org.flywaydb.core.internal.parser.ParsingContext());
        int triggers = 0;
        try (var statements = parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while (statements.hasNext()) if (statements.next().getSql().startsWith("CREATE TRIGGER")) triggers++;
        }
        assertThat(triggers).isEqualTo(5);
    }
}
