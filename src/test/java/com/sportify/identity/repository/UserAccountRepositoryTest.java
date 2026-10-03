package com.sportify.identity.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAccountRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldSaveAndFindUserAccount() {
        Role memberRole = roleRepository.findByCode("MEMBER").orElseThrow();

        UserAccount user = UserAccount.builder()
                .email("test@sportify.com")
                .passwordHash("hash")
                .fullName("Test User")
                .role(memberRole)
                .build();

        UserAccount saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<UserAccount> found = userRepository.findByEmailIgnoreCase("TEST@sportify.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Test User");

        boolean exists = userRepository.existsByEmailIgnoreCase("test@sportify.com");
        assertThat(exists).isTrue();
    }
}
