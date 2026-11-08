package com.sportify.identity.service;

import com.sportify.catalog.service.TrainingWriteService;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MW-02 optional health questionnaire. The result is advisory only: it never clears, blocks or diagnoses anyone.
 * Raw answers are visible to the Member only. An assigned Coach sees an outcome summary, and only with consent.
 * Answers are never written to logs, audit details or notifications.
 */
@Service
@RequiredArgsConstructor
public class MemberHealthService {

    public static final String VERSION = "HQ-2026-11";
    public static final String NO_REPORTED_CONCERNS = "NO_REPORTED_CONCERNS";
    public static final String COACH_DISCUSSION_RECOMMENDED = "COACH_DISCUSSION_RECOMMENDED";
    public static final String INCOMPLETE = "INCOMPLETE";
    public static final String DISCLAIMER = "This questionnaire is not a medical assessment. The result is a training note only."
            + " It does not mean you are medically cleared or restricted. Ask a health professional about any health concern.";
    static final Set<String> ANSWERS = Set.of("YES", "NO", "PREFER_NOT_TO_ANSWER");

    /** Fixed questions of version HQ-2026-11, keyed by their column. */
    public static final List<Question> QUESTIONS = List.of(
            new Question("injuryOrMobility", "injury_or_mobility",
                    "Do you have an injury or mobility issue that affects how you exercise?"),
            new Question("activityRestriction", "activity_restriction",
                    "Has a health professional advised you to limit strenuous activity?"),
            new Question("exertionSymptoms", "exertion_symptoms",
                    "Do you feel dizziness or chest discomfort when you exercise hard?"),
            new Question("coachShouldKnow", "coach_should_know",
                    "Is there a health concern your Coach should keep in mind?"),
            new Question("wantsConsultation", "wants_consultation",
                    "Would you like to talk with a Coach before your first class?"));

    public record Question(String key, String column, String text) {}
    public record QuestionView(String key, String text) {}
    public record Assessment(String version, List<QuestionView> questions, Map<String, String> answers, String outcome,
                             boolean shareWithCoach, boolean saved, LocalDateTime updatedAt,
                             LocalDateTime consentUpdatedAt, String disclaimer) {}
    public record SaveRequest(Map<String, String> answers, Boolean shareWithCoach) {}
    /** What an assigned Coach may see: the advisory outcome, never the individual answers. */
    public record CoachSummary(long memberId, boolean shared, String outcome, Boolean consultationRequested,
                               LocalDateTime updatedAt, String disclaimer) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TrainingWriteService training;

    @Transactional(readOnly = true)
    public Assessment get(long memberId) {
        requireMember(memberId);
        return load(memberId);
    }

