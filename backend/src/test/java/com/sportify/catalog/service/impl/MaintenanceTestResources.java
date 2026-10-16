package com.sportify.catalog.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Owns committed fixture identities, worker releases and failure-tolerant teardown. */
final class MaintenanceTestResources implements AutoCloseable {
    static final String FAILURE_TRIGGER = "trg_be06_r3_fourth_update";
    static final List<String> DELETE_ORDER = List.of("activity_log", "check_in", "booking", "class_session",
            "invoice_line", "invoice", "member_card", "sport_package_registration", "payment",
            "sport_package", "sport", "member_profile", "user_account");
    private final JdbcTemplate jdbc;
    private final Supplier<ExecutorService> workers;
    private final Map<String, List<Long>> ids = new LinkedHashMap<>();
    private final List<ExecutorService> pools = new ArrayList<>();
    private final List<CompletableFuture<Void>> releases = new ArrayList<>();

    MaintenanceTestResources(JdbcTemplate jdbc) {
        this(jdbc, () -> Executors.newFixedThreadPool(2));
    }

    MaintenanceTestResources(JdbcTemplate jdbc, Supplier<ExecutorService> workers) {
        this.jdbc = jdbc;
        this.workers = workers;
    }

    long insert(String table, String sql, Object... args) {
        if (!DELETE_ORDER.contains(table)) throw new IllegalArgumentException("Unknown fixture table: " + table);
        long id = Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
        remember(table, id);
        return id;
    }

    void remember(String table, long id) {
        if (!DELETE_ORDER.contains(table)) throw new IllegalArgumentException("Unknown fixture table: " + table);
        ids.computeIfAbsent(table, ignored -> new ArrayList<>()).add(id);
    }

    ExecutorService pool() {
        var pool = workers.get();
        pools.add(pool);
        return pool;
    }

    CompletableFuture<Void> releaseGate() {
        var gate = new CompletableFuture<Void>();
        releases.add(gate);
        return gate;
    }

    private void discover(String table, String foreignKey, List<Long> parents) {
        if (parents.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(parents.size(), "?"));
        for (long id : jdbc.queryForList("SELECT id FROM " + table + " WHERE " + foreignKey
                + " IN (" + placeholders + ")", Long.class, parents.toArray())) remember(table, id);
    }

    private List<Long> owned(String table) {
        return ids.getOrDefault(table, List.of());
    }

    @Override
    public void close() {
        List<Throwable> failures = new ArrayList<>();
        // Release application barriers before interrupting or joining database workers.
        releases.forEach(gate -> gate.complete(null));
        pools.forEach(ExecutorService::shutdownNow);
        boolean interrupted = false;
        for (var pool : pools) {
            try {
                if (!pool.awaitTermination(15, TimeUnit.SECONDS)) {
                    failures.add(new AssertionError("BE-06 worker did not terminate before fixture cleanup"));
                }
            } catch (InterruptedException failure) {
                interrupted = true;
                failures.add(failure);
            }
        }
        attempt(failures, () -> jdbc.execute("IF OBJECT_ID('" + FAILURE_TRIGGER
                + "', 'TR') IS NOT NULL DROP TRIGGER " + FAILURE_TRIGGER));
        // Production confirmation generates these children; discover only through owned parents.
        attempt(failures, () -> discover("activity_log", "actor_user_id", owned("user_account")));
        attempt(failures, () -> discover("invoice", "payment_id", owned("payment")));
        attempt(failures, () -> discover("invoice_line", "invoice_id", owned("invoice")));
        for (String table : DELETE_ORDER) {
            attempt(failures, () -> {
                List<Long> rows = owned(table);
                if (!rows.isEmpty()) {
                    String placeholders = String.join(",", java.util.Collections.nCopies(rows.size(), "?"));
                    jdbc.update("DELETE FROM " + table + " WHERE " + (table.equals("member_profile") ? "user_id" : "id")
                            + " IN (" + placeholders + ")", rows.toArray());
                }
            });
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError("BE-06 teardown failed; all cleanup steps were attempted");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void attempt(List<Throwable> failures, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            failures.add(failure);
        }
    }
}
