package com.sportify.identity.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class PermissionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void shouldFindSeededPermissions() {
        Optional<Permission> viewProfile = permissionRepository.findByCode("VIEW_OWN_PROFILE");
        assertThat(viewProfile).isPresent();
        assertThat(viewProfile.get().getModule()).isEqualTo("PROFILE");

        assertThat(permissionRepository.findByCode("REGISTER_RENEW_MEMBERSHIP")).isPresent();
    }
}
