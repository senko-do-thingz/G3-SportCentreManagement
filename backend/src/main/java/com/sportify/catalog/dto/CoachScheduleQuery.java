package com.sportify.catalog.dto;

import com.sportify.core.exception.FieldValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Same inclusive, bounded calendar window as BE-10; no hidden default window. */
public record CoachScheduleQuery(LocalDate from, LocalDate to, Long coachId) {
    public static CoachScheduleQuery parse(String from, String to, Long coachId) {
        if (coachId != null && coachId <= 0) throw new FieldValidationException("coachId", "Must be positive");
        LocalDate lower = date(from, "from");
        LocalDate upper = date(to, "to");
        if (lower.isAfter(upper)) throw new FieldValidationException("to", "Must not precede from");
        if (ChronoUnit.DAYS.between(lower, upper) + 1 > 62) {
            throw new FieldValidationException("to", "Calendar range must not exceed 62 days inclusive");
        }
        return new CoachScheduleQuery(lower, upper, coachId);
    }

    private static LocalDate date(String value, String field) {
        try {
            if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new DateTimeParseException("Invalid date", "", 0);
            }
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw new DateTimeParseException("Out of range", "", 0);
            return date;
        } catch (DateTimeParseException ex) {
            throw new FieldValidationException(field, "Use YYYY-MM-DD, years 0001-9999");
        }
    }
}
