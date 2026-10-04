package com.sportify.identity.controller;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MemberSearchIntegrationTest extends AbstractIntegrationTest {

    private static final String ALICE_EMAIL = "alice@test.com";
    private static final String BOB_EMAIL = "bob@test.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByCode("MEMBER").orElseGet(() -> {
            Role r = new Role();
            r.setCode("MEMBER");
            r.setName("Member");
            return roleRepository.save(r);
        });

        transactionTemplate.execute(status -> {
            UserAccount user1 = UserAccount.builder()
                    .email(ALICE_EMAIL)
                    .fullName("Alice Wonderland")
                    .passwordHash("hash")
                    .phone("1111")
                    .role(role)
                    .status("ACTIVE")
                    .build();
            user1 = userRepository.save(user1);

            MemberProfile p1 = MemberProfile.builder()
                    .userAccount(user1)
                    .memberCode("MEM-0001")
                    .currentLevel("BEGINNER")
                    .joinedOn(java.time.LocalDate.now())
                    .build();
            memberProfileRepository.save(p1);

            UserAccount user2 = UserAccount.builder()
                    .email(BOB_EMAIL)
                    .fullName("Bob Builder")
                    .passwordHash("hash")
                    .phone("2222")
                    .role(role)
                    .status("ACTIVE")
                    .build();
            user2 = userRepository.save(user2);

            MemberProfile p2 = MemberProfile.builder()
                    .userAccount(user2)
                    .memberCode("MEM-0002")
                    .currentLevel("INTERMEDIATE")
                    .joinedOn(java.time.LocalDate.now())
                    .build();
            memberProfileRepository.save(p2);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // Remove only the two users created in setUp. Never call deleteAll(): the container is shared and the seeded manager account must survive.
        jdbcTemplate.update("DELETE FROM member_profile WHERE user_id IN (SELECT id FROM user_account WHERE email IN (?, ?))", ALICE_EMAIL, BOB_EMAIL);
        jdbcTemplate.update("DELETE FROM user_account WHERE email IN (?, ?)", ALICE_EMAIL, BOB_EMAIL);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void searchMembers_NoQuery_ReturnsAll() throws Exception {
        mockMvc.perform(get("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void searchMembers_WithQuery_ReturnsFiltered() throws Exception {
        mockMvc.perform(get("/api/v1/members")
                .param("query", "Alice")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Alice Wonderland"));
    }
}
