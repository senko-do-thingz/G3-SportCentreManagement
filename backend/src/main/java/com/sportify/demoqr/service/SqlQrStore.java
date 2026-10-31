package com.sportify.demoqr.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** SQL Server owns time, uniqueness and serialization; there is no process-local session state. */
@Repository
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class SqlQrStore {
    public record Session(UUID id, long paymentId, long ownerId, BigDecimal amount, String currency,
                          DemoQrService.Bank bank, Instant createdAt, Instant expiresAt,
                          DemoQrService.State state, long version) { }

    public record SimulatedTx(long id, String transactionCode, Instant settledAt) { }

    public record SettlementInfo(String settlementType, String settlementProvider, Long simulatedTransactionId,
                                 String transactionCode, Instant settledAt, Long invoiceId, String invoiceNumber) { }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate budgetTransaction;

    public SqlQrStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setTimeout(10);
        budgetTransaction = new TransactionTemplate(manager);
        budgetTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        budgetTransaction.setTimeout(10);
    }

    public <T> T transaction(Supplier<T> work) { return transaction.execute(ignored -> work.get()); }

    /** Failed lookups spend budget too. Committed independently so denial cannot roll it back. */
    public boolean spend(long owner, boolean create) {
        return Boolean.TRUE.equals(budgetTransaction.execute(ignored -> {
            var accounts = jdbc.queryForList("""
                    SELECT u.id FROM dbo.user_account u WITH (UPDLOCK,HOLDLOCK)
                    JOIN dbo.role r ON r.id=u.role_id JOIN dbo.member_profile m ON m.user_id=u.id
                    WHERE u.id=? AND u.status='ACTIVE' AND r.code='MEMBER'
                    """, Long.class, owner);
            if (accounts.isEmpty()) throw DemoQrException.unavailable();
            jdbc.update("""
                    IF NOT EXISTS (SELECT 1 FROM dbo.simulated_qr_rate_limit WHERE owner_id=?)
                    INSERT dbo.simulated_qr_rate_limit VALUES (?,SYSUTCDATETIME(),0,SYSUTCDATETIME(),0)
                    """, owner, owner);
            String prefix = create ? "create" : "access";
            int max = create ? 5 : 60;
            jdbc.update("UPDATE dbo.simulated_qr_rate_limit SET " + prefix + "_count=0," + prefix
                    + "_window_utc=SYSUTCDATETIME() WHERE owner_id=? AND " + prefix
                    + "_window_utc<=DATEADD(second,-60,SYSUTCDATETIME())", owner);
            return jdbc.update("UPDATE dbo.simulated_qr_rate_limit SET " + prefix + "_count=" + prefix
                    + "_count+1 WHERE owner_id=? AND " + prefix + "_count<?", owner, max) == 1;
        }));
    }

    public Instant now() { return jdbc.queryForObject("SELECT SYSUTCDATETIME()", (row, index) -> row.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC)); }

    public Session findId(UUID id, long owner) {
        return jdbc.query("SELECT * FROM dbo.simulated_qr_session WHERE id=? AND owner_id=?",
                SqlQrStore::map, id.toString(), owner).stream().findFirst().orElseThrow(DemoQrException::unavailable);
    }

    public Session findVerifier(String verifier, long owner) {
        return jdbc.query("SELECT * FROM dbo.simulated_qr_session WHERE token_verifier=? AND owner_id=?",
                SqlQrStore::map, verifier, owner).stream().findFirst().orElseThrow(DemoQrException::unavailable);
    }

    public Session lock(UUID id, long owner) {
        return jdbc.query("SELECT * FROM dbo.simulated_qr_session WITH (UPDLOCK,HOLDLOCK) WHERE id=? AND owner_id=?",
                SqlQrStore::map, id.toString(), owner).stream().findFirst().orElseThrow(DemoQrException::unavailable);
    }

    public boolean requestExists(long owner, UUID request) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.simulated_qr_session WHERE owner_id=? AND request_id=?",
                Integer.class, owner, request.toString()) > 0;
    }

    public void replace(long paymentId, long owner) {
        jdbc.update("""
                UPDATE dbo.simulated_qr_session SET state=CASE WHEN expires_at_utc<=SYSUTCDATETIME()
                THEN 'EXPIRED' ELSE 'CANCELED' END, active=0, version=version+1,state_changed_at_utc=SYSUTCDATETIME()
                WHERE payment_id=? AND owner_id=? AND active=1
                """, paymentId, owner);
    }

    public UUID insert(long paymentId, long owner, BigDecimal amount, DemoQrService.Bank bank,
                       String verifier, UUID request, long seconds) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                DECLARE @now DATETIME2(7)=SYSUTCDATETIME();
                INSERT dbo.simulated_qr_session(id,payment_id,owner_id,bank_code,amount,currency,token_verifier,
                request_id,created_at_utc,expires_at_utc,state,state_changed_at_utc,active,version)
                VALUES (?,?,?,?,?,'VND',?,?,@now,DATEADD(second,?,@now),'PENDING',@now,1,0)
                """, id.toString(), paymentId, owner, bank.name(), amount, verifier, request.toString(), seconds);
        return id;
    }

    public Session expire(Session session) {
        jdbc.update("""
                UPDATE dbo.simulated_qr_session SET state='EXPIRED',active=0,version=version+1,
                state_changed_at_utc=SYSUTCDATETIME() WHERE id=? AND active=1 AND expires_at_utc<=SYSUTCDATETIME()
                """, session.id().toString());
        return findId(session.id(), session.ownerId());
    }

    public Session cancel(Session session) {
        jdbc.update("""
                UPDATE dbo.simulated_qr_session SET state='CANCELED',active=0,version=version+1,
                state_changed_at_utc=SYSUTCDATETIME() WHERE id=? AND active=1
                """, session.id().toString());
        return findId(session.id(), session.ownerId());
    }

    public Session authorize(Session session) {
        jdbc.update("""
                UPDATE dbo.simulated_qr_session SET state='AUTHORIZED_PENDING_SETTLEMENT',version=version+1,
                state_changed_at_utc=SYSUTCDATETIME() WHERE id=? AND state='PENDING' AND version=?
                AND expires_at_utc>SYSUTCDATETIME()
                """, session.id().toString(), session.version());
        return expire(findId(session.id(), session.ownerId()));
    }

    public boolean isBeforeDeadline(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.simulated_qr_session WHERE id=? AND active=1 AND expires_at_utc>SYSUTCDATETIME()",
                Integer.class, id.toString());
        return count != null && count > 0;
    }

    public SimulatedTx insertSettlement(Session session) {
        String txCode = "SIM-QR-" + session.paymentId() + "-"
                + session.id().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        return jdbc.queryForObject("""
                DECLARE @now DATETIME2(7) = SYSUTCDATETIME();
                INSERT INTO dbo.simulated_payment_transaction(
                    transaction_code, payment_id, session_id, member_id, amount, currency,
                    bank_code, settlement_type, provider, status, settled_at_utc, created_at_utc
                )
                OUTPUT INSERTED.id, INSERTED.transaction_code, INSERTED.settled_at_utc
                VALUES (?, ?, ?, ?, ?, 'VND', ?, 'SIMULATED', 'SPORTIFY_SIMULATOR', 'SUCCEEDED', @now, @now)
                """,
                (rs, i) -> new SimulatedTx(
                        rs.getLong("id"),
                        rs.getString("transaction_code"),
                        rs.getObject("settled_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                txCode, session.paymentId(), session.id().toString(), session.ownerId(),
                session.amount(), session.bank().name());
    }

    public Session settle(Session session) {
        int updated = jdbc.update("""
                UPDATE dbo.simulated_qr_session
                SET state='SETTLED', active=0, version=version+1, state_changed_at_utc=SYSUTCDATETIME()
                WHERE id=? AND state IN ('PENDING','AUTHORIZED_PENDING_SETTLEMENT') AND version=?
                """, session.id().toString(), session.version());
        if (updated != 1) {
            throw new IllegalStateException("Failed to transition session to SETTLED: " + session.id());
        }
        return findId(session.id(), session.ownerId());
    }

    public java.util.Optional<SettlementInfo> findSettlement(UUID sessionId, long paymentId) {
        var rows = jdbc.query("""
                SELECT tx.settlement_type, tx.provider, tx.id AS tx_id, tx.transaction_code, tx.settled_at_utc,
                       inv.id AS invoice_id, inv.invoice_number
                FROM dbo.simulated_payment_transaction tx
                OUTER APPLY (
                    SELECT TOP 1 i.id, i.invoice_number
                    FROM dbo.invoice i
                    WHERE i.payment_id = tx.payment_id
                    ORDER BY i.id ASC
                ) inv
                WHERE tx.session_id = ? AND tx.payment_id = ?
                """,
                (rs, i) -> {
                    long invId = rs.getLong("invoice_id");
                    Long invoiceId = rs.wasNull() ? null : invId;
                    return new SettlementInfo(
                            rs.getString("settlement_type"),
                            rs.getString("provider"),
                            rs.getLong("tx_id"),
                            rs.getString("transaction_code"),
                            rs.getObject("settled_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            invoiceId,
                            rs.getString("invoice_number"));
                },
                sessionId.toString(), paymentId);
        return rows.stream().findFirst();
    }

    private static Session map(ResultSet row, int index) throws SQLException {
        return new Session(UUID.fromString(row.getString("id")),row.getLong("payment_id"),row.getLong("owner_id"),
                row.getBigDecimal("amount"),row.getString("currency"),DemoQrService.Bank.valueOf(row.getString("bank_code")),
                row.getObject("created_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),row.getObject("expires_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                DemoQrService.State.valueOf(row.getString("state")),row.getLong("version"));
    }
}
