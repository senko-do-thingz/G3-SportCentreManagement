package com.sportify.identity.repository;

import com.sportify.identity.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
    Optional<MemberProfile> findByMemberCode(String memberCode);

    @org.springframework.data.jpa.repository.Query(value = "SELECT NEXT VALUE FOR seq_member_code", nativeQuery = true)
    Long getNextMemberCode();

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT m FROM MemberProfile m JOIN FETCH m.userAccount u",
        countQuery = "SELECT count(m) FROM MemberProfile m"
    )
    org.springframework.data.domain.Page<MemberProfile> findAllWithUser(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT m FROM MemberProfile m JOIN FETCH m.userAccount u " +
                "WHERE LOWER(u.fullName) LIKE :pattern OR " +
                "LOWER(u.email) LIKE :pattern OR " +
                "LOWER(m.memberCode) LIKE :pattern OR " +
                "LOWER(u.phone) LIKE :pattern",
        countQuery = "SELECT count(m) FROM MemberProfile m JOIN m.userAccount u " +
                     "WHERE LOWER(u.fullName) LIKE :pattern OR " +
                     "LOWER(u.email) LIKE :pattern OR " +
                     "LOWER(m.memberCode) LIKE :pattern OR " +
                     "LOWER(u.phone) LIKE :pattern"
    )
    org.springframework.data.domain.Page<MemberProfile> search(@org.springframework.data.repository.query.Param("pattern") String pattern, org.springframework.data.domain.Pageable pageable);
}
