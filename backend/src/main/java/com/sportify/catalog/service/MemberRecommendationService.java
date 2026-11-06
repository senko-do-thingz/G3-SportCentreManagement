package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Flow 5 Member recommendation preferences (F5-01) and saved recommendations (F5-05).
 * A saved recommendation is a bookmark: it never books, reserves a seat or changes credits, and its next session
 * is recomputed on every read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberRecommendationService {
    static final int MAX_SAVED = 50;
    private static final Set<String> LEVELS = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final VisibleTrainingSessions visibility;

    public record SportLevel(long sportId, String sportName, String level) {}
    public record LevelUpdate(@NotBlank String level) {}
    public record SaveRequest(@NotNull @Positive Long classId, @Size(max = 500) String reason) {}
    public record NextSession(long sessionId, String sessionCode, LocalDate date, LocalTime startTime, LocalTime endTime, int freeSeats) {}
    public record Saved(long id, long classId, String className, String sportName, String level, String coachName,
                        String reason, LocalDateTime savedAt, NextSession nextSession) {}

    /** Every active sport with the Member's saved level for it, or null when none is saved. */
    public List<SportLevel> sportLevels(long memberId) {
        return jdbc.query("""
            SELECT sp.id,sp.name,msl.level FROM sport sp
            LEFT JOIN member_sport_level msl ON msl.sport_id=sp.id AND msl.member_id=?
            WHERE sp.is_active=1 ORDER BY sp.display_order,sp.name,sp.id
            """, (rs, row) -> new SportLevel(rs.getLong("id"), rs.getString("name"), rs.getString("level")), memberId);
    }

    @Transactional
    public SportLevel saveSportLevel(long memberId, long sportId, String level) {
        String value = level == null ? null : level.trim();
        if (value == null || !LEVELS.contains(value)) throw new BusinessRuleException("Select a supported level: BEGINNER, INTERMEDIATE or ADVANCED");
        requireMemberProfile(memberId);
        var sport = jdbc.query("SELECT name FROM sport WHERE id=? AND is_active=1", (rs, row) -> rs.getString("name"), sportId);
        if (sport.isEmpty()) throw new ResourceNotFoundException("Active sport not found");
        LocalDateTime now = LocalDateTime.now(clock);
        if (jdbc.update("UPDATE member_sport_level SET level=?,updated_at=? WHERE member_id=? AND sport_id=?", value, now, memberId, sportId) == 0) {
            try {
                jdbc.update("INSERT INTO member_sport_level(member_id,sport_id,level,updated_at) VALUES (?,?,?,?)", memberId, sportId, value, now);
            } catch (DuplicateKeyException concurrent) {
                jdbc.update("UPDATE member_sport_level SET level=?,updated_at=? WHERE member_id=? AND sport_id=?", value, now, memberId, sportId);
            }
        }
        return new SportLevel(sportId, sport.getFirst(), value);
    }

    @Transactional
    public void clearSportLevel(long memberId, long sportId) {
        jdbc.update("DELETE FROM member_sport_level WHERE member_id=? AND sport_id=?", memberId, sportId);
    }

    /** The Member's saved classes, newest first, each with its next bookable-looking session (or none). */
    public List<Saved> saved(long memberId) {
        var rows = jdbc.query("""
            SELECT r.id,r.class_id,c.name class_name,sp.name sport_name,c.level,u.full_name coach_name,r.reason,r.created_at
            FROM saved_recommendation r JOIN sport_class c ON c.id=r.class_id JOIN sport sp ON sp.id=c.sport_id
            LEFT JOIN user_account u ON u.id=c.coach_id
            WHERE r.member_id=? ORDER BY r.created_at DESC,r.id DESC
            """, (rs, row) -> new Saved(rs.getLong("id"), rs.getLong("class_id"), rs.getString("class_name"), rs.getString("sport_name"),
                rs.getString("level"), rs.getString("coach_name"), rs.getString("reason"),
                rs.getTimestamp("created_at").toLocalDateTime(), null), memberId);
        return rows.stream().map(row -> new Saved(row.id(), row.classId(), row.className(), row.sportName(), row.level(),
                row.coachName(), row.reason(), row.savedAt(), nextSession(row.classId()))).toList();
    }

    /** Saves a class; saving the same class again returns the existing bookmark unchanged. */
    @Transactional
    public Saved save(long memberId, SaveRequest request) {
        requireMemberProfile(memberId);
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (reason != null && reason.length() > 500) throw new BusinessRuleException("Reason must be at most 500 characters");
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM sport_class c JOIN sport sp ON sp.id=c.sport_id WHERE c.id=? AND c.is_active=1 AND sp.is_active=1
                """, Integer.class, request.classId()) == 0)
            throw new ResourceNotFoundException("Active class not found");
        var existing = jdbc.query("SELECT id FROM saved_recommendation WHERE member_id=? AND class_id=?",
                (rs, row) -> rs.getLong("id"), memberId, request.classId());
        if (existing.isEmpty()) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM saved_recommendation WHERE member_id=?", Integer.class, memberId);
            if (count != null && count >= MAX_SAVED)
                throw new ConflictException("You can keep at most " + MAX_SAVED + " saved recommendations; remove one first", "SAVED_LIMIT");
            try {
                jdbc.update("INSERT INTO saved_recommendation(member_id,class_id,reason,created_at) VALUES (?,?,?,?)",
                        memberId, request.classId(), reason, LocalDateTime.now(clock));
            } catch (DuplicateKeyException concurrent) {
                // A concurrent save of the same class won: return that bookmark.
            }
        }
        return saved(memberId).stream().filter(item -> item.classId() == request.classId()).findFirst().orElseThrow();
    }

    /** Removes one of the Member's own bookmarks; any other id is reported as not found. */
    @Transactional
    public void remove(long memberId, long id) {
        if (jdbc.update("DELETE FROM saved_recommendation WHERE id=? AND member_id=?", id, memberId) == 0)
            throw new ResourceNotFoundException("Saved recommendation not found");
    }

    private NextSession nextSession(long classId) {
        LocalDate today = LocalDate.now(clock);
        var candidates = jdbc.query("""
            SELECT TOP(20) s.id,s.session_code,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats
            FROM class_session s JOIN sport_class c ON c.id=s.class_id LEFT JOIN user_account u ON u.id=s.coach_id
            WHERE s.class_id=? AND c.is_active=1 AND s.status='PUBLISHED' AND s.booked_count<s.capacity
                AND (s.training_type='SELF_TRAINING' OR (u.status='ACTIVE' AND s.coach_id=c.coach_id))
                AND (s.session_date>? OR (s.session_date=? AND s.start_time>CAST(? AS time)))
            ORDER BY s.session_date,s.start_time,s.id
            """, (rs, row) -> new NextSession(rs.getLong("id"), rs.getString("session_code"), rs.getDate("session_date").toLocalDate(),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getInt("free_seats")),
            classId, Date.valueOf(today), Date.valueOf(today), Time.valueOf(LocalTime.now(clock)));
        var visible = visibility.filter(candidates.stream().map(NextSession::sessionId).toList());
        return candidates.stream().filter(session -> visible.contains(session.sessionId())).findFirst().orElse(null);
    }

    private void requireMemberProfile(long memberId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE user_id=?", Integer.class, memberId) == 0)
            throw new ResourceNotFoundException("Member profile not found");
    }
}
