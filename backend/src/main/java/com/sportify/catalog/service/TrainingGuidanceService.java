package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences;
import com.sportify.catalog.dto.TrainingGuidanceResponse.*;
import com.sportify.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Local deterministic rules only. No member data is sent to an external provider. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingGuidanceService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final VisibleTrainingSessions visibility;
    private static final Set<String> LEVELS = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> GOALS = Set.of("IMPROVE_FITNESS", "BUILD_SKILLS", "COMPETITION");
    private static final Set<String> FORMATS = Set.of("COACH_LED", "SELF_TRAINING");

    public Recommendations recommendations(long memberId, Preferences request) {
        var profile = jdbc.query("""
            SELECT p.current_level,p.main_goal,u.date_of_birth FROM member_profile p JOIN user_account u ON u.id=p.user_id
            WHERE p.user_id=?""",
            (rs,row) -> new Object[]{rs.getString("current_level"),rs.getString("main_goal"),rs.getDate("date_of_birth")}, memberId);
        String explicitLevel = optional(request.level());
        String goal = optional(request.goal());
        String profileLevel = profile.isEmpty() ? null : optional((String) profile.getFirst()[0]);
        if (profileLevel != null && !LEVELS.contains(profileLevel)) profileLevel = null;
        if (goal == null && !profile.isEmpty()) goal = optional((String) profile.getFirst()[1]);
        LocalDate dateOfBirth = profile.isEmpty() || profile.getFirst()[2] == null ? null : ((Date) profile.getFirst()[2]).toLocalDate();
        if (explicitLevel != null && !LEVELS.contains(explicitLevel) || goal != null && !GOALS.contains(goal))
            throw new BusinessRuleException("Select a supported level and training goal");
        LocalDate today = LocalDate.now(clock);
        LocalDate from = request.from() == null ? today : request.from();
        if (from.isBefore(today) || from.isAfter(today.plusDays(365)))
            throw new BusinessRuleException("Select an availability start within the next year");
        LocalDate to = request.to() == null ? from.plusDays(14) : request.to();
        if (from.isBefore(today) || to.isBefore(from) || to.isAfter(from.plusDays(30))
            || request.page() < 0 || request.page() > 1000 || request.size() < 1 || request.size() > 20
            || request.sportId() != null && request.sportId() <= 0
            || request.earliest() != null && request.latest() != null && !request.earliest().isBefore(request.latest()))
            throw new BusinessRuleException("Use a future date range of at most 30 days, valid times and page size 1-20");
        String format = optional(request.format());
        if (format != null && !FORMATS.contains(format)) throw new BusinessRuleException("Select a supported training format: COACH_LED or SELF_TRAINING");
        var days = request.days() == null || request.days().isEmpty() ? null : Set.copyOf(request.days());
        // Sessions overlapping a CONFIRMED booking of this member are never recommended: the booking rule (E2E-BUG-01)
        // would reject them. Same strict intersection as BookingRepository.findOverlappingConfirmedSessionCodes.
        StringBuilder sql = new StringBuilder("""
            SELECT c.id class_id,c.name class_name,sp.id sport_id,sp.name sport_name,c.level,u.full_name coach_name,
                s.id session_id,s.session_code,s.training_type,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats,
                msl.level sport_level,ag.label age_label,ag.min_age,ag.max_age,
                CASE WHEN EXISTS(SELECT 1 FROM member_sport_interest msi WHERE msi.member_id=? AND msi.sport_id=sp.id) THEN 1 ELSE 0 END preferred,
                CASE WHEN EXISTS(SELECT 1 FROM attendance_record a JOIN class_session h ON h.id=a.session_id
                    WHERE a.member_id=? AND h.sport_id=sp.id AND a.status IN ('PRESENT','LATE')) THEN 1 ELSE 0 END familiar
            FROM sport_class c JOIN sport sp ON sp.id=c.sport_id JOIN class_session s ON s.class_id=c.id
            LEFT JOIN user_account u ON u.id=s.coach_id
            LEFT JOIN member_sport_level msl ON msl.member_id=? AND msl.sport_id=sp.id
            LEFT JOIN age_group ag ON ag.id=c.age_group_id AND ag.is_active=1
            WHERE c.is_active=1 AND sp.is_active=1 AND s.status='PUBLISHED' AND s.booked_count<s.capacity
                AND (s.training_type='SELF_TRAINING' OR (u.status='ACTIVE' AND EXISTS(SELECT 1 FROM coach_sport cs WHERE cs.coach_id=s.coach_id AND cs.sport_id=s.sport_id)))
                AND s.session_date BETWEEN ? AND ? AND (s.session_date>? OR s.start_time>CAST(? AS time))
                AND NOT EXISTS(SELECT 1 FROM booking b WHERE b.session_id=s.id AND b.member_id=? AND b.status='CONFIRMED')
                AND NOT EXISTS(SELECT 1 FROM booking ob JOIN class_session os ON os.id=ob.session_id
                    WHERE ob.member_id=? AND ob.status='CONFIRMED' AND os.status<>'CANCELLED'
                        AND os.session_date=s.session_date AND os.start_time<s.end_time AND os.end_time>s.start_time)
            """);
        var values = new ArrayList<Object>(List.of(memberId, memberId, memberId, Date.valueOf(from), Date.valueOf(to), Date.valueOf(today),
            Time.valueOf(LocalTime.now(clock)), memberId, memberId));
        if (request.sportId() != null) { sql.append(" AND sp.id=?"); values.add(request.sportId()); }
        if (format != null) { sql.append(" AND s.training_type=?"); values.add(format); }
        // Level: an explicit request wins; otherwise the saved level for the session's sport, then the general level.
        if (explicitLevel != null) { sql.append(" AND c.level=?"); values.add(explicitLevel); }
        else if (profileLevel != null) { sql.append(" AND c.level=COALESCE(msl.level,?)"); values.add(profileLevel); }
        else sql.append(" AND (msl.level IS NULL OR c.level=msl.level)");
        if (request.earliest() != null) { sql.append(" AND s.start_time>=CAST(? AS time)"); values.add(Time.valueOf(request.earliest())); }
        if (request.latest() != null) { sql.append(" AND s.end_time<=CAST(? AS time)"); values.add(Time.valueOf(request.latest())); }
        final String selectedGoal = goal;
        final String generalLevel = profileLevel;
        var candidates = new ArrayList<>(jdbc.query(sql.toString(), (rs,row) -> {
            LocalDate date = rs.getDate("session_date").toLocalDate();
            if (days != null && !days.contains(date.getDayOfWeek())) return null;
            String ageLabel = rs.getString("age_label");
            if (ageLabel != null && dateOfBirth != null && !fitsAge(dateOfBirth, date,
                    rs.getObject("min_age", Integer.class), rs.getObject("max_age", Integer.class))) return null;
            var reasons = new ArrayList<String>();
            reasons.add("Published upcoming session with " + rs.getInt("free_seats") + " available places");
            int score = 0;
            if (request.sportId() != null) { score += 50; reasons.add("Matches your selected sport"); }
            else if (rs.getBoolean("preferred")) { score += 10; reasons.add("One of your preferred sports"); }
            if (format != null) reasons.add(format.equals("COACH_LED") ? "Coach-led, as you prefer" : "Self-training, as you prefer");
            if (days != null) reasons.add("On one of your available days (" + date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ")");
            String sportLevel = rs.getString("sport_level");
            if (explicitLevel != null) { score += 20; reasons.add("Matches your selected level: " + explicitLevel); }
            else if (sportLevel != null) { score += 20; reasons.add("Matches your saved " + rs.getString("sport_name") + " level: " + sportLevel); }
            else if (generalLevel != null) { score += 20; reasons.add("Matches your saved level: " + generalLevel); }
            if (ageLabel != null) reasons.add(dateOfBirth == null
                ? "Age group " + ageLabel + ": add your date of birth to your profile to confirm it fits"
                : "Fits the " + ageLabel + " age group");
            if (rs.getBoolean("familiar")) { score += 5; reasons.add("You have recorded participation in this sport"); }
            LocalTime start = rs.getTime("start_time").toLocalTime();
            LocalTime end = rs.getTime("end_time").toLocalTime();
            int goalScore = goalScore(selectedGoal, rs.getString("training_type"), rs.getString("level"), Duration.between(start,end).toMinutes());
            if (goalScore > 0) {
                score += goalScore;
                reasons.add(switch(selectedGoal) {
                    case "BUILD_SKILLS" -> "Build skills goal: a coach-led session";
                    case "COMPETITION" -> "Competition goal: advanced coach-led practice";
                    default -> "Improve fitness goal: at least 60 minutes of practice";
                });
            }
            return new ClassRecommendation(rs.getLong("class_id"),rs.getLong("session_id"),rs.getLong("sport_id"),rs.getString("class_name"),
                rs.getString("sport_name"),rs.getString("level"),rs.getString("coach_name"),rs.getString("training_type"),
                date,start,end,rs.getInt("free_seats"),score,List.copyOf(reasons),rs.getString("session_code"));
        }, values.toArray()));
        candidates.removeIf(Objects::isNull);
        var visibleIds=visibility.filter(candidates.stream().map(ClassRecommendation::sessionId).toList());
        candidates.removeIf(candidate->!visibleIds.contains(candidate.sessionId()));
        candidates.sort(Comparator.comparingInt(ClassRecommendation::score).reversed().thenComparing(ClassRecommendation::date)
            .thenComparing(ClassRecommendation::startTime).thenComparingLong(ClassRecommendation::classId).thenComparingLong(ClassRecommendation::sessionId));
        var classes = new LinkedHashMap<Long,ClassRecommendation>();
        for (var candidate : candidates) classes.putIfAbsent(candidate.classId(),candidate);
        var items = classes.values().stream().skip((long)request.page()*request.size()).limit(request.size()).toList();
        return new Recommendations("RULE_BASED","class-rules-v1",false,
            "Preference matches only. Open a session to check package eligibility and current availability. No booking or credit change has occurred.",
            request.page(),request.size(),classes.size(),items);
    }

    /** Age on the session date within the class age group (an open maximum means no upper limit). */
    static boolean fitsAge(LocalDate dateOfBirth, LocalDate sessionDate, Integer minAge, Integer maxAge) {
        int age = java.time.Period.between(dateOfBirth, sessionDate).getYears();
        return (minAge == null || age >= minAge) && (maxAge == null || age <= maxAge);
    }
    static int goalScore(String goal, String format, String level, long minutes) {
        if ("BUILD_SKILLS".equals(goal) && "COACH_LED".equals(format)) return 15;
        if ("COMPETITION".equals(goal) && "ADVANCED".equals(level) && "COACH_LED".equals(format)) return 15;
        if ("IMPROVE_FITNESS".equals(goal) && minutes >= 60) return 15;
        return 0;
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
