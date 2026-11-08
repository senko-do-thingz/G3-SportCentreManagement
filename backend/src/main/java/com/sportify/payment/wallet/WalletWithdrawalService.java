package com.sportify.payment.wallet;

import com.sportify.catalog.service.OperationalMemberPolicy;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.payment.service.VietQrBankDirectory;
import com.sportify.payment.wallet.WalletLedgerService.EntryType;
import com.sportify.payment.wallet.WalletLedgerService.Operation;
import com.sportify.payment.wallet.WalletLedgerService.Pool;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * MW-06 withdrawals. Creating a request reserves the amount in the ledger at once (available -> reserved), so the same
 * money can never be spent or requested twice. A Receptionist approves or rejects; APPROVED moves no money and is not
 * a transfer. A separate completion step records the payout reference (real money) and consumes the reservation; demo
 * funds can only end SIMULATED_COMPLETED. Reject and failure release the reservation exactly once. Full account numbers
 * are returned only to Receptionists processing a request; everyone else sees a masked number.
 */
@Service
@RequiredArgsConstructor
public class WalletWithdrawalService {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    static final int MAX_OPEN_PER_MEMBER = 3;

    public record PayoutAccountRequest(String bankBin, String accountNumber, String accountHolder) {}
    public record PayoutAccount(String bankBin, String bankName, String maskedAccountNumber, String accountHolder, LocalDateTime updatedAt) {}
    public record CreateRequest(BigDecimal amount, Pool pool, String confirmAccountLast4) {}
    public record DecisionRequest(String note) {}
    public record CompleteRequest(String payoutReference, String note) {}

    public record Withdrawal(long id, String code, long memberId, String memberCode, String memberName, String pool,
                             BigDecimal amount, String status, String bankName, String accountNumber, String accountHolder,
                             String reviewNote, String reviewedByName, LocalDateTime reviewedAt, String payoutReference,
                             String completedByName, LocalDateTime completedAt, LocalDateTime createdAt) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final WalletLedgerService ledger;
    private final VietQrBankDirectory directory;
    private final OperationalMemberPolicy operational;

    private static final String SELECT = "SELECT w.*, m.member_code, u.full_name AS member_name, r.full_name AS reviewer_name,"
            + " c.full_name AS completer_name FROM dbo.wallet_withdrawal w JOIN dbo.member_profile m ON m.user_id=w.member_id"
            + " JOIN dbo.user_account u ON u.id=w.member_id LEFT JOIN dbo.user_account r ON r.id=w.reviewed_by"
            + " LEFT JOIN dbo.user_account c ON c.id=w.completed_by";

    private static RowMapper<Withdrawal> mapper(boolean fullAccount) {
        return (rs, row) -> new Withdrawal(rs.getLong("id"), rs.getString("withdrawal_code"), rs.getLong("member_id"),
                rs.getString("member_code"), rs.getString("member_name"), rs.getString("pool"), rs.getBigDecimal("amount"),
                rs.getString("status"), rs.getString("bank_name"),
                fullAccount ? rs.getString("account_number") : mask(rs.getString("account_number")),
                rs.getString("account_holder"), rs.getString("review_note"), rs.getString("reviewer_name"),
                local(rs.getTimestamp("reviewed_at")), rs.getString("payout_reference"), rs.getString("completer_name"),
                local(rs.getTimestamp("completed_at")), rs.getTimestamp("created_at").toLocalDateTime());
    }

    @Transactional(readOnly = true)
    public PayoutAccount payoutAccount(long memberId) {
        var rows = jdbc.queryForList("SELECT * FROM dbo.member_payout_account WHERE member_id=?", memberId);
        if (rows.isEmpty()) {
            return null;
        }
        var row = rows.getFirst();
        return new PayoutAccount((String) row.get("bank_bin"), (String) row.get("bank_name"), mask((String) row.get("account_number")),
                (String) row.get("account_holder"), ((Timestamp) row.get("updated_at")).toLocalDateTime());
    }

