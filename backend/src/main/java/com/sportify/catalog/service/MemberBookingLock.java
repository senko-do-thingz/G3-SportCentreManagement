package com.sportify.catalog.service;

import com.sportify.core.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * E2E-BUG-01 (PROVISIONAL, E2E-D1 and E2E-D2 pending): serializes booking creation per member across different
 * sessions, so an overlap check cannot pass in two concurrent transactions for the same member.
 *
 * <p>The lock is a transaction-owned SQL Server application lock on the caller's JPA connection, released at commit
 * or rollback. Booking creation takes it BEFORE the session row lock; no other path takes it, and no path that holds a
 * session, booking or registration lock asks for it afterwards, so it cannot close a lock-order cycle. Different
 * members use different resources and do not wait for each other.
 */
@Component
@RequiredArgsConstructor
public class MemberBookingLock {

    static final int TIMEOUT_MILLIS = 10000;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(long memberId) {
        Integer acquired = jdbcTemplate.queryForObject("""
                DECLARE @result INT;
                -- A table read starts a lazy JDBC implicit transaction; EXEC alone does not.
                IF @@TRANCOUNT=0 SELECT TOP(1) @result=user_id FROM member_profile;
                EXEC @result=sys.sp_getapplock @Resource=?, @LockMode='Exclusive',
                    @LockOwner='Transaction', @LockTimeout=?;
                SELECT @result;
                """, Integer.class, resource(memberId), TIMEOUT_MILLIS);
        if (acquired == null || acquired < 0) {
            throw new ConflictException("Another booking for this member is being processed. Reload and retry.", "BOOKING_BUSY");
        }
    }

    static String resource(long memberId) {
        return "sportify.booking-member." + memberId;
    }
}
