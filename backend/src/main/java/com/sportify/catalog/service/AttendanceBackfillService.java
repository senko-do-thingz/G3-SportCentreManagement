package com.sportify.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.AttendanceBackfillRequest;
import com.sportify.catalog.dto.TrainingWriteResponse.Created;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('MANAGER') and principal.enabled and #actorId == principal.userAccount.id")
public class AttendanceBackfillService {
    private static final ZoneId CENTER = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AttendanceBackfillPolicy policy;
    private final ObjectMapper mapper;

    public record Candidate(long bookingId, long sessionId, String memberName, String className,
                            LocalDate date, LocalTime startTime, LocalTime endTime) {}
    public record Workspace(boolean enabled, String window, String message, List<Candidate> bookings) {}

    public Workspace workspace(long actorId) {
        requireManager(actorId);
        var window = policy.activeWindow();
        if (window.isEmpty()) return new Workspace(false, null,
            "Administrative backfill is disabled until an operational time window is approved and configured.", List.of());
        Instant now = clock.instant();
        LocalDate today = now.atZone(CENTER).toLocalDate();
        var candidates = jdbc.query("""
            SELECT TOP (200) b.id, b.session_id, u.full_name, COALESCE(c.name, sp.name) class_name,
                   s.session_date, s.start_time, s.end_time
            FROM booking b JOIN class_session s ON s.id=b.session_id
            JOIN member_profile m ON m.user_id=b.member_id JOIN user_account u ON u.id=m.user_id
            JOIN role r ON r.id=u.role_id AND r.code='MEMBER'
            JOIN sport sp ON sp.id=s.sport_id LEFT JOIN sport_class c ON c.id=s.class_id
            WHERE b.status='CONFIRMED' AND s.status<>'CANCELLED' AND s.training_type='COACH_LED'
              AND s.session_date < ?
              AND NOT EXISTS(SELECT 1 FROM attendance_record a WHERE a.session_id=b.session_id AND a.member_id=b.member_id)
              AND NOT EXISTS(SELECT 1 FROM session_result x WHERE x.session_id=b.session_id AND x.member_id=b.member_id)
            ORDER BY s.session_date DESC, s.end_time DESC, b.id DESC
            """, (rs, row) -> new Candidate(rs.getLong("id"), rs.getLong("session_id"), rs.getString("full_name"),
                rs.getString("class_name"), rs.getDate("session_date").toLocalDate(),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime()), today);
        return new Workspace(true, window.get().toString(),
            "Manager only. The configured window is an operational setting, not an approved universal policy. Showing up to 200 recent bookings; eligibility is checked again when saved.",
            candidates.stream().filter(c -> within(now, end(c.date(), c.startTime(), c.endTime()), window.get())).toList());
    }

