package com.sportify.payment.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@Import(PaymentAuditingFixedClockIntegrationTest.FixedClockConfig.class)
class PaymentAuditingFixedClockIntegrationTest extends AbstractIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-15T12:30:45Z");
    private static final ZoneId FIXED_ZONE = ZoneId.of("America/New_York");
    private static final LocalDateTime EXPECTED_LOCAL_TIME = LocalDateTime.of(2026, 10, 15, 8, 30, 45);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, FIXED_ZONE);
        }
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CodeFormatter codeFormatter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("markPaymentSuccess sets created_at, updated_at, and payment_time exactly equal to fixed clock date-time")
    void markPaymentSuccess_WithFixedClock_MatchesTimestampsExactly() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Long memberId = null;
        Long userId = null;
        Long paymentId = null;

        try {
            Long[] ids = txTemplate.execute(status -> {
                Role memberRole = roleRepository.findByCode("MEMBER")
                        .orElseThrow(() -> new IllegalStateException("Required seeded role MEMBER not found"));

                UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                        .email("it-fixed-clock-" + System.nanoTime() + "@sportify.com")
                        .passwordHash("DummyHashForTestPurposesOnly")
                        .fullName("Fixed Clock Member")
                        .role(memberRole)
                        .status("ACTIVE")
                        .build());

                Long memberSeq = memberProfileRepository.getNextMemberCode();
                MemberProfile profile = memberProfileRepository.saveAndFlush(MemberProfile.builder()
                        .userAccount(user)
                        .memberCode(codeFormatter.formatMemberCode(memberSeq))
                        .currentLevel("BEGINNER")
                        .joinedOn(LocalDate.now())
                        .build());
                return new Long[] { profile.getId(), profile.getUserAccount().getId() };
            });
            if (ids != null) {
                memberId = ids[0];
                userId = ids[1];
            }

            final Long mId = memberId;
            paymentId = txTemplate.execute(status -> {
                MemberProfile member = memberProfileRepository.findById(mId).orElseThrow();
                Payment payment = paymentService.createPendingPayment(member, BigDecimal.valueOf(150000.00), PaymentMethod.BANK_TRANSFER);
                return payment.getId();
            });

            final Long pId = paymentId;
            txTemplate.execute(status -> {
                paymentService.markPaymentSuccess(pId, null);
                return null;
            });

            var row = jdbcTemplate.queryForMap(
                    "SELECT created_at, updated_at, payment_time FROM payment WHERE id = ?", paymentId
            );

            Timestamp createdAtTs = (Timestamp) row.get("created_at");
            Timestamp updatedAtTs = (Timestamp) row.get("updated_at");
            Timestamp paymentTimeTs = (Timestamp) row.get("payment_time");

            assertThat(createdAtTs).isNotNull();
            assertThat(updatedAtTs).isNotNull();
            assertThat(paymentTimeTs).isNotNull();

            LocalDateTime createdAt = createdAtTs.toLocalDateTime();
            LocalDateTime updatedAt = updatedAtTs.toLocalDateTime();
            LocalDateTime paymentTime = paymentTimeTs.toLocalDateTime();

            // Exact equality assertions with zero second tolerance
            assertThat(createdAt).isEqualTo(EXPECTED_LOCAL_TIME);
            assertThat(updatedAt).isEqualTo(EXPECTED_LOCAL_TIME);
            assertThat(paymentTime).isEqualTo(EXPECTED_LOCAL_TIME);
        } finally {
            cleanupPaymentAndUser(txTemplate, paymentId, memberId, userId);
        }
    }

    private void cleanupPaymentAndUser(TransactionTemplate txTemplate, Long paymentId, Long memberId, Long userId) {
        if (paymentId != null) {
            try {
                txTemplate.execute(status -> {
                    paymentRepository.deleteById(paymentId);
                    return null;
                });
            } catch (Exception ignored) {
            }
        }
        if (memberId != null) {
            try {
                txTemplate.execute(status -> {
                    memberProfileRepository.deleteById(memberId);
                    return null;
                });
            } catch (Exception ignored) {
            }
        }
        if (userId != null) {
            try {
                txTemplate.execute(status -> {
                    userRepository.deleteById(userId);
                    return null;
                });
            } catch (Exception ignored) {
            }
        }
    }
}
