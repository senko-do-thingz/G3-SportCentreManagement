package com.sportify.catalog.service;

import com.sportify.catalog.dto.*;
import com.sportify.core.audit.*;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MemberCheckInPassService {
    public static final String PREFIX="SCIN1.";
    private static final SecureRandom RANDOM=new SecureRandom();
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final OperationalMemberPolicy operational;
    private final CheckInService checkIns;
    private final AuditService audit;
    private final EntityManager em;
    public record Pass(String memberCode,String token,Instant expiresAt,Instant serverTime) {
        @Override public String toString() { return "MemberCheckInPass[expiresAt="+expiresAt+"]"; }
    }
    private record Member(String code,int version) {}
    private record Nonce(String trace,long member,int version,String purpose,Instant expiresAt,Instant usedAt) {}

    @Transactional
    public Pass issue(long memberId) {
        Member member=enabledMember(memberId);
        operational.requireRealMember(memberId);
        byte[] random=new byte[32]; RANDOM.nextBytes(random);
        String token=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String trace=UUID.randomUUID().toString(); Instant now=clock.instant(),expires=now.plusSeconds(120);
        jdbc.update("INSERT member_check_in_pass(pass_id,token_hash,member_id,member_version,issued_at_utc,expires_at_utc) VALUES (?,?,?,?,?,?)",
                trace,hash(token),memberId,member.version(),utc(now),utc(expires));
        audit.record(AuditEvent.builder().actor(em.getReference(UserAccount.class,memberId)).action("CHECK_IN_PASS_ISSUED")
                .entityType("MEMBER_CHECK_IN_PASS").entityCode(trace).summary("Short-lived arrival pass issued")
                .details(Map.of("memberId",memberId,"purpose","MEMBER_ARRIVAL")).build());
        return new Pass(member.code(),token,expires,now);
    }
    @Transactional
    public CheckInResponse redeem(String rawToken,Long bookingId,UserAccount actor) {
        if(actor==null || jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_account u WITH(UPDLOCK,HOLDLOCK) JOIN role r ON r.id=u.role_id
                WHERE u.id=? AND u.status='ACTIVE' AND r.code='RECEPTIONIST'
                """,Integer.class,actor.getId())!=1)
            throw new org.springframework.security.access.AccessDeniedException("An enabled Receptionist is required");
        String token=rawToken==null?"":rawToken.trim();
        if(!token.matches("SCIN1\\.[A-Za-z0-9_-]{43}")) throw invalid();
        var nonces=jdbc.query("SELECT pass_id,member_id,member_version,purpose,expires_at_utc,redeemed_at_utc FROM member_check_in_pass WITH(UPDLOCK,HOLDLOCK,ROWLOCK) WHERE token_hash=?",
                (rs,row)->new Nonce(rs.getString("pass_id"),rs.getLong("member_id"),rs.getInt("member_version"),rs.getString("purpose"),
                    rs.getObject("expires_at_utc",LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getObject("redeemed_at_utc",LocalDateTime.class)==null?null:rs.getObject("redeemed_at_utc",LocalDateTime.class).toInstant(ZoneOffset.UTC)),hash(token));
        if(nonces.isEmpty()) throw invalid();
        Nonce nonce=nonces.getFirst();
        if(nonce.usedAt()!=null) throw new ConflictException("Check-in pass was already used; request a new pass","CHECK_IN_PASS_USED");
        if(!"MEMBER_ARRIVAL".equals(nonce.purpose())) throw invalid();
        if(!clock.instant().isBefore(nonce.expiresAt())) throw new BusinessRuleException("Check-in pass expired; request a new pass","CHECK_IN_PASS_EXPIRED");
        Member member=enabledMember(nonce.member());
        if(member.version()!=nonce.version()) throw new BusinessRuleException("Member account changed; request a new pass","CHECK_IN_PASS_REVOKED");
        operational.requireRealMember(nonce.member());
        var request=new CheckInRequest(); request.setIdentifier(member.code()); request.setBookingId(bookingId);
        CheckInResponse result=checkIns.recordCheckIn(request,actor);
        jdbc.update("UPDATE member_check_in_pass SET redeemed_at_utc=?,check_in_id=? WHERE pass_id=?",
                utc(clock.instant()),result.getId(),nonce.trace());
        audit.record(AuditEvent.builder().actor(em.getReference(UserAccount.class,actor.getId())).action("CHECK_IN_PASS_REDEEMED")
                .entityType("MEMBER_CHECK_IN_PASS").entityCode(nonce.trace()).summary("Arrival pass redeemed")
                .details(Map.of("memberId",nonce.member(),"checkInId",result.getId(),"result",result.getResult())).build());
        return result;
    }
    private Member enabledMember(long id) {
        var members=jdbc.query("""
            SELECT m.member_code,u.version FROM member_profile m
            JOIN user_account u WITH(UPDLOCK,HOLDLOCK) ON u.id=m.user_id JOIN role r ON r.id=u.role_id
            WHERE m.user_id=? AND u.status='ACTIVE' AND r.code='MEMBER'
            """,(rs,row)->new Member(rs.getString("member_code"),rs.getInt("version")),id);
        if(members.isEmpty()) throw new BusinessRuleException("An enabled Member account is required","CHECK_IN_MEMBER_UNAVAILABLE");
        return members.getFirst();
    }
    private BusinessRuleException invalid() { return new BusinessRuleException("Invalid check-in pass","CHECK_IN_PASS_INVALID"); }
    private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant,ZoneOffset.UTC); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable",impossible); }
    }
}