    @Transactional
    public Created backfill(long actorId, AttendanceBackfillRequest request) {
        requireManager(actorId);
        Duration window = policy.activeWindow().orElseThrow(() -> new BusinessRuleException(
            "Administrative backfill is disabled or has no valid positive configured window"));
        if (request == null || request.bookingId() == null || request.bookingId() <= 0 || request.status() == null
            || request.reason() == null || request.reason().trim().length() < 3 || request.reason().length() > 500
            || (request.notes() != null && request.notes().length() > 500))
            throw new BusinessRuleException("Provide a valid booking, status and meaningful reason of 3 to 500 characters; notes allow at most 500 characters");
        var ids = jdbc.query("SELECT session_id FROM booking WHERE id=?", (rs, row) -> rs.getLong(1), request.bookingId());
        if (ids.isEmpty()) throw new ResourceNotFoundException("Booking not found");
        // Match ordinary attendance, result creation and cancellation: session first, then booking.
        var sessions = jdbc.query("""
            SELECT session_date,start_time,end_time,status,training_type FROM class_session WITH (UPDLOCK,HOLDLOCK)
            WHERE id=?
            """, (rs, row) -> new Session(rs.getDate(1).toLocalDate(), rs.getTime(2).toLocalTime(),
                rs.getTime(3).toLocalTime(), rs.getString(4), rs.getString(5)), ids.getFirst());
        if (sessions.isEmpty()) throw new ResourceNotFoundException("Session not found");
        var bookings = jdbc.query("SELECT session_id,member_id,status FROM booking WITH (UPDLOCK,HOLDLOCK) WHERE id=?",
            (rs, row) -> new Booking(rs.getLong(1), rs.getLong(2), rs.getString(3)), request.bookingId());
        if (bookings.isEmpty() || bookings.getFirst().sessionId() != ids.getFirst())
            throw new ConflictException("Booking changed; reload and retry");
        var booking = bookings.getFirst();
        var session = sessions.getFirst();
        if (!booking.status().equals("CONFIRMED") || session.status().equals("CANCELLED") || !session.type().equals("COACH_LED"))
            throw new BusinessRuleException("Backfill requires a confirmed booking for a non-cancelled coach-led session");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM member_profile m JOIN user_account u ON u.id=m.user_id JOIN role r ON r.id=u.role_id WHERE m.user_id=? AND r.code='MEMBER'", Integer.class, booking.memberId()) != 1)
            throw new BusinessRuleException("A real member booking is required");
        Instant now = clock.instant();
        if (!session.date().isBefore(now.atZone(CENTER).toLocalDate()) || !within(now, end(session.date(), session.start(), session.end()), window))
            throw new BusinessRuleException("Backfill requires a completed past-date session within the configured window");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM attendance_record WHERE session_id=? AND member_id=?", Integer.class, booking.sessionId(), booking.memberId()) > 0)
            throw new ConflictException("Attendance already exists; use the correction workflow with a reason");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM session_result WHERE session_id=? AND member_id=?", Integer.class, booking.sessionId(), booking.memberId()) > 0)
            throw new ConflictException("A saved result already exists; backfill cannot change its attendance context");
        LocalDateTime actionTime = LocalDateTime.ofInstant(now, CENTER).withNano(0);
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT attendance_record(session_id,member_id,booking_id,status,marked_at,marked_by,notes) VALUES (?,?,?,?,?,?,?)", new String[]{"id"});
            Object[] values = {booking.sessionId(), booking.memberId(), request.bookingId(), request.status().name(), actionTime, actorId, request.notes()};
            for (int i=0; i<values.length; i++) statement.setObject(i+1, values[i]);
            return statement;
        }, keys);
        long id = keys.getKey().longValue();
        String details;
        try {
            details = mapper.writeValueAsString(Map.of("actorId", actorId, "bookingId", request.bookingId(),
                "sessionId", booking.sessionId(), "memberId", booking.memberId(), "status", request.status().name(),
                "reason", request.reason().trim(), "actionTime", actionTime.toString(), "zone", CENTER.toString()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Backfill audit serialization failed", failure);
        }
        // Required audit data must never be silently dropped or committed in a separate transaction.
        jdbc.update("""
            INSERT activity_log(actor_user_id,action,entity_type,entity_id,summary,details,created_at)
            VALUES (?,'TRAINING_ATTENDANCE_BACKFILLED','ATTENDANCE_RECORD',?,'Manager attendance backfill',?,?)
            """, actorId, id, details, actionTime);
        return new Created(id);
    }

    private void requireManager(long actorId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user_account u WITH (HOLDLOCK) JOIN role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code='MANAGER'", Integer.class, actorId) != 1)
            throw new AccessDeniedException("An enabled Manager account is required");
    }
    static Instant end(LocalDate date, LocalTime start, LocalTime end) {
        return LocalDateTime.of(end.isAfter(start) ? date : date.plusDays(1), end).atZone(CENTER).toInstant();
    }
    static boolean within(Instant now, Instant end, Duration window) {
        try { return !now.isBefore(end) && !now.isAfter(end.plus(window)); }
        catch (DateTimeException | ArithmeticException invalid) { return false; }
    }
    private record Session(LocalDate date, LocalTime start, LocalTime end, String status, String type) {}
    private record Booking(long sessionId, long memberId, String status) {}
}
