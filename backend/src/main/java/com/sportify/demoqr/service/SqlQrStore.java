package com.sportify.demoqr.service;

import com.sportify.demoqr.config.TokenQrEnabledCondition;
import org.springframework.context.annotation.Conditional;
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
@Conditional(TokenQrEnabledCondition.class)
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

    /** Marks this new owner-verified flow without reclassifying historical simulation rows. */
    public void markOperationalSettlement(long paymentId, long simulatedTransactionId) {
        jdbc.update("INSERT dbo.operational_qr_settlement(payment_id, simulated_transaction_id) VALUES (?, ?)",
                paymentId, simulatedTransactionId);
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

    /** Read-only settlement proof, separate from current benefit availability. Never reactivate on replay. */
    public void requireUnifiedReplay(long paymentId, long ownerId) {
        Integer valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.payment p
                JOIN dbo.simulated_payment_transaction tx ON tx.id=p.simulated_transaction_id
                JOIN dbo.simulated_qr_session s ON s.id=tx.session_id
                JOIN dbo.invoice i ON i.payment_id=p.id
                WHERE p.id=? AND p.member_id=? AND p.payment_status='SUCCESS'
                  AND p.payment_method='BANK_TRANSFER' AND p.amount>0
                  AND p.settlement_type='SIMULATED' AND p.settlement_provider='SPORTIFY_SIMULATOR'
                  AND tx.payment_id=p.id AND tx.member_id=p.member_id AND tx.amount=p.amount
                  AND tx.currency='VND' AND tx.status='SUCCEEDED' AND tx.settled_at_utc IS NOT NULL
                  AND tx.settlement_type=p.settlement_type AND tx.provider=p.settlement_provider
                  AND tx.transaction_code=p.reference_code
                  AND s.payment_id=p.id AND s.owner_id=p.member_id AND s.amount=p.amount
                  AND s.currency=tx.currency AND s.bank_code=tx.bank_code AND s.state='SETTLED' AND s.active=0
                  AND i.member_id=p.member_id AND i.status='PAID' AND i.total_amount=p.amount
                  AND LEN(LTRIM(RTRIM(i.invoice_number)))>0
                  AND (SELECT COUNT(*) FROM dbo.invoice WHERE payment_id=p.id)=1
                  AND (SELECT COUNT(*) FROM dbo.member_card WHERE payment_id=p.id)
                    + (SELECT COUNT(*) FROM dbo.sport_package_registration WHERE payment_id=p.id)=1
                  AND (SELECT COUNT(*) FROM dbo.invoice_line WHERE invoice_id=i.id)=1
                  AND EXISTS (SELECT 1 FROM dbo.invoice_line l WHERE l.invoice_id=i.id AND l.quantity=1
                    AND l.line_total=i.subtotal_amount AND l.unit_price=i.subtotal_amount
                    AND ((l.item_type='MEMBERSHIP_CARD' AND EXISTS (SELECT 1 FROM dbo.member_card c
                        WHERE c.id=l.item_reference_id AND c.payment_id=p.id AND c.member_id=p.member_id
                          AND (c.status='ACTIVE'
                            OR (c.status='EXPIRED' AND c.end_date>=c.start_date
                                AND CONVERT(date,c.updated_at)>c.end_date)
                            OR (c.status='REPLACED' AND EXISTS (SELECT 1 FROM dbo.member_card successor
                                WHERE successor.replaces_card_id=c.id AND successor.id<>c.id
                                  AND successor.member_id=c.member_id AND successor.upgrade_request=1
                                  AND successor.end_date=c.end_date
                                  AND successor.status IN ('ACTIVE','EXPIRED','REPLACED')
                                  AND ((successor.payment_id IS NULL AND successor.price_paid=0)
                                    OR EXISTS (SELECT 1 FROM dbo.payment replacement_payment
                                      JOIN dbo.invoice replacement_invoice ON replacement_invoice.payment_id=replacement_payment.id
                                      WHERE replacement_payment.id=successor.payment_id
                                        AND replacement_payment.member_id=c.member_id
                                        AND replacement_payment.payment_status='SUCCESS'
                                        AND replacement_payment.amount=successor.price_paid
                                        AND replacement_invoice.member_id=c.member_id AND replacement_invoice.status='PAID'
                                        AND replacement_invoice.total_amount=replacement_payment.amount)))))))
                      OR (l.item_type='SPORT_PACKAGE' AND EXISTS (SELECT 1 FROM dbo.sport_package_registration r
                        WHERE r.id=l.item_reference_id AND r.payment_id=p.id AND r.member_id=p.member_id
                          AND r.activated_at IS NOT NULL
                          AND (r.status='ACTIVE' OR (r.status='EXPIRED' AND r.end_date>=r.start_date
                            AND CONVERT(date,r.updated_at)>r.end_date AND r.updated_at>=r.activated_at))))))
                  AND (SELECT COUNT(*) FROM dbo.simulated_payment_transaction WHERE payment_id=p.id)=1
                  AND (SELECT COUNT(*) FROM dbo.activity_log WHERE action='PAYMENT_CONFIRMED'
                       AND entity_type='PAYMENT' AND entity_id=p.id)=1
                  AND EXISTS (SELECT 1 FROM dbo.activity_log a
                       CROSS APPLY (SELECT CASE WHEN ISJSON(a.details)=1 THEN a.details ELSE '{}' END AS body) j
                       WHERE a.action='PAYMENT_CONFIRMED' AND a.entity_type='PAYMENT' AND a.entity_id=p.id
                         AND a.actor_user_id=p.member_id
                         AND JSON_VALUE(j.body,'$.method')=p.payment_method
                         AND JSON_VALUE(j.body,'$.settlementType')=p.settlement_type
                         AND JSON_VALUE(j.body,'$.settlementProvider')=p.settlement_provider
                         AND TRY_CONVERT(BIGINT,JSON_VALUE(j.body,'$.simulatedTransactionId'))=tx.id
                         AND JSON_VALUE(j.body,'$.referenceCode')=tx.transaction_code
                         AND TRY_CONVERT(DECIMAL(38,10),JSON_VALUE(j.body,'$.amount'))=p.amount
                         AND JSON_VALUE(j.body,'$.invoiceNumber')=i.invoice_number
                         AND EXISTS (SELECT 1 FROM dbo.invoice_line l WHERE l.invoice_id=i.id
                           AND l.item_type=JSON_VALUE(j.body,'$.itemType')
                           AND l.item_reference_id=TRY_CONVERT(BIGINT,JSON_VALUE(j.body,'$.itemReferenceId'))))
                """, Integer.class, paymentId, ownerId);
        if (!Integer.valueOf(1).equals(valid)) throw DemoQrException.unavailable();
    }

    public java.util.Optional<Session> findLatestByPayment(long paymentId) {
        var rows = jdbc.query(
                "SELECT TOP 1 id, payment_id, owner_id, amount, currency, bank_code, created_at_utc, expires_at_utc, state, version " +
                "FROM dbo.simulated_qr_session WHERE payment_id = ? ORDER BY created_at_utc DESC",
                SqlQrStore::map,
                paymentId);
        return rows.stream().findFirst();
    }

    private static Session map(ResultSet row, int index) throws SQLException {
        return new Session(UUID.fromString(row.getString("id")),row.getLong("payment_id"),row.getLong("owner_id"),
                row.getBigDecimal("amount"),row.getString("currency"),DemoQrService.Bank.valueOf(row.getString("bank_code")),
                row.getObject("created_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),row.getObject("expires_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                DemoQrService.State.valueOf(row.getString("state")),row.getLong("version"));
    }
}
