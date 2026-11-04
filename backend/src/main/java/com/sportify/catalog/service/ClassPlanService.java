package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.TrainingWriteRequest.Step;
import com.sportify.core.audit.*;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ClassPlanService {
    public record Request(@NotNull @Positive Long classId,@NotBlank @Size(max=150) String title,
            @Size(max=1000) String description,@NotEmpty @Size(max=100) List<@NotNull @Valid Step> steps) {}
    public record Summary(long id,long classId,String className,long coachId,String coachName,
            String title,String description,String scope,LocalDateTime createdAt) {}
    public record PlanStep(long id,int order,String title,int durationMinutes,String instructions) {}
    public record Detail(Summary plan,List<PlanStep> steps) {}
    private record Receipt(long id,String fingerprint) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final EntityManager em;
    private final ObjectMapper json;
    private static final String SELECT="""
        SELECT p.id,p.class_id,c.name class_name,p.coach_id,u.full_name coach_name,
            p.title,p.description,p.plan_scope,p.created_at
        FROM session_plan p JOIN sport_class c ON c.id=p.class_id
        JOIN user_account u ON u.id=p.coach_id JOIN role r ON r.id=u.role_id AND r.code='COACH'
        WHERE p.coach_id=? AND c.coach_id=p.coach_id AND c.is_active=1 AND u.status='ACTIVE'
            AND p.plan_scope='CLASS' AND p.is_template=0
        """;
    public List<Summary> list(long coach) {
        return jdbc.query(SELECT+" ORDER BY p.created_at DESC,p.id DESC",this::summary,coach);
    }
    public Detail detail(long coach,long id) {
        var plans=jdbc.query(SELECT+" AND p.id=?",this::summary,coach,id);
        if(plans.isEmpty()) throw new ResourceNotFoundException("Assigned class plan not found");
        var steps=jdbc.query("SELECT id,step_order,title,duration_minutes,instructions FROM session_plan_step WHERE plan_id=? ORDER BY step_order,id",
                (rs,row)->new PlanStep(rs.getLong("id"),rs.getInt("step_order"),rs.getString("title"),rs.getInt("duration_minutes"),rs.getString("instructions")),id);
        return new Detail(plans.getFirst(),steps);
    }
    @Transactional
    public Detail create(long coach,Request request,UUID operation) {
        if(operation==null) throw new BusinessRuleException("Idempotency-Key is required","OPERATION_KEY_REQUIRED");
        var owned=jdbc.queryForList("""
            SELECT c.id FROM sport_class c WITH(UPDLOCK,HOLDLOCK)
            JOIN user_account u ON u.id=c.coach_id JOIN role r ON r.id=u.role_id
            WHERE c.id=? AND c.coach_id=? AND c.is_active=1 AND u.status='ACTIVE' AND r.code='COACH'
            """,Long.class,request.classId(),coach);
        if(owned.isEmpty()) throw new ResourceNotFoundException("Assigned active class not found");
        String fingerprint=fingerprint(request);
        var receipts=jdbc.query("SELECT id,request_fingerprint FROM session_plan WHERE coach_id=? AND operation_id=?",
                (rs,row)->new Receipt(rs.getLong("id"),rs.getString("request_fingerprint")),coach,operation.toString());
        if(!receipts.isEmpty()) {
            if(!fingerprint.equals(receipts.getFirst().fingerprint())) throw new ConflictException("Operation key was used for different plan content","OPERATION_KEY_CONFLICT");
            return detail(coach,receipts.getFirst().id());
        }
        var keys=new GeneratedKeyHolder(); LocalDateTime now=LocalDateTime.now(clock);
        jdbc.update(connection->{
            PreparedStatement statement=connection.prepareStatement("INSERT session_plan(class_id,coach_id,title,description,is_template,plan_scope,operation_id,request_fingerprint,created_at,updated_at) VALUES (?,?,?,?,0,'CLASS',?,?,?,?)",new String[]{"id"});
            Object[] values={request.classId(),coach,request.title().trim(),request.description(),operation.toString(),fingerprint,now,now};
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue(); int order=0;
        for(var step:request.steps()) jdbc.update("INSERT session_plan_step(plan_id,step_order,title,duration_minutes,instructions) VALUES (?,?,?,?,?)",
                id,++order,step.title().trim(),step.durationMinutes(),step.instructions());
        audit.record(AuditEvent.builder().actor(em.getReference(UserAccount.class,coach)).action("CLASS_PLAN_CREATED")
                .entityType("SESSION_PLAN").entityId(id).summary("Class-wide training plan created")
                .details(Map.of("classId",request.classId(),"scope","CLASS")).build());
        return detail(coach,id);
    }
    private String fingerprint(Request request) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request))); }
        catch(java.security.NoSuchAlgorithmException | java.io.IOException failure) { throw new IllegalStateException("Cannot fingerprint a validated plan",failure); }
    }
    private Summary summary(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new Summary(rs.getLong("id"),rs.getLong("class_id"),rs.getString("class_name"),rs.getLong("coach_id"),rs.getString("coach_name"),
                rs.getString("title"),rs.getString("description"),rs.getString("plan_scope"),rs.getTimestamp("created_at").toLocalDateTime());
    }
}
