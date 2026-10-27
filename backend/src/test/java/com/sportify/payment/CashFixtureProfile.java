package com.sportify.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in diagnostics for synthetic CI fixtures, never a production connection or a test bypass. */
final class CashFixtureProfile implements AutoCloseable {
    private final boolean enabled = Boolean.getBoolean("ci.cash.profile");
    private final String test;
    private final long started = System.nanoTime();

    CashFixtureProfile(String test) { this.test = test; }

    void stage(String name, Runnable action) {
        long start = System.nanoTime();
        try { action.run(); }
        finally { record(name, start); }
    }

    void sqlStage(String name, DataSource source, Runnable action) {
        if (!enabled) { action.run(); return; }
        var jdbc = new JdbcTemplate(source);
        var samples = new AtomicInteger();
        var diagnosticFailure = new AtomicReference<RuntimeException>();
        var monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            if (samples.incrementAndGet() > 90) return;
            try {
                System.out.println("CI_CASH_SQL stage=" + name + " requests=" + jdbc.queryForList("""
                        SELECT session_id, blocking_session_id, status, command, wait_type,
                            cpu_time, total_elapsed_time, logical_reads, reads, writes,
                            SUBSTRING(t.text, r.statement_start_offset / 2 + 1,
                                (CASE WHEN r.statement_end_offset = -1 THEN DATALENGTH(t.text)
                                    ELSE r.statement_end_offset END - r.statement_start_offset) / 2 + 1) AS statement
                        FROM sys.dm_exec_requests r CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
                        WHERE r.database_id = DB_ID() AND r.session_id <> @@SPID
                        """));
            } catch (RuntimeException failure) {
                diagnosticFailure.compareAndSet(null, failure);
            }
        }, 0, 10, TimeUnit.SECONDS);
        try { stage(name, action); }
        finally {
            monitor.shutdownNow();
            try {
                if (!monitor.awaitTermination(10, TimeUnit.SECONDS)) throw new IllegalStateException("Profile monitor did not stop");
                var plans = jdbc.queryForList("""
                        SELECT OBJECT_NAME(object_id) AS trigger_name, execution_count, total_worker_time,
                            total_elapsed_time, total_logical_reads, CONVERT(NVARCHAR(MAX), p.query_plan) AS plan_xml
                        FROM sys.dm_exec_trigger_stats s CROSS APPLY sys.dm_exec_query_plan(s.plan_handle) p
                        WHERE database_id = DB_ID()
                        """);
                Path directory = Path.of("target", "cash-profile");
                Files.createDirectories(directory);
                int index = 0;
                for (var plan : plans) {
                    Object xml = plan.remove("plan_xml");
                    System.out.println("CI_CASH_TRIGGER stage=" + name + " stats=" + plan);
                    if (xml != null) Files.writeString(directory.resolve(name + "-" + index++ + ".xml"), xml.toString());
                }
                if (diagnosticFailure.get() != null) throw new IllegalStateException("SQL sampling failed", diagnosticFailure.get());
            } catch (Exception failure) {
                throw new IllegalStateException("Cash fixture profile collection failed", failure);
            }
        }
    }

    private void record(String stage, long start) {
        if (enabled) System.out.printf("CI_CASH_PROFILE test=%s stage=%s elapsed_ms=%.3f%n",
                test, stage, (System.nanoTime() - start) / 1_000_000.0);
    }

    @Override public void close() { record("method.total", started); }
}
