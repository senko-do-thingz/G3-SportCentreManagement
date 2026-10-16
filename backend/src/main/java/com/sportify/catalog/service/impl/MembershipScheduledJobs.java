package com.sportify.catalog.service.impl;

import com.sportify.catalog.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipScheduledJobs {

    private final MembershipService membershipService;
    private final ScheduledMaintenanceService maintenanceService;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Ho_Chi_Minh") // Daily at midnight in Vietnam
    public void processDailyMemberships() {
        var result = maintenanceService.runDailyMaintenance();
        log.info("Maintenance committed for {}: cancelledPendingCards={}, expiredCards={}, "
                        + "cancelledPendingRegistrations={}, expiredRegistrations={}",
                result.businessDate(), result.cancelledPendingCards(), result.expiredCards(),
                result.cancelledPendingRegistrations(), result.expiredRegistrations());
        // Preserve the two existing legacy membership workflows and their separate transactions.
        membershipService.processScheduledMemberships(result.businessDate());
        membershipService.processExpiredMemberships(result.businessDate());
    }
}
