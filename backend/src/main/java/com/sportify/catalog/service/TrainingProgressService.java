package com.sportify.catalog.service;

import com.sportify.catalog.dto.MemberTrainingResponse;
import com.sportify.catalog.dto.TrainingWriteResponse.LearnerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Chronological source records, without invented scores or cross-metric trends. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class TrainingProgressService {
    private final JdbcTemplate jdbc;
    private final TrainingWriteService writer;
    private final MemberTrainingService reader;
    public record MetricPoint(long metricId,String metricName,int maxScore,BigDecimal score) {}
    public record Outcome(long resultId,long sessionId,String sessionCode,LocalDate date,String notes,
                          String attendanceStatus,boolean notesOnly,List<MetricPoint> scores) {}
    public record Progress(LearnerProfile learner,List<Outcome> sessions,List<MemberTrainingResponse.Feedback> feedback,
                           String interpretation) {}

    public Progress coach(long coachId,long classId,long memberId) {
        var learner=writer.learnerProfile(coachId,classId,memberId);
        return new Progress(learner,outcomes(memberId,classId,coachId),feedback(memberId,classId,coachId),
                "Chronological recorded outcomes; compare only identical metric IDs and scales. Notes-only sessions have no numeric trend.");
    }
    public Progress member(long memberId) {
        return new Progress(null,outcomes(memberId,null,null),reader.feedback(memberId),
                "Chronological recorded outcomes; compare only identical metric IDs and scales. Notes-only sessions have no numeric trend.");
    }
    private List<Outcome> outcomes(long memberId,Long classId,Long coachId) {
        String filter=classId==null?"":" AND s.class_id=? AND s.coach_id=?";
        Object[] parameters=classId==null?new Object[]{memberId}:new Object[]{memberId,classId,coachId};
        return jdbc.query("""
                SELECT r.id,s.id session_id,s.session_code,s.session_date,r.notes,a.status
                FROM session_result r JOIN class_session s ON s.id=r.session_id
                LEFT JOIN attendance_record a ON a.session_id=r.session_id AND a.member_id=r.member_id
                WHERE r.member_id=?
                """+filter+" ORDER BY s.session_date,s.start_time,s.id,r.id",(rs,row)-> {
                    long id=rs.getLong("id");
                    var scores=jdbc.query("""
                        SELECT m.id,m.name,m.max_score,sc.score FROM session_result_score sc
                        JOIN skill_metric m ON m.id=sc.metric_id WHERE sc.result_id=? ORDER BY m.id,sc.id
                        """,(score,index)->new MetricPoint(score.getLong("id"),score.getString("name"),score.getInt("max_score"),score.getBigDecimal("score")),id);
                    return new Outcome(id,rs.getLong("session_id"),rs.getString("session_code"),rs.getDate("session_date").toLocalDate(),
                            rs.getString("notes"),rs.getString("status"),scores.isEmpty(),scores);
                },parameters);
    }
    private List<MemberTrainingResponse.Feedback> feedback(long memberId,long classId,long coachId) {
        var ids=jdbc.queryForList("""
            SELECT f.id FROM coach_feedback f JOIN session_plan p ON p.id=f.plan_id
            WHERE f.member_id=? AND f.coach_id=? AND p.class_id=? AND p.member_id=f.member_id
            """,Long.class,memberId,coachId,classId);
        return reader.feedback(memberId).stream().filter(f->ids.contains(f.id()))
                .sorted(java.util.Comparator.comparing(MemberTrainingResponse.Feedback::createdAt).thenComparing(MemberTrainingResponse.Feedback::id)).toList();
    }
}
