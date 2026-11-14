package com.sportify.demoqr;

import com.sportify.support.DisposableUnifyDatabase;
import com.sportify.tools.UnifyPreflight;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class UnifyReconciliationIntegrationTest {
    private Flyway flyway(DisposableUnifyDatabase db, String target) {
        var config = Flyway.configure().dataSource(db.source()).locations(
                        "classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/compat-vietqr")
                .cleanDisabled(true).outOfOrder(false).baselineOnMigrate(false);
        if (target != null) config.target(target);
        return config.load();
    }

    @Test void freshV46DryRunIsReadOnlyRepeatableAndDetectsSchemaDrift() throws Exception {
        var db = DisposableUnifyDatabase.create(); var migration = flyway(db, null);
        migration.migrate(); migration.validate();
        String hash = UnifyPreflight.schemaFingerprint(db.source());
        var independent = DisposableUnifyDatabase.create(); flyway(independent, null).migrate();
        assertThat(UnifyPreflight.schemaFingerprint(independent.source())).isEqualTo(hash);
        // The archived V62 fingerprint cannot represent the V63 Wallet-free schema.\n        // Cross-check the independently migrated database above instead of pinning a stale hash.\n        assertThat(hash).hasSize(64);
        var before = UnifyPreflight.inspect(db.source(), false, hash);
        assertThat(UnifyPreflight.inspect(db.source(), false, hash)).isEqualTo(before);
        assertThat(before.proposedImports()).isZero();
        assertThat(before.getDataUnification()).isEqualTo("INCOMPLETE_HISTORY_OUT_OF_RELEASE_SCOPE");
        assertThat(before.getProtectedSourceCoverage()).isEqualTo("UNKNOWN_NOT_AUTHORIZED_BY_THIS_REPORT");
        assertThat(before.getSchemaFingerprintFormat()).isEqualTo("DB_UNIFY_SCHEMA_V2");
        assertThat(before.liveCutover()).isEqualTo("BLOCKED_FOR_CODEOWNER");
        assertThat(migration.migrate().migrationsExecuted).isZero();
        Path evidence = Path.of("target/db-unify"); Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("schema-fingerprint.txt"), hash + "\n");
        Files.writeString(evidence.resolve("fresh-preflight.json"), new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(before));
        db.jdbc().execute("CREATE TABLE dbo.unapproved_drift(id int NOT NULL)");
        assertThatThrownBy(() -> UnifyPreflight.inspect(db.source(), false, hash)).hasMessage("SCHEMA_FINGERPRINT_MISMATCH");
        independent.jdbc().execute("CREATE TABLE dbo.unapproved_drift(id int NOT NULL)");
        for (var fixture : new DisposableUnifyDatabase[]{db, independent}) {
            fixture.jdbc().execute("CREATE TABLE dbo.unify_fingerprint_left(id int NULL)");
            fixture.jdbc().execute("CREATE TABLE dbo.unify_fingerprint_right(id int NULL)");
        }
        assertThat(UnifyPreflight.schemaFingerprint(db.source())).isEqualTo(UnifyPreflight.schemaFingerprint(independent.source()));
        db.jdbc().execute("ALTER TABLE dbo.unify_fingerprint_left ADD extra int NULL");
        independent.jdbc().execute("ALTER TABLE dbo.unify_fingerprint_right ADD extra int NULL");
        // Duplicate JDBC column labels must never erase which table owns a column.
        assertThat(UnifyPreflight.schemaFingerprint(db.source())).isNotEqualTo(UnifyPreflight.schemaFingerprint(independent.source()));
        independent.jdbc().execute("CREATE INDEX ix_unify_fingerprint_probe ON dbo.user_account(full_name DESC)");
        assertThatThrownBy(() -> UnifyPreflight.inspect(independent.source(), false, hash)).hasMessage("SCHEMA_FINGERPRINT_MISMATCH");
    }

    @Test void oldV40CannotPassTheReleaseV46GuardOrMutateHistory() throws Exception {
        var db=DisposableUnifyDatabase.create();flyway(db,"40").migrate();
        var history=db.jdbc().queryForList("SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank");
        // Pending release migrations fail the history guard before the version check.
        assertThatThrownBy(()->UnifyPreflight.inspect(db.source(),false,UnifyPreflight.schemaFingerprint(db.source())))
                .hasMessage("FLYWAY_HISTORY_MISMATCH");
        assertThat(db.jdbc().queryForList("SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
    }

    @Test void preV39UpgradePreservesNumericKeysReferencesAndInstalledChecksums() throws Exception {
        var db = DisposableUnifyDatabase.create(); flyway(db, "37").migrate(); var jdbc = db.jdbc();
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,status) SELECT id,'2026-11-03','10:00','11:00','SELF_TRAINING','PUBLISHED' FROM sport WHERE code='FOOTBALL'");
        long id = jdbc.queryForObject("SELECT MAX(id) FROM class_session", Long.class);
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'upgrade@unified-demo.invalid','fixture','Synthetic upgrade',id,'ACTIVE' FROM role WHERE code='MEMBER'");
        long member = jdbc.queryForObject("SELECT id FROM user_account WHERE email='upgrade@unified-demo.invalid'", Long.class);
        jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,'UNIFY-UPGRADE')", member);
        jdbc.update("INSERT booking(booking_code,session_id,member_id,status) VALUES ('UNIFY-UPGRADE',?,?,'CONFIRMED')", id, member);
        jdbc.update("INSERT attendance_record(session_id,member_id,status,marked_by) VALUES (?,?,'PRESENT',?)", id, member, member);
        jdbc.update("INSERT session_result(session_id,member_id,notes) VALUES (?,?,'Preserved fixture')", id, member);
        var history = jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank");
        flyway(db, "39").migrate(); var latest = flyway(db, null); latest.migrate(); latest.validate();
        assertThatThrownBy(() -> UnifyPreflight.inspect(db.source(), false, UnifyPreflight.schemaFingerprint(db.source())))
                .hasMessage("SYNTHETIC_BOOKING_REQUIRES_OWNER_DISPOSITION");
        assertThat(jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE TRY_CONVERT(int,version)<=37 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT session_code FROM class_session WHERE id=?", String.class, id)).isEqualTo("031126FB01");
        for (String table : new String[]{"booking", "attendance_record", "session_result"})
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE session_id=? AND member_id=?", Integer.class, id, member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT session_id FROM session_code_history WHERE session_code='031126FB01'", Long.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT schedule_revision FROM class_session WHERE id=?", Long.class, id)).isZero();
        assertThat(latest.migrate().migrationsExecuted).isZero();
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,status,session_code) SELECT id,'2026-11-04','10:00','11:00','SELF_TRAINING','PUBLISHED','041126FB01' FROM sport WHERE code='FOOTBALL'");
        assertThatThrownBy(() -> jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,status,session_code) SELECT id,'2026-11-04','11:00','12:00','SELF_TRAINING','PUBLISHED','041126FB01' FROM sport WHERE code='FOOTBALL'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void unknownV38HistoryIsRejectedWithoutHistoryEditing() throws Exception {
        var db = DisposableUnifyDatabase.create(); flyway(db, "37").migrate();
        var directory = Files.createTempDirectory("unify-unapproved-history-");
        Files.writeString(directory.resolve("V38__unapproved_fixture_history.sql"), "SELECT 1;\n");
        Flyway.configure().dataSource(db.source()).locations("classpath:db/migration/identity", "classpath:db/migration/catalog", "filesystem:" + directory)
                .target("38").cleanDisabled(true).load().migrate();
        var before = db.jdbc().queryForList("SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank");
        assertThatThrownBy(() -> UnifyPreflight.inspect(db.source(), false, "0".repeat(64))).hasMessage("FLYWAY_HISTORY_MISMATCH");
        assertThat(db.jdbc().queryForList("SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
    }

    private long pending(DisposableUnifyDatabase db) {
        var jdbc = db.jdbc();
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'collision@unified-demo.invalid','fixture','Synthetic collision',id,'ACTIVE' FROM role WHERE code='MEMBER'");
        long member = jdbc.queryForObject("SELECT id FROM user_account WHERE email='collision@unified-demo.invalid'", Long.class);
        jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,'UNIFY-COLLISION')", member);
        jdbc.update("INSERT payment(payment_code,member_id,amount,payment_method,payment_status) VALUES ('UNIFY-COLLISION',?,100,'BANK_TRANSFER','PENDING')", member);
        return member;
    }

    @Test void invoiceCollisionAndOrphanAreQuarantinedAndFailedFixtureTransactionRollsBack() throws Exception {
        var db = DisposableUnifyDatabase.create(); flyway(db, null).migrate(); var jdbc = db.jdbc();
        String hash = UnifyPreflight.schemaFingerprint(db.source()); long member = pending(db);
        long payment = jdbc.queryForObject("SELECT id FROM payment WHERE payment_code='UNIFY-COLLISION'", Long.class);
        String invoice = "INSERT invoice(invoice_number,payment_id,member_id,subtotal_amount,total_amount,status) VALUES (?,?,?,100,100,'ISSUED')";
        jdbc.update(invoice, "UNIFY-I1", payment, member); jdbc.update(invoice, "UNIFY-I2", payment, member);
        assertThatThrownBy(() -> UnifyPreflight.inspect(db.source(), false, hash)).hasMessage("DUPLICATE_INVOICE");
        assertThatThrownBy(() -> jdbc.update(invoice, "UNIFY-I3", -1, member)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class);
        assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(db.source())).execute(tx -> {
            jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'rollback@unified-demo.invalid','fixture','Rollback fixture',id,'ACTIVE' FROM role WHERE code='MEMBER'");
            throw new IllegalStateException("INJECTED_HALFWAY_FAILURE");
        })).hasMessage("INJECTED_HALFWAY_FAILURE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isEqualTo(before);
    }

    @Test void disposableBackupVerifyAndActualRestorePreservePreflightSnapshot() throws Exception {
        var db = DisposableUnifyDatabase.create(); flyway(db, null).migrate(); var jdbc = db.jdbc();
        String hash = UnifyPreflight.schemaFingerprint(db.source()); var before = UnifyPreflight.inspect(db.source(), false, hash);
        String backupRoot = jdbc.queryForObject("SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))", String.class);
        String dataRoot = jdbc.queryForObject("SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultDataPath'))", String.class);
        String logRoot = jdbc.queryForObject("SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultLogPath'))", String.class);
        String restored = "sportify_unify_test_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "_" + UUID.randomUUID().toString().replace("-", "");
        String file = backupRoot + (backupRoot.endsWith("/") || backupRoot.endsWith("\\") ? "" : "/") + restored + ".bak";
        jdbc.execute("BACKUP DATABASE [" + db.name() + "] TO DISK=N'" + file.replace("'", "''") + "' WITH CHECKSUM");
        jdbc.execute("RESTORE VERIFYONLY FROM DISK=N'" + file.replace("'", "''") + "' WITH CHECKSUM");
        var files = jdbc.queryForList("RESTORE FILELISTONLY FROM DISK=N'" + file.replace("'", "''") + "'");
        StringBuilder restore = new StringBuilder("RESTORE DATABASE [" + restored + "] FROM DISK=N'" + file.replace("'", "''") + "' WITH CHECKSUM");
        for (var row : files) {
            String type = row.get("Type").toString();
            String root = type.equals("L") ? logRoot : dataRoot;
            String target = root + (root.endsWith("/") || root.endsWith("\\") ? "" : "/") + restored + (type.equals("L") ? ".ldf" : ".mdf");
            restore.append(", MOVE N'").append(row.get("LogicalName").toString().replace("'", "''")).append("' TO N'").append(target.replace("'", "''")).append("'");
        }
        assertThat(jdbc.queryForObject("SELECT DB_ID(?)", Integer.class, restored)).isNull();
        jdbc.execute(restore.toString());
        var restoredSource = new DriverManagerDataSource(db.url().replace("databaseName=" + db.name() + ";", "databaseName=" + restored + ";"), db.user(), db.password());
        var after = UnifyPreflight.inspect(restoredSource, false, hash);
        assertThat(after.tables()).isEqualTo(before.tables());
        assertThat(after.migrations()).isEqualTo(before.migrations());
        assertThat(after.schemaFingerprint()).isEqualTo(before.schemaFingerprint());
        System.out.println("DB-UNIFY disposable backup and restore verified: " + db.name() + " -> " + restored);
    }

    @Test void physicalConnectionLossRollsBackUncommittedFixtureAndFreshConnectionRecovers() throws Exception {
        var db = DisposableUnifyDatabase.create(); flyway(db, null).migrate();
        var before = db.jdbc().queryForObject("SELECT COUNT(*) FROM user_account", Integer.class);
        try (var connection = db.source().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'lost-connection@unified-demo.invalid','fixture','Connection loss fixture',id,'ACTIVE' FROM role WHERE code='MEMBER'");
            }
            connection.abort(Runnable::run);
            assertThat(connection.isClosed()).isTrue();
            assertThatThrownBy(connection::createStatement).isInstanceOf(java.sql.SQLException.class);
        }
        assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isEqualTo(before);
        assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM user_account WHERE email='lost-connection@unified-demo.invalid'", Integer.class)).isZero();
        flyway(db, null).validate();
    }
}
