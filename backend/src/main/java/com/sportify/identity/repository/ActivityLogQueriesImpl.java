package com.sportify.identity.repository;

import com.sportify.core.common.SearchUtils;
import com.sportify.identity.dto.ActivityLogQuery;
import com.sportify.identity.dto.ActivityLogResponse;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityLogQueriesImpl implements ActivityLogQueries {
    private final EntityManager entityManager;

    @Override
    public Page<ActivityLogResponse> search(ActivityLogQuery filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var data = cb.createQuery(ActivityLogResponse.class);
        Root<ActivityLog> log = data.from(ActivityLog.class);
        Join<ActivityLog, UserAccount> actor = log.join("actor", JoinType.LEFT);
        Join<UserAccount, Role> role = actor.join("role", JoinType.LEFT);
        data.select(cb.construct(ActivityLogResponse.class, log.get("id"), log.get("createdAt"),
                log.get("actor").get("id"), actor.get("fullName"), role.get("code"),
                log.get("action"), log.get("entityType"), log.get("entityId")))
                .where(predicates(filter, cb, log, actor))
                .orderBy(cb.desc(log.get("createdAt")), cb.desc(log.get("id")));
        var page = PageRequest.of(filter.page(), filter.size());
        var content = entityManager.createQuery(data).setFirstResult((int) page.getOffset())
                .setMaxResults(page.getPageSize()).getResultList();

        var count = cb.createQuery(Long.class);
        Root<ActivityLog> counted = count.from(ActivityLog.class);
        // Only the name-search predicate needs an actor join in the count query.
        Join<ActivityLog, UserAccount> countedActor = filter.search() == null ? null : counted.join("actor", JoinType.LEFT);
        count.select(cb.count(counted)).where(predicates(filter, cb, counted, countedActor));
        long total = entityManager.createQuery(count).getSingleResult();
        return new PageImpl<>(content, page, total);
    }

    static Predicate[] predicates(ActivityLogQuery filter, CriteriaBuilder cb, Root<ActivityLog> log,
            Join<ActivityLog, UserAccount> actor) {
        List<Predicate> result = new ArrayList<>();
        if (filter.actorId() != null) result.add(cb.equal(log.get("actor").get("id"), filter.actorId()));
        if (filter.action() != null) result.add(cb.equal(log.get("action"), filter.action()));
        if (filter.from() != null) result.add(cb.greaterThanOrEqualTo(log.get("createdAt"), filter.from()));
        if (filter.to() != null) result.add(filter.toExclusive()
                ? cb.lessThan(log.get("createdAt"), filter.to())
                : cb.lessThanOrEqualTo(log.get("createdAt"), filter.to()));
        if (filter.search() != null) {
            String pattern = "%" + SearchUtils.escapeLike(filter.search()) + "%";
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(cb.lower(actor.get("fullName")), pattern, SearchUtils.LIKE_ESCAPE));
            matches.add(cb.like(cb.lower(log.get("action")), pattern, SearchUtils.LIKE_ESCAPE));
            matches.add(cb.like(cb.lower(log.get("entityType")), pattern, SearchUtils.LIKE_ESCAPE));
            try {
                matches.add(cb.equal(log.get("entityId"), Long.valueOf(filter.search())));
            } catch (NumberFormatException ignored) {
                // Text and oversized integers simply do not match the numeric target ID.
            }
            result.add(cb.or(matches.toArray(Predicate[]::new)));
        }
        return result.toArray(Predicate[]::new);
    }
}