    /** Saves the Member's own payout account. Only bank details are collected; never a bank login, PIN or OTP. */
    @Transactional
    public PayoutAccount savePayoutAccount(long memberId, PayoutAccountRequest request) {
        ledger.ensureWallet(memberId);
        if (request == null) {
            throw new FieldValidationException("accountNumber", "Payout account details are required");
        }
        String bin = request.bankBin() == null ? "" : request.bankBin().trim();
        var bank = directory.banks().stream().filter(b -> b.bin().equals(bin) && b.transferSupported()).findFirst()
                .orElseThrow(() -> new FieldValidationException("bankBin", "Choose a supported bank"));
        String number = request.accountNumber() == null ? "" : request.accountNumber().replace(" ", "");
        if (!number.matches("[0-9]{6,19}")) {
            throw new FieldValidationException("accountNumber", "Account number must be 6 to 19 digits");
        }
        String holder = request.accountHolder() == null ? "" : request.accountHolder().trim().toUpperCase(Locale.ROOT);
        if (!holder.matches("[A-Z0-9 ]{3,60}")) {
            throw new FieldValidationException("accountHolder", "Account holder must be 3 to 60 letters without accents, as printed by the bank");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        int updated = jdbc.update("UPDATE dbo.member_payout_account SET bank_bin=?, bank_name=?, account_number=?, account_holder=?,"
                + " updated_at=?, version=version+1 WHERE member_id=?", bin, bank.name(), number, holder, now, memberId);
        if (updated == 0) {
            jdbc.update("INSERT INTO dbo.member_payout_account (member_id, bank_bin, bank_name, account_number, account_holder, updated_at)"
                    + " VALUES (?,?,?,?,?,?)", memberId, bin, bank.name(), number, holder, now);
        }
        return payoutAccount(memberId);
    }

    @Transactional
    public Withdrawal create(long memberId, CreateRequest request, String idempotencyKey) {
        if (request == null || request.amount() == null) {
            throw new BusinessRuleException("Amount is required", "WITHDRAWAL_INVALID_AMOUNT");
        }
        Pool pool = request.pool() == null ? Pool.ACTUAL : request.pool();
        BigDecimal amount = request.amount();
        if (amount.stripTrailingZeros().scale() > 0 || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("Withdrawal amount must be a whole number of VND from " + MIN_AMOUNT.toPlainString()
                    + " to " + MAX_AMOUNT.toPlainString(), "WITHDRAWAL_INVALID_AMOUNT");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        long walletId = ledger.ensureWallet(memberId).walletId();
        // Serialize this Member's withdrawal requests on the wallet row before any check.
        jdbc.queryForObject("SELECT id FROM dbo.member_wallet WITH (UPDLOCK, ROWLOCK) WHERE id=?", Long.class, walletId);
        if (key != null) {
            var existing = jdbc.query(SELECT + " WHERE w.member_id=? AND w.idempotency_key=?", mapper(false), memberId, key);
            if (!existing.isEmpty()) {
                Withdrawal found = existing.getFirst();
                if (found.amount().compareTo(amount) != 0 || !found.pool().equals(pool.name())) {
                    throw new ConflictException("This request key was already used for a different withdrawal", "WITHDRAWAL_KEY_REUSED");
                }
                return found;
            }
        }
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.wallet_withdrawal WHERE member_id=? AND status IN ('PENDING','APPROVED')",
                Integer.class, memberId);
        if (open != null && open >= MAX_OPEN_PER_MEMBER) {
            throw new BusinessRuleException("You already have " + MAX_OPEN_PER_MEMBER + " open withdrawals", "WITHDRAWAL_TOO_MANY_OPEN");
        }
        String bin = null, bank = null, number = null, holder = null;
        if (pool == Pool.ACTUAL) {
            operational.requireRealMember(memberId);
            var rows = jdbc.queryForList("SELECT * FROM dbo.member_payout_account WHERE member_id=?", memberId);
            if (rows.isEmpty()) {
                throw new BusinessRuleException("Save your payout bank account before requesting a withdrawal", "WITHDRAWAL_ACCOUNT_REQUIRED");
            }
            var row = rows.getFirst();
            number = (String) row.get("account_number");
            String last4 = request.confirmAccountLast4() == null ? "" : request.confirmAccountLast4().trim();
            if (!number.endsWith(last4) || last4.length() != 4) {
                throw new FieldValidationException("confirmAccountLast4", "Enter the last 4 digits of your payout account to confirm it");
            }
            bin = (String) row.get("bank_bin");
            bank = (String) row.get("bank_name");
            holder = (String) row.get("account_holder");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        Long id = jdbc.queryForObject("INSERT INTO dbo.wallet_withdrawal (member_id, pool, amount, status, idempotency_key, bank_bin,"
                + " bank_name, account_number, account_holder, created_at, updated_at) OUTPUT INSERTED.id VALUES (?,?,?,'PENDING',?,?,?,?,?,?,?)",
                Long.class, memberId, pool.name(), amount, key, bin, bank, number, holder, now, now);
        // Insufficient funds throws here and rolls back the request row as well.
        var reserve = ledger.apply(new Operation(memberId, EntryType.WITHDRAWAL_RESERVE, pool, amount, "WD-RES-" + id,
                "WALLET_WITHDRAWAL", id, (pool == Pool.ACTUAL ? "Withdrawal " : "Demo withdrawal ") + code(id) + " requested", memberId));
        jdbc.update("UPDATE dbo.wallet_withdrawal SET reserve_entry_id=? WHERE id=?", reserve.entry().id(), id);
        return find(id, false);
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> mine(long memberId) {
        return jdbc.query(SELECT + " WHERE w.member_id=? ORDER BY w.id DESC OFFSET 0 ROWS FETCH NEXT 50 ROWS ONLY", mapper(false), memberId);
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> staffList(String status) {
        String filter = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        String where = switch (filter) {
            case "OPEN" -> " WHERE w.status IN ('PENDING','APPROVED') ORDER BY w.id ASC";
            case "ALL" -> " ORDER BY w.id DESC";
            case "PENDING", "APPROVED", "REJECTED", "COMPLETED", "FAILED", "SIMULATED_COMPLETED" -> " WHERE w.status='" + filter + "' ORDER BY w.id DESC";
            default -> throw new BusinessRuleException("Unknown withdrawal status " + status);
        };
        return jdbc.query(SELECT + where + " OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY", mapper(false));
    }

    /** Receptionist processing view: the only place the full account number is returned. */
    @Transactional(readOnly = true)
    public Withdrawal staffDetail(long id) {
        return find(id, true);
    }

    @Transactional
    public Withdrawal approve(long id, DecisionRequest request, long actorId) {
        Withdrawal w = lock(id);
        requireStatus(w, "PENDING");
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        jdbc.update("UPDATE dbo.wallet_withdrawal SET status='APPROVED', review_note=?, reviewed_by=?, reviewed_at=?, updated_at=?,"
                + " version=version+1 WHERE id=?", trim(request == null ? null : request.note()), actorId, now, now, id);
        return find(id, true);
    }

    @Transactional
    public Withdrawal reject(long id, DecisionRequest request, long actorId) {
        Withdrawal w = lock(id);
        if (!"PENDING".equals(w.status()) && !"APPROVED".equals(w.status())) {
            throw notOpen(w);
        }
        return release(w, "REJECTED", requireReason(request), actorId, true);
    }

    @Transactional
    public Withdrawal fail(long id, DecisionRequest request, long actorId) {
        Withdrawal w = lock(id);
        requireStatus(w, "APPROVED");
        return release(w, "FAILED", requireReason(request), actorId, false);
    }

    /** Separate from approval: records the actual (or demo-only) payout and consumes the reservation once. */
    @Transactional
    public Withdrawal complete(long id, CompleteRequest request, long actorId) {
        Withdrawal w = lock(id);
        requireStatus(w, "APPROVED");
        boolean demo = Pool.SIMULATED.name().equals(w.pool());
        String reference = request == null || request.payoutReference() == null ? null : request.payoutReference().trim();
        if (demo) {
            if (reference != null && !reference.isEmpty()) {
                throw new BusinessRuleException("A demo withdrawal has no bank payout reference", "WITHDRAWAL_SIMULATED_REFERENCE");
            }
            reference = null;
        } else if (reference == null || reference.length() < 4 || reference.length() > 60) {
            throw new BusinessRuleException("Enter the bank payout reference (4 to 60 characters) of the transfer you made",
                    "WITHDRAWAL_REFERENCE_REQUIRED");
        }
        var payout = ledger.apply(new Operation(w.memberId(), EntryType.WITHDRAWAL_PAYOUT, Pool.valueOf(w.pool()), w.amount(),
                "WD-PAY-" + id, "WALLET_WITHDRAWAL", id, (demo ? "Demo withdrawal " : "Withdrawal ") + w.code()
                + (demo ? " completed (no real transfer)" : " paid out"), actorId));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        try {
            jdbc.update("UPDATE dbo.wallet_withdrawal SET status=?, payout_reference=?, payout_entry_id=?, completed_by=?, completed_at=?,"
                    + " review_note=COALESCE(?, review_note), updated_at=?, version=version+1 WHERE id=?",
                    demo ? "SIMULATED_COMPLETED" : "COMPLETED", reference, payout.entry().id(), actorId, now,
                    trim(request == null ? null : request.note()), now, id);
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("This payout reference was already used for another withdrawal", "WITHDRAWAL_DUPLICATE_REFERENCE");
        }
        return find(id, true);
    }

    private Withdrawal release(Withdrawal w, String status, String reason, long actorId, boolean review) {
        var released = ledger.apply(new Operation(w.memberId(), EntryType.WITHDRAWAL_RELEASE, Pool.valueOf(w.pool()), w.amount(),
                "WD-REL-" + w.id(), "WALLET_WITHDRAWAL", w.id(), "Withdrawal " + w.code() + " " + status.toLowerCase(Locale.ROOT)
                + ", funds released", actorId));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        if (review) {
            jdbc.update("UPDATE dbo.wallet_withdrawal SET status=?, release_entry_id=?, review_note=?, reviewed_by=?, reviewed_at=?,"
                    + " updated_at=?, version=version+1 WHERE id=?", status, released.entry().id(), reason, actorId, now, now, w.id());
        } else {
            jdbc.update("UPDATE dbo.wallet_withdrawal SET status=?, release_entry_id=?, review_note=?, completed_by=?, completed_at=?,"
                    + " updated_at=?, version=version+1 WHERE id=?", status, released.entry().id(), reason, actorId, now, now, w.id());
        }
        return find(w.id(), true);
    }

    private Withdrawal find(long id, boolean fullAccount) {
        var found = jdbc.query(SELECT + " WHERE w.id=?", mapper(fullAccount), id);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Withdrawal not found");
        }
        return found.getFirst();
    }

    private Withdrawal lock(long id) {
        if (jdbc.queryForList("SELECT id FROM dbo.wallet_withdrawal WITH (UPDLOCK, ROWLOCK) WHERE id=?", Long.class, id).isEmpty()) {
            throw new ResourceNotFoundException("Withdrawal not found");
        }
        return find(id, true);
    }

    private static void requireStatus(Withdrawal w, String status) {
        if (!status.equals(w.status())) {
            throw notOpen(w);
        }
    }

    private static ConflictException notOpen(Withdrawal w) {
        return new ConflictException("Withdrawal " + w.code() + " is already " + w.status(), "WITHDRAWAL_STATE_CONFLICT");
    }

    private static String requireReason(DecisionRequest request) {
        String reason = trim(request == null ? null : request.note());
        if (reason == null) {
            throw new BusinessRuleException("A reason is required", "WITHDRAWAL_REASON_REQUIRED");
        }
        return reason;
    }

    static String mask(String number) {
        if (number == null) return null;
        return number.length() <= 4 ? "****" : "****" + number.substring(number.length() - 4);
    }

    private static String code(long id) {
        return "WD-" + String.format("%06d", id);
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) return null;
        String t = value.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    private static LocalDateTime local(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
