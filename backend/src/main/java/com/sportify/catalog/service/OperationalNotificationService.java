package com.sportify.catalog.service;

import com.sportify.catalog.entity.ClassSession;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class OperationalNotificationService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final OperationalMemberPolicy operational;
    @Value("${app.notifications.expiry-window-days:7}") private int expiryDays;
    public record Schedule(long id,String code,String className,LocalDate date,LocalTime startTime,LocalTime endTime,
                           String coachName,String facilityName,String status,long revision) {}
    public record Notice(long id,String title,String content,String type,boolean read,LocalDateTime createdAt,
                         String eventKey,Long originRevision,Schedule currentSchedule) {}
    public record Reminder(String key,String type,String title,String content,LocalDate date,Schedule currentSchedule) {}
    public record Inbox(List<Notice> notices,List<Reminder> reminders,int expiryWindowDays) {}

    public List<Long> recipients(long session) {
        var recipients=jdbc.queryForList("""
            SELECT DISTINCT b.member_id FROM booking b JOIN user_account u ON u.id=b.member_id AND u.status='ACTIVE'
            JOIN role r ON r.id=u.role_id AND r.code='MEMBER' WHERE b.session_id=? AND b.status='CONFIRMED'
            """,Long.class,session);
        return recipients.stream().filter(id->{try{operational.requireRealMember(id);return true;}
            catch(com.sportify.core.exception.BusinessRuleException quarantined){return false;}}).toList();
    }
    @Transactional public void changed(ClassSession session,List<Long> recipients,boolean cancelled) {
        String type=cancelled?"SESSION_CANCELLED":"SESSION_CHANGED";
        String event="SESSION:"+session.getId()+":"+session.getScheduleRevision()+":"+type;
        for(long member:recipients) jdbc.update("""
            INSERT notification(user_id,title,content,type,created_at,event_key,origin_session_id,origin_revision)
            SELECT ?,?,?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM notification WITH(UPDLOCK,HOLDLOCK) WHERE user_id=? AND event_key=?)
            """,member,cancelled?"Booked session cancelled":"Booked session schedule changed",
            cancelled?"Your booked session was cancelled. Review your current bookings and restored package credits.":"Your booked session was updated. Review the current schedule before attending.",
            type,LocalDateTime.now(clock),event,session.getId(),session.getScheduleRevision(),member,event);
    }
    public Inbox inbox(long member) {
        operational.requireRealMember(member);
        if(expiryDays<0 || expiryDays>30) throw new IllegalStateException("Expiry notice window must be between 0 and 30 days");
        var notices=jdbc.query("SELECT TOP (100) * FROM notification WHERE user_id=? ORDER BY created_at DESC,id DESC",(rs,row)->{
            Long session=rs.getObject("origin_session_id",Long.class);
            return new Notice(rs.getLong("id"),rs.getString("title"),rs.getString("content"),rs.getString("type"),rs.getBoolean("is_read"),
                rs.getTimestamp("created_at").toLocalDateTime(),rs.getString("event_key"),rs.getObject("origin_revision",Long.class),session==null?null:schedule(session));
        },member);
        var today=LocalDate.now(clock);var reminders=new ArrayList<Reminder>();
        jdbc.queryForList("""
            SELECT TOP (100) s.id FROM booking b JOIN class_session s ON s.id=b.session_id
            WHERE b.member_id=? AND b.status='CONFIRMED' AND s.status='PUBLISHED'
                AND s.session_date>=? AND s.session_date<=? AND (s.session_date>? OR s.start_time>CAST(? AS TIME))
            ORDER BY s.session_date,s.start_time,s.id
            """,Long.class,member,today,today.plusDays(7),today,LocalTime.now(clock)).forEach(id->{
                var session=schedule(id);reminders.add(new Reminder("SESSION:"+id,"UPCOMING_SESSION","Upcoming booked session","Review the current class schedule.",session.date(),session));
            });
        reminders.addAll(jdbc.query("""
            SELECT TOP (100) r.id,r.end_date,p.name FROM sport_package_registration r JOIN sport_package p ON p.id=r.package_id
            WHERE r.member_id=? AND r.status='ACTIVE' AND r.start_date<=? AND r.end_date BETWEEN ? AND ? ORDER BY r.end_date,r.id
            """,(rs,row)->new Reminder("PACKAGE:"+rs.getLong("id"),"PACKAGE_EXPIRY","Sport package ends soon",rs.getString("name")+" ends on "+rs.getDate("end_date")+".",rs.getDate("end_date").toLocalDate(),null),member,today,today,today.plusDays(expiryDays)));
        reminders.addAll(jdbc.query("""
            SELECT TOP (100) c.id,c.end_date,t.name FROM member_card c JOIN membership_card_tier t ON t.id=c.tier_id
            WHERE c.member_id=? AND c.status='ACTIVE' AND c.start_date<=? AND c.end_date BETWEEN ? AND ? ORDER BY c.end_date,c.id
            """,(rs,row)->new Reminder("CARD:"+rs.getLong("id"),"CARD_EXPIRY","Membership card ends soon",rs.getString("name")+" ends on "+rs.getDate("end_date")+".",rs.getDate("end_date").toLocalDate(),null),member,today,today,today.plusDays(expiryDays)));
        return new Inbox(notices,List.copyOf(reminders),expiryDays);
    }
    /** Manager/Coach header inbox: only the signed-in staff member's own notification rows, never another user's. */
    public List<Notice> own(long user) {
        return jdbc.query("SELECT TOP (20) id,title,content,type,is_read,created_at,event_key,origin_revision FROM notification WHERE user_id=? ORDER BY created_at DESC,id DESC",
            (rs,row)->new Notice(rs.getLong("id"),rs.getString("title"),rs.getString("content"),rs.getString("type"),rs.getBoolean("is_read"),
                rs.getTimestamp("created_at").toLocalDateTime(),rs.getString("event_key"),rs.getObject("origin_revision",Long.class),null),user);
    }
    @Transactional public void read(long member,long id) {
        if(jdbc.update("UPDATE notification SET is_read=1 WHERE id=? AND user_id=?",id,member)!=1)
            throw new ResourceNotFoundException("Notification not found");
    }
    private Schedule schedule(long id) {
        var rows=jdbc.query("""
            SELECT s.*,c.name class_name,u.full_name coach_name,f.name facility_name FROM class_session s
            LEFT JOIN sport_class c ON c.id=s.class_id LEFT JOIN user_account u ON u.id=s.coach_id LEFT JOIN facility f ON f.id=s.facility_id WHERE s.id=?
            """,(rs,row)->new Schedule(rs.getLong("id"),rs.getString("session_code"),rs.getString("class_name"),rs.getDate("session_date").toLocalDate(),rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(),rs.getString("coach_name"),rs.getString("facility_name"),rs.getString("status"),rs.getLong("schedule_revision")),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
}
