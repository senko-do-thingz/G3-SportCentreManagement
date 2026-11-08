package com.sportify.catalog.service.impl;

import com.sportify.catalog.service.ReceptionSupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Support request lifecycle: a request left RESOLVED past the Member reopen window becomes CLOSED. On by default; the
 * Maven test runs switch it off so a scheduled tick cannot rewrite fixtures mid-test.
 */
@Component
@ConditionalOnProperty(name = "app.support.auto-close.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SupportLifecycleJobs {

    private final ReceptionSupportService support;

    /** Daily at 00:15 Vietnam time, after the midnight membership maintenance. */
    @Scheduled(cron = "0 15 0 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void closeStaleResolvedRequests() {
        int closed = support.closeStaleResolved();
        if (closed > 0) log.info("Support requests closed after the reopen window: {}", closed);
    }
}
