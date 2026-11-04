package com.sportify.payment.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.payment.dto.finance.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OperationsReportService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FinanceReportService finance;
    private final Clock clock;
    private final Environment environment;
    public record BookingCounts(long total,long confirmed,long cancelled,long upcoming,long completed) {}
    public record Report(LocalDate from,LocalDate to,String timeZone,long registeredMembers,
            long currentlyEnabledMembersRegistered,BookingCounts classBookings,RevenueBreakdownResponse revenue) {}
    private static final String REAL_MEMBER="""
        u.id NOT IN (:reserved) AND LOWER(u.email) NOT LIKE '%@unified-demo.invalid'
        AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.member_id=u.id AND
            (p.settlement_type='SIMULATED' OR p.settlement_provider='SPORTIFY_SIMULATOR' OR p.simulated_transaction_id IS NOT NULL))
        AND NOT EXISTS (SELECT 1 FROM simulated_payment_transaction t WHERE t.member_id=u.id)
        """;
    @Transactional(readOnly=true)
    public Report report(LocalDate from,LocalDate to) {
        // Reuse the authoritative finance range validation, defaults and actual-vs-simulated revenue.
        var revenue=finance.revenue(from,to,RevenueGroupBy.ITEM_TYPE);
        var today=LocalDateTime.now(clock);
        var params=new MapSqlParameterSource().addValue("start",Timestamp.valueOf(revenue.from().atStartOfDay()))
                .addValue("end",Timestamp.valueOf(revenue.to().plusDays(1).atStartOfDay()))
                .addValue("today",java.sql.Date.valueOf(today.toLocalDate()))
                .addValue("time",java.sql.Time.valueOf(today.toLocalTime())).addValue("reserved",reservedIds());
        String members=" FROM user_account u JOIN role r ON r.id=u.role_id WHERE r.code='MEMBER' AND "+REAL_MEMBER
                +" AND u.created_at>=:start AND u.created_at<:end";
        long registered=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*)"+members,params,Long.class));
        long enabled=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*)"+members+" AND u.status='ACTIVE'",params,Long.class));
        BookingCounts counts=jdbc.queryForObject("""
            SELECT COUNT_BIG(*) total,
                COALESCE(SUM(CASE WHEN b.status='CONFIRMED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) confirmed,
                COALESCE(SUM(CASE WHEN b.status='CANCELLED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) cancelled,
                COALESCE(SUM(CASE WHEN b.status='CONFIRMED' AND s.status='PUBLISHED'
                    AND (s.session_date>:today OR (s.session_date=:today AND s.start_time>=:time)) THEN CAST(1 AS BIGINT) ELSE 0 END),0) upcoming,
                COALESCE(SUM(CASE WHEN b.status='CONFIRMED' AND s.status='COMPLETED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) completed
            FROM booking b JOIN class_session s ON s.id=b.session_id JOIN user_account u ON u.id=b.member_id
            WHERE s.class_id IS NOT NULL AND b.booked_at>=:start AND b.booked_at<:end AND
            """+REAL_MEMBER,params,(rs,row)->new BookingCounts(rs.getLong("total"),rs.getLong("confirmed"),rs.getLong("cancelled"),rs.getLong("upcoming"),rs.getLong("completed")));
        return new Report(revenue.from(),revenue.to(),clock.getZone().getId(),registered,enabled,counts,revenue);
    }
    private List<Long> reservedIds() {
        var result=new ArrayList<Long>(); result.add(-1L);
        for(String value:environment.getProperty("app.payment.unified-demo.member-ids","").split(",")) {
            if(value.isBlank()) continue;
            try { long id=Long.parseLong(value.trim()); if(id<=0) throw new NumberFormatException(); result.add(id); }
            catch(NumberFormatException invalid) { throw new BusinessRuleException("Synthetic reservation configuration is invalid","REPORT_CONFIGURATION_INVALID"); }
        }
        return result;
    }
}
