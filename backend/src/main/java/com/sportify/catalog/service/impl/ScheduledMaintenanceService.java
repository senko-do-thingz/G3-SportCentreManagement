package com.sportify.catalog.service.impl;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Four set-based lifecycle transitions; no payment, booking, credit or audit writes. */
@Service
@RequiredArgsConstructor
public class ScheduledMaintenanceService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;
    private final Clock clock;

    /**
     * A dedicated persistence context prevents this bulk work from detaching a caller's entities.
     * The proxy returns only after commit; the scheduler logs counts after that return.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaintenanceResult runDailyMaintenance() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDateTime cutoff = today.minusDays(7).atStartOfDay();
        int cancelledCards = cards.cancelOverduePending(cutoff, now);
        int expiredCards = cards.expireActive(today, now);
        int cancelledRegistrations = registrations.cancelOverduePending(cutoff, now);
        int expiredRegistrations = registrations.expireActive(today, now);
        return new MaintenanceResult(today, cancelledCards, expiredCards,
                cancelledRegistrations, expiredRegistrations);
    }

    public record MaintenanceResult(LocalDate businessDate, int cancelledPendingCards, int expiredCards,
                                    int cancelledPendingRegistrations, int expiredRegistrations) {
    }
}
