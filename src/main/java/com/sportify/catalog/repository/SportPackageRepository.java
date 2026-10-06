package com.sportify.catalog.repository;

import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.TrainingFormat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SportPackageRepository extends JpaRepository<SportPackage, Long> {
    Optional<SportPackage> findByCode(String code);
    List<SportPackage> findByIsActiveTrue();
    List<SportPackage> findBySportIdAndIsActiveTrue(Long sportId);
    List<SportPackage> findBySportIdAndTrainingFormatAndIsActiveTrue(Long sportId, TrainingFormat format);
}
