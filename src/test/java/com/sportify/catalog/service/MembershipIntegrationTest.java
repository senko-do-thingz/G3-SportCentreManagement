package com.sportify.catalog.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.MembershipRegistrationRequest;
import com.sportify.catalog.dto.MembershipResponse;
import com.sportify.catalog.entity.MembershipPlan;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.repository.MembershipPlanRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
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
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MembershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private java.time.Clock clock;

    private UserAccount testMember;
    private MemberProfile testProfile;
    private MembershipPlan swimStarter;
    private Sport swimming;

    @BeforeEach
    void setUp() {
        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseGet(() -> roleRepository.save(Role.builder().code("MEMBER").name("Member").build()));

        testMember = userRepository.saveAndFlush(UserAccount.builder()
                .email("test_" + UUID.randomUUID() + "@sportify.com")
                .fullName("Integration Test Member")
                .passwordHash("hash")
                .status("ACTIVE")
                .role(memberRole)
                .build());

        testProfile = memberProfileRepository.saveAndFlush(MemberProfile.builder()
                .userAccount(testMember)
                .memberCode("MEM-T" + UUID.randomUUID().toString().substring(0, 4))
                .build());

        swimStarter = planRepository.findAll().stream().filter(p -> "SWIM_STARTER".equals(p.getCode())).findFirst().orElseThrow();
        swimming = sportRepository.findAll().stream().filter(s -> "SWIMMING".equals(s.getCode())).findFirst().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        membershipRepository.deleteAll();
        memberProfileRepository.deleteAll();
        userRepository.delete(testMember);
    }

    @Test
    void registerAndActivateFlow() {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(swimStarter.getId());
        req.setSportIds(List.of(swimming.getId()));

        MembershipResponse res = membershipService.registerMembership(testMember, req);
        
        assertNotNull(res.getId());
        assertEquals("PENDING_PAYMENT", res.getStatus());

        // Try double registration -> should throw ConflictException
        assertThrows(ConflictException.class, () -> membershipService.registerMembership(testMember, req));

        // Activate
        membershipService.activateMembership(res.getId(), testMember);

        MembershipResponse activated = membershipService.getMyMemberships(testMember).stream()
                .filter(m -> m.getId().equals(res.getId()))
                .findFirst()
                .orElseThrow();
        
        assertEquals("ACTIVE", activated.getStatus());
    }

    @Test
    void register_LazyProfileCreation() {
        // Create an orphan member account without member_profile
        Role memberRole = roleRepository.findByCode("MEMBER").orElseThrow();
        UserAccount orphanMember = userRepository.saveAndFlush(UserAccount.builder()
                .email("orphan_" + UUID.randomUUID() + "@sportify.com")
                .fullName("Orphan Member")
                .passwordHash("hash")
                .status("ACTIVE")
                .role(memberRole)
                .build());

        try {
            MembershipRegistrationRequest req = new MembershipRegistrationRequest();
            req.setPlanId(swimStarter.getId());
            req.setSportIds(List.of(swimming.getId()));

            MembershipResponse res = membershipService.registerMembership(orphanMember, req);
            
            assertNotNull(res.getId());

            // Check lazy profile creation
            MemberProfile profile = memberProfileRepository.findById(orphanMember.getId())
                    .orElseThrow(() -> new AssertionError("Profile should have been created lazily"));
            
            assertEquals("BEGINNER", profile.getCurrentLevel());
            assertEquals(LocalDate.now(clock), profile.getJoinedOn());
            assertTrue(profile.getMemberCode().matches("MEM-\\d{4}"), "Code should be MEM-xxxx format");
        } finally {
            membershipRepository.deleteAll();
            memberProfileRepository.deleteById(orphanMember.getId());
            userRepository.delete(orphanMember);
        }
    }

    @Test
    void register_NonMemberRole_ThrowsException() {
        Role managerRole = roleRepository.findByCode("MANAGER")
                .orElseGet(() -> roleRepository.save(Role.builder().code("MANAGER").name("Manager").build()));
        
        UserAccount manager = userRepository.saveAndFlush(UserAccount.builder()
                .email("manager_" + UUID.randomUUID() + "@sportify.com")
                .fullName("Integration Test Manager")
                .passwordHash("hash")
                .status("ACTIVE")
                .role(managerRole)
                .build());

        try {
            MembershipRegistrationRequest req = new MembershipRegistrationRequest();
            req.setPlanId(swimStarter.getId());
            req.setSportIds(List.of(swimming.getId()));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> membershipService.registerMembership(manager, req));
            assertTrue(ex.getMessage().contains("Only users with MEMBER role can have a member profile"));
        } finally {
            userRepository.delete(manager);
        }
    }
}
