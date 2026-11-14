package com.sportify.payment;

import com.sportify.support.DisposableUnifyDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs exclusively on disposable SQL Server fixtures; never opens the operational sportify DB. */
@Tag("integration")
class WalletErasureMigrationIntegrationTest {
    private static final List<String> RETIRED_TABLES = List.of(
            "member_wallet", "wallet_ledger_entry", "wallet_topup", "wallet_pending_operation",
            "payment_wallet_settlement", "member_payout_account", "wallet_withdrawal");

    private static Flyway migrator(DisposableUnifyDatabase db, String target) {
        var config = Flyway.configure().dataSource(db.source())
                .locations("classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/compat-vietqr")
                .cleanDisabled(true).validateOnMigrate(true).outOfOrder(false);
        if (target != null) config.target(target);
        return config.load();
    }

    private static long member(JdbcTemplate jdbc) {
        Long role = jdbc.queryForObject("SELECT id FROM dbo.role WHERE code='MEMBER'", Long.class);
        String email = UUID.randomUUID() + "@example.invalid";
        Long id = jdbc.queryForObject("""
                INSERT INTO dbo.user_account (email,password_hash,full_name,role_id,status)
                OUTPUT INSERTED.id VALUES (?, 'fixture-hash', 'Wallet cleanup fixture', ?, 'ACTIVE')
                """, Long.class, email, role);
        jdbc.update("INSERT INTO dbo.member_profile (user_id,member_code) VALUES (?,?)",
                id, "WC-" + UUID.randomUUID().toString().substring(0, 12));
        return id;
    }

    private static long payment(JdbcTemplate jdbc, long member, String method, String code) {
        // payment has enabled triggers; SQL Server rejects OUTPUT without INTO on this table.
        jdbc.update("""
                INSERT INTO dbo.payment (payment_code,member_id,amount,payment_method,payment_status)
                VALUES (?, ?, 20000, ?, 'PENDING')
                """, code, member, method);
        return jdbc.queryForObject(
                "SELECT id FROM dbo.payment WHERE payment_code = ?", Long.class, code);
    }

    private static void assertNoWallet(JdbcTemplate jdbc) {
        for (String table : RETIRED_TABLES) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name=?",
                    Integer.class, table)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.payment WHERE payment_method='WALLET'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.notification WHERE event_key LIKE 'WALLET[_]%'", Integer.class)).isZero();
    }

    @Test
    void freshDatabaseHasNoWalletTablesAndRejectsWalletPaymentMethod() throws Exception {
        var db = DisposableUnifyDatabase.create();
        var flyway = migrator(db, null);
        flyway.migrate();
        flyway.validate();
        JdbcTemplate jdbc = db.jdbc();
        assertNoWallet(jdbc);
        long member = member(jdbc);
        assertThatThrownBy(() -> payment(jdbc, member, "WALLET", "W-REJECT"))
                .isInstanceOf(DataAccessException.class);
        long bank = payment(jdbc, member, "BANK_TRANSFER", "W-KEEP");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE id=?", Integer.class, bank)).isEqualTo(1);
    }

    @Test
    void upgradingV62ErasesHistoricalWalletRowsButPreservesNonWalletPaymentsAndMember() throws Exception {
        var db = DisposableUnifyDatabase.create();
        migrator(db, "62").migrate();
        JdbcTemplate jdbc = db.jdbc();
        long member = member(jdbc);
        long retiredPayment = payment(jdbc, member, "WALLET", "W-REMOVE");
        long preservedPayment = payment(jdbc, member, "CASH", "C-PRESERVE");
        long preservedBankPayment = payment(jdbc, member, "BANK_TRANSFER", "B-PRESERVE");
        jdbc.update("""
                INSERT INTO dbo.invoice
                    (invoice_number,payment_id,member_id,subtotal_amount,total_amount,status)
                VALUES ('W-OLD-INVOICE',?,?,20000,20000,'ISSUED')
                """, retiredPayment, member);
        long wallet = jdbc.queryForObject("""
                INSERT INTO dbo.member_wallet(member_id,created_at,updated_at)
                OUTPUT INSERTED.id VALUES (?,SYSDATETIME(),SYSDATETIME())
                """, Long.class, member);
        // This append-only table also has a trigger; INSERT must not use OUTPUT without INTO.
        jdbc.update("""
                INSERT INTO dbo.wallet_ledger_entry
                    (wallet_id,operation_key,entry_type,pool,amount,available_after,reserved_after,
                     source_type,source_id,description,created_at)
                VALUES (?,'W-OP-1','TOPUP_CREDIT','SIMULATED',20000,20000,0,
                           'TOPUP',1,'Old Wallet event',SYSDATETIME())
                """, wallet);
        long ledger = jdbc.queryForObject(
                "SELECT id FROM dbo.wallet_ledger_entry WHERE operation_key='W-OP-1'", Long.class);
        jdbc.update("""
                INSERT INTO dbo.payment_wallet_settlement(payment_id,ledger_entry_id,created_at)
                VALUES (?,?,SYSDATETIME())
                """, retiredPayment, ledger);
        long topup = jdbc.queryForObject("""
                INSERT INTO dbo.wallet_topup (member_id,mode,amount,status,created_at,updated_at)
                OUTPUT INSERTED.id VALUES (?,'SIMULATED',20000,'PENDING',SYSDATETIME(),SYSDATETIME())
                """, Long.class, member);
        jdbc.update("""
                INSERT INTO dbo.wallet_pending_operation(member_id,operation_type,operation_id,created_at)
                VALUES (?,'TOPUP',?,SYSDATETIME())
                """, member, topup);
        jdbc.update("""
                INSERT INTO dbo.wallet_withdrawal(member_id,pool,amount,status,created_at,updated_at)
                VALUES (?,'SIMULATED',20000,'PENDING',SYSDATETIME(),SYSDATETIME())
                """, member);
        jdbc.update("""
                INSERT INTO dbo.member_payout_account
                    (member_id,bank_bin,bank_name,account_number,account_holder,updated_at)
                VALUES (?,'970436','Demo bank','1234567890','Demo account',SYSDATETIME())
                """, member);
        jdbc.update("""
                INSERT INTO dbo.notification(user_id,title,content,type,event_key)
                VALUES (?,'Old Wallet request','Wallet transaction','RECEPTION_TASK','WALLET_TOPUP:1')
                """, member);
        jdbc.update("""
                INSERT INTO dbo.activity_log(action,entity_type,entity_id,summary)
                VALUES ('WALLET_PAYMENT','PAYMENT',?,'Old Wallet history')
                """, retiredPayment);

        var current = migrator(db, null);
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        current.validate();
        assertNoWallet(jdbc);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE id=?", Integer.class, retiredPayment))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.invoice WHERE payment_id=?", Integer.class, retiredPayment))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.activity_log WHERE entity_type='PAYMENT' AND entity_id=?",
                Integer.class, retiredPayment)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE id=?", Integer.class, preservedPayment))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE id=?", Integer.class, preservedBankPayment))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.member_profile WHERE user_id=?", Integer.class, member))
                .isEqualTo(1);
        assertThat(current.migrate().migrationsExecuted).isZero();
    }
}
