package com.sportify.payment.wallet;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MW-03 internal wallet. The backend owns every balance: a change is applied only here, under the wallet row lock,
 * and always writes exactly one append-only ledger row with a unique operation key. Replaying an operation key returns
 * the original entry instead of moving money again. There is no endpoint for a manual or arbitrary credit.
 * ACTUAL money comes only from staff-verified receipts; SIMULATED money is demo-only and never pays a real purchase.
 */
@Service
@RequiredArgsConstructor
public class WalletLedgerService {

    public static final BigDecimal MAX_OPERATION_AMOUNT = new BigDecimal("100000000");
    static final String CODE_INSUFFICIENT = "WALLET_INSUFFICIENT_FUNDS";
    static final String CODE_REPLAY_MISMATCH = "WALLET_OPERATION_REPLAY_MISMATCH";
    static final String CODE_COMPENSATION_INVALID = "WALLET_COMPENSATION_INVALID";
    /** sourceType of a COMPENSATION_CREDIT; its sourceId is the compensated PURCHASE_DEBIT ledger entry id. */
    public static final String COMPENSATION_SOURCE = "WALLET_LEDGER_ENTRY";

    public enum Pool { ACTUAL, SIMULATED }

    public enum EntryType {
        /** available += amount */ TOPUP_CREDIT,
        /** available -= amount (ACTUAL pool only) */ PURCHASE_DEBIT,
        /** available += amount, tied to one earlier debit */ COMPENSATION_CREDIT,
        /** available -= amount, reserved += amount */ WITHDRAWAL_RESERVE,
        /** reserved -= amount, available += amount */ WITHDRAWAL_RELEASE,
        /** reserved -= amount */ WITHDRAWAL_PAYOUT
    }

    public record Operation(long memberId, EntryType type, Pool pool, BigDecimal amount, String operationKey,
                            String sourceType, long sourceId, String description, Long actorId) {}

    public record Balance(long walletId, long memberId, BigDecimal actualAvailable, BigDecimal actualReserved,
                          BigDecimal simulatedAvailable, BigDecimal simulatedReserved, LocalDateTime updatedAt) {
        BigDecimal available(Pool pool) { return pool == Pool.ACTUAL ? actualAvailable : simulatedAvailable; }
        BigDecimal reserved(Pool pool) { return pool == Pool.ACTUAL ? actualReserved : simulatedReserved; }
    }

    public record Entry(long id, String entryType, String pool, BigDecimal amount, String direction,
                        BigDecimal availableAfter, BigDecimal reservedAfter, String sourceType, long sourceId,
                        String description, LocalDateTime createdAt) {}

    /** The entry and whether it already existed (a replayed operation key moves no money). */
    public record Applied(Entry entry, boolean replay) {}

    private static final RowMapper<Balance> BALANCE = (rs, row) -> new Balance(rs.getLong("id"), rs.getLong("member_id"),
            rs.getBigDecimal("actual_available"), rs.getBigDecimal("actual_reserved"),
            rs.getBigDecimal("simulated_available"), rs.getBigDecimal("simulated_reserved"),
            rs.getTimestamp("updated_at").toLocalDateTime());

