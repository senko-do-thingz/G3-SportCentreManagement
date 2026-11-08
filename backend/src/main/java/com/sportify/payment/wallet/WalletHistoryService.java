package com.sportify.payment.wallet;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MW-08 Member wallet activity: one row per business record of the signed-in Member (top-up request, withdrawal
 * request, wallet payment, compensation), with the status taken from that record and the resulting balance taken from
 * its ledger entry. A pending top-up shows no balance change; an approved withdrawal is shown as held, not paid. Bank
 * account numbers are masked. Each row appears once however often an action was retried, because every source row and
 * ledger entry is unique.
 */
@Service
@RequiredArgsConstructor
public class WalletHistoryService {

    static final Set<String> TYPES = Set.of("TOPUP", "WITHDRAWAL", "WALLET_PAYMENT", "COMPENSATION");

    public record Row(String type, long id, String reference, LocalDateTime createdAt, BigDecimal amount, String direction,
                      String status, String pool, BigDecimal availableAfter, String detail, Long paymentId,
                      String bankReference, LocalDateTime settledAt) {}

    public record History(PageResponse<Row> page, BigDecimal availableBalance, BigDecimal reservedBalance,
                          BigDecimal ledgerAvailable, BigDecimal ledgerReserved, boolean reconciled) {}

    private final JdbcTemplate jdbc;
    private final WalletLedgerService ledger;

    private static final String ROWS = """
            SELECT 'TOPUP' AS type, t.id, t.topup_code AS reference, t.created_at, t.amount,
                   CASE WHEN t.status = 'CONFIRMED' THEN 'CREDIT' ELSE 'NONE' END AS direction, t.status, t.mode AS pool,
                   e.available_after,
                   CASE WHEN t.mode = 'SIMULATED' THEN N'Demo top-up (not real money)' ELSE N'Bank transfer top-up, note ' + t.transfer_reference END AS detail,
                   CAST(NULL AS BIGINT) AS payment_id, t.bank_reference, t.reviewed_at AS settled_at
            FROM dbo.wallet_topup t LEFT JOIN dbo.wallet_ledger_entry e ON e.id = t.ledger_entry_id WHERE t.member_id = ?
            UNION ALL
            SELECT 'WITHDRAWAL', w.id, w.withdrawal_code, w.created_at, w.amount,
                   CASE WHEN w.status IN ('REJECTED', 'FAILED') THEN 'NONE' ELSE 'DEBIT' END, w.status, w.pool,
                   COALESCE(rel.available_after, res.available_after),
                   CASE WHEN w.pool = 'SIMULATED' THEN N'Demo withdrawal (no real transfer)'
                        ELSE N'To ' + w.bank_name + N' ****' + RIGHT(w.account_number, 4) END,
                   NULL, w.payout_reference, w.completed_at
            FROM dbo.wallet_withdrawal w
            LEFT JOIN dbo.wallet_ledger_entry res ON res.id = w.reserve_entry_id
            LEFT JOIN dbo.wallet_ledger_entry rel ON rel.id = w.release_entry_id
            WHERE w.member_id = ?
            UNION ALL
            SELECT CASE WHEN e.entry_type = 'PURCHASE_DEBIT' THEN 'WALLET_PAYMENT' ELSE 'COMPENSATION' END, e.id,
                   COALESCE(p.payment_code, 'WL-' + CAST(e.id AS VARCHAR(20))), e.created_at, e.amount,
                   CASE WHEN e.entry_type = 'PURCHASE_DEBIT' THEN 'DEBIT' ELSE 'CREDIT' END,
                   CASE WHEN e.entry_type = 'PURCHASE_DEBIT' THEN 'PAID' ELSE 'CREDITED' END, e.pool, e.available_after,
                   e.description, p.id, CAST(NULL AS VARCHAR(60)), e.created_at
            FROM dbo.wallet_ledger_entry e JOIN dbo.member_wallet mw ON mw.id = e.wallet_id
            LEFT JOIN dbo.payment p ON e.source_type = 'PAYMENT' AND p.id = e.source_id AND p.member_id = mw.member_id
            WHERE mw.member_id = ? AND e.entry_type IN ('PURCHASE_DEBIT', 'COMPENSATION_CREDIT')
            """;

    @Transactional
    public History history(long memberId, String type, String status, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("Page must be 0 or more and size between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The from date must not be after the to date");
        }
        String t = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        if (t != null && !TYPES.contains(t)) {
            throw new BusinessRuleException("Unknown activity type " + type);
        }
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !s.matches("[A-Z_]{3,25}")) {
            throw new BusinessRuleException("Unknown status " + status);
        }
        var balance = ledger.balance(memberId);
        List<Object> args = new ArrayList<>(List.of(memberId, memberId, memberId));
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (t != null) { where.append(" AND a.type = ?"); args.add(t); }
        if (s != null) { where.append(" AND a.status = ?"); args.add(s); }
        if (from != null) { where.append(" AND a.created_at >= ?"); args.add(Timestamp.valueOf(from.atStartOfDay())); }
        if (to != null) { where.append(" AND a.created_at < ?"); args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay())); }
        String base = "SELECT * FROM (" + ROWS + ") a" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + base + ") c", Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page * size);
        pageArgs.add(size);
        List<Row> rows = jdbc.query(base + " ORDER BY a.created_at DESC, a.type, a.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, n) -> new Row(rs.getString("type"), rs.getLong("id"), rs.getString("reference"),
                        rs.getTimestamp("created_at").toLocalDateTime(), rs.getBigDecimal("amount"), rs.getString("direction"),
                        rs.getString("status"), rs.getString("pool"), rs.getBigDecimal("available_after"), rs.getString("detail"),
                        rs.getObject("payment_id") == null ? null : rs.getLong("payment_id"), rs.getString("bank_reference"),
                        rs.getTimestamp("settled_at") == null ? null : rs.getTimestamp("settled_at").toLocalDateTime()),
                pageArgs.toArray());
        long count = total == null ? 0 : total;

        // Reconciliation: the stored balance must equal the sum of the Member's ACTUAL ledger rows.
        var sums = jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(CASE WHEN e.entry_type IN ('TOPUP_CREDIT','COMPENSATION_CREDIT','WITHDRAWAL_RELEASE') THEN e.amount
                                    WHEN e.entry_type IN ('PURCHASE_DEBIT','WITHDRAWAL_RESERVE') THEN -e.amount ELSE 0 END), 0) AS available,
                  COALESCE(SUM(CASE WHEN e.entry_type = 'WITHDRAWAL_RESERVE' THEN e.amount
                                    WHEN e.entry_type IN ('WITHDRAWAL_RELEASE','WITHDRAWAL_PAYOUT') THEN -e.amount ELSE 0 END), 0) AS reserved
                FROM dbo.wallet_ledger_entry e JOIN dbo.member_wallet w ON w.id = e.wallet_id
                WHERE w.member_id = ? AND e.pool = 'ACTUAL'""", memberId);
        BigDecimal ledgerAvailable = (BigDecimal) sums.get("available");
        BigDecimal ledgerReserved = (BigDecimal) sums.get("reserved");
        boolean reconciled = ledgerAvailable.compareTo(balance.actualAvailable()) == 0
                && ledgerReserved.compareTo(balance.actualReserved()) == 0;
        return new History(new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size)),
                balance.actualAvailable(), balance.actualReserved(), ledgerAvailable, ledgerReserved, reconciled);
    }
}
