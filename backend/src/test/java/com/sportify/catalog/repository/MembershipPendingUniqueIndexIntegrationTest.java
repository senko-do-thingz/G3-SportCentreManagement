package com.sportify.catalog.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.Membership;
import com.sportify.catalog.entity.MembershipPlan;
import com.sportify.catalog.entity.MembershipStatus;
import com.sportify.catalog.entity.PlanStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.RegistrationType;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This test checks the `ux_membership_one_pending` index created in migration V5.
 */
public class MembershipPendingUniqueIndexIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private MembershipPlanRepository membershipPlanRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserAccount testUser;
    private MemberProfile testProfile;
    private MembershipPlan testPlan;
    private UserAccount testUser2;
    private MemberProfile testProfile2;

    @BeforeEach
    void setUp() {
        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("MEMBER")
                        .name("Member")
                        .build()));

        transactionTemplate.execute(status -> {
            testUser = userRepository.save(UserAccount.builder()
                    .email("racer_" + UUID.randomUUID() + "@test.com")
                    .fullName("Speed Racer")
                    .passwordHash("hash")
                    .phone("999999" + UUID.randomUUID().toString().substring(0, 4))
                    .status("ACTIVE")
                    .role(memberRole)
                    .build());

            testProfile = memberProfileRepository.save(MemberProfile.builder()
                    .userAccount(testUser)
                    .memberCode("MEM-T" + UUID.randomUUID().toString().substring(0, 4))
                    .build());
            return null;
        });

        testPlan = membershipPlanRepository.saveAndFlush(MembershipPlan.builder()
                .name("Race Plan")
                .code("RACE_" + UUID.randomUUID().toString().substring(0, 4))
                .maxSports(5)
                .durationDays(30)
                .price(new BigDecimal("100"))
                .status(PlanStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        membershipRepository.deleteAll(membershipRepository.findAllByMemberIdOrderByStartDateDesc(testUser.getId()));
        if (testUser2 != null) {
            membershipRepository.deleteAll(membershipRepository.findAllByMemberIdOrderByStartDateDesc(testUser2.getId()));
        }
        membershipPlanRepository.delete(testPlan);
        memberProfileRepository.delete(testProfile);
        userRepository.delete(testUser);
        if (testUser2 != null) {
            memberProfileRepository.delete(testProfile2);
            userRepository.delete(testUser2);
        }
    }

    @Test
    void register_TwoPendingMemberships_ThrowsDataIntegrityViolationException() {
        Membership m1 = Membership.builder()
                .member(testProfile)
                .plan(testPlan)
                .registrationCode("REG-T1")
                .registrationType(RegistrationType.NEW)
                .channel(RegistrationChannel.ONLINE)
                .priceAmount(testPlan.getPrice())
                .durationDays(testPlan.getDurationDays())
                .status(MembershipStatus.PENDING_PAYMENT)
                .createdByUser(testUser)
                .build();
        membershipRepository.saveAndFlush(m1);

        Membership m2 = Membership.builder()
                .member(testProfile)
                .plan(testPlan)
                .registrationCode("REG-T2")
                .registrationType(RegistrationType.NEW)
                .channel(RegistrationChannel.ONLINE)
                .priceAmount(testPlan.getPrice())
                .durationDays(testPlan.getDurationDays())
                .status(MembershipStatus.PENDING_PAYMENT)
                .createdByUser(testUser)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> membershipRepository.saveAndFlush(m2));
    }

    @Test
    void register_CancelledAndPendingMemberships_DoesNotThrowException() {
        Membership m1 = Membership.builder()
                .member(testProfile)
                .plan(testPlan)
                .registrationCode("REG-T3")
                .registrationType(RegistrationType.NEW)
                .channel(RegistrationChannel.ONLINE)
                .priceAmount(testPlan.getPrice())
                .durationDays(testPlan.getDurationDays())
                .status(MembershipStatus.CANCELLED)
                .createdByUser(testUser)
                .build();
        membershipRepository.saveAndFlush(m1);

        Membership m2 = Membership.builder()
                .member(testProfile)
                .plan(testPlan)
                .registrationCode("REG-T4")
                .registrationType(RegistrationType.NEW)
                .channel(RegistrationChannel.ONLINE)
                .priceAmount(testPlan.getPrice())
                .durationDays(testPlan.getDurationDays())
                .status(MembershipStatus.PENDING_PAYMENT)
                .createdByUser(testUser)
                .build();

        assertDoesNotThrow(() -> membershipRepository.saveAndFlush(m2));
    }

    @Test
    void register_TwoPendingMembershipsForDifferentMembers_DoesNotThrowException() {
        Role memberRole = roleRepository.findByCode("MEMBER").orElseThrow();
        transactionTemplate.execute(status -> {
            testUser2 = userRepository.save(UserAccount.builder()
                    .email("racer2_" + UUID.randomUUID() + "@test.com")
                    .fullName("Speed Racer 2")
                    .passwordHash("hash")
                    .phone("999998" + UUID.randomUUID().toString().substring(0, 4))
                    .status("ACTIVE")
                    .role(memberRole)
                    .build());

            testProfile2 = memberProfileRepository.save(MemberProfile.builder()
                    .userAccount(testUser2)
                    .memberCode("MEM-U" + UUID.randomUUID().toString().substring(0, 4))
                    .build());
            return null;
        });

        Membership m1 = Membership.builder()
                .member(testProfile)
                .plan(testPlan)
                .registrationCode("REG-U1")
                .registrationType(RegistrationType.NEW)
                .channel(RegistrationChannel.ONLINE)
                .priceAmount(testPlan.getPrice())
                .durationDays(testPlan.getDurationDays())
                .status(MembershipStatus.PENDING_PAYMENT)
                .createdByUser(testUser)
                .build();
        membershipRepository.saveAndFlush(m1);

        Membership m2 = Membership.builder()
                .member(testProfile2)
                .plan(testPlan)
                .registrationCode("REG-U2")
                .registrationType(RegistrationType.NEW)
                .channel(RegistrationChannel.ONLINE)
                .priceAmount(testPlan.getPrice())
                .durationDays(testPlan.getDurationDays())
                .status(MembershipStatus.PENDING_PAYMENT)
                .createdByUser(testUser2)
                .build();

        assertDoesNotThrow(() -> membershipRepository.saveAndFlush(m2));
    }
}
