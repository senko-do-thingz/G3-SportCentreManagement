package com.sportify.catalog.repository;

import com.sportify.catalog.entity.AgeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgeGroupRepository extends JpaRepository<AgeGroup, Long> {

    Optional<AgeGroup> findByCode(String code);
}
