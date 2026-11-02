package com.sportify.catalog.service;
import com.sportify.catalog.dto.ClassLearningResponse.*;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassLearningService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final jakarta.persistence.EntityManager entityManager;
    private static final String CLASSES = "SELECT c.id,c.name,sp.name sport_name,u.full_name coach_name,c.level,c.max_members,c.is_active FROM sport_class c JOIN sport sp ON sp.id=c.sport_id LEFT JOIN user_account u ON u.id=c.coach_id ";
    private static final String QUESTIONS = "SELECT q.id,c.id class_id,c.name class_name,u.full_name member_name,q.subject,q.description,q.status,q.created_at FROM support_request q JOIN sport_class c ON q.category=CONCAT('TRAINING_CLASS_',c.id) JOIN user_account u ON u.id=q.member_id ";
    public List<LearningClass> memberClasses(long memberId) {
        return jdbc.query(CLASSES + "WHERE EXISTS(SELECT 1 FROM booking b JOIN class_session s ON s.id=b.session_id WHERE s.class_id=c.id AND b.member_id=?) ORDER BY c.name,c.id", this::mapClass, memberId);
    }
    public ClassDetail memberClass(long memberId, long classId) {
        var classes = memberClasses(memberId).stream().filter(c -> c.id()==classId).toList();
        if (classes.isEmpty()) throw new ResourceNotFoundException("Class not found");
        var rows = jdbc.query("""
            SELECT b.id booking_id,s.id session_id,s.session_date,s.start_time,s.end_time,b.status booking_status,
            a.status attendance_status,a.notes,r.id result_id,s.status session_status,s.training_type,s.capacity,
            CASE WHEN s.capacity>s.booked_count THEN s.capacity-s.booked_count ELSE 0 END free_seats
            FROM booking b JOIN class_session s ON s.id=b.session_id
            LEFT JOIN attendance_record a ON a.session_id=s.id AND a.member_id=b.member_id
            LEFT JOIN session_result r ON r.session_id=s.id AND r.member_id=b.member_id
            WHERE b.member_id=? AND s.class_id=? ORDER BY s.session_date DESC,s.start_time DESC,b.id
            """, (rs,row) -> new Participation(rs.getLong("booking_id"),rs.getLong("session_id"),rs.getDate("session_date").toLocalDate(),
            rs.getTime("start_time").toLocalTime(),rs.getTime("end_time").toLocalTime(),rs.getString("booking_status"),rs.getString("attendance_status"),rs.getString("notes"),rs.getObject("result_id",Long.class),
            rs.getString("session_status"),rs.getString("training_type"),rs.getInt("capacity"),rs.getInt("free_seats")),memberId,classId);
        return new ClassDetail(classes.getFirst(),rows);
    }
    public List<Question> questions(long actorId, boolean coach) {
        return jdbc.query(QUESTIONS + (coach ? "WHERE c.coach_id=?" : "WHERE q.member_id=?") + " ORDER BY q.created_at DESC,q.id DESC",this::mapQuestion,actorId);
    }
    public Discussion discussion(long actorId, boolean coach, long id) {
        var q=jdbc.query(QUESTIONS + (coach ? "WHERE c.coach_id=?" : "WHERE q.member_id=?") + " AND q.id=?",this::mapQuestion,actorId,id);
        if(q.isEmpty()) throw new ResourceNotFoundException("Class question not found");
        return new Discussion(q.getFirst(),jdbc.query("SELECT m.id,u.full_name,m.message_body,m.created_at FROM support_request_message m JOIN user_account u ON u.id=m.sender_id WHERE m.request_id=? ORDER BY m.created_at,m.id",
            (rs,row)->new Message(rs.getLong("id"),rs.getString("full_name"),rs.getString("message_body"),rs.getTimestamp("created_at").toLocalDateTime()),id));
    }
    @Transactional
    public long ask(long memberId,long classId,String subject,String body) {
        // A question requires an existing confirmed coached booking, never a fabricated enrollment.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM booking b JOIN class_session s ON s.id=b.session_id JOIN sport_class c ON c.id=s.class_id WHERE b.member_id=? AND c.id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED' AND c.coach_id IS NOT NULL",Integer.class,memberId,classId)==0)
            throw new ResourceNotFoundException("A booked class with an assigned coach is required");
        Long coachId=jdbc.queryForObject("SELECT coach_id FROM sport_class WHERE id=?",Long.class,classId);
        Long seq=jdbc.queryForObject("SELECT NEXT VALUE FOR seq_support_request_code",Long.class);
        long id=insert("INSERT INTO support_request(request_code,member_id,subject,category,description,assigned_staff_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
            "TRN-"+seq,memberId,subject.trim(),"TRAINING_CLASS_"+classId,body.trim(),coachId,now(),now());
        audit(memberId,"TRAINING_QUESTION_CREATED",id,Map.of("classId",classId));return id;
    }
    @Transactional
    public long reply(long actorId,boolean coach,long id,String body) {
        var current=discussion(actorId,coach,id);
        // Serialize conversation writes and repeat ownership validation after the lock.
        jdbc.update("UPDATE support_request SET updated_at=updated_at WHERE id=?",id);
        discussion(actorId,coach,id);
        long message=insert("INSERT INTO support_request_message(request_id,sender_id,message_body,created_at) VALUES (?,?,?,?)",id,actorId,body.trim(),now());
        jdbc.update("UPDATE support_request SET status='IN_PROGRESS',updated_at=? WHERE id=?",now(),id);
        audit(actorId,"TRAINING_QUESTION_REPLIED",id,Map.of("messageId",message,"classId",current.question().classId()));return message;
    }
    public Guidance guidance(long memberId,String query) {
        // Catalog search only: never present deterministic matches as an AI answer.
        String term=query.trim().replace("%", "").replace("_", "").replace("[", "");
        var classes=jdbc.query(CLASSES + "WHERE c.is_active=1 AND (c.name LIKE ? OR sp.name LIKE ? OR c.level LIKE ?) ORDER BY c.name,c.id",this::mapClass,"%"+term+"%","%"+term+"%","%"+term+"%");
        return new Guidance("CATALOG_SEARCH", classes.isEmpty()?"No matching classes. Search by sport or level, or ask your coach about supplementary practice.":"Matching active classes from the center catalog. These are search results, not AI recommendations.",classes);
    }
    private LearningClass mapClass(ResultSet rs,int row)throws SQLException{return new LearningClass(rs.getLong("id"),rs.getString("name"),rs.getString("sport_name"),rs.getString("coach_name"),rs.getString("level"),rs.getInt("max_members"),rs.getBoolean("is_active"));}
    private Question mapQuestion(ResultSet rs,int row)throws SQLException{return new Question(rs.getLong("id"),rs.getLong("class_id"),rs.getString("class_name"),rs.getString("member_name"),rs.getString("subject"),rs.getString("description"),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime());}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    private long insert(String sql,Object... values){var key=new GeneratedKeyHolder();jdbc.update(connection->{PreparedStatement statement=connection.prepareStatement(sql,new String[]{"id"});for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},key);return key.getKey().longValue();}
    private void audit(long actor,String action,long id,Map<String,Object> details){audit.record(AuditEvent.builder().actor(entityManager.getReference(UserAccount.class, actor)).action(action).entityType("SUPPORT_REQUEST").entityId(id).summary(action).details(details).build());}
}
