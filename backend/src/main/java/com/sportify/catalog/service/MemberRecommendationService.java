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
    /** {@code available} is false once the class or its sport is no longer offered; such a bookmark has no next session. */
    public record Saved(long id, long classId, String className, String sportName, String level, String coachName,
                        String reason, LocalDateTime savedAt, boolean available, NextSession nextSession) {}

    /** F5-01 "Your training context": what recommendations are based on, from the Member's own records only. */
    public record ContextPackage(String sportName, String packageName, String trainingFormat, int remainingSessions, int totalSessions, LocalDate endDate) {}
    public record TrainingContext(String memberName, String mainGoal, String currentLevel, boolean dateOfBirthKnown,
                                  List<Long> preferredSportIds, List<ContextPackage> activePackages) {}
    public record PreferredSports(@NotNull @Size(max = MAX_PREFERRED) List<@NotNull @Positive Long> sportIds) {}
    /** F5-04: one upcoming session of a recommended class, with why it can or cannot be booked. */
    public record SuitableSession(long sessionId, String sessionCode, LocalDate date, LocalTime startTime, LocalTime endTime,
                                  String trainingType, String facilityName, int freeSeats, String availability, String packageCheck) {}
    public record SuitableSessions(long classId, String className, String sportName, String level, String coachName, String ageGroup,
                                   List<SuitableSession> sessions, List<ContextPackage> packages) {}
    static final int MAX_PREFERRED = 10;
    static final int SUITABLE_DAYS = 30;

    public TrainingContext context(long memberId) {
        var profile = jdbc.query("""
            SELECT u.full_name,u.date_of_birth,p.main_goal,p.current_level FROM member_profile p JOIN user_account u ON u.id=p.user_id WHERE p.user_id=?
            """, (rs, row) -> new Object[]{rs.getString("full_name"), rs.getDate("date_of_birth") != null, rs.getString("main_goal"), rs.getString("current_level")}, memberId);
        if (profile.isEmpty()) throw new ResourceNotFoundException("Member profile not found");
        var preferred = jdbc.queryForList("""
            SELECT msi.sport_id FROM member_sport_interest msi JOIN sport sp ON sp.id=msi.sport_id
            WHERE msi.member_id=? AND sp.is_active=1 ORDER BY sp.display_order,sp.name,sp.id
            """, Long.class, memberId);
        var p = profile.getFirst();
        return new TrainingContext((String) p[0], (String) p[2], (String) p[3], (Boolean) p[1], preferred, activePackages(memberId, null));
    }

    /** Replaces the Member's preferred sports (F5-01). Only active sports; at most {@value #MAX_PREFERRED}. */
    @Transactional
    public TrainingContext savePreferredSports(long memberId, List<Long> sportIds) {
        requireMemberProfile(memberId);
        var unique = new java.util.LinkedHashSet<>(sportIds == null ? List.<Long>of() : sportIds);
        if (unique.size() > MAX_PREFERRED) throw new BusinessRuleException("Choose at most " + MAX_PREFERRED + " preferred sports");
        for (Long sport : unique)
            if (sport == null || jdbc.queryForObject("SELECT COUNT(*) FROM sport WHERE id=? AND is_active=1", Integer.class, sport) == 0)
                throw new ResourceNotFoundException("Active sport not found");
        // Serialises concurrent saves for one Member so the stored set is exactly one request's choice.
        jdbc.queryForList("SELECT user_id FROM member_profile WITH(UPDLOCK,HOLDLOCK) WHERE user_id=?", Long.class, memberId);
        jdbc.update("DELETE FROM member_sport_interest WHERE member_id=?", memberId);
        for (Long sport : unique) jdbc.update("INSERT INTO member_sport_interest(member_id,sport_id) VALUES (?,?)", memberId, sport);
        return context(memberId);
    }

    /**
     * F5-04: the next sessions of one active class over {@value #SUITABLE_DAYS} days. Every session is listed with its
     * availability for this Member (AVAILABLE, FULL, BOOKED, OVERLAP or AGE) and a package check using the booking rule
     * (ELIGIBLE, NO_PACKAGE, NO_SESSIONS_LEFT or OUTSIDE_VALIDITY). Reading this list books or reserves nothing.
     */
    public SuitableSessions suitableSessions(long memberId, long classId) {
        var classes = jdbc.query("""
            SELECT c.name,c.level,sp.id sport_id,sp.name sport_name,u.full_name coach_name,ag.label age_label,ag.min_age,ag.max_age
            FROM sport_class c JOIN sport sp ON sp.id=c.sport_id LEFT JOIN user_account u ON u.id=c.coach_id
            LEFT JOIN age_group ag ON ag.id=c.age_group_id AND ag.is_active=1
            WHERE c.id=? AND c.is_active=1 AND sp.is_active=1
            """, (rs, row) -> new Object[]{rs.getString("name"), rs.getString("level"), rs.getLong("sport_id"), rs.getString("sport_name"),
                rs.getString("coach_name"), rs.getString("age_label"), rs.getObject("min_age", Integer.class), rs.getObject("max_age", Integer.class)}, classId);
        if (classes.isEmpty()) throw new ResourceNotFoundException("Active class not found");
        var c = classes.getFirst();
        long sportId = (Long) c[2];
        var birth = jdbc.query("SELECT date_of_birth FROM user_account WHERE id=?", (rs, row) -> rs.getDate("date_of_birth"), memberId);
        LocalDate dateOfBirth = birth.isEmpty() || birth.getFirst() == null ? null : birth.getFirst().toLocalDate();
        LocalDate today = LocalDate.now(clock);
        record Registration(String format, LocalDate start, LocalDate end, int remaining) {}
        var registrations = jdbc.query("""
            SELECT p.training_format,r.start_date,r.end_date,r.remaining_sessions FROM sport_package_registration r
            JOIN sport_package p ON p.id=r.package_id WHERE r.member_id=? AND r.status='ACTIVE' AND p.sport_id=?
            """, (rs, row) -> new Registration(rs.getString("training_format"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(), rs.getInt("remaining_sessions")), memberId, sportId);
        var rows = jdbc.query("""
            SELECT TOP(20) s.id,s.session_code,s.session_date,s.start_time,s.end_time,s.training_type,f.name facility_name,
                CASE WHEN s.capacity>s.booked_count THEN s.capacity-s.booked_count ELSE 0 END free_seats,
                CASE WHEN EXISTS(SELECT 1 FROM booking b WHERE b.session_id=s.id AND b.member_id=? AND b.status='CONFIRMED') THEN 1 ELSE 0 END booked,
                CASE WHEN EXISTS(SELECT 1 FROM booking ob JOIN class_session os ON os.id=ob.session_id
                    WHERE ob.member_id=? AND ob.status='CONFIRMED' AND os.status<>'CANCELLED' AND os.id<>s.id
                        AND os.session_date=s.session_date AND os.start_time<s.end_time AND os.end_time>s.start_time) THEN 1 ELSE 0 END overlap
            FROM class_session s JOIN sport_class c ON c.id=s.class_id LEFT JOIN user_account u ON u.id=s.coach_id
            LEFT JOIN facility f ON f.id=s.facility_id
            WHERE s.class_id=? AND s.status='PUBLISHED'
                AND (s.training_type='SELF_TRAINING' OR (u.status='ACTIVE' AND s.coach_id=c.coach_id))
                AND s.session_date<=? AND (s.session_date>? OR (s.session_date=? AND s.start_time>CAST(? AS time)))
            ORDER BY s.session_date,s.start_time,s.id
            """, (rs, row) -> {
                LocalDate date = rs.getDate("session_date").toLocalDate();
                String type = rs.getString("training_type");
                int free = rs.getInt("free_seats");
                String availability = rs.getBoolean("booked") ? "BOOKED" : free == 0 ? "FULL" : rs.getBoolean("overlap") ? "OVERLAP"
                    : c[5] != null && dateOfBirth != null && !TrainingGuidanceService.fitsAge(dateOfBirth, date, (Integer) c[6], (Integer) c[7]) ? "AGE" : "AVAILABLE";
                var matching = registrations.stream().filter(r -> type.equals(r.format())).toList();
                var covering = matching.stream().filter(r -> !date.isBefore(r.start()) && !date.isAfter(r.end())).toList();
                String check = covering.stream().anyMatch(r -> r.remaining() > 0) ? "ELIGIBLE" : !covering.isEmpty() ? "NO_SESSIONS_LEFT"
                    : matching.isEmpty() ? "NO_PACKAGE" : "OUTSIDE_VALIDITY";
                return new SuitableSession(rs.getLong("id"), rs.getString("session_code"), date, rs.getTime("start_time").toLocalTime(),
                    rs.getTime("end_time").toLocalTime(), type, rs.getString("facility_name"), free, availability, check);
            }, memberId, memberId, classId, Date.valueOf(today.plusDays(SUITABLE_DAYS)), Date.valueOf(today), Date.valueOf(today), Time.valueOf(LocalTime.now(clock)));
        var visible = visibility.filter(rows.stream().map(SuitableSession::sessionId).toList());
        var sessions = rows.stream().filter(s -> visible.contains(s.sessionId())).limit(10).toList();
        return new SuitableSessions(classId, (String) c[0], (String) c[3], (String) c[1], (String) c[4], (String) c[5], sessions, activePackages(memberId, sportId));
    }

    private List<ContextPackage> activePackages(long memberId, Long sportId) {
        var args = new java.util.ArrayList<Object>(List.of(memberId, Date.valueOf(LocalDate.now(clock))));
        if (sportId != null) args.add(sportId);
        return jdbc.query("""
            SELECT TOP(10) sp.name sport_name,p.name package_name,p.training_format,r.remaining_sessions,r.total_sessions,r.end_date
            FROM sport_package_registration r JOIN sport_package p ON p.id=r.package_id JOIN sport sp ON sp.id=p.sport_id
            WHERE r.member_id=? AND r.status='ACTIVE' AND r.end_date>=?""" + (sportId == null ? "" : " AND p.sport_id=?") + " ORDER BY r.end_date,r.id",
            (rs, row) -> new ContextPackage(rs.getString("sport_name"), rs.getString("package_name"), rs.getString("training_format"),
                rs.getInt("remaining_sessions"), rs.getInt("total_sessions"), rs.getDate("end_date").toLocalDate()), args.toArray());
    }

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

    /**
     * The Member's saved classes, newest first. Each still-offered class shows its next session the Member could
     * actually book: the same filters as the recommendation list (not already booked, no overlap with another
     * confirmed booking, inside the class age group). A class no longer offered stays listed so it can be removed.
     */
    public List<Saved> saved(long memberId) {
        var rows = jdbc.query("""
            SELECT r.id,r.class_id,c.name class_name,sp.name sport_name,c.level,u.full_name coach_name,r.reason,r.created_at,
                CASE WHEN c.is_active=1 AND sp.is_active=1 THEN 1 ELSE 0 END available
            FROM saved_recommendation r JOIN sport_class c ON c.id=r.class_id JOIN sport sp ON sp.id=c.sport_id
            LEFT JOIN user_account u ON u.id=c.coach_id
            WHERE r.member_id=? ORDER BY r.created_at DESC,r.id DESC
            """, (rs, row) -> new Saved(rs.getLong("id"), rs.getLong("class_id"), rs.getString("class_name"), rs.getString("sport_name"),
                rs.getString("level"), rs.getString("coach_name"), rs.getString("reason"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getBoolean("available"), null), memberId);
        var birth = jdbc.query("SELECT date_of_birth FROM user_account WHERE id=?", (rs, row) -> rs.getDate("date_of_birth"), memberId);
        LocalDate dateOfBirth = birth.isEmpty() || birth.getFirst() == null ? null : birth.getFirst().toLocalDate();
        return rows.stream().map(row -> new Saved(row.id(), row.classId(), row.className(), row.sportName(), row.level(),
                row.coachName(), row.reason(), row.savedAt(), row.available(),
                row.available() ? nextSession(memberId, row.classId(), dateOfBirth) : null)).toList();
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

    private NextSession nextSession(long memberId, long classId, LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(clock);
        record Candidate(NextSession session, Integer minAge, Integer maxAge, boolean ageGroup) {}
        var candidates = jdbc.query("""
            SELECT TOP(20) s.id,s.session_code,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats,
                ag.id age_group_id,ag.min_age,ag.max_age
            FROM class_session s JOIN sport_class c ON c.id=s.class_id LEFT JOIN user_account u ON u.id=s.coach_id
            LEFT JOIN age_group ag ON ag.id=c.age_group_id AND ag.is_active=1
            WHERE s.class_id=? AND c.is_active=1 AND s.status='PUBLISHED' AND s.booked_count<s.capacity
                AND (s.training_type='SELF_TRAINING' OR (u.status='ACTIVE' AND s.coach_id=c.coach_id))
                AND (s.session_date>? OR (s.session_date=? AND s.start_time>CAST(? AS time)))
                AND NOT EXISTS(SELECT 1 FROM booking b WHERE b.session_id=s.id AND b.member_id=? AND b.status='CONFIRMED')
                AND NOT EXISTS(SELECT 1 FROM booking ob JOIN class_session os ON os.id=ob.session_id
                    WHERE ob.member_id=? AND ob.status='CONFIRMED' AND os.status<>'CANCELLED'
                        AND os.session_date=s.session_date AND os.start_time<s.end_time AND os.end_time>s.start_time)
            ORDER BY s.session_date,s.start_time,s.id
            """, (rs, row) -> new Candidate(new NextSession(rs.getLong("id"), rs.getString("session_code"), rs.getDate("session_date").toLocalDate(),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getInt("free_seats")),
                rs.getObject("min_age", Integer.class), rs.getObject("max_age", Integer.class), rs.getObject("age_group_id") != null),
            classId, Date.valueOf(today), Date.valueOf(today), Time.valueOf(LocalTime.now(clock)), memberId, memberId);
        // Same age rule as the recommendation list: a known date of birth must fit the class age group on the session date.
        var eligible = candidates.stream().filter(c -> !c.ageGroup() || dateOfBirth == null
                || TrainingGuidanceService.fitsAge(dateOfBirth, c.session().date(), c.minAge(), c.maxAge()))
            .map(Candidate::session).toList();
        var visible = visibility.filter(eligible.stream().map(NextSession::sessionId).toList());
        return eligible.stream().filter(session -> visible.contains(session.sessionId())).findFirst().orElse(null);
    }

    private void requireMemberProfile(long memberId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE user_id=?", Integer.class, memberId) == 0)
            throw new ResourceNotFoundException("Member profile not found");
    }
}
