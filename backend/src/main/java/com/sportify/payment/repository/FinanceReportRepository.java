package com.sportify.payment.repository;

import com.sportify.payment.dto.finance.FinanceTransactionResponse;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only SQL for the manager finance reports (BE-22). Every revenue figure comes from the view vw_paid_payment
 * (SUCCESS payments only, filtered by payment_time); the ledger reads vw_payment_ledger (every status). Both views are
 * created by V25. Native SQL is used because the reports are aggregations over views, which JPQL cannot express.
 * Time ranges are half open: start inclusive, end exclusive.
 */
@Repository
public class FinanceReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FinanceReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sum and count of a set of rows. */
    public record Totals(BigDecimal amount, long count) {
    }

    /** Revenue of one calendar month. */
    public record MonthTotal(int year, int month, BigDecimal amount, long count) {
    }

    /** Revenue of one group of the breakdown; key and label may be null. */
    public record GroupTotal(String key, String label, BigDecimal amount, long count) {
    }

    /** Filters of the ledger; null means no filter. search is matched as "contains", wildcards escaped. */
    public record LedgerFilter(String status, String method, String itemType, LocalDateTime from,
                               LocalDateTime toExclusive, String search) {
    }

    /** One paid payment for the CSV export. */
    public record ExportRow(String paymentCode, LocalDateTime paymentTime, String invoiceNumber, String memberCode,
                            String memberName, String itemType, String itemCode, String itemName, String sportName,
                            String method, BigDecimal amount) {
    }

    public Totals paidTotals(LocalDateTime from, LocalDateTime toExclusive) {
        return totals("""
                SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) AS cnt
                FROM vw_paid_payment
                WHERE payment_time >= :from AND payment_time < :to
                """, from, toExclusive);
    }

    /** PENDING payments requested (created_at) in the range. */
    public Totals pendingTotals(LocalDateTime from, LocalDateTime toExclusive) {
        return totals("""
                SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) AS cnt
                FROM payment
                WHERE payment_status = 'PENDING' AND created_at >= :from AND created_at < :to
                """, from, toExclusive);
    }

    /**
     * COMPLETED refund requests in the range. refund_request has no completed_at column, so the time of the last
     * change (updated_at, which completion sets) is used, falling back to reviewed_at and created_at.
     * The amount is what was approved, or what was requested when nothing was approved.
     */
    public Totals refundedTotals(LocalDateTime from, LocalDateTime toExclusive) {
        return totals("""
                SELECT COALESCE(SUM(COALESCE(amount_approved, amount_requested)), 0) AS total, COUNT(*) AS cnt
                FROM refund_request
                WHERE status = 'COMPLETED'
                  AND COALESCE(updated_at, reviewed_at, created_at) >= :from
                  AND COALESCE(updated_at, reviewed_at, created_at) < :to
                """, from, toExclusive);
    }

    public List<MonthTotal> monthlyRevenue(LocalDateTime from, LocalDateTime toExclusive) {
        return jdbc.query("""
                SELECT YEAR(payment_time) AS y, MONTH(payment_time) AS m,
                       COALESCE(SUM(amount), 0) AS total, COUNT(*) AS cnt
                FROM vw_paid_payment
                WHERE payment_time >= :from AND payment_time < :to
                GROUP BY YEAR(payment_time), MONTH(payment_time)
                ORDER BY y, m
                """, range(from, toExclusive),
                (rs, i) -> new MonthTotal(rs.getInt("y"), rs.getInt("m"), rs.getBigDecimal("total"), rs.getLong("cnt")));
    }

    /** Revenue grouped by item type, method or sport; the key and label columns come from a fixed list, never from input. */
    public List<GroupTotal> revenueBy(RevenueGroupBy groupBy, LocalDateTime from, LocalDateTime toExclusive) {
        String key;
        String label;
        switch (groupBy) {
            case ITEM_TYPE -> {
                key = "item_type";
                label = "item_type";
            }
            case METHOD -> {
                key = "payment_method";
                label = "payment_method";
            }
            case SPORT -> {
                key = "CAST(sport_id AS NVARCHAR(20))";
                label = "sport_name";
            }
            default -> throw new IllegalArgumentException("Unknown grouping " + groupBy);
        }
        String sql = "SELECT " + key + " AS group_key, MAX(" + label + ") AS group_label,"
                + " COALESCE(SUM(amount), 0) AS total, COUNT(*) AS cnt"
                + " FROM vw_paid_payment"
                + " WHERE payment_time >= :from AND payment_time < :to"
                + " GROUP BY " + key
                + " ORDER BY total DESC, group_key";
        return jdbc.query(sql, range(from, toExclusive), (rs, i) -> new GroupTotal(
                rs.getString("group_key"), rs.getString("group_label"), rs.getBigDecimal("total"), rs.getLong("cnt")));
    }

    public long countLedger(LedgerFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = ledgerWhere(filter, params);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM vw_payment_ledger" + where, params, Long.class);
        return count != null ? count : 0L;
    }

    /** One page of the ledger, newest first (ledger_time, then payment id, both descending). */
    public List<FinanceTransactionResponse> ledgerPage(LedgerFilter filter, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = ledgerWhere(filter, params);
        params.addValue("offset", (long) page * size);
        params.addValue("size", size);
        String sql = "SELECT * FROM vw_payment_ledger" + where
                + " ORDER BY ledger_time DESC, payment_id DESC"
                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
        return jdbc.query(sql, params, LEDGER_ROW);
    }

    /** Paid payments of the range, oldest first, for the CSV export. */
    public List<ExportRow> paidRows(LocalDateTime from, LocalDateTime toExclusive) {
        return jdbc.query("""
                SELECT payment_code, payment_time, invoice_number, member_code, member_name,
                       item_type, item_code, item_name, sport_name, payment_method, amount
                FROM vw_paid_payment
                WHERE payment_time >= :from AND payment_time < :to
                ORDER BY payment_time, payment_id
                """, range(from, toExclusive), (rs, i) -> new ExportRow(
                rs.getString("payment_code"), toLocalDateTime(rs.getTimestamp("payment_time")),
                rs.getString("invoice_number"), rs.getString("member_code"), rs.getString("member_name"),
                rs.getString("item_type"), rs.getString("item_code"), rs.getString("item_name"),
                rs.getString("sport_name"), rs.getString("payment_method"), rs.getBigDecimal("amount")));
    }

    private Totals totals(String sql, LocalDateTime from, LocalDateTime toExclusive) {
        Totals totals = jdbc.queryForObject(sql, range(from, toExclusive),
                (rs, i) -> new Totals(rs.getBigDecimal("total"), rs.getLong("cnt")));
        return totals != null ? totals : new Totals(BigDecimal.ZERO, 0L);
    }

    private static MapSqlParameterSource range(LocalDateTime from, LocalDateTime toExclusive) {
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.valueOf(from))
                .addValue("to", Timestamp.valueOf(toExclusive));
    }

    private static String ledgerWhere(LedgerFilter filter, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        if (filter.status() != null) {
            clauses.add("payment_status = :status");
            params.addValue("status", filter.status());
        }
        if (filter.method() != null) {
            clauses.add("payment_method = :method");
            params.addValue("method", filter.method());
        }
        if (filter.itemType() != null) {
            clauses.add("item_type = :itemType");
            params.addValue("itemType", filter.itemType());
        }
        if (filter.from() != null) {
            clauses.add("ledger_time >= :from");
            params.addValue("from", Timestamp.valueOf(filter.from()));
        }
        if (filter.toExclusive() != null) {
            clauses.add("ledger_time < :to");
            params.addValue("to", Timestamp.valueOf(filter.toExclusive()));
        }
        if (filter.search() != null) {
            clauses.add("(payment_code LIKE :search ESCAPE '\\' OR member_name LIKE :search ESCAPE '\\'"
                    + " OR member_code LIKE :search ESCAPE '\\' OR invoice_number LIKE :search ESCAPE '\\'"
                    + " OR reference_code LIKE :search ESCAPE '\\')");
            params.addValue("search", "%" + escapeLike(filter.search()) + "%");
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    /** Escapes the LIKE wildcards so a search for "50%" matches the text literally. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
    }

    private static final RowMapper<FinanceTransactionResponse> LEDGER_ROW = FinanceReportRepository::mapLedgerRow;

    private static FinanceTransactionResponse mapLedgerRow(ResultSet rs, int rowNum) throws SQLException {
        long invoiceId = rs.getLong("invoice_id");
        Long invoice = rs.wasNull() ? null : invoiceId;
        return new FinanceTransactionResponse(
                rs.getLong("payment_id"),
                rs.getString("payment_code"),
                toLocalDateTime(rs.getTimestamp("ledger_time")),
                toLocalDateTime(rs.getTimestamp("payment_time")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                rs.getString("payment_status"),
                rs.getString("payment_method"),
                rs.getBigDecimal("amount"),
                rs.getString("reference_code"),
                rs.getLong("member_id"),
                rs.getString("member_code"),
                rs.getString("member_name"),
                upper(rs.getString("item_type")),
                rs.getString("item_code"),
                rs.getString("item_name"),
                rs.getString("sport_name"),
                invoice,
                rs.getString("invoice_number"));
    }

    private static String upper(String value) {
        return value != null ? value.toUpperCase(Locale.ROOT) : null;
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }
}
