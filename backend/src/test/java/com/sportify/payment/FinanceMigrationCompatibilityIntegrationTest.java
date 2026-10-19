package com.sportify.payment;

import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.FinanceReportRepository;
import com.sportify.payment.service.impl.FinanceReportServiceImpl;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each path owns a newly created database inside this test's ephemeral SQL Server, never an external target. */
@Tag("integration")
@Testcontainers
class FinanceMigrationCompatibilityIntegrationTest {
    @Container
    private static final MSSQLServerContainer<?> SQL = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense().withPassword("Aa1!" + UUID.randomUUID());
    private static final LocalDate FROM = LocalDate.of(2001, 3, 1);
    private static final LocalDate TO = LocalDate.of(2001, 3, 31);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2001-03-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    record Database(String name, DataSource source, JdbcTemplate jdbc) { }

    private Database newDatabase() throws SQLException {
        String name = "finance_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(SQL.getJdbcUrl(), SQL.getUsername(), SQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE [" + name + "]");
        }
        DataSource source = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return getConnection(SQL.getUsername(), SQL.getPassword());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                Connection connection = DriverManager.getConnection(SQL.getJdbcUrl(), username, password);
                connection.setCatalog(name);
                return connection;
            }
        };
        var database = new Database(name, source, new JdbcTemplate(source));
        assertThat(database.jdbc().queryForObject("SELECT DB_NAME()", String.class)).isEqualTo(name);
        return database;
    }

    private Flyway flyway(Database database, boolean demo, String target) {
        var configuration = Flyway.configure().dataSource(database.source())
                .defaultSchema("dbo").schemas("dbo").outOfOrder(false).validateOnMigrate(true).cleanDisabled(true)
                .locations(demo
                        ? new String[]{"classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/demo"}
                        : new String[]{"classpath:db/migration/identity", "classpath:db/migration/catalog"});
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private List<Map<String, Object>> history(Database database) {
        return database.jdbc().queryForList("SELECT version, script, checksum, success FROM dbo.flyway_schema_history ORDER BY installed_rank");
    }

    private void assertObjects(Database database) {
        var jdbc = database.jdbc();
        assertThat(jdbc.queryForObject("SELECT OBJECT_ID(N'dbo.vw_payment_ledger', N'V')", Long.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT OBJECT_ID(N'dbo.vw_paid_payment', N'V')", Long.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = 'ix_payment_status_time'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT version FROM dbo.flyway_schema_history WHERE success = 1", String.class))
                .contains("27", "30").doesNotContain("24", "25");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 0", Integer.class)).isZero();
    }

    private void seedLedger(Database database) {
        var jdbc = database.jdbc();
        Long role = jdbc.queryForObject("SELECT id FROM dbo.[role] WHERE code = 'MEMBER'", Long.class);
        Long member = jdbc.queryForObject("""
                INSERT INTO dbo.user_account (email, password_hash, full_name, role_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'Synthetic finance member', ?, 'ACTIVE')
                """, Long.class, UUID.randomUUID() + "@example.invalid", UUID.randomUUID().toString(), role);
        jdbc.update("INSERT INTO dbo.member_profile (user_id, member_code) VALUES (?, ?)", member, "FM-" + UUID.randomUUID().toString().substring(0, 12));
        Timestamp time = Timestamp.valueOf(FROM.atStartOfDay());
        Long first = jdbc.queryForObject("""
                SET NOCOUNT ON;
                DECLARE @cash_fixture_id TABLE(id BIGINT);
                INSERT INTO dbo.payment (payment_code, member_id, amount, payment_method, payment_status, payment_time)
                OUTPUT INSERTED.id INTO @cash_fixture_id VALUES ('FM-%_[\\', ?, 100.25, 'BANK_TRANSFER', 'SUCCESS', ?);
                SELECT id FROM @cash_fixture_id
                """, Long.class, member, time);
        jdbc.update("""
                INSERT INTO dbo.payment (payment_code, member_id, amount, payment_method, payment_status, payment_time)
                VALUES ('FM-OTHER', ?, 200.75, 'CASH', 'SUCCESS', ?)
                """, member, time);
        // Multiple invoices and lines must not multiply the payment amount or transaction count.
        for (int invoice = 0; invoice < 2; invoice++) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO dbo.invoice (invoice_number, payment_id, member_id, subtotal_amount, total_amount, status)
                    OUTPUT INSERTED.id VALUES (?, ?, ?, 100.25, 100.25, 'PAID')
                    """, Long.class, "FM-INV-" + invoice, first, member);
            for (int line = 0; line < 2; line++) {
                jdbc.update("""
                        INSERT INTO dbo.invoice_line (invoice_id, item_type, item_reference_id, description, unit_price, line_total)
                        VALUES (?, 'PENALTY_FEE', 1, 'Synthetic line', ?, ?)
                        """, id, line == 0 ? new BigDecimal("50.12") : new BigDecimal("50.13"),
                        line == 0 ? new BigDecimal("50.12") : new BigDecimal("50.13"));
            }
        }
    }

    private void assertQueriesAndUnchangedPayments(Database database) {
        var jdbc = database.jdbc();
        var before = jdbc.queryForList("SELECT id, payment_status, amount, payment_time FROM dbo.payment ORDER BY id");
        var repository = new FinanceReportRepository(new NamedParameterJdbcTemplate(database.source()));
        var service = new FinanceReportServiceImpl(repository, CLOCK);
        assertThat(service.overview("2001-03").paidRevenue()).isEqualByComparingTo("301.00");
        assertThat(service.overview("2001-03").paidTransactions()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.vw_payment_ledger", Long.class)).isEqualTo(2L);
        for (var group : RevenueGroupBy.values()) {
            var report = service.revenue(FROM, TO, group);
            assertThat(report.totalRevenue()).isEqualByComparingTo("301.00");
            assertThat(report.totalTransactions()).isEqualTo(2);
            assertThat(report.rows().stream().map(r -> r.sharePercent()).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("100.00");
        }
        assertThat(service.revenue(FROM, TO, RevenueGroupBy.SPORT).rows().getFirst().label()).isEqualTo("No sport (membership card or other)");
        var literal = service.transactions(PaymentStatus.SUCCESS, PaymentMethod.BANK_TRANSFER, null, FROM, TO, "%_[\\", 0, 20);
        assertThat(literal.totalElements()).isEqualTo(1);
        assertThat(literal.content().getFirst().code()).isEqualTo("FM-%_[\\");
        assertThat(service.transactions(null, null, null, FROM, TO, null, 0, 1).content().getFirst().code()).isEqualTo("FM-OTHER");
        assertThat(service.transactions(null, null, null, FROM, TO, null, 1, 1).content().getFirst().code()).isEqualTo("FM-%_[\\");
        assertThat(service.exportRevenue(FROM, TO).content()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(jdbc.queryForList("SELECT id, payment_status, amount, payment_time FROM dbo.payment ORDER BY id")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freshNormalAndDemoDatabasesMigrateOnceAndRunFinanceQueries(boolean demo) throws Exception {
        var database = newDatabase();
        var migration = flyway(database, demo, null);
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(demo ? 27 : 25);
        assertObjects(database);
        seedLedger(database);
        assertQueriesAndUnchangedPayments(database);
        var applied = history(database);
        migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();
        assertThat(history(database)).isEqualTo(applied);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void normalV27AndDemoV29UpgradeWithoutChangingHistoryOrData(boolean demo) throws Exception {
        var database = newDatabase();
        var baseline = flyway(database, demo, demo ? "29" : "27");
        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(demo ? 25 : 23);
        var previous = history(database);
        seedLedger(database);
        var rows = database.jdbc().queryForList("SELECT * FROM dbo.payment ORDER BY id");
        assertThat(database.jdbc().queryForObject("SELECT OBJECT_ID(N'dbo.vw_paid_payment')", Long.class)).isNull();
        var upgrade = flyway(database, demo, null);
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(history(database).subList(0, previous.size())).isEqualTo(previous);
        assertThat(database.jdbc().queryForList("SELECT * FROM dbo.payment ORDER BY id")).isEqualTo(rows);
        assertObjects(database);
        assertQueriesAndUnchangedPayments(database);
        upgrade.validate();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"vw_payment_ledger", "vw_paid_payment", "ix_payment_status_time"})
    void unexpectedObjectsFailBeforeCreatingOrOverwritingFinanceObjects(String object) throws Exception {
        var database = newDatabase();
        flyway(database, false, "27").migrate();
        var previous = history(database);
        if (object.startsWith("vw_")) {
            database.jdbc().execute("CREATE VIEW dbo." + object + " AS SELECT 1 AS original_marker");
        } else {
            database.jdbc().execute("CREATE INDEX ix_payment_status_time ON dbo.payment(id)");
        }
        assertThatThrownBy(() -> flyway(database, false, null).migrate()).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("BE-22 finance objects already exist");
        assertThat(history(database)).isEqualTo(previous);
        if (object.startsWith("vw_")) {
            assertThat(database.jdbc().queryForObject("SELECT OBJECT_DEFINITION(OBJECT_ID(?))", String.class, "dbo." + object))
                    .contains("original_marker");
        }
        for (String view : List.of("vw_payment_ledger", "vw_paid_payment")) {
            if (!view.equals(object)) assertThat(database.jdbc().queryForObject("SELECT OBJECT_ID(?)", Long.class, "dbo." + view)).isNull();
        }
    }

    @Test
    void committedInsertBetweenCountAndStreamCannotReturnAnOversizedSuccessfulCsv() throws Exception {
        var database = newDatabase();
        flyway(database, false, null).migrate();
        seedLedger(database);
        Long member = database.jdbc().queryForObject("SELECT TOP 1 member_id FROM dbo.payment ORDER BY id", Long.class);
        database.jdbc().update("""
                WITH digit(n) AS (SELECT n FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) d(n)),
                tally(n) AS (SELECT TOP (49998) ROW_NUMBER() OVER (ORDER BY a.n,b.n,c.n,d.n,e.n)
                    FROM digit a CROSS JOIN digit b CROSS JOIN digit c CROSS JOIN digit d CROSS JOIN digit e)
                INSERT INTO dbo.payment (payment_code, member_id, amount, payment_method, payment_status, payment_time)
                SELECT CONCAT('FC-ROW-', n), ?, 1.00, 'CASH', 'SUCCESS', ? FROM tally
                """, member, Timestamp.valueOf(FROM.atStartOfDay()));
        CountDownLatch counted = new CountDownLatch(1);
        var insertion = CompletableFuture.runAsync(() -> {
            try {
                assertThat(counted.await(30, TimeUnit.SECONDS)).isTrue();
                database.jdbc().update("""
                        INSERT INTO dbo.payment (payment_code, member_id, amount, payment_method, payment_status, payment_time)
                        VALUES ('FC-LATE', ?, 1.00, 'CASH', 'SUCCESS', ?)
                        """, member, Timestamp.valueOf(FROM.atStartOfDay()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        var repository = new FinanceReportRepository(new NamedParameterJdbcTemplate(database.source())) {
            @Override
            public Totals paidTotals(java.time.LocalDateTime from, java.time.LocalDateTime to) {
                var totals = super.paidTotals(from, to);
                assertThat(totals.count()).isEqualTo(50000);
                counted.countDown();
                try {
                    insertion.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("Concurrent fixture insert did not commit", e);
                }
                return totals;
            }
        };
        var service = new FinanceReportServiceImpl(repository, CLOCK);
        assertThatThrownBy(() -> service.exportRevenue(FROM, TO)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds the limit of 50000");
        insertion.get(30, TimeUnit.SECONDS);
        assertThat(database.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.vw_paid_payment", Long.class)).isEqualTo(50001);
        AtomicInteger rows = new AtomicInteger();
        repository.forEachPaidRow(FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(), 3, row -> rows.incrementAndGet());
        assertThat(rows.get()).isEqualTo(3);
    }
}
