package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * E2E-BUG-04 (PROVISIONAL, E2E-D11 pending confirmation): the TARGET member account, not the staff actor, must be
 * ACTIVE when a sale or a pending payment confirmation creates benefits, a successful payment or an invoice.
 *
 * <p>The check reads the target user_account row WITH (UPDLOCK) inside the caller's transaction. Deactivation updates
 * that same row, so the two serialize: a deactivation that commits first is seen here and the purchase is rejected; a
 * deactivation that starts later waits until the purchase commits. Deactivation locks nothing else, and every caller
 * takes this lock after its own first lock (payment row) or as its first lock, so no lock-order cycle is added.
 */
@Component
@RequiredArgsConstructor
public class MemberAccountEligibility {

    public static final String MEMBER_INACTIVE = "MEMBER_INACTIVE";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveForPurchase(Long memberUserId) {
        if (memberUserId == null) {
            throw new BusinessRuleException("A target member is required");
        }
        var status = jdbcTemplate.queryForList(
                "SELECT status FROM user_account WITH (UPDLOCK, ROWLOCK) WHERE id = ?", String.class, memberUserId);
        if (status.isEmpty()) {
            throw new ResourceNotFoundException("User not found: " + memberUserId);
        }
        if (!"ACTIVE".equals(status.getFirst())) {
            throw new BusinessRuleException("The member account is not active, so new purchases and payment"
                    + " confirmations are not allowed for it", MEMBER_INACTIVE);
        }
    }
}
