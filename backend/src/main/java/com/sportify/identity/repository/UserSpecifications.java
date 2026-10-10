package com.sportify.identity.repository;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic filters for the manager user list. Every filter is optional.
 */
public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    /**
     * @param roleCode role code to match exactly, or null
     * @param status   account status to match, or null
     * @param search   free text matched (contains, case-insensitive) against full name, email, phone
     *                 and the member code of member accounts, or null/blank
     */
    public static Specification<UserAccount> withFilters(String roleCode, UserStatus status, String search) {
        return (root, query, cb) -> {
            Join<UserAccount, Role> role;
            if (!Long.class.equals(query.getResultType())) {
                // Data query: load the role in the same SQL statement. The count query must not fetch.
                role = (Join<UserAccount, Role>) root.<UserAccount, Role>fetch("role", JoinType.INNER);
            } else {
                role = root.join("role", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (roleCode != null && !roleCode.isBlank()) {
                predicates.add(cb.equal(role.get("code"), roleCode));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status.name()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";

                Subquery<Long> memberSubquery = query.subquery(Long.class);
                Root<MemberProfile> member = memberSubquery.from(MemberProfile.class);
                memberSubquery.select(member.<Long>get("id"))
                        .where(
                                cb.equal(member.get("id"), root.get("id")),
                                cb.like(cb.lower(member.get("memberCode")), pattern, LIKE_ESCAPE)
                        );

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("phone")), pattern, LIKE_ESCAPE),
                        cb.exists(memberSubquery)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escapes LIKE wildcards so user input is matched literally. */
    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
