package com.sportify.payment.service;

import com.sportify.payment.dto.finance.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OperationsReportService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FinanceReportService finance;
    private final Clock clock;
    public record BookingCounts(long total,long confirmed,long cancelled,long upcoming,long completed) {}
    public record Report(LocalDate from,LocalDate to,String timeZone,long registeredMembers,
            long currentlyEnabledMembersRegistered,BookingCounts classBookings,RevenueBreakdownResponse revenue) {}
    @Transactional(readOnly=true)
    public Report report(LocalDate from,LocalDate to) {
        // Reuse the authoritative finance range validation, defaults and actual-vs-simulated revenue.
        var revenue=finance.revenue(from,to,RevenueGroupBy.ITEM_TYPE);
        var today=LocalDateTime.now(clock);
        var params=new MapSqlParameterSource().addValue("start",Timestamp.valueOf(revenue.from().atStartOfDay()))
                .addValue("end",Timestamp.valueOf(revenue.to().plusDays(1).atStartOfDay()))
                .addValue("today",java.sql.Date.valueOf(today.toLocalDate()))
                .addValue("time",java.sql.Time.valueOf(today.toLocalTime()));
        String members=" FROM user_account u JOIN role r ON r.id=u.role_id WHERE r.code='MEMBER'"
                +" AND u.created_at>=:start AND u.created_at<:end";
        long registered=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*)"+members,params,Long.class));
        long enabled=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*)"+members+" AND u.status='ACTIVE'",params,Long.class));
        BookingCounts counts=jdbc.queryForObject("""
            SELECT COUNT_BIG(*) total,
                COALESCE(SUM(CASE WHEN b.status='CONFIRMED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) confirmed,
                COALESCE(SUM(CASE WHEN b.status='CANCELLED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) cancelled,
                COALESCE(SUM(CASE WHEN b.status='CONFIRMED' AND s.status='PUBLISHED'
                    AND (s.session_date>:today OR (s.session_date=:today AND s.start_time>=CAST(:time AS TIME))) THEN CAST(1 AS BIGINT) ELSE 0 END),0) upcoming,
                COALESCE(SUM(CASE WHEN b.status='CONFIRMED' AND s.status='COMPLETED' THEN CAST(1 AS BIGINT) ELSE 0 END),0) completed
            FROM booking b JOIN class_session s ON s.id=b.session_id
            WHERE s.class_id IS NOT NULL AND b.booked_at>=:start AND b.booked_at<:end
            """,params,(rs,row)->new BookingCounts(rs.getLong("total"),rs.getLong("confirmed"),rs.getLong("cancelled"),rs.getLong("upcoming"),rs.getLong("completed")));
        return new Report(revenue.from(),revenue.to(),clock.getZone().getId(),registered,enabled,counts,revenue);
    }
}
