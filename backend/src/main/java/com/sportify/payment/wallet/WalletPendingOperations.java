package com.sportify.payment.wallet;

import com.sportify.core.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CODEOWNER rule: a Member has at most one blocking in-flight wallet operation at a time, across top-up, wallet
 * purchase and withdrawal. The slot is one row in {@code wallet_pending_operation} keyed by member_id, so the database
 * enforces the rule even if two requests race past the service check.
 *
 * <p>Lock order for every wallet flow: the {@code member_wallet} row first ({@link #lockWallet}), then the operation's
 * own row (for example {@code wallet_topup}), then this slot, then the ledger (which re-locks the wallet row already
 * held). Callers must hold the wallet lock before calling {@link #claim}, {@link #release} or {@link #requireNone}.
 * Synchronous operations that complete in one transaction (a wallet purchase) call {@link #requireNone} instead of
 * claiming a slot.</p>
 */
@Service
@RequiredArgsConstructor
public class WalletPendingOperations {

    public static final String CODE_IN_FLIGHT = "WALLET_OPERATION_IN_FLIGHT";

    public enum Type { TOPUP, PURCHASE, WITHDRAWAL }

    public record Pending(Type type, long operationId) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** Takes the wallet row lock. Must be the first lock taken by any wallet flow for this Member. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long lockWallet(long memberId) {
        return jdbc.queryForObject("SELECT id FROM dbo.member_wallet WITH (UPDLOCK, ROWLOCK) WHERE member_id=?", Long.class, memberId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Pending> current(long memberId) {
        List<Pending> found = jdbc.query("SELECT operation_type, operation_id FROM dbo.wallet_pending_operation WHERE member_id=?",
                (rs, row) -> new Pending(Type.valueOf(rs.getString("operation_type")), rs.getLong("operation_id")), memberId);
        return found.stream().findFirst();
    }

    /** Refuses when any operation is in flight for this Member. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNone(long memberId) {
        current(memberId).ifPresent(pending -> { throw inFlight(pending); });
    }

    /** Reserves the Member's single slot for this operation; a second claim fails with {@value #CODE_IN_FLIGHT}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(long memberId, Type type, long operationId) {
        requireNone(memberId);
        try {
            jdbc.update("INSERT INTO dbo.wallet_pending_operation (member_id, operation_type, operation_id, created_at) VALUES (?,?,?,?)",
                    memberId, type.name(), operationId, Timestamp.valueOf(LocalDateTime.now(clock)));
        } catch (DuplicateKeyException race) {
            throw inFlight(current(memberId).orElse(null));
        }
    }

    /** Frees the slot held by this exact operation; a no-op when the operation does not hold it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long memberId, Type type, long operationId) {
        jdbc.update("DELETE FROM dbo.wallet_pending_operation WHERE member_id=? AND operation_type=? AND operation_id=?",
                memberId, type.name(), operationId);
    }

    private static ConflictException inFlight(Pending pending) {
        String what = pending == null ? "another wallet request" : switch (pending.type()) {
            case TOPUP -> "a pending top-up";
            case PURCHASE -> "a pending wallet payment";
            case WITHDRAWAL -> "a pending withdrawal";
        };
        return new ConflictException("You already have " + what + ". Wait for it to be reviewed or cancel it first.", CODE_IN_FLIGHT);
    }
}
