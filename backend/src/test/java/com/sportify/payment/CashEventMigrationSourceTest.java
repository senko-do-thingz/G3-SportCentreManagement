package com.sportify.payment;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class CashEventMigrationSourceTest {
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
