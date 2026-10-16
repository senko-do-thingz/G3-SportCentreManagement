package com.sportify.catalog.dto;

import com.sportify.core.exception.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class CoachScheduleQueryTest {
    @Test void inclusive62DayLimit() {
        var from = LocalDate.of(2026, 10, 1);
        var query = CoachScheduleQuery.parse(from.toString(), from.plusDays(61).toString(), 19L);
        assertThat(query.from()).isEqualTo(from);
        assertThat(query.to()).isEqualTo(from.plusDays(61));
        assertThat(query.coachId()).isEqualTo(19L);
        assertThatThrownBy(() -> CoachScheduleQuery.parse(from.toString(), from.plusDays(62).toString(), null))
                .isInstanceOf(FieldValidationException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"2026-10-16", "0001-01-01", "9999-12-31", "2028-02-29"})
    void sameDayAndSqlDateExtremes(String date) {
        var query = CoachScheduleQuery.parse(date, date, null);
        assertThat(query.from()).isEqualTo(query.to()).isEqualTo(LocalDate.parse(date));
        assertThat(query.coachId()).isNull();
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-02-29", "2026-02-30", "0000-01-01", "+10000-01-01", "2026-1-01",
            "2026-10-16T00:00:00", "2026-10-16+07:00", " 2026-10-16"})
    void invalidOrMissingBounds(String value) {
        assertThatThrownBy(() -> CoachScheduleQuery.parse(value, "2026-10-16", null))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> CoachScheduleQuery.parse("2026-10-16", value, null))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test void reversedRange() {
        assertThatThrownBy(() -> CoachScheduleQuery.parse("2026-10-17", "2026-10-16", null))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(error -> assertThat(((FieldValidationException) error).getDetails()).containsKey("to"));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidCoachId(long id) {
        assertThatThrownBy(() -> CoachScheduleQuery.parse("2026-10-16", "2026-10-16", id))
                .isInstanceOf(FieldValidationException.class);
    }
}
