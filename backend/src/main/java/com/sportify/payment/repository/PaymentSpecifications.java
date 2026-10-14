package com.sportify.payment.repository;

import com.sportify.core.common.SearchUtils;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic filters for the front desk payment request list (F3-04). Every filter is optional.
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    /**
     * @param status        payment status to match, or null
     * @param method        payment method to match, or null
     * @param search        free text matched (contains, case-insensitive) against the member full name,
     *                      member code, payment code and reference code, or null/blank
     * @param createdFrom   inclusive lower bound on created_at, or null
     * @param createdBefore exclusive upper bound on created_at, or null
     * The data query orders PENDING rows first (oldest first, the order they are verified in),
     * then every other row newest first. The count query is left unordered.
     */
    @SuppressWarnings("unchecked")
    public static Specification<Payment> withFilters(PaymentStatus status,
                                                     PaymentMethod method,
                                                     String search,
                                                     LocalDateTime createdFrom,
                                                     LocalDateTime createdBefore) {
        return (root, query, cb) -> {
            boolean countQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<Payment, MemberProfile> member;
            Join<MemberProfile, UserAccount> user;
            if (!countQuery) {
                // Data query: load the member and the account in the same SQL statement.
                member = (Join<Payment, MemberProfile>) root.<Payment, MemberProfile>fetch("member", JoinType.INNER);
                user = (Join<MemberProfile, UserAccount>) member.<MemberProfile, UserAccount>fetch("userAccount", JoinType.INNER);
            } else {
                member = root.join("member", JoinType.INNER);
                user = member.join("userAccount", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), status));
            }
            if (method != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), method));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdBefore != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdBefore));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + SearchUtils.escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("fullName")), pattern, SearchUtils.LIKE_ESCAPE),
                        cb.like(cb.lower(member.get("memberCode")), pattern, SearchUtils.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("paymentCode")), pattern, SearchUtils.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("referenceCode")), pattern, SearchUtils.LIKE_ESCAPE)
                ));
            }

            if (!countQuery) {
                Expression<Integer> pendingRank = cb.<Integer>selectCase()
                        .when(cb.equal(root.get("paymentStatus"), PaymentStatus.PENDING), 0)
                        .otherwise(1);
                Expression<LocalDateTime> pendingCreatedAt = cb.<LocalDateTime>selectCase()
                        .when(cb.equal(root.get("paymentStatus"), PaymentStatus.PENDING), root.<LocalDateTime>get("createdAt"))
                        .otherwise(cb.nullLiteral(LocalDateTime.class));
                query.orderBy(
                        cb.asc(pendingRank),
                        cb.asc(pendingCreatedAt),
                        cb.desc(root.get("createdAt")),
                        cb.desc(root.get("id"))
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
