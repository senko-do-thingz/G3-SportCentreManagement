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

    public Recommendations recommendations(long memberId, Preferences request) {
        var profile = jdbc.query("SELECT current_level,main_goal FROM member_profile WHERE user_id=?",
            (rs,row) -> new String[]{rs.getString("current_level"),rs.getString("main_goal")}, memberId);
        String level = optional(request.level());
        String goal = optional(request.goal());
        if (level == null && !profile.isEmpty()) level = optional(profile.getFirst()[0]);
        if (goal == null && !profile.isEmpty()) goal = optional(profile.getFirst()[1]);
        if (level != null && !LEVELS.contains(level) || goal != null && !GOALS.contains(goal))
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
        StringBuilder sql = new StringBuilder("""
            SELECT c.id class_id,c.name class_name,sp.id sport_id,sp.name sport_name,c.level,u.full_name coach_name,
                s.id session_id,s.training_type,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats,
                CASE WHEN EXISTS(SELECT 1 FROM attendance_record a JOIN class_session h ON h.id=a.session_id
                    WHERE a.member_id=? AND h.sport_id=sp.id AND a.status IN ('PRESENT','LATE')) THEN 1 ELSE 0 END familiar
            FROM sport_class c JOIN sport sp ON sp.id=c.sport_id JOIN class_session s ON s.class_id=c.id
            LEFT JOIN user_account u ON u.id=s.coach_id
            WHERE c.is_active=1 AND sp.is_active=1 AND s.status='PUBLISHED' AND s.booked_count<s.capacity
                AND (s.training_type='SELF_TRAINING' OR (u.status='ACTIVE' AND s.coach_id=c.coach_id))
                AND s.session_date BETWEEN ? AND ? AND (s.session_date>? OR s.start_time>CAST(? AS time))
                AND NOT EXISTS(SELECT 1 FROM booking b WHERE b.session_id=s.id AND b.member_id=? AND b.status='CONFIRMED')
            """);
        var values = new ArrayList<Object>(List.of(memberId, Date.valueOf(from), Date.valueOf(to), Date.valueOf(today), Time.valueOf(LocalTime.now(clock)), memberId));
        if (request.sportId() != null) { sql.append(" AND sp.id=?"); values.add(request.sportId()); }
        if (level != null) { sql.append(" AND c.level=?"); values.add(level); }
        if (request.earliest() != null) { sql.append(" AND s.start_time>=CAST(? AS time)"); values.add(Time.valueOf(request.earliest())); }
        if (request.latest() != null) { sql.append(" AND s.end_time<=CAST(? AS time)"); values.add(Time.valueOf(request.latest())); }
        final String selectedGoal = goal;
        final String selectedLevel = level;
        var candidates = jdbc.query(sql.toString(), (rs,row) -> {
            var reasons = new ArrayList<String>();
            reasons.add("Published upcoming session with " + rs.getInt("free_seats") + " available places");
            int score = 0;
            if (request.sportId() != null) { score += 50; reasons.add("Matches your selected sport"); }
            if (selectedLevel != null) { score += 20; reasons.add("Matches your selected or saved level: " + selectedLevel); }
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
                rs.getDate("session_date").toLocalDate(),start,end,rs.getInt("free_seats"),score,List.copyOf(reasons));
        }, values.toArray());
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
    static int goalScore(String goal, String format, String level, long minutes) {
        if ("BUILD_SKILLS".equals(goal) && "COACH_LED".equals(format)) return 15;
        if ("COMPETITION".equals(goal) && "ADVANCED".equals(level) && "COACH_LED".equals(format)) return 15;
        if ("IMPROVE_FITNESS".equals(goal) && minutes >= 60) return 15;
        return 0;
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
