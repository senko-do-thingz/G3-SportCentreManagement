package com.sportify.identity.dto;

import com.sportify.core.audit.AuditAction;
import com.sportify.core.common.PagingDefaults;
import com.sportify.core.exception.FieldValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/** Validated query in the same local wall-clock time as activity_log.created_at. */
public record ActivityLogQuery(Long actorId, String action, LocalDateTime from,
        LocalDateTime to, boolean toExclusive, String search, int page, int size) {
    public static final Set<String> ACTIONS = Set.of(
            AuditAction.REGISTER,
            AuditAction.USER_CREATED,
            AuditAction.USER_UPDATED,
            AuditAction.USER_STATUS_CHANGED,
            AuditAction.ROLE_UPDATED,
            AuditAction.ROLE_PERMISSIONS_UPDATED,
            AuditAction.LOGIN,
            AuditAction.LOGIN_FAILED,
            AuditAction.REFRESH_TOKEN,
            AuditAction.PASSWORD_CHANGED,
            AuditAction.PASSWORD_RESET_REQUESTED,
            AuditAction.PASSWORD_RESET_COMPLETED,
            AuditAction.TOKEN_REUSE_DETECTED,
            AuditAction.MEMBERSHIP_REGISTERED,
            AuditAction.MEMBERSHIP_RENEWED,
            AuditAction.MEMBERSHIP_CANCELLED,
            AuditAction.MEMBERSHIP_ACTIVATED,
            AuditAction.MEMBERSHIP_ACTIVATED_BY_JOB,
            AuditAction.MEMBERSHIP_EXPIRED_BY_JOB,
            AuditAction.MEMBERSHIP_AUTO_CANCELLED,
            AuditAction.MEMBER_CHECKED_IN,
            AuditAction.PACKAGE_CREATED,
            AuditAction.PACKAGE_UPDATED,
            AuditAction.CARD_TIER_UPDATED,
            AuditAction.CLASS_CREATED,
            AuditAction.CLASS_UPDATED,
            AuditAction.CLASS_STATUS_CHANGED,
            AuditAction.SESSION_CREATED,
            AuditAction.SESSION_UPDATED,
            AuditAction.SESSION_CANCELLED,
            AuditAction.BOOKING_CREATED,
            AuditAction.BOOKING_CANCELLED,
            AuditAction.PAYMENT_CONFIRMED,
            AuditAction.PAYMENT_FAILED,
            AuditAction.PAYMENT_AMOUNT_MISMATCH,
            AuditAction.REFUND_COMPLETED);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
    private static final LocalDateTime MAX_TIME = MAX_DATE.atTime(23, 59, 59);

    public static ActivityLogQuery parse(Long actorId, String action, String from, String to,
            String search, int page, int size) {
        if (actorId != null && actorId <= 0) fail("actorId", "Must be positive");
        if (page < 0) fail("page", "Must be zero or greater");
        if (size < 1 || size > PagingDefaults.MAX_PAGE_SIZE) fail("size", "Must be between 1 and 100");
        if ((long) page * size > Integer.MAX_VALUE) fail("page", "Page offset is too large");
        String normalizedAction = action == null || action.isBlank() ? null : action.trim();
        if (normalizedAction != null && !ACTIONS.contains(normalizedAction)) fail("action", "Unsupported audit action");
        LocalDateTime lower = boundary(from, "from");
        LocalDateTime upper = boundary(to, "to");
        boolean exclusive = false;
        if (upper != null && to.length() == 10) {
            exclusive = !upper.toLocalDate().equals(MAX_DATE);
            upper = exclusive ? upper.plusDays(1) : MAX_TIME;
        }
        if (lower != null && upper != null && (lower.isAfter(upper) || (exclusive && lower.equals(upper)))) {
            fail("to", "Must not precede from");
        }
        String text = search == null || search.isBlank() ? null : search.strip();
        if (text != null && text.length() > 200) fail("search", "Must be at most 200 characters");
        return new ActivityLogQuery(actorId, normalizedAction, lower, upper, exclusive,
                text == null ? null : text.toLowerCase(Locale.ROOT), page, size);
    }

    private static LocalDateTime boundary(String value, String field) {
        if (value == null) return null;
        try {
            LocalDateTime time;
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                time = LocalDate.parse(value).atStartOfDay();
            } else if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")) {
                time = LocalDateTime.parse(value);
            } else {
                throw new DateTimeParseException("Invalid format", value, 0);
            }
            if (time.getYear() < 1 || time.isAfter(MAX_TIME)) throw new DateTimeParseException("Out of range", value, 0);
            return time;
        } catch (DateTimeParseException ex) {
            fail(field, "Use YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss, years 0001-9999, without an offset");
            return null;
        }
    }

    private static void fail(String field, String message) {
        throw new FieldValidationException(field, message);
    }
}
