package com.sportify.payment.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Prepares transaction-bound input for the database's atomic source/event triggers. */
@Service
public class CashCaptureService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enableCutover;

    public CashCaptureService(JdbcTemplate jdbc, Clock clock,
            @Value("${app.finance.cash-capture-enabled:false}") boolean enableCutover) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enableCutover = enableCutover;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Instant prepareCollection(long paymentId) {
        return prepare("COLLECTION", paymentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Instant preparePayout(long refundId) {
        return prepare("REFUND_PAYOUT", refundId);
    }

    private Instant prepare(String kind, long sourceId) {
        LocalDateTime cutover = cutover(false);
        if (cutover == null && enableCutover) {
            jdbc.update("UPDATE dbo.cash_coverage SET cutover_utc = ? WHERE id = 1 AND cutover_utc IS NULL",
                    utc(clock.instant().truncatedTo(ChronoUnit.MICROS)));
            cutover = cutover(false);
        }
        // Hold the inactive read until commit so activation cannot overtake an uncovered transition.
        if (cutover == null) cutover = cutover(true);
        if (cutover == null) return null;
        Instant eventTime = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (eventTime.isBefore(cutover.toInstant(ZoneOffset.UTC))) {
            throw new IllegalStateException("Application Clock precedes the immutable cash cutover");
        }
        jdbc.update("""
                INSERT INTO dbo.cash_capture_ticket(transaction_id, event_type, source_id, event_time_utc)
                VALUES (CURRENT_TRANSACTION_ID(), ?, ?, ?)
                """, kind, sourceId, utc(eventTime));
        return eventTime;
    }

    private LocalDateTime cutover(boolean hold) {
        return jdbc.queryForObject("SELECT cutover_utc FROM dbo.cash_coverage "
                + (hold ? "WITH (HOLDLOCK) " : "") + "WHERE id = 1",
                (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toLocalDateTime());
    }

    static Timestamp utc(Instant value) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(value, ZoneOffset.UTC));
    }
}
