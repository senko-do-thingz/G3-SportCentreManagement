package com.sportify.identity.dto;

import com.sportify.core.audit.AuditAction;
import com.sportify.core.exception.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.lang.reflect.Modifier;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ActivityLogQueryTest {
    ActivityLogQuery parse(String from, String to) { return ActivityLogQuery.parse(null, null, from, to, null, 0, 20); }
    @Test void defaults() {
        assertThat(parse(null, null)).isEqualTo(new ActivityLogQuery(null, null, null, null, false, null, 0, 20));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void blankSearch(String search) {
        assertThat(ActivityLogQuery.parse(null, " ", null, null, search, 0, 20).search()).isNull();
    }
    @Test void combinedFilters() {
        var q = ActivityLogQuery.parse(17L, " PASSWORD_CHANGED ", "2026-10-01", "2026-10-16", "  A%_[\\Z  ", 2, 5);
        assertThat(q.actorId()).isEqualTo(17L); assertThat(q.action()).isEqualTo("PASSWORD_CHANGED");
        assertThat(q.search()).isEqualTo("a%_[\\z"); assertThat(q.from()).isEqualTo("2026-10-01T00:00:00");
        assertThat(q.to()).isEqualTo("2026-10-17T00:00:00"); assertThat(q.toExclusive()).isTrue();
        assertThat(q.page()).isEqualTo(2); assertThat(q.size()).isEqualTo(5);
    }
    @ParameterizedTest @ValueSource(strings = {"2026-10-16", "9999-12-31", "0001-01-01"})
    void sameDayAndExtremes(String date) {
        var q = parse(date, date);
        assertThat(q.from().toLocalDate()).isEqualTo(LocalDate.parse(date));
        assertThat(q.to()).isEqualTo(date.equals("9999-12-31") ? LocalDateTime.of(9999,12,31,23,59,59)
                : LocalDate.parse(date).plusDays(1).atStartOfDay());
        assertThat(q.toExclusive()).isEqualTo(!date.equals("9999-12-31"));
    }
    @Test void timestampsAreInclusive() {
        var q = parse("2026-10-16T12:30:45", "2026-10-16T12:30:45");
        assertThat(q.from()).isEqualTo(q.to()); assertThat(q.toExclusive()).isFalse();
    }
    @Test void independentOfJvmTimezone() {
        var previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var first = parse("2026-10-16", "2026-10-16");
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            assertThat(parse("2026-10-16", "2026-10-16")).isEqualTo(first);
        } finally { TimeZone.setDefault(previous); }
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "2026-02-30", "2026-13-01", "0000-01-01", "+10000-01-01",
            "2026-10-16T12:30", "2026-10-16T12:30:45Z", "2026-10-16T12:30:45+07:00", "2026-10-16T12:30:45.1", "2026-10-16T24:00:00"})
    void malformedDate(String date) {
        assertThatThrownBy(() -> parse(date, null)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> parse(null, date)).isInstanceOf(FieldValidationException.class);
    }
    @ParameterizedTest @CsvSource({"2026-10-17,2026-10-16", "2026-10-17T00:00:00,2026-10-16", "2026-10-16T12:00:01,2026-10-16T12:00:00"})
    void reversed(String from, String to) { assertThatThrownBy(() -> parse(from,to)).isInstanceOf(FieldValidationException.class); }
    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void invalidPaging(int page,int size) {
        assertThatThrownBy(() -> ActivityLogQuery.parse(null,null,null,null,null,page,size)).isInstanceOf(FieldValidationException.class);
    }
    @ParameterizedTest @ValueSource(longs = {0,-1,Long.MIN_VALUE})
    void invalidActor(long id) {
        assertThatThrownBy(() -> ActivityLogQuery.parse(id,null,null,null,null,0,20)).isInstanceOf(FieldValidationException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"UNKNOWN", "password_changed", "LOGIN OR 1=1"})
    void unsupportedAction(String action) {
        assertThatThrownBy(() -> ActivityLogQuery.parse(null,action,null,null,null,0,20)).isInstanceOf(FieldValidationException.class);
    }
    @Test void searchLength() {
        assertThat(ActivityLogQuery.parse(null,null,null,null,"a".repeat(200),0,100).search()).hasSize(200);
        assertThatThrownBy(() -> ActivityLogQuery.parse(null,null,null,null,"a".repeat(201),0,20)).isInstanceOf(FieldValidationException.class);
    }
    @Test void allCurrentAndLegacyActions() throws Exception {
        Set<String> constants = new HashSet<>();
        for (var field : AuditAction.class.getDeclaredFields()) {
            if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
                String action=(String)field.get(null); constants.add(action);
                assertThat(ActivityLogQuery.parse(null,action,null,null,null,0,20).action()).isEqualTo(action);
            }
        }
        assertThat(ActivityLogQuery.ACTIONS).isEqualTo(constants);
    }
}
