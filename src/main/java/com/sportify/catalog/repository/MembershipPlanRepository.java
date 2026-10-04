package com.sportify.catalog.repository;

import com.sportify.catalog.entity.MembershipPlan;
import com.sportify.catalog.entity.PlanStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Queries that return plans for the API fetch {@code features} and {@code eligibleSports} in the same query
 * (open-in-view is disabled, so LAZY collections must be loaded inside the transaction; this also avoids N+1).
 */
@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = {"features", "eligibleSports"})
    List<MembershipPlan> findByStatusOrderByDisplayOrderAscIdAsc(PlanStatus status);

    @EntityGraph(attributePaths = {"features", "eligibleSports"})
    Optional<MembershipPlan> findByIdAndStatus(Long id, PlanStatus status);

    @EntityGraph(attributePaths = {"features", "eligibleSports"})
    Optional<MembershipPlan> findWithDetailsById(Long id);

    /**
     * Second step of paginated listing: the page itself is loaded without collection fetches
     * (to keep SQL-level pagination), then details are fetched for the ids of that page.
     */
    @EntityGraph(attributePaths = {"features", "eligibleSports"})
    List<MembershipPlan> findWithDetailsByIdIn(Collection<Long> ids);
}
