package com.sportify.payment;

import com.sportify.payment.repository.CashFlowRepository;
import com.sportify.payment.service.impl.*;
import com.sportify.core.exception.BusinessRuleException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.sql.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration") @Testcontainers
class CashMigrationIntegrationTest {
    @Container static final MSSQLServerContainer<?> SQL = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense().withPassword("Aa1!" + UUID.randomUUID());
    record Database(DataSource source, JdbcTemplate jdbc) { }
    Database database(boolean migrate) throws Exception {
        String name = "cash_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(SQL.getJdbcUrl(), SQL.getUsername(), SQL.getPassword()); var s = c.createStatement()) {
            s.execute("CREATE DATABASE [" + name + "]");
        }
        DataSource source = new AbstractDataSource() {
            public Connection getConnection() throws SQLException { return getConnection(SQL.getUsername(), SQL.getPassword()); }
            public Connection getConnection(String user, String credential) throws SQLException {
                var c = DriverManager.getConnection(SQL.getJdbcUrl(), user, credential); c.setCatalog(name); return c;
            }
        };
        var db = new Database(source, new JdbcTemplate(source));
        if (migrate) flyway(db, false, null).migrate();
        return db;
    }
    Flyway flyway(Database db, boolean demo, String target) {
        var config = Flyway.configure().dataSource(db.source()).defaultSchema("dbo").schemas("dbo")
                .outOfOrder(false).validateOnMigrate(true).cleanDisabled(true)
                .locations(demo ? new String[]{"classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/demo"}
                        : new String[]{"classpath:db/migration/identity", "classpath:db/migration/catalog"});
        if (target != null) config.target(target);
        return config.load();
    }
    Clock clock(String value) { return Clock.fixed(Instant.parse(value), ZoneId.of("Asia/Ho_Chi_Minh")); }
    long member(Database db) {
        String email = UUID.randomUUID() + "@example.invalid";
        db.jdbc().update("INSERT INTO dbo.user_account(email,password_hash,full_name,role_id,status) VALUES(?,?,'Synthetic cash fixture',(SELECT id FROM dbo.role WHERE code='MEMBER'),'ACTIVE')", email, UUID.randomUUID().toString());
        long id = db.jdbc().queryForObject("SELECT id FROM dbo.user_account WHERE email=?", Long.class, email);
        db.jdbc().update("INSERT INTO dbo.member_profile(user_id,member_code) VALUES(?,?)", id, "CF-" + UUID.randomUUID().toString().substring(0,16));
        return id;
    }
    TransactionTemplate tx(Database db) { return new TransactionTemplate(new DataSourceTransactionManager(db.source())); }
    long collect(Database db, long member, String amount, Clock clock) {
        return tx(db).execute(s -> {
            new CashCaptureService(db.jdbc(), clock, true).prepareCollection(0);
            String code = "CF-" + UUID.randomUUID().toString().substring(0,20);
            db.jdbc().update("INSERT INTO dbo.payment(payment_code,member_id,amount,payment_method,payment_status,payment_time) VALUES(?,?,?,'CASH','SUCCESS',?)",
                    code, member, new BigDecimal(amount), Timestamp.valueOf(LocalDateTime.now(clock)));
            return db.jdbc().queryForObject("SELECT id FROM dbo.payment WHERE payment_code=?", Long.class, code);
        });
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void freshDefaultAndDemoAreInactiveAndIdempotent(boolean demo) throws Exception {
        var db = database(false); var migration = flyway(db, demo, null);
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(demo ? 27 : 25);
        assertThat(db.jdbc().queryForObject("SELECT cutover_utc FROM dbo.cash_coverage", Timestamp.class)).isNull();
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event", Long.class)).isZero();
        var history = db.jdbc().queryForList("SELECT version,checksum FROM dbo.flyway_schema_history ORDER BY installed_rank");
        migration.validate(); assertThat(migration.migrate().migrationsExecuted).isZero();
        assertThat(db.jdbc().queryForList("SELECT version,checksum FROM dbo.flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
    }
    @Test void upgradeFromActualMainPreservesHistoryAndDoesNotBackfillErasedLegacyTime() throws Exception {
        var db = database(false); flyway(db, false, "30").migrate(); long member = member(db);
        db.jdbc().update("INSERT INTO dbo.payment(payment_code,member_id,amount,payment_method,payment_status,payment_time) VALUES('CF-LEGACY',?,500000,'CASH','REFUNDED',NULL)", member);
        var before = db.jdbc().queryForList("SELECT version,checksum FROM dbo.flyway_schema_history ORDER BY installed_rank");
        var rows = db.jdbc().queryForList("SELECT * FROM dbo.payment");
        var migration = flyway(db, false, null); assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.jdbc().queryForList("SELECT version,checksum FROM dbo.flyway_schema_history ORDER BY installed_rank").subList(0,before.size())).isEqualTo(before);
        assertThat(db.jdbc().queryForList("SELECT * FROM dbo.payment")).isEqualTo(rows);
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event", Long.class)).isZero();
        migration.validate();
    }
    @Test void conflictingObjectsFailWithoutOverwritingExistingObjectsOrHistory() throws Exception {
        var db = database(false); flyway(db, false, "30").migrate();
        db.jdbc().execute("CREATE TABLE dbo.cash_event(original_marker INT)");
        var history = db.jdbc().queryForList("SELECT version,checksum FROM dbo.flyway_schema_history ORDER BY installed_rank");
        assertThatThrownBy(() -> flyway(db, false, null).migrate()).hasStackTraceContaining("Unexpected cash reporting objects");
        assertThat(db.jdbc().queryForList("SELECT version,checksum FROM dbo.flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
        assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.cash_event') AND name='original_marker'", Integer.class)).isEqualTo(1);
    }
    @Test void failedFirstTransitionRollsBackActivationAndEvent() throws Exception {
        var db = database(true); long member = member(db);
        assertThatThrownBy(() -> tx(db).execute(s -> {
            collect(db, member, "100.25", clock("2026-01-01T00:00:00Z")); throw new IllegalStateException("Synthetic rollback");
        })).hasMessageContaining("Synthetic rollback");
        assertThat(db.jdbc().queryForObject("SELECT cutover_utc FROM dbo.cash_coverage", Timestamp.class)).isNull();
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event", Long.class)).isZero();
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.payment", Long.class)).isZero();
    }
    @Test void concurrentFirstWritersShareOneCutoverAndNeverLoseEvents() throws Exception {
        var db = database(true); long member = member(db); Clock time = clock("2026-01-01T00:00:00Z");
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Long> action = () -> { if(!go.await(10,TimeUnit.SECONDS)) throw new AssertionError("Start gate"); return collect(db,member,"100.25",time); };
            var a=pool.submit(action); var b=pool.submit(action); go.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS)).isNotEqualTo(b.get(30,TimeUnit.SECONDS));
        }
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event", Long.class)).isEqualTo(2);
        assertThat(db.jdbc().queryForObject("SELECT SUM(amount) FROM dbo.cash_event", BigDecimal.class)).isEqualByComparingTo("200.50");
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_capture_ticket", Long.class)).isZero();
    }
    @Test void cashUsesUtcMicrosecondsEvenWhenLegacyPaymentTimeRoundsAcrossMidnight() throws Exception {
        var db = database(true); long member=member(db);
        collect(db,member,"1.00",clock("2026-01-01T00:00:00Z"));
        long march=collect(db,member,"10.25",clock("2026-03-31T16:59:59.999999Z"));
        collect(db,member,"20.50",clock("2026-03-31T17:00:00Z"));
        var repository=new CashFlowRepository(new NamedParameterJdbcTemplate(db.source()));
        assertThat(repository.totals(Instant.parse("2026-02-28T17:00:00Z"),Instant.parse("2026-03-31T17:00:00Z")).grossCashReceived()).isEqualByComparingTo("10.25");
        assertThat(repository.totals(Instant.parse("2026-03-31T17:00:00Z"),Instant.parse("2026-04-30T17:00:00Z")).grossCashReceived()).isEqualByComparingTo("20.50");
        assertThat(db.jdbc().queryForObject("SELECT event_time_utc FROM dbo.cash_event WHERE payment_id=?",Timestamp.class,march).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026,3,31,16,59,59,999999000));
    }
    @Test void repeatedSourceSuccessCannotDuplicateCollectionOrChangeOriginalEvent() throws Exception {
        var db=database(true); long member=member(db); Clock time=clock("2026-01-01T00:00:00Z");
        long payment=collect(db,member,"100.25",time);
        var before=db.jdbc().queryForList("SELECT * FROM dbo.cash_event");
        assertThatThrownBy(() -> tx(db).execute(s -> {
            db.jdbc().update("UPDATE dbo.payment SET payment_status='PENDING' WHERE id=?",payment);
            new CashCaptureService(db.jdbc(),time,false).prepareCollection(payment);
            db.jdbc().update("UPDATE dbo.payment SET payment_status='SUCCESS' WHERE id=?",payment); return null;
        })).isInstanceOf(RuntimeException.class);
        assertThat(db.jdbc().queryForList("SELECT * FROM dbo.cash_event")).isEqualTo(before);
        assertThat(db.jdbc().queryForObject("SELECT payment_status FROM dbo.payment WHERE id=?",String.class,payment)).isEqualTo("SUCCESS");
    }
    @Test void boundedExportRejects50001ExactCentEventsBeforeAttachment() throws Exception {
        var db=database(true); long member=member(db); Clock time=clock("2026-04-01T00:00:00Z");
        collect(db,member,"1.00",clock("2026-01-01T00:00:00Z"));
        tx(db).executeWithoutResult(s -> {
            db.jdbc().update("""
                    WITH digit(n) AS (SELECT n FROM (VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) d(n)),
                    tally(n) AS (SELECT TOP(50001) ROW_NUMBER() OVER(ORDER BY a.n,b.n,c.n,d.n,e.n) FROM digit a CROSS JOIN digit b CROSS JOIN digit c CROSS JOIN digit d CROSS JOIN digit e)
                    INSERT INTO dbo.payment(payment_code,member_id,amount,payment_method,payment_status)
                    SELECT CONCAT('CF-LIMIT-',n),?,0.01,'CASH','PENDING' FROM tally
                    """,member);
            db.jdbc().update("""
                    INSERT INTO dbo.cash_capture_ticket(transaction_id,event_type,source_id,event_time_utc)
                    SELECT CURRENT_TRANSACTION_ID(),'COLLECTION',id,'2026-04-01T00:00:00' FROM dbo.payment WHERE payment_code LIKE 'CF-LIMIT-%'
                    """);
            db.jdbc().update("UPDATE dbo.payment SET payment_status='SUCCESS',payment_time='2026-04-01T07:00:00' WHERE payment_code LIKE 'CF-LIMIT-%'");
        });
        var repository=new CashFlowRepository(new NamedParameterJdbcTemplate(db.source()));
        var service=new CashFlowReportService(repository,time);
        assertThat(service.overview("2026-04").grossCashReceived()).isEqualByComparingTo("500.01");
        assertThatThrownBy(() -> service.export(LocalDate.of(2026,4,1),LocalDate.of(2026,4,30))).isInstanceOf(BusinessRuleException.class);
        List<Long> ids=new ArrayList<>(); repository.events(Instant.parse("2026-03-31T17:00:00Z"),Instant.parse("2026-04-30T17:00:00Z"),50001,e->ids.add(e.id()));
        assertThat(ids).hasSize(50001).isSorted().doesNotHaveDuplicates();
    }
    @Test void collectionLedgerFailureRollsBackFirstActivationAndSourceInsert() throws Exception {
        var db=database(true); long member=member(db);
        db.jdbc().execute("CREATE TRIGGER dbo.tr_cash_test_failure ON dbo.cash_event AFTER INSERT AS BEGIN SET NOCOUNT ON; THROW 51032, 'Synthetic ledger insert failure', 1; END");
        assertThatThrownBy(() -> collect(db,member,"100.25",clock("2026-01-01T00:00:00Z"))).isInstanceOf(RuntimeException.class);
        assertThat(db.jdbc().queryForObject("SELECT cutover_utc FROM dbo.cash_coverage",Timestamp.class)).isNull();
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event",Long.class)).isZero();
        assertThat(db.jdbc().queryForObject("SELECT COUNT_BIG(*) FROM dbo.payment",Long.class)).isZero();
    }
    @Test void mixedInvoicesNeverMultiplyEventsOrRewriteOriginalAttribution() throws Exception {
        var db=database(true); long member=member(db);
        collect(db,member,"1.00",clock("2026-01-01T00:00:00Z"));
        long payment=collect(db,member,"500.25",clock("2026-01-02T00:00:00Z"));
        for(int i=0;i<2;i++) {
            String invoice="CF-MIX-"+i;
            db.jdbc().update("INSERT INTO dbo.invoice(invoice_number,payment_id,member_id,subtotal_amount,total_amount,status) VALUES(?,?,?,500.25,500.25,'PAID')",invoice,payment,member);
            long id=db.jdbc().queryForObject("SELECT id FROM dbo.invoice WHERE invoice_number=?",Long.class,invoice);
            for(String type:List.of("SPORT_PACKAGE","MEMBERSHIP_CARD")) db.jdbc().update("INSERT INTO dbo.invoice_line(invoice_id,item_type,item_reference_id,description,unit_price,line_total) VALUES(?,?,1,'Synthetic mixed line',250,250)",id,type);
        }
        db.jdbc().update("UPDATE dbo.payment SET payment_method='MOMO' WHERE id=?",payment);
        var repository=new CashFlowRepository(new NamedParameterJdbcTemplate(db.source()));
        var service=new CashFlowReportService(repository,clock("2026-02-01T00:00:00Z"));
        var byMethod=service.breakdown(LocalDate.of(2026,1,2),LocalDate.of(2026,1,2),com.sportify.payment.dto.finance.RevenueGroupBy.METHOD);
        assertThat(byMethod.rows()).hasSize(1); assertThat(byMethod.rows().getFirst().key()).isEqualTo("CASH");
        var byItem=service.breakdown(LocalDate.of(2026,1,2),LocalDate.of(2026,1,2),com.sportify.payment.dto.finance.RevenueGroupBy.ITEM_TYPE);
        assertThat(byItem.rows()).hasSize(1); assertThat(byItem.rows().getFirst().key()).isEqualTo("UNALLOCATED");
        assertThat(byItem.collectionCount()).isEqualTo(1); assertThat(byItem.grossCashReceived()).isEqualByComparingTo("500.25");
    }
}
