package com.sportify.catalog.service;

import com.sportify.catalog.service.impl.MembershipScheduledJobs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MembershipScheduledJobsTest {

    @Mock
    private MembershipService membershipService;
    @org.mockito.Spy
    private java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-05T00:00:00Z"), java.time.ZoneId.of("UTC"));

    @InjectMocks
    private MembershipScheduledJobs jobs;

    @Test
    void processDailyMemberships_CallsServiceMethods() {
        
        jobs.processDailyMemberships();
        verify(membershipService).processScheduledMemberships(any(LocalDate.class));
        verify(membershipService).processExpiredMemberships(any(LocalDate.class));
    }
}