    private static final RowMapper<Entry> ENTRY = (rs, row) -> new Entry(rs.getLong("id"), rs.getString("entry_type"),
            rs.getString("pool"), rs.getBigDecimal("amount"), direction(EntryType.valueOf(rs.getString("entry_type"))),
            rs.getBigDecimal("available_after"), rs.getBigDecimal("reserved_after"), rs.getString("source_type"),
            rs.getLong("source_id"), rs.getString("description"), rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** Creates the Member's zero wallet once; concurrent first calls serialize on the key range lock. */
    @Transactional
    public Balance ensureWallet(long memberId) {
        Integer member = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.member_profile m JOIN dbo.user_account u ON u.id=m.user_id"
                + " JOIN dbo.role r ON r.id=u.role_id WHERE m.user_id=? AND r.code='MEMBER'", Integer.class, memberId);
        if (member == null || member == 0) {
            throw new ResourceNotFoundException("Member profile not found");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        jdbc.update("INSERT INTO dbo.member_wallet (member_id, created_at, updated_at) SELECT ?, ?, ?"
                + " WHERE NOT EXISTS (SELECT 1 FROM dbo.member_wallet WITH (UPDLOCK, HOLDLOCK) WHERE member_id=?)",
                memberId, now, now, memberId);
        return jdbc.queryForObject("SELECT * FROM dbo.member_wallet WHERE member_id=?", BALANCE, memberId);
    }

    @Transactional
    public Balance balance(long memberId) {
        return ensureWallet(memberId);
    }

    @Transactional(readOnly = true)
    public List<Entry> entries(long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("Page must be 0 or more and size between 1 and 100");
        }
        return jdbc.query("SELECT e.* FROM dbo.wallet_ledger_entry e JOIN dbo.member_wallet w ON w.id=e.wallet_id"
                + " WHERE w.member_id=? ORDER BY e.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", ENTRY, memberId, page * size, size);
    }

