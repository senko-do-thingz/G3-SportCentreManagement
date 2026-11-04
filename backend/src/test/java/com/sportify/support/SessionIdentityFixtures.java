package com.sportify.support;

import com.sportify.catalog.service.SessionCodeAllocator;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Identity-aware setup for disposable SQL fixtures. No application or migration path calls this class. */
public final class SessionIdentityFixtures {
    private record Group(LocalDate date, String sport) { }
    private SessionIdentityFixtures() { }

    public static String reserve(JdbcTemplate jdbc, LocalDate date, long sportId) {
        return reserve(jdbc, date, jdbc.queryForObject("SELECT code FROM sport WHERE id=?", String.class, sportId));
    }

    public static String reserve(JdbcTemplate jdbc, LocalDate date, String sport) {
        var source = Objects.requireNonNull(jdbc.getDataSource());
        var connection = DataSourceUtils.getConnection(source);
        boolean owned = !DataSourceUtils.isConnectionTransactional(connection, source);
        boolean auto = true;
        try {
            auto = connection.getAutoCommit();
            if (owned) connection.setAutoCommit(false);
            var bound = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            String code = new SessionCodeAllocator(bound).reserve(date, sport);
            if (owned) connection.commit();
            return code;
        } catch (SQLException | RuntimeException failure) {
            if (owned) try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw new IllegalStateException("Isolated fixture identity allocation failed", failure);
        } finally {
            if (owned) try { connection.setAutoCommit(auto); } catch (SQLException failure) { throw new IllegalStateException(failure); }
            DataSourceUtils.releaseConnection(connection, source);
        }
    }

    /** Fixture date mutation is explicit and atomic; uses the same date/code invariant as production rescheduling. */
    public static void moveFixture(JdbcTemplate jdbc, long id, LocalDate date) {
        long sport = jdbc.queryForObject("SELECT sport_id FROM class_session WHERE id=?", Long.class, id);
        jdbc.update("UPDATE class_session SET session_date=?,session_code=? WHERE id=?", date, reserve(jdbc, date, sport), id);
    }

    public record Snapshot(Map<String, Integer> counters, java.util.Set<String> codes) { }

    public static Snapshot snapshot(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String, Integer>();
        jdbc.query("SELECT session_date,sport_code,last_value FROM session_code_counter", rows -> {
            result.put(rows.getDate(1).toLocalDate() + "|" + rows.getString(2), rows.getInt(3));
        });
        return new Snapshot(result, new java.util.HashSet<>(jdbc.queryForList("SELECT session_code FROM session_code_history", String.class)));
    }

    /** Emulate fixture rollback after owned rows are removed, retaining all prior and surviving allocations. */
    public static void restoreAfterFixtureCleanup(JdbcTemplate jdbc, Snapshot snapshot) {
        Map<String, Integer> before = snapshot.counters();
        // Disposable fixtures only: remove newly orphaned fixture tombstones, never baseline or live history.
        for (String code : jdbc.queryForList("SELECT session_code FROM session_code_history WHERE session_id IS NULL", String.class)) {
            if (!snapshot.codes().contains(code)) jdbc.update("DELETE FROM session_code_history WHERE session_code=? AND session_id IS NULL", code);
        }
        var groups = jdbc.query("SELECT session_date,sport_code FROM session_code_counter",
                (rows, index) -> new Group(rows.getDate(1).toLocalDate(), rows.getString(2)));
        for (var group : groups) {
            int prior = before.getOrDefault(group.date() + "|" + group.sport(), 0);
            int live = jdbc.queryForObject("SELECT COALESCE(MAX(CONVERT(INT,RIGHT(session_code,2))),0) FROM class_session "
                    + "WHERE session_date=? AND SUBSTRING(session_code,7,2)=?", Integer.class, group.date(), group.sport());
            int retained = Math.max(prior, live);
            if (retained == 0) jdbc.update("DELETE FROM session_code_counter WHERE session_date=? AND sport_code=?", group.date(), group.sport());
            else jdbc.update("UPDATE session_code_counter SET last_value=? WHERE session_date=? AND sport_code=?", retained, group.date(), group.sport());
        }
    }
}
