package com.sportify.identity.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class RoleRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldFindAllSeededRoles() {
        assertThat(roleRepository.findAll()).hasSizeGreaterThanOrEqualTo(4);

        Optional<Role> memberRole = roleRepository.findByCode("MEMBER");
        assertThat(memberRole).isPresent();
        assertThat(memberRole.get().getName()).isEqualTo("Member");

        assertThat(roleRepository.findByCode("COACH")).isPresent();
        assertThat(roleRepository.findByCode("RECEPTIONIST")).isPresent();
        assertThat(roleRepository.findByCode("MANAGER")).isPresent();
    }
}
