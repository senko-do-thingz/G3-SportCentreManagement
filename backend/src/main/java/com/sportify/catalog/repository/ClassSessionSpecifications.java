package com.sportify.catalog.repository;

import com.sportify.catalog.entity.ClassSession;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ClassSessionSpecifications {
    private ClassSessionSpecifications() { }

    public static Specification<ClassSession> calendar(LocalDate from, LocalDate to,
            Long classId, Long coachId, Long facilityId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("sessionDate"), from, to));
            if (classId != null) {
                predicates.add(cb.equal(root.get("sportClass").get("id"), classId));
            }
            if (coachId != null) {
                predicates.add(cb.equal(root.get("coach").get("id"), coachId));
            }
            if (facilityId != null) {
                predicates.add(cb.equal(root.get("facility").get("id"), facilityId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
