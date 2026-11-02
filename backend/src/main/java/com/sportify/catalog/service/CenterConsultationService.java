package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingGuidanceResponse.Consultation;
import com.sportify.catalog.dto.TrainingGuidanceResponse.ConsultationSource;
import com.sportify.core.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A bounded knowledge service: no provider, arbitrary query execution or persisted conversation. */
@Service
@Transactional(readOnly=true)
public class CenterConsultationService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final VisibleTrainingSessions visibility;
    public CenterConsultationService(JdbcTemplate jdbc, Clock clock,
        @Value("${app.training.consultation.enabled:true}") boolean enabled, VisibleTrainingSessions visibility) {
        this.jdbc=jdbc;this.clock=clock;this.enabled=enabled;this.visibility=visibility;
    }
    public Consultation answer(String question,boolean optIn) {
        if(!enabled)return response("UNAVAILABLE","Automated center consultation is disabled.",List.of());
        if(!optIn)return response("CONSENT_REQUIRED","Enable automated consultation before asking a question. No conversation history is stored.",List.of());
        if(question==null || question.isBlank() || question.length()>500)
            throw new BusinessRuleException("Enter a center question of 1-500 characters");
        String text=question.trim().toLowerCase(Locale.ROOT);
        String intent=intent(text);
        if(intent.equals("COACH"))return response("NO_ANSWER","I cannot assess personal training or health concerns. Ask your assigned human coach about your practice plan.",
            List.of(new ConsultationSource("HUMAN_COACH",0,"Ask your coach","Private human conversation; no automated coach reply.","/member/training/questions")));
        if(intent.equals("UNKNOWN"))return response("NO_ANSWER","I do not have a grounded answer to that question. I can show published sessions, active package prices, or explain how to check booking eligibility. Contact reception for other center information.",List.of());
        if(intent.equals("BOOKING"))return response("ANSWERED","Open a session, check its current availability and suitable sport packages, then review and confirm in the booking flow. Use My bookings to check cancellation eligibility. This consultation never books, cancels, takes payment or issues refunds.",
            List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,"Session and booking workflow","Eligibility and credits are checked by the existing booking service.","/member/classes")));
        Long sportId=matchingSport(text);
        return intent.equals("PACKAGES")?packages(sportId):sessions(sportId,text.contains("tomorrow"));
    }
    static String intent(String text) {
        if(contains(text,"password","secret","token","sql","ignore previous","other member","book for me","cancel for me","refund me"))return "UNKNOWN";
        if(contains(text,"injury","pain","medical","exercise","drill","training plan"))return "COACH";
        if(contains(text,"package","price","cost"))return "PACKAGES";
        if(contains(text,"how to book","how do i book","booking rules","cancel","refund","payment"))return "BOOKING";
        if(contains(text,"class","session","schedule","available"))return "SESSIONS";
        return "UNKNOWN";
    }
    private static boolean contains(String text,String... words) {
        for(String word:words)if(text.contains(word))return true;
        return false;
    }
    private Long matchingSport(String text) {
        var matches=jdbc.query("SELECT id,name FROM sport WHERE is_active=1 ORDER BY id",(rs,row)->
            text.contains(rs.getString("name").toLowerCase(Locale.ROOT))?rs.getLong("id"):null);
        return matches.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    private Consultation packages(Long sportId) {
        String sql="SELECT TOP(5) p.id,p.name,p.price_amount,p.session_count,p.duration_days,sp.name sport_name FROM sport_package p JOIN sport sp ON sp.id=p.sport_id WHERE p.is_active=1 AND sp.is_active=1";
        var values=new ArrayList<Object>();
        if(sportId!=null){sql+=" AND p.sport_id=?";values.add(sportId);}
        sql+=" ORDER BY p.price_amount,p.id";
        var sources=jdbc.query(sql,(rs,row)->new ConsultationSource("SPORT_PACKAGE",rs.getLong("id"),rs.getString("name"),
            rs.getString("sport_name")+": "+rs.getBigDecimal("price_amount").toPlainString()+" VND base price, "+rs.getInt("session_count")+" sessions, valid "+rs.getInt("duration_days")+" days. Discounts and payment status are checked in the purchase flow.",
            "/member/packages/"+rs.getLong("id")),values.toArray());
        return response(sources.isEmpty()?"NO_ANSWER":"ANSWERED",sources.isEmpty()?"No matching active packages are available in the current catalog. Reception can confirm future offers.":
            "Current active package base prices from the center catalog (up to 5, lowest price first). These are catalog prices, not a payment confirmation.",sources);
    }
    private Consultation sessions(Long sportId,boolean tomorrow) {
        LocalDate today=LocalDate.now(clock);
        LocalDate from=tomorrow?today.plusDays(1):today;
        LocalDate to=tomorrow?from:today.plusDays(14);
        String sql="""
            SELECT s.id,c.name,sp.name sport_name,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats
            FROM class_session s JOIN sport sp ON sp.id=s.sport_id LEFT JOIN sport_class c ON c.id=s.class_id
            WHERE s.status='PUBLISHED' AND sp.is_active=1 AND (c.id IS NULL OR c.is_active=1)
                AND s.booked_count<s.capacity AND s.session_date BETWEEN ? AND ?
                AND (s.session_date>? OR s.start_time>CAST(? AS time))
            """;
        var values=new ArrayList<Object>(List.of(Date.valueOf(from),Date.valueOf(to),Date.valueOf(today),Time.valueOf(LocalTime.now(clock))));
        if(sportId!=null){sql+=" AND s.sport_id=?";values.add(sportId);}
        sql+=" ORDER BY s.session_date,s.start_time,s.id";
        var sources=jdbc.query(sql,(rs,row)->new ConsultationSource("CLASS_SESSION",rs.getLong("id"),
            rs.getString("name")==null?rs.getString("sport_name")+" practice":rs.getString("name"),
            rs.getString("sport_name")+": "+rs.getDate("session_date")+" "+rs.getTime("start_time")+"-"+rs.getTime("end_time")+" (Vietnam time), "+rs.getInt("free_seats")+" places remaining. Package eligibility has not been checked.",
            "/member/sessions/"+rs.getLong("id")),values.toArray());
        var visibleIds=visibility.filter(sources.stream().map(ConsultationSource::id).toList());
        var visible=sources.stream().filter(source->visibleIds.contains(source.id())).limit(5).toList();
        return response(visible.isEmpty()?"NO_ANSWER":"ANSWERED",visible.isEmpty()?"No matching published sessions with available places were found for "+from+" through "+to+". Reception can confirm other dates.":
            "Published sessions with available places for "+from+" through "+to+" (up to 5, earliest first). Open the source to check current eligibility.",visible);
    }
    private static Consultation response(String status,String answer,List<ConsultationSource> sources) {
        return new Consultation("KNOWLEDGE_BASED",status,answer,false,sources);
    }
}
