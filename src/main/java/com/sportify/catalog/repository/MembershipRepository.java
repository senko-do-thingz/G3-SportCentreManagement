package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Step 2A: schema only. Registration / renewal queries are added in Step 2B.
 * {@code membership_sport} is mapped as {@code Membership.sports} (ManyToMany), so no separate repository is needed.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
}
