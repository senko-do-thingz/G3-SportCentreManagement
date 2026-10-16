package com.sportify.catalog.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sportify.catalog.service.impl.MembershipScheduledJobs;
import com.sportify.catalog.service.impl.ScheduledMaintenanceService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipScheduledJobsTest {
    private final MembershipService memberships = mock(MembershipService.class);
    private final ScheduledMaintenanceService maintenance = mock(ScheduledMaintenanceService.class);
    private final MembershipScheduledJobs jobs = new MembershipScheduledJobs(memberships, maintenance);
    private final LocalDate date = LocalDate.of(2026, 10, 16);

    @Test
    void delegatesOnceAndRetainsBothLegacyOperationsOnSameDate() {
        when(maintenance.runDailyMaintenance()).thenReturn(result(0, 0, 0, 0));
        jobs.processDailyMemberships();
        var order = inOrder(maintenance, memberships);
        order.verify(maintenance).runDailyMaintenance();
        order.verify(memberships).processScheduledMemberships(date);
        order.verify(memberships).processExpiredMemberships(date);
        verifyNoMoreInteractions(maintenance, memberships);
    }

    @Test
    void retainsSingleDailyVietnamTrigger() throws Exception {
        var scheduled = MembershipScheduledJobs.class.getMethod("processDailyMemberships").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(Arrays.stream(MembershipScheduledJobs.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))).hasSize(1);
        assertThat(Arrays.stream(ScheduledMaintenanceService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))).isEmpty();
    }

    @Test
    void logsReturnedCommittedCountsWithoutPersonalDetails() {
        when(maintenance.runDailyMaintenance()).thenReturn(result(3, 5, 2, 4));
        withLogs(logs -> {
            jobs.processDailyMemberships();
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                    "Maintenance committed for 2026-10-16: cancelledPendingCards=3, expiredCards=5, "
                            + "cancelledPendingRegistrations=2, expiredRegistrations=4");
        });
    }

    @Test
    void propagatesMaintenanceRollbackAndDoesNotLogSuccessOrRunLegacyWork() {
        RuntimeException failure = new RuntimeException("Synthetic commit failure");
        when(maintenance.runDailyMaintenance()).thenThrow(failure);
        withLogs(logs -> {
            assertThatThrownBy(jobs::processDailyMemberships).isSameAs(failure);
            assertThat(logs.list).isEmpty();
        });
        verifyNoInteractions(memberships);
    }

    @Test
    void propagatesLegacyFailureAfterLoggingAlreadyCommittedMaintenance() {
        when(maintenance.runDailyMaintenance()).thenReturn(result(0, 0, 0, 0));
        doThrow(new IllegalStateException("Synthetic legacy failure")).when(memberships).processScheduledMemberships(date);
        withLogs(logs -> {
            assertThatThrownBy(jobs::processDailyMemberships).isInstanceOf(IllegalStateException.class);
            assertThat(logs.list).hasSize(1);
        });
        verify(memberships, never()).processExpiredMemberships(any());
    }

    private ScheduledMaintenanceService.MaintenanceResult result(int a, int b, int c, int d) {
        return new ScheduledMaintenanceService.MaintenanceResult(date, a, b, c, d);
    }

    private void withLogs(java.util.function.Consumer<ListAppender<ILoggingEvent>> check) {
        Logger logger = (Logger) LoggerFactory.getLogger(MembershipScheduledJobs.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try { check.accept(appender); } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
