package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('MANAGER') and principal.enabled and #actorId == principal.userAccount.id")
public class ManagerAttendanceReadService {
    private final JdbcTemplate jdbc;
    public record Session(long id, String sessionCode, LocalDate date, String startTime, String className,
                          Long classId, String coachName, Long coachId, String status, long confirmed,
                          long present, long late, long absent, long excused, long unmarked, BigDecimal attendanceRate) {}
    public record Overview(LocalDate from, LocalDate to, List<Session> sessions, boolean truncated) {}
    public record Member(long bookingId, String memberCode, String memberName, String bookingStatus,
                         String attendanceStatus, String markedBy, String markedAt, String notes) {}
    public record Detail(Session session, List<Member> members) {}
    private static final String SELECT = """
        SELECT TOP (201) s.id,s.session_code,s.session_date,CONVERT(varchar(8),s.start_time) start_time,
          COALESCE(c.name,sp.name) class_name,s.class_id,coach.full_name coach_name,s.coach_id,s.status,
          COUNT_BIG(b.id) confirmed,
          COALESCE(SUM(CASE WHEN a.status='PRESENT' THEN CAST(1 AS BIGINT) ELSE 0 END),0) present,
          COALESCE(SUM(CASE WHEN a.status='LATE' THEN CAST(1 AS BIGINT) ELSE 0 END),0) late,
          COALESCE(SUM(CASE WHEN a.status='ABSENT' THEN CAST(1 AS BIGINT) ELSE 0 END),0) absent,
          COALESCE(SUM(CASE WHEN a.status='EXCUSED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) excused,
          COALESCE(SUM(CASE WHEN b.id IS NOT NULL AND a.id IS NULL THEN CAST(1 AS BIGINT) ELSE 0 END),0) unmarked
        FROM class_session s JOIN sport sp ON sp.id=s.sport_id
        LEFT JOIN sport_class c ON c.id=s.class_id LEFT JOIN user_account coach ON coach.id=s.coach_id
        LEFT JOIN booking b ON b.session_id=s.id AND b.status='CONFIRMED'
        LEFT JOIN attendance_record a ON a.session_id=s.id AND a.member_id=b.member_id
        WHERE s.status IN ('PUBLISHED','COMPLETED')
        """;
    private static final String GROUP = """
         GROUP BY s.id,s.session_code,s.session_date,s.start_time,c.name,sp.name,s.class_id,
           coach.full_name,s.coach_id,s.status
         ORDER BY s.session_date DESC,s.start_time DESC,s.id DESC
        """;

    public Overview overview(long actorId, LocalDate from, LocalDate to, Long classId, Long coachId) {
        requireManager(actorId);
        validateRange(from, to, classId, coachId);
        var parameters = new ArrayList<Object>();
        parameters.add(java.sql.Date.valueOf(from)); parameters.add(java.sql.Date.valueOf(to));
        String filter = " AND s.session_date>=? AND s.session_date<=?";
        if (classId != null) { filter += " AND s.class_id=?"; parameters.add(classId); }
        if (coachId != null) { filter += " AND s.coach_id=?"; parameters.add(coachId); }
        var sessions = jdbc.query(SELECT + filter + GROUP, this::session, parameters.toArray());
        return new Overview(from, to, List.copyOf(sessions.subList(0, Math.min(200, sessions.size()))), sessions.size() > 200);
    }

    public Detail detail(long actorId, String code) {
        requireManager(actorId);
        if (code == null || !code.matches("[0-9]{6}(FB|SW|BM|VB|BK|TE)[0-9]{2}"))
            throw new BusinessRuleException("Choose a valid Session ID");
        var sessions = jdbc.query(SELECT + " AND (s.session_code=? OR EXISTS (SELECT 1 FROM session_code_history h WHERE h.session_id=s.id AND h.session_code=?))" + GROUP,
            this::session, code, code);
        if (sessions.isEmpty()) throw new ResourceNotFoundException("Attendance session not found");
        var session = sessions.getFirst();
        var members = jdbc.query("""
            SELECT TOP (2001) b.id,m.member_code,u.full_name,b.status booking_status,a.status attendance_status,
              operator.full_name marked_by,CONVERT(varchar(30),a.marked_at,126) marked_at,a.notes
            FROM booking b JOIN member_profile m ON m.user_id=b.member_id JOIN user_account u ON u.id=b.member_id
            LEFT JOIN attendance_record a ON a.session_id=b.session_id AND a.member_id=b.member_id
            LEFT JOIN user_account operator ON operator.id=a.marked_by
            WHERE b.session_id=? ORDER BY u.full_name,b.id
            """, (rs,row) -> new Member(rs.getLong("id"),rs.getString("member_code"),rs.getString("full_name"),
                rs.getString("booking_status"),rs.getString("attendance_status"),rs.getString("marked_by"),
                rs.getString("marked_at"),rs.getString("notes")), session.id());
        if (members.size() > 2000) throw new BusinessRuleException("Session roster exceeds the supported 2000-booking detail limit");
        return new Detail(session, members);
    }

    static void validateRange(LocalDate from, LocalDate to, Long classId, Long coachId) {
        if (from == null || to == null || from.getYear() < 1 || to.getYear() > 9999 || to.isBefore(from) || to.isAfter(from.plusDays(30)))
            throw new BusinessRuleException("Choose a date range of at most 31 days");
        if ((classId != null && classId <= 0) || (coachId != null && coachId <= 0))
            throw new BusinessRuleException("Class and Coach filters must be positive IDs");
    }

    static BigDecimal rate(String status, long confirmed, long present, long late) {
        return !"COMPLETED".equals(status) || confirmed == 0 ? null :
            BigDecimal.valueOf(present + late).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(confirmed),1,RoundingMode.HALF_UP);
    }

    private Session session(ResultSet rs, int row) throws SQLException {
        long confirmed = rs.getLong("confirmed"), present = rs.getLong("present"), late = rs.getLong("late");
        return new Session(rs.getLong("id"),rs.getString("session_code"),rs.getDate("session_date").toLocalDate(),
            rs.getString("start_time"),rs.getString("class_name"),(Long)rs.getObject("class_id"),
            rs.getString("coach_name"),(Long)rs.getObject("coach_id"),rs.getString("status"),confirmed,
            present,late,rs.getLong("absent"),rs.getLong("excused"),rs.getLong("unmarked"),rate(rs.getString("status"),confirmed,present,late));
    }

    private void requireManager(long actorId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_account u JOIN role r ON r.id=u.role_id
            WHERE u.id=? AND u.status='ACTIVE' AND r.code='MANAGER'
            """,Integer.class,actorId);
        if (count == null || count != 1) throw new AccessDeniedException("Enabled Manager required");
    }
}
