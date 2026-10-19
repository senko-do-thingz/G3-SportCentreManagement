package com.sportify.payment.repository;

import com.sportify.payment.dto.finance.CashFlowDtos.*;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

@Repository
public class CashFlowRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SUMS = """
            COALESCE(SUM(CASE WHEN event_type = 'COLLECTION' THEN amount ELSE 0 END), 0) AS gross,
            COALESCE(SUM(CASE WHEN event_type = 'REFUND_PAYOUT' THEN amount ELSE 0 END), 0) AS payout,
            COALESCE(SUM(CASE WHEN event_type = 'COLLECTION' THEN CAST(1 AS BIGINT) ELSE 0 END), 0) AS collections,
            COALESCE(SUM(CASE WHEN event_type = 'REFUND_PAYOUT' THEN CAST(1 AS BIGINT) ELSE 0 END), 0) AS refunds
            """;
    private static final String RANGE = " FROM dbo.cash_event WHERE event_time_utc >= :start AND event_time_utc < :end ";

    public CashFlowRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Instant cutover() {
        return jdbc.queryForObject("SELECT cutover_utc FROM dbo.cash_coverage WHERE id = 1", new MapSqlParameterSource(),
                (rs, row) -> instant(rs.getTimestamp(1)));
    }

    public boolean guardsActive() {
        return Integer.valueOf(5).equals(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.triggers WHERE is_disabled = 0 AND name IN
                ('tr_cash_coverage_immutable', 'tr_cash_event_immutable', 'tr_cash_event_source', 'tr_cash_collection', 'tr_cash_refund')
                """, new MapSqlParameterSource(), Integer.class));
    }

    public Totals totals(Instant start, Instant end) {
        return jdbc.queryForObject("SELECT " + SUMS + RANGE, range(start, end), (rs, row) -> totals(rs));
    }

    public List<Group> groups(Instant start, Instant end, RevenueGroupBy by) {
        String key = switch (by) {
            case METHOD -> "original_method";
            case ITEM_TYPE -> "original_item_type";
            case SPORT -> "CONVERT(VARCHAR(30), original_sport_id)";
        };
        String label = by == RevenueGroupBy.SPORT ? "original_sport_name" : key;
        return jdbc.query("SELECT COALESCE(" + key + ", 'UNALLOCATED') AS group_key, COALESCE(" + label
                + ", 'UNALLOCATED') AS group_label, " + SUMS + RANGE
                + " GROUP BY " + key + (key.equals(label) ? "" : ", " + label) + " ORDER BY group_key, group_label", range(start, end), (rs, row) -> {
                    Totals value = totals(rs);
                    return new Group(rs.getString("group_key"), rs.getString("group_label"), value.grossCashReceived(),
                            value.completedRefundCashOut(), value.netCashFlow(), value.collectionCount(), value.completedRefundCount());
                });
    }

    public void events(Instant start, Instant end, int limit, Consumer<Event> consumer) {
        jdbc.query("SELECT TOP (:limit) id, event_type, event_time_utc, payment_id, refund_request_id, amount, "
                + "original_method, original_item_type, original_sport_id, original_sport_name" + RANGE
                + " ORDER BY event_time_utc, id", range(start, end).addValue("limit", limit),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> consumer.accept(new Event(rs.getLong("id"),
                        rs.getString("event_type"), instant(rs.getTimestamp("event_time_utc")), nullableId(rs, "payment_id"),
                        nullableId(rs, "refund_request_id"), rs.getBigDecimal("amount"), rs.getString("original_method"),
                        rs.getString("original_item_type"), rs.getString("original_sport_id"), rs.getString("original_sport_name"))));
    }

    private static Totals totals(ResultSet rs) throws SQLException {
        return new Totals(rs.getBigDecimal("gross"), rs.getBigDecimal("payout"), rs.getLong("collections"), rs.getLong("refunds"));
    }
    private static Long nullableId(ResultSet rs, String column) throws SQLException {
        long id = rs.getLong(column); return rs.wasNull() ? null : id;
    }
    private static MapSqlParameterSource range(Instant start, Instant end) {
        return new MapSqlParameterSource("start", Timestamp.valueOf(LocalDateTime.ofInstant(start, ZoneOffset.UTC)))
                .addValue("end", Timestamp.valueOf(LocalDateTime.ofInstant(end, ZoneOffset.UTC)));
    }
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
