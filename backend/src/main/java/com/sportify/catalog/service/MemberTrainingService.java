package com.sportify.catalog.service;

import com.sportify.catalog.dto.MemberTrainingResponse.*;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberTrainingService {
    private final JdbcTemplate jdbc;

    // Ownership survives leaving a class. A booking/attendance never grants another member's plan.
    private static final String PLAN_SELECT = """
        SELECT p.id, p.title, p.description, c.name class_name, u.full_name coach_name
        FROM session_plan p JOIN sport_class c ON c.id = p.class_id
        JOIN user_account u ON u.id = p.coach_id
        WHERE p.is_template = 0 AND p.member_id = ?
        """;
    private static final String SESSION_COLUMNS = """
        s.id session_id, s.session_code, sp.name sport_name, u.full_name coach_name, f.name facility_name,
        s.training_type, COALESCE(c.name, sp.name) class_name,
        s.session_date, s.start_time, s.end_time
        """;
    private static final String SESSION_JOINS = """
        JOIN class_session s ON s.id = r.session_id
        LEFT JOIN sport_class c ON c.id = s.class_id
        JOIN sport sp ON sp.id = s.sport_id
        LEFT JOIN user_account u ON u.id=s.coach_id LEFT JOIN facility f ON f.id=s.facility_id
        """;
    private static final String RESULT_SELECT = "SELECT r.id, r.notes, " + SESSION_COLUMNS
        + " FROM session_result r " + SESSION_JOINS + " WHERE r.member_id = ?";
    private static final String FEEDBACK_SELECT = """
        SELECT r.id, r.plan_id, p.title plan_title, r.rating, r.comments, r.strengths,
               r.areas_for_improvement, r.created_at, u.full_name coach_name
        FROM coach_feedback r JOIN session_plan p ON p.id = r.plan_id AND p.member_id = r.member_id
        JOIN user_account u ON u.id = r.coach_id
        WHERE r.member_id = ? AND p.is_template = 0
        """;
    private static final String FEEDBACK_ORDER = " ORDER BY r.created_at DESC, r.id DESC";

    public List<Plan> plans(long memberId) {
        return jdbc.query(PLAN_SELECT + " ORDER BY p.created_at DESC, p.id DESC", this::plan, memberId);
    }

    public PlanDetail plan(long memberId, long id) {
        var plans = jdbc.query(PLAN_SELECT + " AND p.id = ?", this::plan, memberId, id);
        if (plans.isEmpty()) throw new ResourceNotFoundException("Training plan not found");
        var steps = jdbc.query("""
            SELECT id, step_order, title, duration_minutes, instructions FROM session_plan_step
            WHERE plan_id = ? ORDER BY step_order, id
            """, (rs, row) -> new Step(rs.getLong("id"), rs.getInt("step_order"), rs.getString("title"),
                rs.getInt("duration_minutes"), rs.getString("instructions")), id);
        return new PlanDetail(plans.getFirst(), steps, jdbc.query(
            FEEDBACK_SELECT + " AND r.plan_id = ?" + FEEDBACK_ORDER, this::mapFeedback, memberId, id));
    }

    public List<Attendance> attendance(long memberId) {
        return jdbc.query("""
            SELECT r.id,r.status recorded_status,r.notes,b.id booking_id,b.status booking_status,s.status session_status,
                CASE WHEN b.status='CANCELLED' OR s.status='CANCELLED' THEN 'CANCELLED'
                     WHEN r.id IS NULL THEN 'UNMARKED' ELSE r.status END status,
            """ + SESSION_COLUMNS + """
             FROM attendance_record r FULL OUTER JOIN booking b ON b.session_id=r.session_id AND b.member_id=r.member_id
                 AND (r.booking_id IS NULL OR r.booking_id=b.id)
             JOIN class_session s ON s.id=COALESCE(b.session_id,r.session_id)
             LEFT JOIN sport_class c ON c.id=s.class_id JOIN sport sp ON sp.id=s.sport_id
             LEFT JOIN user_account u ON u.id=s.coach_id LEFT JOIN facility f ON f.id=s.facility_id
             WHERE COALESCE(b.member_id,r.member_id)=?
             ORDER BY s.session_date DESC,s.start_time DESC,s.id DESC,b.id DESC,r.id DESC
            """, (rs, row) -> new Attendance(rs.getObject("id", Long.class), session(rs), rs.getString("status"), rs.getString("notes"),
                rs.getObject("booking_id", Long.class), rs.getString("booking_status"), rs.getString("session_status"), rs.getString("recorded_status")), memberId);
    }

    public List<Result> results(long memberId) {
        return jdbc.query(RESULT_SELECT + " ORDER BY s.session_date DESC, s.start_time DESC, r.id DESC",
            this::result, memberId);
    }

    public ResultDetail result(long memberId, long id) {
        var results = jdbc.query(RESULT_SELECT + " AND r.id = ?", this::result, memberId, id);
        if (results.isEmpty()) throw new ResourceNotFoundException("Training result not found");
        var result = results.getFirst();
        var scores = jdbc.query("""
            SELECT sc.id, m.name, m.description, sc.score, m.max_score FROM session_result_score sc
            JOIN skill_metric m ON m.id = sc.metric_id WHERE sc.result_id = ? ORDER BY m.name, sc.id
            """, (rs, row) -> new Score(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("score"), rs.getInt("max_score")), id);
        return new ResultDetail(result, scores);
    }

    public List<Feedback> feedback(long memberId) {
        return jdbc.query(FEEDBACK_SELECT + FEEDBACK_ORDER, this::mapFeedback, memberId);
    }

    private Feedback mapFeedback(ResultSet rs, int row) throws SQLException {
        return new Feedback(rs.getLong("id"), rs.getLong("plan_id"), rs.getString("plan_title"), rs.getString("coach_name"),
            rs.getInt("rating"), rs.getString("comments"), rs.getString("strengths"),
            rs.getString("areas_for_improvement"), rs.getTimestamp("created_at").toLocalDateTime());
    }

    private Plan plan(ResultSet rs, int row) throws SQLException {
        return new Plan(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
            rs.getString("class_name"), rs.getString("coach_name"));
    }
    private Result result(ResultSet rs, int row) throws SQLException {
        return new Result(rs.getLong("id"), session(rs), rs.getString("notes"));
    }
    private Session session(ResultSet rs) throws SQLException {
        return new Session(rs.getLong("session_id"), rs.getString("class_name"),
            rs.getDate("session_date").toLocalDate(), rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
            rs.getString("session_code"),rs.getString("sport_name"),rs.getString("coach_name"),rs.getString("facility_name"),rs.getString("training_type"));
    }
}
