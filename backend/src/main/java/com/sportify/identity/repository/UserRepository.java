package com.sportify.identity.repository;

import com.sportify.identity.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "role")
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> lockByEmail(@org.springframework.data.repository.query.Param("email") String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
}
