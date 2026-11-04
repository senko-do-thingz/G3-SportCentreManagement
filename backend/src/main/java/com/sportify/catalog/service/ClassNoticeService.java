package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.core.audit.*;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ClassNoticeService {
    public enum Type { HOMEWORK,ANNOUNCEMENT }
    public record Request(@NotNull @Positive Long classId,@NotNull Type type,@NotBlank @Size(max=150) String title,
                          @NotBlank @Size(max=2000) String content,LocalDate dueDate) {}
    public record Notice(long id,long classId,String className,long coachId,String coachName,String type,String title,
                         String content,LocalDate dueDate,String status,LocalDateTime createdAt) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final OperationalMemberPolicy operational;
    private final AuditService audit;
    private final EntityManager em;
    private final ObjectMapper json;
    private static final String SELECT="""
        SELECT n.*,c.name class_name,u.full_name coach_name FROM class_training_notice n
        JOIN sport_class c ON c.id=n.class_id AND c.coach_id=n.coach_id AND c.is_active=1
        JOIN user_account u ON u.id=n.coach_id AND u.status='ACTIVE'
        JOIN role r ON r.id=u.role_id AND r.code='COACH'
        """;
    public List<Notice> coach(long coach) { return jdbc.query(SELECT+" WHERE n.coach_id=? ORDER BY n.created_at DESC,n.id DESC",this::map,coach); }
    public List<Notice> member(long member) {
        operational.requireRealMember(member);
        return jdbc.query(SELECT+"""
            WHERE n.status='PUBLISHED' AND EXISTS(
                SELECT 1 FROM booking b JOIN class_session s ON s.id=b.session_id
                JOIN user_account mu ON mu.id=b.member_id AND mu.status='ACTIVE'
                JOIN role mr ON mr.id=mu.role_id AND mr.code='MEMBER'
                WHERE b.member_id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                    AND s.class_id=n.class_id AND s.coach_id=n.coach_id AND s.training_type='COACH_LED')
            ORDER BY n.created_at DESC,n.id DESC
            """,this::map,member);
    }
    @Transactional public Notice create(long coach,Request request,UUID operation) {
        if(operation==null) throw new BusinessRuleException("Idempotency-Key is required","OPERATION_KEY_REQUIRED");
        own(coach,request.classId());
        if(request.type()==Type.HOMEWORK && (request.dueDate()==null || request.dueDate().isBefore(LocalDate.now(clock))))
            throw new BusinessRuleException("Homework requires a due date today or later","NOTICE_DUE_DATE_INVALID");
        if(request.type()==Type.ANNOUNCEMENT && request.dueDate()!=null)
            throw new BusinessRuleException("Announcements do not have a homework due date","NOTICE_DUE_DATE_INVALID");
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request))); }
        catch(java.security.NoSuchAlgorithmException | java.io.IOException failure) { throw new IllegalStateException("Cannot fingerprint notice",failure); }
        var existing=jdbc.queryForList("SELECT id,request_fingerprint FROM class_training_notice WHERE coach_id=? AND operation_id=?",coach,operation.toString());
        if(!existing.isEmpty()) {
            if(!fingerprint.equals(existing.getFirst().get("request_fingerprint"))) throw new ConflictException("Operation key was used for different notice content","OPERATION_KEY_CONFLICT");
            return detail(coach,((Number)existing.getFirst().get("id")).longValue());
        }
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement("INSERT class_training_notice(class_id,coach_id,notice_type,title,content,due_date,operation_id,request_fingerprint,created_at) VALUES (?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            Object[] values={request.classId(),coach,request.type().name(),request.title().trim(),request.content().trim(),request.dueDate(),operation.toString(),fingerprint,LocalDateTime.now(clock)};
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);return statement;},keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue();record(coach,id,"CLASS_NOTICE_PUBLISHED");return detail(coach,id);
    }
    @Transactional public Notice cancel(long coach,long id) {
        var notice=detail(coach,id);own(coach,notice.classId());
        int changed=jdbc.update("UPDATE class_training_notice SET status='CANCELLED',cancelled_at=? WHERE id=? AND status='PUBLISHED'",LocalDateTime.now(clock),id);
        if(changed==1) record(coach,id,"CLASS_NOTICE_CANCELLED");
        return detail(coach,id);
    }
    public Notice detail(long coach,long id) {
        var notices=jdbc.query(SELECT+" WHERE n.coach_id=? AND n.id=?",this::map,coach,id);
        if(notices.isEmpty()) throw new ResourceNotFoundException("Assigned class notice not found");return notices.getFirst();
    }
    private void own(long coach,long classId) {
        var classes=jdbc.queryForList("""
            SELECT c.id FROM sport_class c WITH(UPDLOCK,HOLDLOCK)
            JOIN user_account u WITH(UPDLOCK,HOLDLOCK) ON u.id=c.coach_id JOIN role r ON r.id=u.role_id
            WHERE c.id=? AND c.coach_id=? AND c.is_active=1 AND u.status='ACTIVE' AND r.code='COACH'
            """,Long.class,classId,coach);
        if(classes.isEmpty()) throw new ResourceNotFoundException("Assigned active class not found");
    }
    private void record(long actor,long id,String action) {
        audit.record(AuditEvent.builder().actor(em.getReference(UserAccount.class,actor)).action(action).entityType("CLASS_NOTICE").entityId(id)
                .summary("Class notice state recorded").build());
    }
    private Notice map(ResultSet rs,int row) throws SQLException {
        return new Notice(rs.getLong("id"),rs.getLong("class_id"),rs.getString("class_name"),rs.getLong("coach_id"),rs.getString("coach_name"),
            rs.getString("notice_type"),rs.getString("title"),rs.getString("content"),rs.getObject("due_date",LocalDate.class),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime());
    }
}