    @Transactional(readOnly = true)
    public long entryCount(long memberId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.wallet_ledger_entry e JOIN dbo.member_wallet w"
                + " ON w.id=e.wallet_id WHERE w.member_id=?", Long.class, memberId);
        return count == null ? 0 : count;
    }

    /**
     * Applies one operation exactly once. Lock order: the wallet row first, then the ledger key lookup, so two
     * requests for the same wallet serialize and a replay always sees the committed first entry.
     */
    @Transactional
    public Applied apply(Operation op) {
        validate(op);
        long walletId = ensureWallet(op.memberId()).walletId();
        Balance current = jdbc.queryForObject("SELECT * FROM dbo.member_wallet WITH (UPDLOCK, ROWLOCK) WHERE id=?", BALANCE, walletId);
        List<Entry> existing = jdbc.query("SELECT * FROM dbo.wallet_ledger_entry WHERE operation_key=?", ENTRY, op.operationKey());
        if (!existing.isEmpty()) {
            Entry entry = existing.getFirst();
            Long owner = jdbc.queryForObject("SELECT wallet_id FROM dbo.wallet_ledger_entry WHERE id=?", Long.class, entry.id());
            if (!Long.valueOf(walletId).equals(owner) || !entry.entryType().equals(op.type().name())
                    || !entry.pool().equals(op.pool().name()) || entry.amount().compareTo(op.amount()) != 0) {
                throw new ConflictException("This wallet operation was already recorded with different details", CODE_REPLAY_MISMATCH);
            }
            return new Applied(entry, true);
        }

        if (op.type() == EntryType.COMPENSATION_CREDIT) {
            requireCompensable(walletId, op);
        }

        BigDecimal available = current.available(op.pool());
        BigDecimal reserved = current.reserved(op.pool());
        BigDecimal amount = op.amount();
        switch (op.type()) {
            case TOPUP_CREDIT, COMPENSATION_CREDIT -> available = available.add(amount);
            case PURCHASE_DEBIT -> available = available.subtract(amount);
            case WITHDRAWAL_RESERVE -> { available = available.subtract(amount); reserved = reserved.add(amount); }
            case WITHDRAWAL_RELEASE -> { reserved = reserved.subtract(amount); available = available.add(amount); }
            case WITHDRAWAL_PAYOUT -> reserved = reserved.subtract(amount);
        }
        if (available.signum() < 0) {
            throw new BusinessRuleException("Not enough " + (op.pool() == Pool.ACTUAL ? "" : "demo ")
                    + "wallet balance for this operation", CODE_INSUFFICIENT);
        }
        if (reserved.signum() < 0) {
            throw new IllegalStateException("Reserved wallet balance would become negative for operation " + op.operationKey());
        }

        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        String prefix = op.pool() == Pool.ACTUAL ? "actual" : "simulated";
        jdbc.update("UPDATE dbo.member_wallet SET " + prefix + "_available=?, " + prefix + "_reserved=?, updated_at=?,"
                + " version=version+1 WHERE id=?", available, reserved, now, walletId);
        Long id = jdbc.queryForObject("INSERT INTO dbo.wallet_ledger_entry (wallet_id, operation_key, entry_type, pool, amount,"
                + " available_after, reserved_after, source_type, source_id, description, created_by, created_at)"
                + " OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", Long.class,
                walletId, op.operationKey(), op.type().name(), op.pool().name(), amount, available, reserved,
                op.sourceType(), op.sourceId(), op.description(), op.actorId(), now);
        return new Applied(jdbc.queryForObject("SELECT * FROM dbo.wallet_ledger_entry WHERE id=?", ENTRY, id), false);
    }

    /**
     * A compensation credit returns money from one earlier PURCHASE_DEBIT of the same wallet and pool, named by
     * sourceType {@value #COMPENSATION_SOURCE} and sourceId = that debit's ledger entry id. All compensations for the
     * debit together can never exceed it. Runs under the wallet row lock, so concurrent compensations serialize.
     */
    private void requireCompensable(long walletId, Operation op) {
        if (!COMPENSATION_SOURCE.equals(op.sourceType())) {
            throw new BusinessRuleException("A compensation credit must name the wallet debit it compensates", CODE_COMPENSATION_INVALID);
        }
        List<java.util.Map<String, Object>> debit = jdbc.queryForList("SELECT amount FROM dbo.wallet_ledger_entry"
                + " WHERE id=? AND wallet_id=? AND entry_type='PURCHASE_DEBIT' AND pool=?", op.sourceId(), walletId, op.pool().name());
        if (debit.isEmpty()) {
            throw new BusinessRuleException("A compensation credit must refer to an earlier wallet payment of this wallet",
                    CODE_COMPENSATION_INVALID);
        }
        BigDecimal debited = (BigDecimal) debit.getFirst().get("amount");
        BigDecimal compensated = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM dbo.wallet_ledger_entry"
                + " WHERE wallet_id=? AND entry_type='COMPENSATION_CREDIT' AND source_type=? AND source_id=?",
                BigDecimal.class, walletId, COMPENSATION_SOURCE, op.sourceId());
        if (compensated.add(op.amount()).compareTo(debited) > 0) {
            throw new BusinessRuleException("Compensation would exceed the original wallet payment of "
                    + debited.toPlainString() + " VND", CODE_COMPENSATION_INVALID);
        }
    }

    /** + for money that becomes spendable, - for money that leaves the available balance, 0 for release-to-payout moves. */
    static String direction(EntryType type) {
        return switch (type) {
            case TOPUP_CREDIT, COMPENSATION_CREDIT, WITHDRAWAL_RELEASE -> "CREDIT";
            case PURCHASE_DEBIT, WITHDRAWAL_RESERVE -> "DEBIT";
            case WITHDRAWAL_PAYOUT -> "PAYOUT";
        };
    }

    private static void validate(Operation op) {
        if (op == null || op.type() == null || op.pool() == null || op.operationKey() == null || op.operationKey().isBlank()
                || op.operationKey().length() > 80 || op.sourceType() == null || op.description() == null) {
            throw new IllegalArgumentException("Incomplete wallet operation");
        }
        BigDecimal amount = op.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero", "WALLET_INVALID_AMOUNT");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("Amount must be a whole number of VND", "WALLET_INVALID_AMOUNT");
        }
        if (amount.compareTo(MAX_OPERATION_AMOUNT) > 0) {
            throw new BusinessRuleException("Amount must not exceed " + MAX_OPERATION_AMOUNT.toPlainString() + " VND",
                    "WALLET_INVALID_AMOUNT");
        }
        if (op.type() == EntryType.PURCHASE_DEBIT && op.pool() != Pool.ACTUAL) {
            throw new BusinessRuleException("Demo wallet funds cannot pay for a real purchase", "WALLET_SIMULATED_NOT_SPENDABLE");
        }
    }
}
