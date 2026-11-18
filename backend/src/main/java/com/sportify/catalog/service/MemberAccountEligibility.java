package com.sportify.catalog.service;

import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * E2E-BUG-04 (E2E-D11 APPROVED 2026-11-06): the TARGET member account, not the staff actor, must be
 * ACTIVE when a sale, a pending payment confirmation or a direct staff activation creates benefits, a successful payment or an invoice.
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
    public static final String DATE_OF_BIRTH_REQUIRED = "DATE_OF_BIRTH_REQUIRED";
    public static final String DATE_OF_BIRTH_REQUIRED_MESSAGE =
            "Please update your date of birth in your profile before purchasing a subscription plan";

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

    /**
     * A member buying a card or a sport package for themselves must have a known date of birth, because class age
     * groups are checked against it at booking time. Staff desk sales are not blocked, so reception can still sell
     * to a walk-in member whose profile is incomplete.
     */
    public static void requireDateOfBirthForSelfServicePurchase(UserAccount actor, MemberProfile member) {
        if (!SecurityUtils.isMember(actor)) {
            return;
        }
        UserAccount account = member == null ? null : member.getUserAccount();
        if (account == null || account.getDateOfBirth() == null) {
            throw new BusinessRuleException(DATE_OF_BIRTH_REQUIRED_MESSAGE, DATE_OF_BIRTH_REQUIRED);
        }
    }
}
