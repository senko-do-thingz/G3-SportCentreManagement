package com.sportify.identity.repository;

import com.sportify.identity.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
    Optional<MemberProfile> findByMemberCode(String memberCode);
}
