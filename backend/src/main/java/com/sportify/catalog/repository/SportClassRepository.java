package com.sportify.catalog.repository;

import com.sportify.catalog.entity.SportClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SportClassRepository extends JpaRepository<SportClass, Long>, JpaSpecificationExecutor<SportClass> {
    Optional<SportClass> findByCode(String code);
    List<SportClass> findBySportIdAndIsActiveTrue(Long sportId);

    @Query(value = "SELECT NEXT VALUE FOR seq_class_code", nativeQuery = true)
    Long getNextClassCodeSequence();

    /**
     * Loads a class together with every association shown on the manager screens, in one statement.
     */
    @EntityGraph(attributePaths = {"sport", "ageGroup", "coach", "coach.userAccount"})
    Optional<SportClass> findWithDetailsById(Long id);

    /**
     * Overridden to fetch related entities in a single SQL query during pagination, avoiding N+1 queries.
     */
    @Override
    @EntityGraph(attributePaths = {"sport", "ageGroup", "coach", "coach.userAccount"})
    Page<SportClass> findAll(Specification<SportClass> spec, Pageable pageable);
}