    @Transactional
    public Assessment save(long memberId, SaveRequest request) {
        requireMember(memberId);
        if (request == null) {
            throw new FieldValidationException("answers", "Request body is required");
        }
        Map<String, String> answers = request.answers() == null ? Map.of() : request.answers();
        for (var entry : answers.entrySet()) {
            if (QUESTIONS.stream().noneMatch(q -> q.key().equals(entry.getKey()))) {
                throw new FieldValidationException("answers", "Unknown question: " + entry.getKey());
            }
            if (entry.getValue() != null && !ANSWERS.contains(entry.getValue())) {
                throw new FieldValidationException(entry.getKey(), "Answer must be YES, NO or PREFER_NOT_TO_ANSWER");
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // Serialize concurrent saves of the same Member before deciding between insert and update.
        List<Boolean> current = jdbc.query("SELECT share_with_coach FROM dbo.member_health_assessment WITH (UPDLOCK, HOLDLOCK)"
                + " WHERE member_id=?", (rs, row) -> rs.getBoolean(1), memberId);
        boolean share = request.shareWithCoach() != null ? request.shareWithCoach()
                : !current.isEmpty() && current.getFirst();
        boolean consentChanged = current.isEmpty() ? share : current.getFirst() != share;
        Object[] values = QUESTIONS.stream().map(q -> answers.get(q.key())).toArray();
        if (current.isEmpty()) {
            jdbc.update("INSERT INTO dbo.member_health_assessment (member_id, questionnaire_version, injury_or_mobility,"
                    + " activity_restriction, exertion_symptoms, coach_should_know, wants_consultation, share_with_coach,"
                    + " consent_updated_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    memberId, VERSION, values[0], values[1], values[2], values[3], values[4], share,
                    consentChanged ? Timestamp.valueOf(now) : null, Timestamp.valueOf(now), Timestamp.valueOf(now));
        } else {
            jdbc.update("UPDATE dbo.member_health_assessment SET questionnaire_version=?, injury_or_mobility=?,"
                    + " activity_restriction=?, exertion_symptoms=?, coach_should_know=?, wants_consultation=?,"
                    + " share_with_coach=?, consent_updated_at=CASE WHEN ?=1 THEN ? ELSE consent_updated_at END,"
                    + " updated_at=?, version=version+1 WHERE member_id=?",
                    VERSION, values[0], values[1], values[2], values[3], values[4], share, consentChanged ? 1 : 0,
                    Timestamp.valueOf(now), Timestamp.valueOf(now), memberId);
        }
        return load(memberId);
    }

    /** Clears every answer and withdraws Coach sharing. The row keeps only its timestamps. */
    @Transactional
    public Assessment clear(long memberId) {
        requireMember(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE dbo.member_health_assessment SET injury_or_mobility=NULL, activity_restriction=NULL,"
                + " exertion_symptoms=NULL, coach_should_know=NULL, wants_consultation=NULL,"
                + " consent_updated_at=CASE WHEN share_with_coach=1 THEN ? ELSE consent_updated_at END,"
                + " share_with_coach=0, updated_at=?, version=version+1 WHERE member_id=?",
                Timestamp.valueOf(now), Timestamp.valueOf(now), memberId);
        return load(memberId);
    }

    /**
     * The Coach must currently teach a class the Member has a confirmed booking in; the existing learner-profile
     * scope check enforces that (404 otherwise). Without the Member's consent nothing about the answers is returned.
     */
    @Transactional(readOnly = true)
    public CoachSummary coachSummary(long coachId, long classId, long memberId) {
        training.learnerProfile(coachId, classId, memberId);
        var rows = jdbc.queryForList("SELECT * FROM dbo.member_health_assessment WHERE member_id=?", memberId);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("share_with_coach"))) {
            return new CoachSummary(memberId, false, null, null, null, DISCLAIMER);
        }
        Map<String, String> answers = answers(rows.getFirst());
        return new CoachSummary(memberId, true, outcome(answers), "YES".equals(answers.get("wantsConsultation")),
                ((Timestamp) rows.getFirst().get("updated_at")).toLocalDateTime(), DISCLAIMER);
    }

    /** Any YES asks for a Coach conversation; otherwise any skipped or withheld answer leaves it incomplete. */
    public static String outcome(Map<String, String> answers) {
        if (QUESTIONS.stream().anyMatch(q -> "YES".equals(answers.get(q.key())))) {
            return COACH_DISCUSSION_RECOMMENDED;
        }
        if (QUESTIONS.stream().allMatch(q -> "NO".equals(answers.get(q.key())))) {
            return NO_REPORTED_CONCERNS;
        }
        return INCOMPLETE;
    }

    private Assessment load(long memberId) {
        var rows = jdbc.queryForList("SELECT * FROM dbo.member_health_assessment WHERE member_id=?", memberId);
        List<QuestionView> questions = QUESTIONS.stream().map(q -> new QuestionView(q.key(), q.text())).toList();
        if (rows.isEmpty()) {
            return new Assessment(VERSION, questions, Map.of(), INCOMPLETE, false, false, null, null, DISCLAIMER);
        }
        var row = rows.getFirst();
        Map<String, String> answers = answers(row);
        Timestamp consent = (Timestamp) row.get("consent_updated_at");
        return new Assessment(VERSION, questions, answers, outcome(answers),
                Boolean.TRUE.equals(row.get("share_with_coach")), true,
                ((Timestamp) row.get("updated_at")).toLocalDateTime(), consent == null ? null : consent.toLocalDateTime(),
                DISCLAIMER);
    }

    private static Map<String, String> answers(Map<String, Object> row) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Question q : QUESTIONS) {
            Object value = row.get(q.column());
            if (value != null) {
                answers.put(q.key(), value.toString());
            }
        }
        return answers;
    }

    private void requireMember(long memberId) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.member_profile m JOIN dbo.user_account u ON u.id=m.user_id"
                + " JOIN dbo.role r ON r.id=u.role_id WHERE m.user_id=? AND u.status='ACTIVE' AND r.code='MEMBER'",
                Integer.class, memberId);
        if (found == null || found == 0) {
            throw new ResourceNotFoundException("Member profile not found");
        }
    }
}
