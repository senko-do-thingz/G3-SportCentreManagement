package com.sportify.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Class session lifecycle: PUBLISHED sessions become COMPLETED shortly after their end time. On by default; the Maven
 * test runs switch it off so a 10-minute tick cannot rewrite fixtures mid-test.
 */
@Component
@ConditionalOnProperty(name = "app.sessions.auto-complete.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SessionLifecycleJobs {

    private final ScheduledMaintenanceService maintenanceService;

    /** Every 10 minutes, so a session reads COMPLETED soon after it ends rather than only after midnight. */
    @Scheduled(cron = "0 */10 * * * ?", zone = "Asia/Ho_Chi_Minh")
    public void completeEndedSessions() {
        int completed = maintenanceService.completeEndedSessions();
        if (completed > 0) log.info("Sessions marked COMPLETED: {}", completed);
    }
}
