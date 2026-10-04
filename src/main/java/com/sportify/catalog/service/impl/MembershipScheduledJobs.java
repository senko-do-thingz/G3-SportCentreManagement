package com.sportify.catalog.service.impl;

import com.sportify.catalog.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipScheduledJobs {

    private final MembershipService membershipService;
    private final java.time.Clock clock;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Ho_Chi_Minh") // Daily at midnight in Vietnam
    public void processDailyMemberships() {
        LocalDate today = LocalDate.now(clock);
        log.info("Running scheduled membership processing for {}", today);
        membershipService.processScheduledMemberships(today);
        membershipService.processExpiredMemberships(today);
        log.info("Finished scheduled membership processing for {}", today);
    }
}
