package com.sportify.identity.repository;

import com.sportify.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Deprecated
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}
