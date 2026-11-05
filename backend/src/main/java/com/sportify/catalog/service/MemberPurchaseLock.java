package com.sportify.catalog.service;

import com.sportify.core.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * E2E-BUG-06 (PROVISIONAL, E2E-D8 pending): serializes online pending package orders per member, so two identical
 * concurrent submissions cannot both pass the duplicate check and create two pending orders and payments.
 *
 * <p>A transaction-owned SQL Server application lock on the caller's JPA connection, released at commit or rollback.
 * It is taken before any registration or payment row is written and is not requested by any path that already holds
 * such a row lock, so it does not add a lock-order cycle. No unique index is added: the duplicate key is not approved
 * yet, and an index violation would also mark the transaction rollback-only.
 */
@Component
@RequiredArgsConstructor
public class MemberPurchaseLock {

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
                """, Integer.class, "sportify.package-order-member." + memberId, TIMEOUT_MILLIS);
        if (acquired == null || acquired < 0) {
            throw new ConflictException("Another order for this member is being processed. Reload and retry.", "ORDER_BUSY");
        }
    }
}
