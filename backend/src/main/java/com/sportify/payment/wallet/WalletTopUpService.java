package com.sportify.payment.wallet;

import com.sportify.catalog.service.OperationalMemberPolicy;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.service.VietQrBankDirectory;
import com.sportify.payment.service.VietQrPayload;
import com.sportify.payment.wallet.WalletLedgerService.EntryType;
import com.sportify.payment.wallet.WalletLedgerService.Operation;
import com.sportify.payment.wallet.WalletLedgerService.Pool;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * MW-04 wallet top-ups. Creating a request only records PENDING: showing a QR code is never treated as a bank receipt.
 * A Receptionist confirms after checking the bank evidence; the confirmation locks the request, checks amount and
 * reference, and credits the wallet once through the ledger (operation key TOPUP-{id}). SIMULATED requests exist only
 * when demo top-ups are enabled; they credit the separate demo pool and never carry a bank reference.
 */
@Service
@RequiredArgsConstructor
public class WalletTopUpService {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    static final int MAX_OPEN_PER_MEMBER = 3;

    public record CreateRequest(BigDecimal amount, Pool mode) {}
    public record ConfirmRequest(BigDecimal receivedAmount, String bankReference, String note) {}
    public record RejectRequest(String reason) {}

    /** Transfer details for a PENDING real top-up; payable is false unless a verified beneficiary is configured. */
    public record Instructions(boolean payable, String notice, String bankBin, String bankName, String accountNumber,
                               String accountName, BigDecimal amount, String transferReference, String qrPayload) {}

    public record TopUp(long id, String code, long memberId, String memberCode, String memberName, String mode,
                        BigDecimal amount, String status, String transferReference, BigDecimal receivedAmount,
                        String bankReference, String reviewNote, String reviewedByName, LocalDateTime reviewedAt,
                        Long ledgerEntryId, LocalDateTime createdAt, Instructions instructions) {}

    public record MemberTopUps(boolean simulatedTopUpsEnabled, BigDecimal minAmount, BigDecimal maxAmount, List<TopUp> topUps) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final WalletLedgerService ledger;
    private final BankTransferProperties beneficiary;
    private final VietQrBankDirectory directory;
    private final OperationalMemberPolicy operational;

    @Value("${app.wallet.simulated-topups-enabled:false}")
    private boolean simulatedTopUpsEnabled;

    private final RowMapper<TopUp> mapper = (rs, row) -> new TopUp(rs.getLong("id"), rs.getString("topup_code"),
            rs.getLong("member_id"), rs.getString("member_code"), rs.getString("member_name"), rs.getString("mode"),
            rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("transfer_reference"),
            rs.getBigDecimal("received_amount"), rs.getString("bank_reference"), rs.getString("review_note"),
            rs.getString("reviewer_name"), toLocal(rs.getTimestamp("reviewed_at")),
            rs.getObject("ledger_entry_id") == null ? null : rs.getLong("ledger_entry_id"),
            rs.getTimestamp("created_at").toLocalDateTime(), null);

    private static final String SELECT = "SELECT t.*, m.member_code, u.full_name AS member_name, r.full_name AS reviewer_name"
            + " FROM dbo.wallet_topup t JOIN dbo.member_profile m ON m.user_id=t.member_id"
            + " JOIN dbo.user_account u ON u.id=t.member_id LEFT JOIN dbo.user_account r ON r.id=t.reviewed_by";

    @Transactional
    public TopUp create(long memberId, CreateRequest request, String idempotencyKey) {
        if (request == null || request.amount() == null) {
            throw new BusinessRuleException("Amount is required", "TOPUP_INVALID_AMOUNT");
        }
        Pool mode = request.mode() == null ? Pool.ACTUAL : request.mode();
        BigDecimal amount = request.amount();
        if (amount.stripTrailingZeros().scale() > 0 || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("Top-up amount must be a whole number of VND from " + MIN_AMOUNT.toPlainString()
                    + " to " + MAX_AMOUNT.toPlainString(), "TOPUP_INVALID_AMOUNT");
        }
        if (mode == Pool.SIMULATED && !simulatedTopUpsEnabled) {
            throw new BusinessRuleException("Demo top-ups are not enabled", "TOPUP_SIMULATION_DISABLED");
        }
        if (mode == Pool.ACTUAL) {
            // Synthetic demo identities never receive real money (existing quarantine rule).
            operational.requireRealMember(memberId);
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 80) {
            throw new BusinessRuleException("Idempotency-Key must be at most 80 characters");
        }
        ledger.ensureWallet(memberId);
        // Serialize this Member's requests so the open-request limit and the idempotency key hold under retries.
        jdbc.queryForObject("SELECT id FROM dbo.member_wallet WITH (UPDLOCK, ROWLOCK) WHERE member_id=?", Long.class, memberId);
        if (key != null) {
            List<TopUp> existing = jdbc.query(SELECT + " WHERE t.member_id=? AND t.idempotency_key=?", mapper, memberId, key);
            if (!existing.isEmpty()) {
                TopUp found = existing.getFirst();
                if (found.amount().compareTo(amount) != 0 || !found.mode().equals(mode.name())) {
                    throw new ConflictException("This request key was already used for a different top-up", "TOPUP_KEY_REUSED");
                }
                return withInstructions(found);
            }
        }
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.wallet_topup WHERE member_id=? AND status='PENDING'",
                Integer.class, memberId);
        if (open != null && open >= MAX_OPEN_PER_MEMBER) {
            throw new BusinessRuleException("You already have " + MAX_OPEN_PER_MEMBER
                    + " pending top-ups. Wait for reception to review them or cancel one.", "TOPUP_TOO_MANY_PENDING");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        boolean live = mode == Pool.ACTUAL && liveBeneficiary();
        Long id = jdbc.queryForObject("INSERT INTO dbo.wallet_topup (member_id, mode, amount, status, idempotency_key,"
                + " beneficiary_bin, beneficiary_account, beneficiary_name, created_at, updated_at)"
                + " OUTPUT INSERTED.id VALUES (?,?,?,'PENDING',?,?,?,?,?,?)", Long.class,
                memberId, mode.name(), amount, key, live ? beneficiary.getBankBin() : null,
                live ? beneficiary.getAccountNumber().replace(" ", "") : null, live ? beneficiary.getAccountName() : null, now, now);
        return withInstructions(find(id));
    }

