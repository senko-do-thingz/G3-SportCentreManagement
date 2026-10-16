package com.sportify.catalog.service.impl;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduledMaintenanceServiceTest {
    private final MemberCardRepository cards = mock(MemberCardRepository.class);
    private final SportPackageRegistrationRepository registrations = mock(SportPackageRegistrationRepository.class);

    @ParameterizedTest
    @CsvSource({
            "2026-10-15T16:59:59Z,2026-10-15,2026-10-08T00:00:00,2026-10-15T23:59:59",
            "2026-10-15T17:00:00Z,2026-10-16,2026-10-09T00:00:00,2026-10-16T00:00:00",
            "2026-10-16T16:59:59Z,2026-10-16,2026-10-09T00:00:00,2026-10-16T23:59:59",
            "2026-12-31T17:00:00Z,2027-01-01,2026-12-25T00:00:00,2027-01-01T00:00:00",
            "2028-02-29T17:00:00Z,2028-03-01,2028-02-23T00:00:00,2028-03-01T00:00:00"
    })
    void usesExclusiveCalendarMidnightWithOneVietnamDate(String instant, LocalDate date,
                                                         LocalDateTime cutoff, LocalDateTime now) {
        var service = new ScheduledMaintenanceService(cards, registrations,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        var result = service.runDailyMaintenance();
        assertThat(result.businessDate()).isEqualTo(date);
        var order = inOrder(cards, registrations);
        order.verify(cards).cancelOverduePending(cutoff, now);
        order.verify(cards).expireActive(date, now);
        order.verify(registrations).cancelOverduePending(cutoff, now);
        order.verify(registrations).expireActive(date, now);
        verifyNoMoreInteractions(cards, registrations);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-16T00:00:00,false", "2026-10-15T23:59:59,false",
            "2026-10-10T00:00:00,false", "2026-10-09T00:00:00,false",
            "2026-10-09T23:59:59,false", "2026-10-08T23:59:59,true",
            "2026-10-08T00:00:00,true", "2026-10-01T12:00:00,true"
    })
    void calculatedCutoffDistinguishesCreationCalendarDays(LocalDateTime created, boolean eligible) {
        service("2026-10-15T19:00:00Z").runDailyMaintenance();
        var cutoff = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cards).cancelOverduePending(cutoff.capture(), any());
        // Eligibility uses the actual exclusive bound supplied to both database queries.
        assertThat(created.isBefore(cutoff.getValue())).isEqualTo(eligible);
        verify(registrations).cancelOverduePending(eq(cutoff.getValue()), any());
    }

    @Test
    void returnsAllActualAffectedCountsIncludingZero() {
        LocalDateTime now = LocalDateTime.parse("2026-10-16T02:00:00");
        LocalDateTime cutoff = LocalDateTime.parse("2026-10-09T00:00:00");
        when(cards.cancelOverduePending(cutoff, now)).thenReturn(3);
        when(cards.expireActive(now.toLocalDate(), now)).thenReturn(5);
        when(registrations.cancelOverduePending(cutoff, now)).thenReturn(2);
        when(registrations.expireActive(now.toLocalDate(), now)).thenReturn(4);
        var result = service("2026-10-15T19:00:00Z").runDailyMaintenance();
        assertThat(result).isEqualTo(new ScheduledMaintenanceService.MaintenanceResult(now.toLocalDate(), 3, 5, 2, 4));
    }

    @Test
    void zeroCountsRemainZero() {
        var result = service("2026-10-15T19:00:00Z").runDailyMaintenance();
        assertThat(result).isEqualTo(new ScheduledMaintenanceService.MaintenanceResult(LocalDate.of(2026, 10, 16), 0, 0, 0, 0));
    }

    @Test
    void samplesClockOnceEvenAcrossMidnight() {
        AtomicInteger reads = new AtomicInteger();
        Clock ticking = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.ofHours(-10); }
            @Override public Clock withZone(ZoneId zone) { throw new AssertionError("Instant should be converted explicitly"); }
            @Override public Instant instant() {
                return Instant.parse("2026-10-15T16:59:59Z").plusSeconds(reads.getAndIncrement());
            }
        };
        var result = new ScheduledMaintenanceService(cards, registrations, ticking).runDailyMaintenance();
        assertThat(reads).hasValue(1);
        assertThat(result.businessDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        verify(registrations).expireActive(LocalDate.of(2026, 10, 15), LocalDateTime.parse("2026-10-15T23:59:59"));
    }

    @Test
    void propagatesFailureWithoutRunningLaterUpdates() {
        RuntimeException failure = new RuntimeException("Synthetic database failure");
        when(cards.expireActive(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> service("2026-10-15T19:00:00Z").runDailyMaintenance()).isSameAs(failure);
        verify(cards).cancelOverduePending(any(), any());
        verifyNoInteractions(registrations);
    }

    @Test
    void ownsAnIsolatedWriteTransactionAndHasNoAuditDependency() throws Exception {
        var tx = ScheduledMaintenanceService.class.getMethod("runDailyMaintenance").getAnnotation(Transactional.class);
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx.readOnly()).isFalse();
        assertThat(ScheduledMaintenanceService.class.getDeclaredFields())
                .noneMatch(f -> f.getType().getName().contains("Audit"));
    }

    private ScheduledMaintenanceService service(String instant) {
        return new ScheduledMaintenanceService(cards, registrations, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
