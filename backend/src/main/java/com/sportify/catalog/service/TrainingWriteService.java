package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.catalog.dto.TrainingWriteResponse.*;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.identity.entity.UserAccount;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingWriteService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final jakarta.persistence.EntityManager entityManager;
    private final OperationalMemberPolicy operationalMemberPolicy;

    private static final String PLAN_SELECT = """
        SELECT p.id, p.title, p.description, p.class_id, c.name class_name, p.member_id,
               m.full_name member_name, u.full_name coach_name
        FROM session_plan p JOIN sport_class c ON c.id = p.class_id
        JOIN user_account m ON m.id = p.member_id JOIN user_account u ON u.id = p.coach_id
        WHERE p.coach_id = ? AND c.coach_id = p.coach_id AND p.is_template = 0
        """;
    private static final String SESSION_SELECT = """
        SELECT s.id, s.class_id, COALESCE(c.name, sp.name) class_name, s.sport_id,
               s.session_date, s.start_time, s.end_time, s.training_type, s.session_code, sp.name sport_name,
               u.full_name coach_name, f.name facility_name, s.status,
               COALESCE(counts.booked_count,0) booked_count, COALESCE(counts.marked_count,0) marked_count
        FROM class_session s LEFT JOIN sport_class c ON c.id = s.class_id
        JOIN sport sp ON sp.id = s.sport_id
        LEFT JOIN user_account u ON u.id=s.coach_id LEFT JOIN facility f ON f.id=s.facility_id
        LEFT JOIN (
            SELECT b.session_id,COUNT(*) booked_count,COUNT(a.id) marked_count
            FROM booking b LEFT JOIN attendance_record a ON a.session_id=b.session_id AND a.member_id=b.member_id
            WHERE b.status='CONFIRMED' GROUP BY b.session_id
        ) counts ON counts.session_id=s.id
        """;
    private static final String SESSION_MEMBERS_SELECT = """
        SELECT b.id booking_id, b.member_id, u.full_name member_name, a.id attendance_id, a.status, a.notes, r.id result_id
        FROM booking b JOIN class_session s ON s.id = b.session_id
        JOIN user_account u ON u.id = b.member_id
        LEFT JOIN attendance_record a ON a.session_id = b.session_id AND a.member_id = b.member_id
        LEFT JOIN session_result r ON r.session_id = b.session_id AND r.member_id = b.member_id
        WHERE b.session_id = ? AND b.status = 'CONFIRMED'
        """;

    public List<ClassSummary> coachClasses(long coachId) {
        return jdbc.query("SELECT id, name, sport_id FROM sport_class WHERE coach_id = ? ORDER BY name, id",
            (rs, row) -> new ClassSummary(rs.getLong("id"), rs.getString("name"), rs.getLong("sport_id")), coachId);
    }
    public List<BookingCandidate> coachMembers(long coachId, long classId) {
        requireCoachClass(coachId, classId);
        return jdbc.query("""
            SELECT b.id booking_id, b.member_id, u.full_name member_name, s.session_date
            FROM booking b JOIN class_session s ON s.id = b.session_id
            JOIN sport_class c ON c.id = s.class_id AND c.coach_id = ?
            JOIN user_account u ON u.id = b.member_id
            WHERE s.class_id = ? AND b.status = 'CONFIRMED' AND s.status <> 'CANCELLED'
              AND s.training_type = 'COACH_LED' AND s.coach_id = c.coach_id
            ORDER BY u.full_name, s.session_date DESC, b.id
            """, (rs, row) -> new BookingCandidate(rs.getLong("booking_id"), rs.getLong("member_id"),
                rs.getString("member_name"), rs.getDate("session_date").toLocalDate()), coachId, classId);
    }

    public LearnerProfile learnerProfile(long coachId, long classId, long memberId) {
        var profiles=jdbc.query("""
            SELECT m.user_id,m.member_code,u.full_name,m.main_goal,m.current_level
            FROM member_profile m JOIN user_account u ON u.id=m.user_id JOIN role ur ON ur.id=u.role_id
            WHERE m.user_id=? AND u.status='ACTIVE' AND ur.code='MEMBER' AND EXISTS (
                SELECT 1 FROM booking b JOIN class_session s ON s.id=b.session_id
                JOIN sport_class c ON c.id=s.class_id
                JOIN user_account ca ON ca.id=c.coach_id AND ca.status='ACTIVE'
                JOIN role cr ON cr.id=ca.role_id AND cr.code='COACH'
                WHERE b.member_id=m.user_id AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                    AND c.id=? AND c.is_active=1 AND c.coach_id=? AND s.coach_id=?
                    AND s.training_type='COACH_LED')
            """,(rs,row)->new LearnerProfile(rs.getLong("user_id"),rs.getString("member_code"),
                rs.getString("full_name"),rs.getString("main_goal"),rs.getString("current_level")),memberId,classId,coachId,coachId);
        if(profiles.isEmpty()) throw new ResourceNotFoundException("Assigned learner not found");
        operationalMemberPolicy.requireRealMember(memberId);
        return profiles.getFirst();
    }

    /** Individual preview without Coach inputs. Not transactional, see the overload below. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public DrillSuggestions drillSuggestions(long coachId,long bookingId) {
        return drillSuggestions(coachId,bookingId,DrillGenerator.Options.NONE);
    }

    /**
     * Read-only drill preview for the assigned Coach of one member booking. Runs without a transaction so that no
     * database connection is held while the optional AI generator is called; each query below is a single read.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public DrillSuggestions drillSuggestions(long coachId,long bookingId,DrillGenerator.Options options) {
        var context=jdbc.query("""
            SELECT b.member_id,c.id class_id,c.level,sp.code sport_code,sp.name sport_name,m.main_goal,m.current_level
            FROM booking b JOIN class_session s ON s.id=b.session_id JOIN sport_class c ON c.id=s.class_id
            JOIN sport sp ON sp.id=s.sport_id
            JOIN user_account mu ON mu.id=b.member_id AND mu.status='ACTIVE'
            JOIN role mr ON mr.id=mu.role_id AND mr.code='MEMBER'
            JOIN member_profile m ON m.user_id=b.member_id
            WHERE b.id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED' AND s.training_type='COACH_LED'
                AND s.coach_id=? AND c.coach_id=? AND c.is_active=1
            """,(rs,row)->new DrillContext(rs.getLong("member_id"),rs.getLong("class_id"),rs.getString("level"),
                rs.getString("sport_code"),rs.getString("sport_name"),rs.getString("main_goal"),rs.getString("current_level")),bookingId,coachId,coachId);
        // Same message for every miss so a Coach cannot probe other Coaches' bookings or account states.
        if(context.isEmpty())throw new ResourceNotFoundException("Assigned member booking not found");
        var current=context.getFirst();
        try {
            operationalMemberPolicy.requireRealMember(current.memberId());
        } catch(BusinessRuleException synthetic) {
            // This read-only preview reports a quarantined synthetic Member like any other denied booking,
            // so the account state is not revealed. The shared policy keeps its own error for write paths.
            throw new ResourceNotFoundException("Assigned member booking not found");
        }
        var feedback=jdbc.query("""
            SELECT TOP(1) f.areas_for_improvement FROM coach_feedback f JOIN session_plan p ON p.id=f.plan_id
            WHERE f.coach_id=? AND f.member_id=? AND p.class_id=? ORDER BY f.created_at DESC,f.id DESC
            """,(rs,row)->rs.getString("areas_for_improvement"),coachId,current.memberId(),current.classId());
        var metrics=jdbc.query("""
            SELECT TOP(1) m.name FROM session_result r JOIN class_session s ON s.id=r.session_id
            JOIN session_result_score sc ON sc.result_id=r.id JOIN skill_metric m ON m.id=sc.metric_id
            WHERE s.coach_id=? AND r.member_id=? AND s.class_id=?
                AND EXISTS(SELECT 1 FROM activity_log a WHERE a.entity_type='SESSION_RESULT' AND a.entity_id=r.id
                    AND a.actor_user_id=? AND a.action='TRAINING_RESULT_CREATED')
            ORDER BY r.created_at DESC,r.id DESC,sc.score/NULLIF(m.max_score,0),m.id
            """,(rs,row)->rs.getString("name"),coachId,current.memberId(),current.classId(),coachId);
        if(drillGenerator!=null) {
            var ai=generateDrafts(()->individualInput(coachId,current,options));
            if(!ai.isEmpty())return new DrillSuggestions("AI_GENERATED",drillGenerator.version(),true,false,bookingId,current.memberId(),
                current.classId(),current.sportName(),DrillGenerator.effectiveLevel(current.memberLevel(),current.level()),ai);
        }
        String effectiveLevel=DrillGenerator.effectiveLevel(current.memberLevel(),current.level());
        var drafts=fitToSession(TrainingDrillCatalog.suggest(current.sportCode(),current.sportName(),effectiveLevel,
            joinText(options.focus(),feedback.isEmpty()?null:feedback.getFirst()),metrics.isEmpty()?null:metrics.getFirst(),current.goal()),
            options.sessionMinutes());
        return new DrillSuggestions("RULE_BASED","curated-drills-v2",true,false,bookingId,current.memberId(),current.classId(),current.sportName(),effectiveLevel,drafts);
    }

    /**
     * Read-only drill preview for a whole class the Coach is assigned to (Flow 5 plan scope "class"). Only counts and
     * class averages are used: no individual member row, name or id reaches the generator. Nothing is saved.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ClassDrillSuggestions classDrillSuggestions(long coachId,long classId,DrillGenerator.Options options) {
        var classes=jdbc.query("""
            SELECT c.level,sp.code sport_code,sp.name sport_name FROM sport_class c JOIN sport sp ON sp.id=c.sport_id
            JOIN user_account u ON u.id=c.coach_id JOIN role cr ON cr.id=u.role_id AND cr.code='COACH'
            WHERE c.id=? AND c.coach_id=? AND c.is_active=1 AND u.status='ACTIVE'
            """,(rs,row)->new String[]{rs.getString("level"),rs.getString("sport_code"),rs.getString("sport_name")},classId,coachId);
        if(classes.isEmpty())throw new ResourceNotFoundException("Assigned active class not found");
        String level=classes.getFirst()[0], sportCode=classes.getFirst()[1], sportName=classes.getFirst()[2];
        var members=new java.util.ArrayList<>(jdbc.query("""
            SELECT m.user_id,m.current_level,m.main_goal FROM member_profile m JOIN user_account u ON u.id=m.user_id
            JOIN role r ON r.id=u.role_id AND r.code='MEMBER'
            WHERE u.status='ACTIVE' AND LOWER(u.email) NOT LIKE '%@unified-demo.invalid'
                AND m.user_id IN (SELECT b.member_id FROM booking b JOIN class_session s ON s.id=b.session_id
                    WHERE s.class_id=? AND s.coach_id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                        AND s.training_type='COACH_LED')
            """,(rs,row)->new ClassMember(rs.getLong("user_id"),rs.getString("current_level"),rs.getString("main_goal")),classId,coachId));
        members.removeIf(member -> {
            try { operationalMemberPolicy.requireRealMember(member.id()); return false; }
            catch(BusinessRuleException synthetic) { return true; }
        });
        List<DrillGenerator.MetricAverage> averages=List.of();
        if(members.size()>=3 && members.size()<=2000) {
            String placeholders=String.join(",",java.util.Collections.nCopies(members.size(),"?"));
            String averageSql="""
            SELECT TOP(10) m.name,CAST(ROUND(AVG(CAST(sc.score AS DECIMAL(9,2))),2) AS DECIMAL(9,2)) average_score,
                m.max_score,COUNT(DISTINCT r.member_id) samples
            FROM session_result r JOIN class_session s ON s.id=r.session_id JOIN session_result_score sc ON sc.result_id=r.id
            JOIN skill_metric m ON m.id=sc.metric_id
            WHERE s.class_id=? AND s.coach_id=? AND r.member_id IN (%s)
                AND EXISTS(SELECT 1 FROM activity_log a WHERE a.entity_type='SESSION_RESULT' AND a.entity_id=r.id
                    AND a.actor_user_id=? AND a.action='TRAINING_RESULT_CREATED')
            GROUP BY m.id,m.name,m.max_score
            HAVING COUNT(DISTINCT r.member_id)>=3
            ORDER BY AVG(CAST(sc.score AS DECIMAL(9,2)))/NULLIF(m.max_score,0),m.id
            """.formatted(placeholders);
            var values=new java.util.ArrayList<Object>();
            values.add(classId); values.add(coachId);
            members.forEach(member->values.add(member.id()));
            values.add(coachId);
            averages=jdbc.query(averageSql,(rs,row)->new DrillGenerator.MetricAverage(rs.getString("name"),rs.getBigDecimal("average_score"),
                rs.getObject("max_score",Integer.class),rs.getInt("samples")),values.toArray());
        }
        var group=new DrillGenerator.GroupSummary(members.size(),countBy(members,false,"UNKNOWN"),countBy(members,true,"NOT_SET"),averages);
        if(drillGenerator!=null && members.size()>=3 && members.size()<=2000) {
            var ai=generateDrafts(()->new DrillGenerator.Input(DrillGenerator.SCOPE_CLASS,sportName,level,null,null,0,List.of(),
                null,null,options.focus(),options.sessionMinutes(),group));
            if(!ai.isEmpty())return new ClassDrillSuggestions("AI_GENERATED",drillGenerator.version(),true,false,classId,sportName,level,members.size(),ai);
        }
        var drafts=fitToSession(TrainingDrillCatalog.suggest(sportCode,sportName,level,options.focus(),
            averages.isEmpty()?null:averages.getFirst().metric(),null),options.sessionMinutes());
        return new ClassDrillSuggestions("RULE_BASED","curated-drills-v2",true,false,classId,sportName,level,members.size(),drafts);
    }

    /** Optional Flow 5 AI drafting; absent unless app.training.ai-drills.enabled=true. */
    private DrillGenerator drillGenerator;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDrillGenerator(DrillGenerator drillGenerator) { this.drillGenerator = drillGenerator; }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TrainingWriteService.class);

    /** Calls the generator; any failure returns an empty list so the rule catalog is used. */
    private List<DrillDraft> generateDrafts(java.util.function.Supplier<DrillGenerator.Input> input) {
        try {
            return drillGenerator.generate(input.get());
        } catch(RuntimeException failure) {
            // Never log member data or model output; the exception class is enough to diagnose provider failures.
            log.warn("AI drill drafting failed, using the rule catalog: {}",failure.getClass().getSimpleName());
            return List.of();
        }
    }

    /**
     * De-identified individual context: goal, levels, attendance, and the scores and feedback this Coach recorded for
     * this member in this class, plus the Coach's optional focus and session duration.
     */
    private DrillGenerator.Input individualInput(long coachId,DrillContext current,DrillGenerator.Options options) {
        var scores=jdbc.query("""
            SELECT TOP(10) m.name,sc.score,m.max_score,s.session_date FROM session_result r
            JOIN class_session s ON s.id=r.session_id JOIN session_result_score sc ON sc.result_id=r.id
            JOIN skill_metric m ON m.id=sc.metric_id
            WHERE s.coach_id=? AND r.member_id=? AND s.class_id=?
                AND EXISTS(SELECT 1 FROM activity_log a WHERE a.entity_type='SESSION_RESULT' AND a.entity_id=r.id
                    AND a.actor_user_id=? AND a.action='TRAINING_RESULT_CREATED')
            ORDER BY s.session_date DESC,r.id DESC,m.id
            """,(rs,row)->new DrillGenerator.RecentScore(rs.getString("name"),rs.getBigDecimal("score"),
                rs.getObject("max_score",Integer.class),rs.getDate("session_date").toLocalDate()),
            coachId,current.memberId(),current.classId(),coachId);
        var feedback=jdbc.query("""
            SELECT TOP(1) f.strengths,f.areas_for_improvement FROM coach_feedback f JOIN session_plan p ON p.id=f.plan_id
            WHERE f.coach_id=? AND f.member_id=? AND p.class_id=? ORDER BY f.created_at DESC,f.id DESC
            """,(rs,row)->new String[]{rs.getString("strengths"),rs.getString("areas_for_improvement")},
            coachId,current.memberId(),current.classId());
        Integer attended=jdbc.queryForObject("""
            SELECT COUNT(*) FROM attendance_record a JOIN class_session s ON s.id=a.session_id
            WHERE a.member_id=? AND s.class_id=? AND a.status IN ('PRESENT','LATE')
            """,Integer.class,current.memberId(),current.classId());
        return new DrillGenerator.Input(DrillGenerator.SCOPE_INDIVIDUAL,current.sportName(),current.level(),current.memberLevel(),
            current.goal(),attended==null?0:attended,scores,feedback.isEmpty()?null:feedback.getFirst()[0],
            feedback.isEmpty()?null:feedback.getFirst()[1],options.focus(),options.sessionMinutes(),null);
    }

    /** Keeps rule-catalog drafts, in order, while their total fits the optional session duration. */
    static List<DrillDraft> fitToSession(List<DrillDraft> drafts,Integer sessionMinutes) {
        if(sessionMinutes==null)return drafts;
        var kept=new java.util.ArrayList<DrillDraft>();
        int total=0;
        for(var draft:drafts) if(total+draft.durationMinutes()<=sessionMinutes){kept.add(draft);total+=draft.durationMinutes();}
        return List.copyOf(kept);
    }

    private static String joinText(String first,String second) {
        if(first==null)return second;
        return second==null?first:first+" "+second;
    }

    /** Counts members per value of one column; a missing value is counted under the given label. */
    private record ClassMember(long id,String level,String goal) {}
    private static Map<String,Integer> countBy(List<ClassMember> rows,boolean goal,String missing) {
        var counts=new java.util.TreeMap<String,Integer>();
        for(var row:rows) {
            String value=goal?row.goal():row.level();
            counts.merge(value==null?missing:value,1,Integer::sum);
        }
        return counts;
    }

    @Transactional
    public Created createPlan(long coachId, TrainingWriteRequest.Plan request) {
        requireCoachClass(coachId, request.classId());
        // Follow the existing session-first booking/cancellation lock order.
        lockBooking(request.bookingId());
        if (!exists("SELECT COUNT(*) FROM booking b JOIN class_session s ON s.id = b.session_id JOIN sport_class c ON c.id = s.class_id JOIN user_account m ON m.id=b.member_id JOIN role mr ON mr.id=m.role_id JOIN user_account u ON u.id=c.coach_id JOIN role cr ON cr.id=u.role_id WHERE b.id = ? AND b.member_id = ? AND s.class_id = ? AND c.coach_id = ? AND s.coach_id = c.coach_id AND b.status = 'CONFIRMED' AND s.status <> 'CANCELLED' AND s.training_type = 'COACH_LED' AND c.is_active=1 AND m.status='ACTIVE' AND mr.code='MEMBER' AND u.status='ACTIVE' AND cr.code='COACH'",
                request.bookingId(), request.memberId(), request.classId(), coachId))
            throw new BusinessRuleException("Select a confirmed booking belonging to this member and class");
        if (exists("SELECT COUNT(*) FROM session_plan WHERE booking_id=? AND member_id=? AND coach_id=? AND title=? AND is_template=0",
                request.bookingId(), request.memberId(), coachId, request.title().trim()))
            throw new ConflictException("A plan with this title already exists for this booking; choose a distinct title for a new plan");
        long id = insert("INSERT INTO session_plan(class_id, member_id, booking_id, coach_id, title, description, is_template, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
            request.classId(), request.memberId(), request.bookingId(), coachId, request.title().trim(), request.description(), now(), now());
        int order = 0;
        for (var step : request.steps()) jdbc.update("INSERT INTO session_plan_step(plan_id, step_order, title, duration_minutes, instructions) VALUES (?, ?, ?, ?, ?)",
            id, ++order, step.title().trim(), step.durationMinutes(), step.instructions());
        audit(coachId, "TRAINING_PLAN_CREATED", "SESSION_PLAN", id, Map.of("memberId", request.memberId(), "bookingId", request.bookingId()));
        return new Created(id);
    }
    public List<CoachPlan> coachPlans(long coachId) {
        return jdbc.query(PLAN_SELECT + " ORDER BY p.created_at DESC, p.id DESC", this::mapPlan, coachId);
    }
    public CoachPlanDetail coachPlan(long coachId, long planId) {
        var rows = jdbc.query(PLAN_SELECT + " AND p.id = ?", this::mapPlan, coachId, planId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Training plan not found");
        return new CoachPlanDetail(rows.getFirst(), jdbc.query("SELECT id, step_order, title, duration_minutes, instructions FROM session_plan_step WHERE plan_id = ? ORDER BY step_order, id",
            (rs, row) -> new PlanStep(rs.getLong("id"), rs.getInt("step_order"), rs.getString("title"), rs.getInt("duration_minutes"), rs.getString("instructions")), planId));
    }
    @Transactional
    public Created feedback(long coachId, long planId, TrainingWriteRequest.Feedback request) {
        var plan = coachPlan(coachId, planId).plan();
        operationalMemberPolicy.requireRealMember(plan.memberId());
        long id = insert("INSERT INTO coach_feedback(plan_id, session_id, member_id, coach_id, rating, comments, strengths, areas_for_improvement, created_at) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?)",
            planId, plan.memberId(), coachId, request.rating(), request.comments().trim(), request.strengths(), request.areasForImprovement(), now());
        audit(coachId, "TRAINING_FEEDBACK_CREATED", "COACH_FEEDBACK", id, Map.of("planId", planId, "memberId", plan.memberId()));
        return new Created(id);
    }

    public List<SessionSummary> coachSessions(long coachId) {
        return jdbc.query(SESSION_SELECT + " WHERE s.coach_id = ? AND s.status <> 'CANCELLED' ORDER BY s.session_date DESC, s.start_time DESC, s.id DESC", this::mapSession, coachId);
    }
    public List<SessionMember> coachSessionMembers(long coachId, long sessionId) {
        requireCoachSession(coachId, sessionId);
        return jdbc.query(SESSION_MEMBERS_SELECT + " AND s.coach_id = ? ORDER BY u.full_name, b.id", this::mapSessionMember, sessionId, coachId);
    }
    public List<SessionMember> coachSessionMembersByCode(long coachId, String sessionId) {
        return coachSessionMembers(coachId, sessionKey(sessionId));
    }
    private long sessionKey(String sessionId) {
        String code = new PublicSessionId(sessionId).value();
        var rows = jdbc.query("SELECT s.id FROM class_session s JOIN session_code_history h ON h.session_id=s.id WHERE h.session_code=?", (rs,row)->rs.getLong(1), code);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Session not found");
        return rows.getFirst();
    }
    @Transactional
    public Created createResultByCode(long coachId, String sessionId, TrainingWriteRequest.ResultByCode request) {
        return createResult(coachId, new TrainingWriteRequest.Result(sessionKey(sessionId), request.memberId(), request.notes(), request.scores()));
    }
    public List<Metric> metrics(long sportId) {
        return jdbc.query("SELECT id, name, max_score FROM skill_metric WHERE sport_id = ? ORDER BY name, id",
            (rs, row) -> new Metric(rs.getLong("id"), rs.getString("name"), rs.getInt("max_score")), sportId);
    }

    @Transactional
    public Created createResult(long coachId, TrainingWriteRequest.Result request) {
        if (request.scores().isEmpty() && (request.notes() == null || request.notes().isBlank()))
            throw new BusinessRuleException("Provide result notes or at least one skill score");
        requireCoachSession(coachId, request.sessionId());
        lockSessionBooking(request.sessionId(), request.memberId());
        requireCoachSession(coachId, request.sessionId());
        requireSessionEnded(request.sessionId());
        jdbc.update("UPDATE attendance_record SET notes = notes WHERE session_id = ? AND member_id = ?", request.sessionId(), request.memberId());
        if (!exists("SELECT COUNT(*) FROM attendance_record WHERE session_id = ? AND member_id = ? AND status IN ('PRESENT','LATE')", request.sessionId(), request.memberId()))
            throw new BusinessRuleException("Only members marked PRESENT or LATE can receive results");
        if (exists("SELECT COUNT(*) FROM session_result WHERE session_id = ? AND member_id = ?", request.sessionId(), request.memberId()))
            throw new ConflictException("A result already exists for this member and session");
        var metricIds = new HashSet<Long>();
        for (var score : request.scores()) {
            if (!metricIds.add(score.metricId())) throw new BusinessRuleException("Duplicate skill metric");
            var max = jdbc.query("SELECT m.max_score FROM skill_metric m JOIN class_session s ON s.sport_id = m.sport_id WHERE s.id = ? AND m.id = ?",
                (rs, row) -> rs.getBigDecimal("max_score"), request.sessionId(), score.metricId());
            if (max.isEmpty() || score.score().signum() < 0 || score.score().compareTo(max.getFirst()) > 0
                || score.score().compareTo(new BigDecimal("999.99")) > 0 || score.score().scale() > 2)
                throw new BusinessRuleException("Score must use a metric for the session sport, be between 0 and its maximum (at most 999.99), and have at most two decimal places");
        }
        long id = insert("INSERT INTO session_result(session_id, member_id, notes, created_at) VALUES (?, ?, ?, ?)",
            request.sessionId(), request.memberId(), request.notes(), now());
        for (var score : request.scores()) jdbc.update("INSERT INTO session_result_score(result_id, metric_id, score) VALUES (?, ?, ?)", id, score.metricId(), score.score());
        audit(coachId, "TRAINING_RESULT_CREATED", "SESSION_RESULT", id, Map.of("sessionId", request.sessionId(), "memberId", request.memberId()));
        return new Created(id);
    }

    @Transactional
    public Created selfAttendance(long actorId, long bookingId, String notes) {
        return recordAttendance(actorId, new TrainingWriteRequest.Attendance(bookingId, TrainingWriteRequest.AttendanceStatus.PRESENT, notes), true);
    }
    private Created recordAttendance(long actorId, TrainingWriteRequest.Attendance request, boolean selfOnly) {
        lockBooking(request.bookingId());
        var rows = jdbc.query("SELECT b.session_id, b.member_id, b.status, s.training_type FROM booking b JOIN class_session s ON s.id = b.session_id WHERE b.id = ? AND s.status <> 'CANCELLED'",
            (rs, row) -> new AttendanceBooking(rs.getLong("session_id"), rs.getLong("member_id"), rs.getString("status"), rs.getString("training_type")), request.bookingId());
        if (rows.isEmpty() || !rows.getFirst().status().equals("CONFIRMED")) throw new BusinessRuleException("A confirmed booking for a non-cancelled session is required");
        var booking = rows.getFirst();
        operationalMemberPolicy.requireRealMember(booking.memberId());
        if (selfOnly && !booking.trainingType().equals("SELF_TRAINING")) throw new BusinessRuleException("Booking is not for self-training");
        requireAttendanceDate(booking.sessionId(), selfOnly);
        // Within an eligible day attendance time stays flexible; results wait until session end.
        var existing = jdbc.query("SELECT id, status FROM attendance_record WHERE session_id = ? AND member_id = ?",
            (rs, row) -> new ExistingAttendance(rs.getLong("id"), rs.getString("status")), booking.sessionId(), booking.memberId());
        if (!existing.isEmpty()) {
            if (selfOnly && existing.getFirst().status().equals("PRESENT")) return new Created(existing.getFirst().id());
            throw new ConflictException("Attendance already exists; use the correction endpoint with a reason");
        }
        long id = insert("INSERT INTO attendance_record(session_id, member_id, booking_id, status, marked_at, marked_by, notes) VALUES (?, ?, ?, ?, ?, ?, ?)",
            booking.sessionId(), booking.memberId(), request.bookingId(), request.status().name(), now(), actorId, request.notes());
        audit(actorId, "TRAINING_ATTENDANCE_CREATED", "ATTENDANCE_RECORD", id, Map.of("sessionId", booking.sessionId(), "memberId", booking.memberId(), "status", request.status().name()));
        return new Created(id);
    }

    private Created correctAttendance(long actorId, long id, TrainingWriteRequest.Correction request) {
        if (request.reason() == null || request.reason().trim().length() < 3)
            throw new BusinessRuleException("Provide a meaningful correction reason of at least 3 characters");
        var sessionIds = jdbc.query("SELECT session_id FROM attendance_record WHERE id = ?", (rs, row) -> rs.getLong("session_id"), id);
        if (sessionIds.isEmpty()) throw new ResourceNotFoundException("Attendance not found");
        jdbc.queryForList("SELECT member_id FROM attendance_record WHERE id=?", Long.class, id)
                .forEach(operationalMemberPolicy::requireRealMember);
        jdbc.queryForList("SELECT booking_id FROM attendance_record WHERE id=? AND booking_id IS NOT NULL", Long.class, id)
                .forEach(operationalMemberPolicy::requireRealBooking);
        // Match result creation/cancellation: session first, then participation row.
        lockSession(sessionIds.getFirst());
        if (!exists("SELECT COUNT(*) FROM attendance_record a JOIN booking b ON b.id=a.booking_id JOIN class_session s ON s.id=a.session_id WHERE a.id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'", id))
            throw new BusinessRuleException("Attendance correction requires a confirmed booking and non-cancelled session");
        jdbc.update("UPDATE attendance_record SET notes = notes WHERE id = ?", id);
        String previous = jdbc.queryForObject("SELECT status FROM attendance_record WHERE id = ?", String.class, id);
        if (request.status() == TrainingWriteRequest.AttendanceStatus.ABSENT || request.status() == TrainingWriteRequest.AttendanceStatus.EXCUSED) {
            if (exists("SELECT COUNT(*) FROM session_result r JOIN attendance_record a ON a.session_id=r.session_id AND a.member_id=r.member_id WHERE a.id = ?", id))
                throw new ConflictException("A saved result requires PRESENT or LATE attendance; this correction is not allowed");
        }
        jdbc.update("INSERT INTO attendance_correction(attendance_record_id, previous_status, new_status, reason, corrected_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, previous, request.status().name(), request.reason().trim(), actorId, now());
        jdbc.update("UPDATE attendance_record SET status = ?, notes = ?, marked_at = ?, marked_by = ? WHERE id = ?",
            request.status().name(), request.notes(), now(), actorId, id);
        audit(actorId, "TRAINING_ATTENDANCE_CORRECTED", "ATTENDANCE_RECORD", id,
            Map.of("sessionId", sessionIds.getFirst(), "previousStatus", previous,
                "newStatus", request.status().name(), "reason", request.reason().trim()));
        return new Created(id);
    }

    @Transactional
    public Created coachAttendance(long coachId, TrainingWriteRequest.Attendance request) {
        var rows = jdbc.query("SELECT session_id FROM booking WHERE id = ?", (rs, row) -> rs.getLong("session_id"), request.bookingId());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Booking not found");
        long sessionId = rows.getFirst();
        lockSession(sessionId);
        requireCoachSession(coachId, sessionId);
        return recordAttendance(coachId, request, false);
    }
    @Transactional
    public Created coachCorrection(long coachId, long id, TrainingWriteRequest.Correction request) {
        var sessions = jdbc.query("SELECT session_id FROM attendance_record WHERE id = ?", (rs, row) -> rs.getLong("session_id"), id);
        if (sessions.isEmpty()) throw new ResourceNotFoundException("Attendance not found");
        lockSession(sessions.getFirst());
        requireCoachSession(coachId, sessions.getFirst());
        return correctAttendance(coachId, id, request);
    }
    private void audit(long actorId, String action, String entity, long id, Map<String, Object> details) {
        var enriched = new java.util.LinkedHashMap<>(details);
        if(details.get("sessionId") instanceof Number session) {
            enriched.put("sessionCode", jdbc.queryForObject("SELECT session_code FROM class_session WHERE id=?", String.class, session.longValue()));
        }
        audit.record(AuditEvent.builder().actor(entityManager.getReference(UserAccount.class, actorId)).action(action)
            .entityType(entity).entityId(id).summary(action).details(enriched).build());
    }

    private void lockSessionBooking(long sessionId, long memberId) {
        operationalMemberPolicy.requireRealMember(memberId);
        lockSession(sessionId);
        if (jdbc.update("UPDATE booking SET status = status WHERE session_id = ? AND member_id = ? AND status = 'CONFIRMED'", sessionId, memberId) == 0)
            throw new BusinessRuleException("Member does not have a confirmed booking for this session");
    }
    private void lockBooking(long bookingId) {
        operationalMemberPolicy.requireRealBooking(bookingId);
        var sessions = jdbc.query("SELECT session_id FROM booking WHERE id = ?", (rs, row) -> rs.getLong("session_id"), bookingId);
        if (sessions.isEmpty()) throw new ResourceNotFoundException("Booking not found");
        lockSession(sessions.getFirst());
        if (jdbc.update("UPDATE booking SET status = status WHERE id = ? AND session_id = ?", bookingId, sessions.getFirst()) == 0)
            throw new ConflictException("Booking changed; reload and retry");
    }
    private void lockSession(long sessionId) {
        if (jdbc.update("UPDATE class_session SET booked_count = booked_count WHERE id = ?", sessionId) == 0)
            throw new ResourceNotFoundException("Session not found");
    }
    private void requireAttendanceDate(long sessionId, boolean selfOnly) {
        LocalDate date = jdbc.queryForObject("SELECT session_date FROM class_session WHERE id = ?", java.sql.Date.class, sessionId).toLocalDate();
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today) || (selfOnly && !date.equals(today)))
            throw new BusinessRuleException(selfOnly ? "Self-training attendance must be recorded on the session date" : "Future session attendance cannot be recorded");
        if (!date.equals(today))
            throw new BusinessRuleException("Past session attendance cannot be recorded through ordinary attendance. Contact authorized staff about an existing record or correction.");
    }
    private void requireSessionEnded(long sessionId) {
        LocalDateTime end = jdbc.queryForObject("SELECT session_date, end_time FROM class_session WHERE id = ?",
            (rs, row) -> LocalDateTime.of(rs.getDate("session_date").toLocalDate(), rs.getTime("end_time").toLocalTime()), sessionId);
        if (now().isBefore(end)) throw new BusinessRuleException("Results can only be recorded after the session ends");
    }
    private void requireCoachClass(long coachId, long classId) {
        if (!exists("SELECT COUNT(*) FROM sport_class WHERE id = ? AND coach_id = ?", classId, coachId))
            throw new ResourceNotFoundException("Assigned class not found");
    }
    private void requireCoachSession(long coachId, long sessionId) {
        if (!exists("SELECT COUNT(*) FROM class_session WHERE id = ? AND coach_id = ? AND status <> 'CANCELLED'", sessionId, coachId))
            throw new ResourceNotFoundException("Assigned session not found");
    }
    private boolean exists(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args) > 0; }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
    private CoachPlan mapPlan(ResultSet rs, int row) throws SQLException {
        return new CoachPlan(rs.getLong("id"), rs.getString("title"), rs.getString("description"), rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("member_id"), rs.getString("member_name"), rs.getString("coach_name"));
    }
    private SessionMember mapSessionMember(ResultSet rs, int row) throws SQLException {
        return new SessionMember(rs.getLong("booking_id"), rs.getLong("member_id"), rs.getString("member_name"),
            rs.getObject("attendance_id", Long.class), rs.getString("status"), rs.getString("notes"), rs.getObject("result_id", Long.class));
    }
    private SessionSummary mapSession(ResultSet rs, int row) throws SQLException {
        LocalDate date = rs.getDate("session_date").toLocalDate();
        return new SessionSummary(rs.getLong("id"), rs.getObject("class_id", Long.class), rs.getString("class_name"), rs.getLong("sport_id"), date, rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getString("training_type"), date.equals(LocalDate.now(clock)),
            rs.getString("session_code"),rs.getString("sport_name"),rs.getString("coach_name"),rs.getString("facility_name"),rs.getString("status"),rs.getLong("booked_count"),rs.getLong("marked_count"),
            date.isAfter(LocalDate.now(clock)) ? "UPCOMING" : date.isBefore(LocalDate.now(clock)) ? "CLOSED" : "OPEN");
    }
    private record AttendanceBooking(long sessionId, long memberId, String status, String trainingType) {}
    private record ExistingAttendance(long id, String status) {}
    private record DrillContext(long memberId,long classId,String level,String sportCode,String sportName,String goal,String memberLevel) {}
}
