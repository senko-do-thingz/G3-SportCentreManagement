package com.sportify.core.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditConfigTest {

    private final AuditConfig auditConfig = new AuditConfig();

    @ParameterizedTest
    @ValueSource(strings = {
            "America/New_York",
            "Europe/London",
            "Asia/Tokyo",
            "Pacific/Auckland"
    })
    @DisplayName("auditingDateTimeProvider returns LocalDateTime.now(clock) for different timezones")
    void auditingDateTimeProvider_WithFixedClock_ReturnsExactClockLocalDateTime(String zoneId) {
        Instant fixedInstant = Instant.parse("2026-10-15T12:30:45Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of(zoneId));

        DateTimeProvider provider = auditConfig.auditingDateTimeProvider(fixedClock);

        Optional<TemporalAccessor> now = provider.getNow();
        assertThat(now).isPresent();
        assertThat(now.get()).isEqualTo(LocalDateTime.now(fixedClock));
    }
}
