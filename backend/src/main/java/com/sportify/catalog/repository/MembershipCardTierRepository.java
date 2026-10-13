package com.sportify.catalog.repository;

import com.sportify.catalog.entity.MembershipCardTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipCardTierRepository extends JpaRepository<MembershipCardTier, Long> {
    Optional<MembershipCardTier> findByCode(String code);
    List<MembershipCardTier> findByIsActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM MembershipCardTier t ORDER BY t.id")
    List<MembershipCardTier> findAllForUpdate();
}
