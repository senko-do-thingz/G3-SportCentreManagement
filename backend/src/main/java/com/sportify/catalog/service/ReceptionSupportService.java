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

/**
 * Flow 6 general support requests. Reception can record a request for a Member, and a Member can submit one for
 * themselves. Reception answers with replies the Member can read; resolution notes stay internal to staff.
 */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReceptionSupportService {
    public enum Category { GENERAL,FACILITY,BOOKING,MEMBERSHIP,PAYMENT }
    public enum Status { OPEN,IN_PROGRESS,RESOLVED,CLOSED }
    /** Open (OPEN or IN_PROGRESS) requests a Member may have submitted at the same time. */
    static final int MAX_OPEN_MEMBER_REQUESTS=5;
    static final int MAX_EXCERPT=1000;
    static final int MAX_DESCRIPTION=2000;
    /** The excerpt is text the Member sent, so staff must not treat it as a verified record. */
    static final String EXCERPT_HEADER="\n\nAssistant answer shared by the member (not verified by staff):\n";

    public record Intake(@NotNull @Positive Long memberId,@NotNull Category category,@NotBlank @Size(max=200) String subject,
                         @NotBlank @Size(max=2000) String description,@Positive Long bookingId) {}
    /** A request a Member submits for themselves; the excerpt is the assistant answer the Member chose to share. */
    public record MemberIntake(@NotNull Category category,@NotBlank @Size(max=200) String subject,
                               @NotBlank @Size(max=2000) String description,@Positive Long bookingId,
                               @Size(max=MAX_EXCERPT) String assistantExcerpt) {}
    public record Update(@NotNull Status status,@Size(max=2000) String resolutionNotes) {}
    public record ReplyRequest(@NotBlank @Size(max=2000) String message) {}
    public record Reply(long id,String message,String staffName,LocalDateTime createdAt) {}
    /** Staff view: includes the internal resolution notes and staff identifiers. */
    public record Ticket(long id,String code,long memberId,String memberCode,String memberName,String category,String subject,
                         String description,String status,String resolutionNotes,long createdBy,Long assignedStaffId,
                         Long bookingId,String sessionCode,LocalDateTime createdAt,LocalDateTime updatedAt,
                         boolean memberSubmitted,int replyCount,List<Reply> replies) {}
    /** Member view: never contains internal notes or staff account identifiers. */
    public record MemberReply(String message,LocalDateTime createdAt) {}
    public record MemberTicket(long id,String code,String category,String subject,String description,String status,
                               String sessionCode,boolean submittedByMe,LocalDateTime createdAt,LocalDateTime updatedAt,
                               int unreadReplies,List<MemberReply> replies) {}
    public record Summary(int open,int inProgress,int resolved,int closed) {}
    public record VerificationPackage(String name,String sportName,int remainingSessions,int totalSessions,LocalDate endDate) {}
    public record Verification(String memberCode,String memberName,String memberStatus,String cardTier,Integer cardDiscount,
                               LocalDate cardEndDate,List<VerificationPackage> activePackages) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final OperationalMemberPolicy operational;
    private final AuditService audit;
    private final EntityManager em;
    private final ObjectMapper json;
    private static final String SELECT="""
        SELECT q.*,m.member_code,u.full_name,s.session_code,
            (SELECT COUNT(*) FROM support_request_message x WHERE x.request_id=q.id) reply_count
        FROM support_request q
        JOIN member_profile m ON m.user_id=q.member_id JOIN user_account u ON u.id=q.member_id
        LEFT JOIN booking b ON b.id=q.intake_booking_id LEFT JOIN class_session s ON s.id=b.session_id
        WHERE q.intake_created_by IS NOT NULL AND q.category IN ('GENERAL','FACILITY','BOOKING','MEMBERSHIP','PAYMENT')
        """;

    public List<Ticket> staff(long actor,Long member,Status status) { return staff(actor,member,status,null); }

    /** Up to 100 newest requests, filtered by Member id, status and free text (request code, member name or code, subject). */
    public List<Ticket> staff(long actor,Long member,Status status,String search) {
        staffAccount(actor,false);
        var args=new ArrayList<Object>();String filter="";
        if(member!=null){filter+=" AND q.member_id=?";args.add(member);}
        if(status!=null){filter+=" AND q.status=?";args.add(status.name());}
        String text=search==null?"":search.trim();
        if(text.length()>100)throw new BusinessRuleException("Search text must be at most 100 characters");
        if(!text.isEmpty()) {
            String like="%"+text.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";
            filter+=" AND (q.request_code LIKE ? OR u.full_name LIKE ? OR m.member_code LIKE ? OR q.subject LIKE ?)";
            args.addAll(List.of(like,like,like,like));
        }
        return jdbc.query(SELECT.replace("SELECT q.*","SELECT TOP (100) q.*")+filter+" ORDER BY q.created_at DESC,q.id DESC",this::map,args.toArray());
    }

    /** Number of general support requests in each status, for the inbox counters. */
    public Summary summary(long actor) {
        staffAccount(actor,false);
        var counts=new EnumMap<Status,Integer>(Status.class);
        jdbc.query("SELECT q.status,COUNT(*) n FROM support_request q WHERE q.intake_created_by IS NOT NULL"
                + " AND q.category IN ('GENERAL','FACILITY','BOOKING','MEMBERSHIP','PAYMENT') GROUP BY q.status",
            (ResultSet rs)->{counts.put(Status.valueOf(rs.getString("status")),rs.getInt("n"));});
        return new Summary(counts.getOrDefault(Status.OPEN,0),counts.getOrDefault(Status.IN_PROGRESS,0),
            counts.getOrDefault(Status.RESOLVED,0),counts.getOrDefault(Status.CLOSED,0));
    }

    /** The Member's own requests, newest first. */
    public List<MemberTicket> member(long member) {
        var rows=jdbc.query(SELECT.replace("SELECT q.*","SELECT TOP (100) q.*")+" AND q.member_id=? ORDER BY q.created_at DESC,q.id DESC",this::map,member);
        return rows.stream().map(row->memberView(member,row,false)).toList();
    }

    /** One of the Member's own requests with every reception reply; any other id is reported as not found. */
    public MemberTicket memberDetail(long member,long id) {
        var rows=jdbc.query(SELECT+" AND q.id=? AND q.member_id=?",this::map,id,member);
        if(rows.isEmpty())throw new ResourceNotFoundException("Support request not found");
        return memberView(member,rows.getFirst(),true);
    }

    /** Marks the reply and status notifications of one of the Member's own requests as read. */
    @Transactional public void memberRead(long member,long id) {
        memberDetail(member,id);
        jdbc.update("UPDATE notification SET is_read=1 WHERE user_id=? AND is_read=0 AND event_key LIKE ?",member,eventPrefix(id)+"%");
    }

    public Ticket detail(long actor,long id) {staffAccount(actor,false);return withReplies(ticket(id));}

    /** Facts Reception checks before answering (F6-07 Verification): account state, active card and active packages. */
    public Verification verification(long actor,long id) {
        staffAccount(actor,false);
        var current=ticket(id);
        LocalDate today=LocalDate.now(clock);
        var status=jdbc.queryForObject("SELECT status FROM user_account WHERE id=?",String.class,current.memberId());
        var cards=jdbc.query("""
            SELECT TOP (1) t.name,t.discount_percentage,c.end_date FROM member_card c JOIN membership_card_tier t ON t.id=c.tier_id
            WHERE c.member_id=? AND c.status='ACTIVE' AND c.start_date<=? AND (c.end_date IS NULL OR c.end_date>=?)
            ORDER BY t.discount_percentage DESC,c.end_date DESC
            """,(rs,row)->new Object[]{rs.getString("name"),rs.getInt("discount_percentage"),
                rs.getDate("end_date")==null?null:rs.getDate("end_date").toLocalDate()},
            current.memberId(),today,today);
        var packages=jdbc.query("""
            SELECT TOP (10) p.name,sp.name sport_name,r.remaining_sessions,r.total_sessions,r.end_date
            FROM sport_package_registration r JOIN sport_package p ON p.id=r.package_id JOIN sport sp ON sp.id=p.sport_id
            WHERE r.member_id=? AND r.status='ACTIVE' AND r.end_date>=? ORDER BY r.end_date,r.id
            """,(rs,row)->new VerificationPackage(rs.getString("name"),rs.getString("sport_name"),rs.getInt("remaining_sessions"),
                rs.getInt("total_sessions"),rs.getDate("end_date").toLocalDate()),current.memberId(),today);
        var card=cards.isEmpty()?null:cards.getFirst();
        return new Verification(current.memberCode(),current.memberName(),status,card==null?null:(String)card[0],
            card==null?null:(Integer)card[1],card==null?null:(LocalDate)card[2],packages);
    }

    @Transactional public Ticket create(long actor,Intake request,UUID operation) {
        staffAccount(actor,true);
        if(operation==null)throw new BusinessRuleException("Idempotency-Key is required","OPERATION_KEY_REQUIRED");
        requireMember(request.memberId());
        requireOwnBooking(request.memberId(),request.bookingId());
        String fingerprint=fingerprint(request);
        var prior=prior(actor,operation,fingerprint);
        if(prior!=null)return withReplies(ticket(prior));
        long id=insert(actor,request.memberId(),request.category(),request.subject().trim(),request.description().trim(),
            request.bookingId(),actor,operation,fingerprint);
        record(actor,id,"RECEPTION_SUPPORT_CREATED",Map.of("memberId",request.memberId(),"category",request.category().name()));
        return withReplies(ticket(id));
    }

    /**
     * A Member submits a request for themselves (F6-05). Retrying with the same Idempotency-Key and content returns the
     * same request; at most {@value #MAX_OPEN_MEMBER_REQUESTS} self-submitted requests may be open at once.
     */
    @Transactional public MemberTicket memberCreate(long member,MemberIntake request,UUID operation) {
        if(operation==null)throw new BusinessRuleException("Idempotency-Key is required","OPERATION_KEY_REQUIRED");
        // Serialises concurrent submissions of one Member so the open-request limit cannot be exceeded.
        if(jdbc.queryForList("""
                SELECT m.user_id FROM member_profile m WITH(UPDLOCK,HOLDLOCK) JOIN user_account u ON u.id=m.user_id
                JOIN role r ON r.id=u.role_id WHERE m.user_id=? AND r.code='MEMBER' AND u.status='ACTIVE'
                """,Long.class,member).size()!=1)
            throw new ResourceNotFoundException("Member not found");
        operational.requireRealMember(member);
        String fingerprint=fingerprint(request);
        var prior=prior(member,operation,fingerprint);
        if(prior!=null)return memberDetail(member,prior);
        requireOwnBooking(member,request.bookingId());
        String description=request.description().trim();
        String excerpt=request.assistantExcerpt()==null?"":request.assistantExcerpt().trim();
        if(!excerpt.isEmpty())description+=EXCERPT_HEADER+excerpt;
        if(description.length()>MAX_DESCRIPTION)
            throw new BusinessRuleException("Issue details and the shared assistant excerpt must be at most "+MAX_DESCRIPTION+" characters together");
        Integer open=jdbc.queryForObject("""
            SELECT COUNT(*) FROM support_request WHERE member_id=? AND intake_created_by=member_id AND status IN ('OPEN','IN_PROGRESS')
            """,Integer.class,member);
        if(open!=null && open>=MAX_OPEN_MEMBER_REQUESTS)
            throw new ConflictException("You already have "+MAX_OPEN_MEMBER_REQUESTS+" open support requests. Wait for a reply before sending another","SUPPORT_OPEN_LIMIT");
        long id=insert(member,member,request.category(),request.subject().trim(),description,request.bookingId(),null,operation,fingerprint);
        record(member,id,"MEMBER_SUPPORT_SUBMITTED",Map.of("category",request.category().name()));
        return memberDetail(member,id);
    }

    /**
     * Reception answers the Member (F6-07). Allowed while the request is OPEN or IN_PROGRESS; an OPEN request moves to
     * IN_PROGRESS. Sending the same text as this Receptionist's latest reply again is a retry and changes nothing.
     */
    @Transactional public Ticket reply(long actor,long id,ReplyRequest request) {
        staffAccount(actor,true);
        jdbc.queryForList("SELECT id FROM support_request WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id);
        var current=ticket(id);
        String message=request.message()==null?"":request.message().trim();
        if(message.isEmpty())throw new BusinessRuleException("Enter a reply for the Member");
        var latest=jdbc.query("SELECT TOP (1) sender_id,message_body FROM support_request_message WHERE request_id=? ORDER BY created_at DESC,id DESC",
            (rs,row)->new Object[]{rs.getLong("sender_id"),rs.getString("message_body")},id);
        if(!latest.isEmpty() && ((Long)latest.getFirst()[0])==actor && message.equals(latest.getFirst()[1]))return withReplies(current);
        Status from=Status.valueOf(current.status());
        if(from!=Status.OPEN && from!=Status.IN_PROGRESS)
            throw new ConflictException("Replies can only be sent while a request is open or in progress","SUPPORT_STATE_CONFLICT");
        var now=LocalDateTime.now(clock);
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement("INSERT support_request_message(request_id,sender_id,message_body,created_at) VALUES (?,?,?,?)",new String[]{"id"});
            statement.setLong(1,id);statement.setLong(2,actor);statement.setString(3,message);statement.setObject(4,now);return statement;},keys);
        long messageId=Objects.requireNonNull(keys.getKey()).longValue();
        jdbc.update("UPDATE support_request SET status=?,assigned_staff_id=?,updated_at=? WHERE id=?",Status.IN_PROGRESS.name(),actor,now,id);
        notifyMember(current,eventPrefix(id)+"REPLY:"+messageId,"SUPPORT_REPLY","Reception replied to "+current.code(),
            "Reception answered your support request \""+current.subject()+"\". Open My support requests to read the reply.");
        record(actor,id,"RECEPTION_SUPPORT_REPLIED",from==Status.OPEN?Map.of("from","OPEN","to","IN_PROGRESS"):Map.of());
        return withReplies(ticket(id));
    }

    @Transactional public Ticket update(long actor,long id,Update request) {
        staffAccount(actor,true);
        jdbc.queryForList("SELECT id FROM support_request WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id);
        var current=ticket(id);Status from=Status.valueOf(current.status()),to=request.status();
        String notes=request.resolutionNotes()==null?null:request.resolutionNotes().trim();
        if(from==to) {
            if(!Objects.equals(current.resolutionNotes(),notes))throw new ConflictException("The recorded status already has different resolution notes","SUPPORT_STATE_CONFLICT");
            return withReplies(current);
        }
        boolean allowed=switch(from){case OPEN->to==Status.IN_PROGRESS || to==Status.CLOSED;case IN_PROGRESS->to==Status.RESOLVED || to==Status.CLOSED;case RESOLVED->to==Status.CLOSED;case CLOSED->false;};
        if(!allowed)throw new ConflictException("Support status transition is not allowed","SUPPORT_STATE_CONFLICT");
        if((to==Status.RESOLVED || to==Status.CLOSED) && (notes==null || notes.length()<3))throw new BusinessRuleException("Provide resolution notes before resolving or closing a request","SUPPORT_RESOLUTION_REQUIRED");
        // A Member who asked in writing must receive a written answer before the request counts as resolved.
        if(to==Status.RESOLVED && current.memberSubmitted() && current.replyCount()==0)
            throw new BusinessRuleException("Send a reply to the Member before resolving a request they submitted","SUPPORT_REPLY_REQUIRED");
        jdbc.update("UPDATE support_request SET status=?,resolution_notes=?,assigned_staff_id=?,updated_at=? WHERE id=?",to.name(),notes,actor,LocalDateTime.now(clock),id);
        if(to==Status.RESOLVED || to==Status.CLOSED)
            notifyMember(current,eventPrefix(id)+"STATUS:"+to.name(),"SUPPORT_STATUS","Support request "+current.code()+" "+(to==Status.RESOLVED?"resolved":"closed"),
                "Your support request \""+current.subject()+"\" is now "+(to==Status.RESOLVED?"resolved":"closed")+". Open My support requests for details.");
        record(actor,id,"RECEPTION_SUPPORT_STATUS_CHANGED",Map.of("from",from.name(),"to",to.name()));return withReplies(ticket(id));
    }

    private void requireMember(long member) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM member_profile m JOIN user_account u ON u.id=m.user_id JOIN role r ON r.id=u.role_id WHERE m.user_id=? AND r.code='MEMBER'",Integer.class,member)!=1)
            throw new ResourceNotFoundException("Member not found");
        operational.requireRealMember(member);
    }
    private void requireOwnBooking(long member,Long booking) {
        if(booking==null)return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE id=? AND member_id=?",Integer.class,booking,member)!=1)
            throw new ResourceNotFoundException("Member booking not found");
        operational.requireRealBooking(booking);
    }
    private String fingerprint(Object request) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));}
        catch(java.security.NoSuchAlgorithmException | java.io.IOException failure){throw new IllegalStateException("Cannot fingerprint intake",failure);}
    }
    /** The request already created with this key, or null; the same key with different content is a conflict. */
    private Long prior(long creator,UUID operation,String fingerprint) {
        var prior=jdbc.queryForList("SELECT id,intake_fingerprint FROM support_request WHERE intake_created_by=? AND intake_operation_id=?",creator,operation.toString());
        if(prior.isEmpty())return null;
        if(!fingerprint.equals(prior.getFirst().get("intake_fingerprint")))throw new ConflictException("Operation key was used for different intake content","OPERATION_KEY_CONFLICT");
        return ((Number)prior.getFirst().get("id")).longValue();
    }
    private long insert(long creator,long member,Category category,String subject,String description,Long booking,Long assigned,UUID operation,String fingerprint) {
        Long sequence=jdbc.queryForObject("SELECT NEXT VALUE FOR seq_support_request_code",Long.class);
        var keys=new GeneratedKeyHolder();var now=LocalDateTime.now(clock);
        jdbc.update(connection->{var statement=connection.prepareStatement("INSERT support_request(request_code,member_id,category,subject,description,assigned_staff_id,intake_created_by,intake_booking_id,intake_operation_id,intake_fingerprint,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            Object[] values={"SUP-"+sequence,member,category.name(),subject,description,assigned,creator,booking,operation.toString(),fingerprint,now,now};
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void notifyMember(Ticket ticket,String event,String type,String title,String content) {
        jdbc.update("""
            INSERT notification(user_id,title,content,type,created_at,event_key)
            SELECT ?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM notification WITH(UPDLOCK,HOLDLOCK) WHERE user_id=? AND event_key=?)
            """,ticket.memberId(),title,content,type,LocalDateTime.now(clock),event,ticket.memberId(),event);
    }
    private static String eventPrefix(long id){return "SUPPORT:"+id+":";}
    private void staffAccount(long actor,boolean lock) {
        String hint=lock?" WITH(UPDLOCK,HOLDLOCK)":"";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM user_account u"+hint+" JOIN role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code='RECEPTIONIST'",Integer.class,actor)!=1)
            throw new org.springframework.security.access.AccessDeniedException("An enabled Receptionist is required");
    }
    private Ticket ticket(long id) {var rows=jdbc.query(SELECT+" AND q.id=?",this::map,id);if(rows.isEmpty())throw new ResourceNotFoundException("General support request not found");return rows.getFirst();}
    private List<Reply> replies(long id) {
        return jdbc.query("""
            SELECT x.id,x.message_body,x.created_at,u.full_name FROM support_request_message x JOIN user_account u ON u.id=x.sender_id
            WHERE x.request_id=? ORDER BY x.created_at,x.id
            """,(rs,row)->new Reply(rs.getLong("id"),rs.getString("message_body"),rs.getString("full_name"),rs.getTimestamp("created_at").toLocalDateTime()),id);
    }
    private Ticket withReplies(Ticket t) {
        return new Ticket(t.id(),t.code(),t.memberId(),t.memberCode(),t.memberName(),t.category(),t.subject(),t.description(),t.status(),
            t.resolutionNotes(),t.createdBy(),t.assignedStaffId(),t.bookingId(),t.sessionCode(),t.createdAt(),t.updatedAt(),
            t.memberSubmitted(),t.replyCount(),replies(t.id()));
    }
    private MemberTicket memberView(long member,Ticket t,boolean withReplies) {
        Integer unread=jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND is_read=0 AND event_key LIKE ?",
            Integer.class,member,eventPrefix(t.id())+"REPLY:%");
        var replies=withReplies?replies(t.id()).stream().map(r->new MemberReply(r.message(),r.createdAt())).toList():List.<MemberReply>of();
        return new MemberTicket(t.id(),t.code(),t.category(),t.subject(),t.description(),t.status(),t.sessionCode(),t.memberSubmitted(),
            t.createdAt(),t.updatedAt(),unread==null?0:unread,replies);
    }
    private void record(long actor,long id,String action,Map<String,Object> details) {
        audit.record(AuditEvent.builder().actor(em.getReference(UserAccount.class,actor)).action(action).entityType("SUPPORT_REQUEST").entityId(id)
            .summary("Support request state recorded").details(details).build());
    }
    private Ticket map(ResultSet rs,int row)throws SQLException {
        long member=rs.getLong("member_id");long createdBy=rs.getLong("intake_created_by");
        return new Ticket(rs.getLong("id"),rs.getString("request_code"),member,rs.getString("member_code"),rs.getString("full_name"),rs.getString("category"),
            rs.getString("subject"),rs.getString("description"),rs.getString("status"),rs.getString("resolution_notes"),createdBy,rs.getObject("assigned_staff_id",Long.class),
            rs.getObject("intake_booking_id",Long.class),rs.getString("session_code"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime(),
            createdBy==member,rs.getInt("reply_count"),List.of());
    }
}
