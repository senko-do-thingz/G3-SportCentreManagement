package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.TrainingFormat;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic filters for manager sport package catalog (F1-02). All filters are optional.
 */
public final class SportPackageSpecifications {

    private SportPackageSpecifications() {
    }

    public static Specification<SportPackage> withFilters(Long sportId, TrainingFormat format, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (sportId != null) {
                predicates.add(cb.equal(root.<Sport>get("sport").get("id"), sportId));
            }
            if (format != null) {
                predicates.add(cb.equal(root.get("trainingFormat"), format));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
