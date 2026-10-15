package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Facility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Facility f WHERE f.id = :id")
    Optional<Facility> findForScheduleUpdate(@Param("id") Long id);

    Optional<Facility> findByCode(String code);
}
