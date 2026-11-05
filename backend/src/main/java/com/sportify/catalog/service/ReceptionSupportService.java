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

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReceptionSupportService {
    public enum Category { GENERAL,FACILITY,BOOKING,MEMBERSHIP,PAYMENT }
    public enum Status { OPEN,IN_PROGRESS,RESOLVED,CLOSED }
    public record Intake(@NotNull @Positive Long memberId,@NotNull Category category,@NotBlank @Size(max=200) String subject,
                         @NotBlank @Size(max=2000) String description,@Positive Long bookingId) {}
    public record Update(@NotNull Status status,@Size(max=2000) String resolutionNotes) {}
    public record Ticket(long id,String code,long memberId,String memberCode,String memberName,String category,String subject,
                         String description,String status,String resolutionNotes,long createdBy,Long assignedStaffId,
                         Long bookingId,String sessionCode,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final OperationalMemberPolicy operational;
    private final AuditService audit;
    private final EntityManager em;
    private final ObjectMapper json;
    private static final String SELECT="""
        SELECT q.*,m.member_code,u.full_name,s.session_code FROM support_request q
        JOIN member_profile m ON m.user_id=q.member_id JOIN user_account u ON u.id=q.member_id
        LEFT JOIN booking b ON b.id=q.intake_booking_id LEFT JOIN class_session s ON s.id=b.session_id
        WHERE q.intake_created_by IS NOT NULL AND q.category IN ('GENERAL','FACILITY','BOOKING','MEMBERSHIP','PAYMENT')
        """;
    public List<Ticket> staff(long actor,Long member,Status status) {
        staffAccount(actor,false);
        var args=new ArrayList<Object>();String filter="";
        if(member!=null){filter+=" AND q.member_id=?";args.add(member);}
        if(status!=null){filter+=" AND q.status=?";args.add(status.name());}
        return jdbc.query(SELECT.replace("SELECT q.*","SELECT TOP (100) q.*")+filter+" ORDER BY q.created_at DESC,q.id DESC",this::map,args.toArray());
    }
    public List<Ticket> member(long member) {
        return jdbc.query(SELECT.replace("SELECT q.*","SELECT TOP (100) q.*")+" AND q.member_id=? ORDER BY q.created_at DESC,q.id DESC",this::map,member);
    }
    public Ticket detail(long actor,long id) {staffAccount(actor,false);return ticket(id);}
    @Transactional public Ticket create(long actor,Intake request,UUID operation) {
        staffAccount(actor,true);
        if(operation==null)throw new BusinessRuleException("Idempotency-Key is required","OPERATION_KEY_REQUIRED");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM member_profile m JOIN user_account u ON u.id=m.user_id JOIN role r ON r.id=u.role_id WHERE m.user_id=? AND r.code='MEMBER'",Integer.class,request.memberId())!=1)
            throw new ResourceNotFoundException("Member not found");
        operational.requireRealMember(request.memberId());
        if(request.bookingId()!=null) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE id=? AND member_id=?",Integer.class,request.bookingId(),request.memberId())!=1)
                throw new ResourceNotFoundException("Member booking not found");
            operational.requireRealBooking(request.bookingId());
        }
        String fingerprint;
        try {fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));}
        catch(java.security.NoSuchAlgorithmException | java.io.IOException failure){throw new IllegalStateException("Cannot fingerprint intake",failure);}
        var prior=jdbc.queryForList("SELECT id,intake_fingerprint FROM support_request WHERE intake_created_by=? AND intake_operation_id=?",actor,operation.toString());
        if(!prior.isEmpty()) {
            if(!fingerprint.equals(prior.getFirst().get("intake_fingerprint")))throw new ConflictException("Operation key was used for different intake content","OPERATION_KEY_CONFLICT");
            return ticket(((Number)prior.getFirst().get("id")).longValue());
        }
        Long sequence=jdbc.queryForObject("SELECT NEXT VALUE FOR seq_support_request_code",Long.class);
        var keys=new GeneratedKeyHolder();var now=LocalDateTime.now(clock);
        jdbc.update(connection->{var statement=connection.prepareStatement("INSERT support_request(request_code,member_id,category,subject,description,assigned_staff_id,intake_created_by,intake_booking_id,intake_operation_id,intake_fingerprint,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            Object[] values={"SUP-"+sequence,request.memberId(),request.category().name(),request.subject().trim(),request.description().trim(),actor,actor,request.bookingId(),operation.toString(),fingerprint,now,now};
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue();record(actor,id,"RECEPTION_SUPPORT_CREATED",Map.of("memberId",request.memberId(),"category",request.category().name()));return ticket(id);
    }
    @Transactional public Ticket update(long actor,long id,Update request) {
        staffAccount(actor,true);
        jdbc.queryForList("SELECT id FROM support_request WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id);
        var current=ticket(id);Status from=Status.valueOf(current.status()),to=request.status();
        String notes=request.resolutionNotes()==null?null:request.resolutionNotes().trim();
        if(from==to) {
            if(!Objects.equals(current.resolutionNotes(),notes))throw new ConflictException("The recorded status already has different resolution notes","SUPPORT_STATE_CONFLICT");
            return current;
        }
        boolean allowed=switch(from){case OPEN->to==Status.IN_PROGRESS || to==Status.CLOSED;case IN_PROGRESS->to==Status.RESOLVED || to==Status.CLOSED;case RESOLVED->to==Status.CLOSED;case CLOSED->false;};
        if(!allowed)throw new ConflictException("Support status transition is not allowed","SUPPORT_STATE_CONFLICT");
        if((to==Status.RESOLVED || to==Status.CLOSED) && (notes==null || notes.length()<3))throw new BusinessRuleException("Provide resolution notes before resolving or closing a request","SUPPORT_RESOLUTION_REQUIRED");
        jdbc.update("UPDATE support_request SET status=?,resolution_notes=?,assigned_staff_id=?,updated_at=? WHERE id=?",to.name(),notes,actor,LocalDateTime.now(clock),id);
        record(actor,id,"RECEPTION_SUPPORT_STATUS_CHANGED",Map.of("from",from.name(),"to",to.name()));return ticket(id);
    }
    private void staffAccount(long actor,boolean lock) {
        String hint=lock?" WITH(UPDLOCK,HOLDLOCK)":"";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM user_account u"+hint+" JOIN role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code='RECEPTIONIST'",Integer.class,actor)!=1)
            throw new org.springframework.security.access.AccessDeniedException("An enabled Receptionist is required");
    }
    private Ticket ticket(long id) {var rows=jdbc.query(SELECT+" AND q.id=?",this::map,id);if(rows.isEmpty())throw new ResourceNotFoundException("General support request not found");return rows.getFirst();}
    private void record(long actor,long id,String action,Map<String,Object> details) {
        audit.record(AuditEvent.builder().actor(em.getReference(UserAccount.class,actor)).action(action).entityType("SUPPORT_REQUEST").entityId(id)
            .summary("Reception support state recorded").details(details).build());
    }
    private Ticket map(ResultSet rs,int row)throws SQLException {
        return new Ticket(rs.getLong("id"),rs.getString("request_code"),rs.getLong("member_id"),rs.getString("member_code"),rs.getString("full_name"),rs.getString("category"),
            rs.getString("subject"),rs.getString("description"),rs.getString("status"),rs.getString("resolution_notes"),rs.getLong("intake_created_by"),rs.getObject("assigned_staff_id",Long.class),
            rs.getObject("intake_booking_id",Long.class),rs.getString("session_code"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
