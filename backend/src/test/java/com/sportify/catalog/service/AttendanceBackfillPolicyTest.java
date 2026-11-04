package com.sportify.catalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class AttendanceBackfillPolicyTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","48","PT0S","-PT1H","invalid","PT999999999999999999999H"})
    void missingOrInvalidWindowFailsClosed(String window) {
        assertThat(new AttendanceBackfillPolicy(true,window).activeWindow()).isEmpty();
    }
    @Test void disabledSwitchDoesNotActivateEvenWithWindow() {
        assertThat(new AttendanceBackfillPolicy(false,"PT48H").activeWindow()).isEmpty();
    }
    @Test void explicitPositiveFixtureWindowIsParsedWithoutFallback() {
        assertThat(new AttendanceBackfillPolicy(true," PT48H ").activeWindow()).contains(Duration.ofHours(48));
    }
    @Test void expiryIsInclusiveAndOverflowFailsClosed() {
        Instant end=Instant.parse("2026-11-01T03:00:00Z");
        assertThat(AttendanceBackfillService.within(end.plusSeconds(172800),end,Duration.ofHours(48))).isTrue();
        assertThat(AttendanceBackfillService.within(end.plusSeconds(172801),end,Duration.ofHours(48))).isFalse();
        assertThat(AttendanceBackfillService.within(end.minusNanos(1),end,Duration.ofHours(48))).isFalse();
        assertThat(AttendanceBackfillService.within(end,end,Duration.ofSeconds(Long.MAX_VALUE))).isFalse();
    }
    @Test void endUsesCenterZoneIncludingOvernightSession() {
        assertThat(AttendanceBackfillService.end(LocalDate.of(2026,11,1),LocalTime.of(9,0),LocalTime.of(10,0)))
            .isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
        assertThat(AttendanceBackfillService.end(LocalDate.of(2026,11,1),LocalTime.of(23,0),LocalTime.of(1,0)))
            .isEqualTo(Instant.parse("2026-11-01T18:00:00Z"));
    }
}
