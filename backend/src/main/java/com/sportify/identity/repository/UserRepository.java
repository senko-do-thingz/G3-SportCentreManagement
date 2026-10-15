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
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
}
