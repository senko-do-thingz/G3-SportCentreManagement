package com.sportify.catalog.repository;

import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportClass;
import com.sportify.core.common.SearchUtils;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic filters for the manager class list (F2-01). Every filter is optional.
 */
public final class SportClassSpecifications {

    private SportClassSpecifications() {
    }

    /**
     * @param sportId sport to match, or null
     * @param level   level to match, or null
     * @param coachId coach (user id) to match, or null
     * @param active  active flag to match, or null for both
     * @param search  free text matched (contains, case-insensitive) against class name, class code
     *                and coach full name, or null/blank
     */
    public static Specification<SportClass> withFilters(Long sportId, ClassLevel level, Long coachId,
                                                        Boolean active, String search) {
        return (root, query, cb) -> {
            Join<SportClass, CoachProfile> coach = root.join("coach", JoinType.LEFT);
            Join<CoachProfile, UserAccount> coachUser = coach.join("userAccount", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            if (sportId != null) {
                predicates.add(cb.equal(root.<Sport>get("sport").get("id"), sportId));
            }
            if (level != null) {
                predicates.add(cb.equal(root.get("level"), level.name()));
            }
            if (coachId != null) {
                predicates.add(cb.equal(coach.get("id"), coachId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + SearchUtils.escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, SearchUtils.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("code")), pattern, SearchUtils.LIKE_ESCAPE),
                        cb.like(cb.lower(coachUser.get("fullName")), pattern, SearchUtils.LIKE_ESCAPE)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
