package com.sportify.catalog.repository;

import com.sportify.catalog.entity.SportClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SportClassRepository extends JpaRepository<SportClass, Long> {
    Optional<SportClass> findByCode(String code);
    List<SportClass> findBySportIdAndIsActiveTrue(Long sportId);

    @Query(value = "SELECT NEXT VALUE FOR seq_class_code", nativeQuery = true)
    Long getNextClassCodeSequence();
}