    @Transactional(readOnly = true)
    public MemberTopUps mine(long memberId) {
        List<TopUp> topUps = jdbc.query(SELECT + " WHERE t.member_id=? ORDER BY t.id DESC OFFSET 0 ROWS FETCH NEXT 50 ROWS ONLY",
                mapper, memberId).stream().map(this::withInstructions).toList();
        return new MemberTopUps(simulatedTopUpsEnabled, MIN_AMOUNT, MAX_AMOUNT, topUps);
    }

    @Transactional(readOnly = true)
    public TopUp memberDetail(long memberId, long id) {
        TopUp topUp = find(id);
        if (topUp.memberId() != memberId) {
            throw new ResourceNotFoundException("Top-up not found");
        }
        return withInstructions(topUp);
    }

    @Transactional
    public TopUp cancel(long memberId, long id) {
        TopUp topUp = lock(id);
        if (topUp.memberId() != memberId) {
            throw new ResourceNotFoundException("Top-up not found");
        }
        requirePending(topUp);
        jdbc.update("UPDATE dbo.wallet_topup SET status='CANCELLED', updated_at=?, version=version+1 WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now(clock)), id);
        return withInstructions(find(id));
    }

    @Transactional(readOnly = true)
    public List<TopUp> staffList(String status) {
        String filter = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase();
        if (!List.of("PENDING", "CONFIRMED", "REJECTED", "CANCELLED", "ALL").contains(filter)) {
            throw new BusinessRuleException("Unknown top-up status " + status);
        }
        if ("ALL".equals(filter)) {
            return jdbc.query(SELECT + " ORDER BY t.id DESC OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY", mapper);
        }
        // Oldest pending first, so the queue is worked in arrival order.
        return jdbc.query(SELECT + " WHERE t.status=? ORDER BY " + ("PENDING".equals(filter) ? "t.id ASC" : "t.id DESC")
                + " OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY", mapper, filter);
    }

    @Transactional(readOnly = true)
    public TopUp staffDetail(long id) {
        return find(id);
    }

    /**
     * Lock order: the top-up row, then (inside the ledger) the wallet row. A second confirmation waits for the first
     * and then sees CONFIRMED (409 TOPUP_NOT_PENDING). A wrong amount or a reused bank reference changes nothing.
     */
    @Transactional
    public TopUp confirm(long id, ConfirmRequest request, long actorId) {
        TopUp topUp = lock(id);
        requirePending(topUp);
        if (request == null || request.receivedAmount() == null || request.receivedAmount().compareTo(topUp.amount()) != 0) {
            throw new ConflictException("The received amount must equal the requested " + topUp.amount().toPlainString()
                    + " VND. Reject the request if the bank shows a different amount.", "TOPUP_AMOUNT_MISMATCH");
        }
        String reference = request.bankReference() == null ? null : request.bankReference().trim();
        boolean simulated = Pool.SIMULATED.name().equals(topUp.mode());
        if (simulated) {
            if (reference != null && !reference.isEmpty()) {
                throw new BusinessRuleException("A demo top-up has no bank reference", "TOPUP_SIMULATED_REFERENCE");
            }
            reference = null;
        } else if (reference == null || reference.length() < 4 || reference.length() > 60) {
            throw new BusinessRuleException("Enter the bank transaction reference (4 to 60 characters) from the bank statement",
                    "TOPUP_REFERENCE_REQUIRED");
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.user_account WHERE id=? AND status='ACTIVE'", Integer.class,
                topUp.memberId());
        if (active == null || active == 0) {
            throw new ConflictException("The member account is not active. Reject the top-up instead.", "TOPUP_MEMBER_INACTIVE");
        }
        String note = trim(request.note(), 300);
        var applied = ledger.apply(new Operation(topUp.memberId(), EntryType.TOPUP_CREDIT,
                simulated ? Pool.SIMULATED : Pool.ACTUAL, topUp.amount(), "TOPUP-" + id, "WALLET_TOPUP", id,
                (simulated ? "Demo top-up " : "Top-up ") + topUp.code(), actorId));
        try {
            jdbc.update("UPDATE dbo.wallet_topup SET status='CONFIRMED', received_amount=?, bank_reference=?, review_note=?,"
                    + " reviewed_by=?, reviewed_at=?, ledger_entry_id=?, updated_at=?, version=version+1 WHERE id=? AND status='PENDING'",
                    request.receivedAmount(), reference, note, actorId, Timestamp.valueOf(LocalDateTime.now(clock)),
                    applied.entry().id(), Timestamp.valueOf(LocalDateTime.now(clock)), id);
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("This bank reference was already used for another top-up", "TOPUP_DUPLICATE_REFERENCE");
        }
        return find(id);
    }

    @Transactional
    public TopUp reject(long id, RejectRequest request, long actorId) {
        TopUp topUp = lock(id);
        requirePending(topUp);
        String reason = request == null ? null : trim(request.reason(), 300);
        if (reason == null) {
            throw new BusinessRuleException("A reason is required to reject a top-up", "TOPUP_REASON_REQUIRED");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        jdbc.update("UPDATE dbo.wallet_topup SET status='REJECTED', review_note=?, reviewed_by=?, reviewed_at=?, updated_at=?,"
                + " version=version+1 WHERE id=?", reason, actorId, now, now, id);
        return find(id);
    }

    private TopUp withInstructions(TopUp t) {
        Instructions instructions;
        if (!"PENDING".equals(t.status())) {
            instructions = null;
        } else if (Pool.SIMULATED.name().equals(t.mode())) {
            instructions = new Instructions(false, "DEMO TOP-UP. No bank transfer is needed. Reception confirms it as demo funds,"
                    + " which are not real money.", null, null, null, null, t.amount(), t.transferReference(), null);
        } else {
            var row = jdbc.queryForMap("SELECT beneficiary_bin, beneficiary_account, beneficiary_name FROM dbo.wallet_topup WHERE id=?", t.id());
            String bin = (String) row.get("beneficiary_bin");
            var bank = bin == null ? java.util.Optional.<VietQrBankDirectory.Bank>empty() : directory.banks().stream()
                    .filter(b -> b.bin().equals(bin)).findFirst();
            if (bin == null || bank.isEmpty()) {
                instructions = new Instructions(false, "DEMO NON-PAYABLE. No verified receiving account is configured, so no QR"
                        + " is shown. Do not transfer money for this request.", null, null, null, null, t.amount(),
                        t.transferReference(), null);
            } else {
                String account = (String) row.get("beneficiary_account");
                instructions = new Instructions(true, "Transfer instructions only. Your wallet is credited after reception"
                        + " checks the money arrived. Keep the transfer note exactly as shown.", bin, bank.get().name(), account,
                        (String) row.get("beneficiary_name"), t.amount(), t.transferReference(),
                        VietQrPayload.encode(bin, account, t.amount(), t.transferReference()));
            }
        }
        return new TopUp(t.id(), t.code(), t.memberId(), t.memberCode(), t.memberName(), t.mode(), t.amount(), t.status(),
                t.transferReference(), t.receivedAmount(), t.bankReference(), t.reviewNote(), t.reviewedByName(),
                t.reviewedAt(), t.ledgerEntryId(), t.createdAt(), instructions);
    }

    private boolean liveBeneficiary() {
        return beneficiary.isLiveInstructionsEnabled() && beneficiary.isLiveBeneficiaryValid()
                && directory.supported(beneficiary.getBankBin()).isPresent();
    }

    private TopUp find(long id) {
        List<TopUp> found = jdbc.query(SELECT + " WHERE t.id=?", mapper, id);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Top-up not found");
        }
        return found.getFirst();
    }

    private TopUp lock(long id) {
        List<Long> locked = jdbc.queryForList("SELECT id FROM dbo.wallet_topup WITH (UPDLOCK, ROWLOCK) WHERE id=?", Long.class, id);
        if (locked.isEmpty()) {
            throw new ResourceNotFoundException("Top-up not found");
        }
        return find(id);
    }

    private static void requirePending(TopUp topUp) {
        if (!"PENDING".equals(topUp.status())) {
            throw new ConflictException("Top-up " + topUp.code() + " is already " + topUp.status(), "TOPUP_NOT_PENDING");
        }
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static LocalDateTime toLocal(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
