package com.sportify.catalog.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaintenanceTestResourcesTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test void aFailedDeleteDoesNotPreventOtherOwnedTablesFromBeingCleaned() {
        var resources = new MaintenanceTestResources(jdbc);
        resources.remember("sport",71L); resources.remember("sport_package",81L);
        resources.remember("member_profile",91L); resources.remember("user_account",91L);
        doThrow(new IllegalStateException("Synthetic package deletion failure"))
                .when(jdbc).update("DELETE FROM sport_package WHERE id IN (?)",81L);
        assertThatThrownBy(resources::close).isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getSuppressed()).hasSize(1));
        var order = inOrder(jdbc);
        order.verify(jdbc).update("DELETE FROM sport_package WHERE id IN (?)",81L);
        order.verify(jdbc).update("DELETE FROM sport WHERE id IN (?)",71L);
        order.verify(jdbc).update("DELETE FROM member_profile WHERE user_id IN (?)",91L);
        order.verify(jdbc).update("DELETE FROM user_account WHERE id IN (?)",91L);
    }

    @Test void barrierReleasesAndWorkerTerminationPrecedeFixtureDeletion() throws Exception {
        var resources = new MaintenanceTestResources(jdbc);
        var release = resources.releaseGate(); var started = new CompletableFuture<Void>();
        var ended = new CompletableFuture<Void>(); var worker = resources.pool();
        worker.submit(() -> { started.complete(null); try { release.join(); } finally { ended.complete(null); } });
        started.get(5,TimeUnit.SECONDS);
        resources.remember("sport",71L);
        doAnswer(call -> { assertThat(ended.isDone()).isTrue(); assertThat(worker.isTerminated()).isTrue(); return 1; })
                .when(jdbc).update("DELETE FROM sport WHERE id IN (?)",71L);
        try { resources.close(); } finally { worker.shutdownNow(); }
        assertThat(worker.isTerminated()).isTrue();
        verify(jdbc).update("DELETE FROM sport WHERE id IN (?)",71L);
    }

    @Test void aWorkerTerminationFailureStillAttemptsDatabaseCleanup() throws Exception {
        ExecutorService worker = mock(ExecutorService.class);
        when(worker.awaitTermination(15,TimeUnit.SECONDS)).thenReturn(false);
        var resources = new MaintenanceTestResources(jdbc, () -> worker);
        resources.pool(); resources.remember("sport",71L);
        assertThatThrownBy(resources::close).isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getSuppressed()[0]).hasMessageContaining("worker did not terminate"));
        verify(worker).shutdownNow();
        verify(jdbc).update("DELETE FROM sport WHERE id IN (?)",71L);
    }

    @Test void interruptionIsRestoredAfterAllCleanupAttempts() throws Exception {
        ExecutorService worker = mock(ExecutorService.class);
        when(worker.awaitTermination(15,TimeUnit.SECONDS)).thenThrow(new InterruptedException("Synthetic interrupt"));
        var resources = new MaintenanceTestResources(jdbc, () -> worker);
        resources.pool(); resources.remember("sport",71L);
        try {
            assertThatThrownBy(resources::close).isInstanceOf(AssertionError.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(jdbc).update("DELETE FROM sport WHERE id IN (?)",71L);
        } finally { Thread.interrupted(); }
    }

    @Test void generatedReceiptChildrenAreDiscoveredOnlyThroughOwnedPaymentIds() {
        var resources = new MaintenanceTestResources(jdbc);
        when(jdbc.queryForObject(eq("INSERT payment fixture"),eq(Long.class),any(Object[].class))).thenReturn(77L);
        assertThat(resources.insert("payment","INSERT payment fixture")).isEqualTo(77L);
        resources.remember("user_account",91L);
        when(jdbc.queryForList("SELECT id FROM activity_log WHERE actor_user_id IN (?)",Long.class,91L)).thenReturn(List.of(17L));
        when(jdbc.queryForList("SELECT id FROM invoice WHERE payment_id IN (?)",Long.class,77L)).thenReturn(List.of(88L));
        when(jdbc.queryForList("SELECT id FROM invoice_line WHERE invoice_id IN (?)",Long.class,88L)).thenReturn(List.of(99L));
        resources.close();
        var order = inOrder(jdbc);
        order.verify(jdbc).update("DELETE FROM activity_log WHERE id IN (?)",17L);
        order.verify(jdbc).update("DELETE FROM invoice_line WHERE id IN (?)",99L);
        order.verify(jdbc).update("DELETE FROM invoice WHERE id IN (?)",88L);
        order.verify(jdbc).update("DELETE FROM payment WHERE id IN (?)",77L);
        order.verify(jdbc).update("DELETE FROM user_account WHERE id IN (?)",91L);
    }
}
