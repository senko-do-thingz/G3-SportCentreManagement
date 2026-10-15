package com.sportify.payment;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.MembershipCardTierRepository;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.test.SportTestData;
import com.sportify.core.audit.AuditAction;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.repository.InvoiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-19 acceptance criterion: a desk sale is one transaction. When the receipt step fails after the payment and the
 * card or package registration were written, nothing of the sale stays in SQL Server: no payment, no card or
 * registration, no invoice, no invoice line and no PAYMENT_CONFIRMED activity_log row.
 *
 * The failures are forced with spies on the real repositories, so every other call reaches SQL Server. This class
 * has its own Spring context (the spies), which keeps the other integration test classes unaffected.
 */
@AutoConfigureMockMvc
class DeskSaleRollbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;
    @Autowired private SportRepository sportRepository;
    @Autowired private SportPackageRepository sportPackageRepository;
    @Autowired private MembershipCardTierRepository tierRepository;
    @Autowired private MemberProfileRepository memberProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @SpyBean private InvoiceRepository invoiceRepository;
    @SpyBean private ActivityLogRepository activityLogRepository;

    /** Member codes and tier ranks outside the ranges other test classes use. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final AtomicInteger TIER_RANK = new AtomicInteger(7000);

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> sportIds = new ArrayList<>();
    private final List<Long> packageIds = new ArrayList<>();
    private final List<Long> tierIds = new ArrayList<>();

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        if (!userIds.isEmpty()) {
            // Nothing should be left by a rolled back sale; this only protects other classes if an assertion fails.
            String users = idList(userIds);
            String memberPayments = "SELECT id FROM payment WHERE member_id IN (" + users + ")";
            jdbcTemplate.update("DELETE FROM activity_log WHERE actor_user_id IN (" + users + ")"
                    + " OR (entity_type = 'PAYMENT' AND entity_id IN (" + memberPayments + "))");
            jdbcTemplate.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE member_id IN ("
                    + users + "))");
            jdbcTemplate.update("DELETE FROM invoice WHERE member_id IN (" + users + ")");
            jdbcTemplate.update("DELETE FROM member_card WHERE member_id IN (" + users + ")");
            jdbcTemplate.update("DELETE FROM sport_package_registration WHERE member_id IN (" + users + ")");
            jdbcTemplate.update("DELETE FROM payment WHERE member_id IN (" + users + ")");
            jdbcTemplate.update("DELETE FROM member_profile WHERE user_id IN (" + users + ")");
        }
        deleteByIds("sport_package", packageIds);
        deleteByIds("sport", sportIds);
        deleteByIds("membership_card_tier", tierIds);
        deleteByIds("user_account", userIds);
    }

    private void deleteByIds(String table, List<Long> ids) {
        if (!ids.isEmpty()) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE id IN (" + idList(ids) + ")");
        }
    }

    private static String idList(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private UserAccount newUser(String roleCode) {
        return tx().execute(st -> {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Seeded role " + roleCode + " not found"));
            UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                    .email("be19-rb-" + roleCode.toLowerCase() + "-" + System.nanoTime() + "@sportify.com")
                    .passwordHash("DummyHashForTestPurposesOnly")
                    .fullName("BE19 Rollback " + roleCode)
                    .role(role)
                    .status("ACTIVE")
                    .build());
            userIds.add(user.getId());
            return user;
        });
    }

    private MemberProfile newMember() {
        UserAccount user = newUser("MEMBER");
        return tx().execute(st -> memberProfileRepository.saveAndFlush(MemberProfile.builder()
                .userAccount(user)
                .memberCode("BE19-RB-" + SEQUENCE.incrementAndGet())
                .currentLevel("BEGINNER")
                .joinedOn(LocalDate.now(clock))
                .build()));
    }

    private Long newPackage() {
        return tx().execute(st -> {
            Sport sport = sportRepository.save(SportTestData.aValidSport().build());
            SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                    .code("BE19-RB-" + System.nanoTime())
                    .name("BE19 Rollback 30 Days")
                    .sport(sport)
                    .trainingFormat(TrainingFormat.SELF_TRAINING)
                    .durationDays(30)
                    .sessionCount(10)
                    .sessionMinutes(60)
                    .priceAmount(new BigDecimal("400000.00"))
                    .isActive(true)
                    .build());
            sportIds.add(sport.getId());
            packageIds.add(pkg.getId());
            return pkg.getId();
        });
    }

    private Long newTier() {
        return tx().execute(st -> {
            MembershipCardTier tier = tierRepository.save(MembershipCardTier.builder()
                    .code("BE19-RB-" + System.nanoTime())
                    .name("BE19 Rollback Gold")
                    .price(new BigDecimal("1200000.00"))
                    .durationMonths(12)
                    .discountPercentage(5)
                    .tierRank(TIER_RANK.incrementAndGet())
                    .isActive(true)
                    .build());
            tierIds.add(tier.getId());
            return tier.getId();
        });
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value != null ? value : 0;
    }

    /** Global row counts of the receipt tables, compared before and after the failed sale. */
    private Map<String, Integer> receiptTableCounts() {
        return Map.of(
                "invoice", count("SELECT COUNT(*) FROM invoice"),
                "invoice_line", count("SELECT COUNT(*) FROM invoice_line"),
                "payment_confirmed", count("SELECT COUNT(*) FROM activity_log WHERE action = ?", AuditAction.PAYMENT_CONFIRMED));
    }

    /** Nothing of the sale is left: payment, card, registration, invoice, invoice lines and activity log. */
    private void assertNothingLeft(MemberProfile member, UserAccount receptionist, Map<String, Integer> before) {
        Long memberId = member.getId();
        assertThat(count("SELECT COUNT(*) FROM payment WHERE member_id = ?", memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM member_card WHERE member_id = ?", memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ?", memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE member_id = ?", memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id = ?", receptionist.getId())).isZero();
        assertThat(receiptTableCounts()).isEqualTo(before);
    }

    @Test
    @DisplayName("BE-19: invoice creation fails after the package payment was written: the whole desk sale rolls back")
    void deskPackageSale_InvoiceFails_EverythingRollsBack() throws Exception {
        MemberProfile member = newMember();
        Long packageId = newPackage();
        UserAccount receptionist = newUser("RECEPTIONIST");
        doThrow(new IllegalStateException("Simulated invoice failure"))
                .when(invoiceRepository).save(any(Invoice.class));
        Map<String, Integer> before = receiptTableCounts();

        mockMvc.perform(post("/api/v1/packages/registrations")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"packageId\":" + packageId
                                + ",\"channel\":\"RECEPTION\",\"paymentMethod\":\"BANK_TRANSFER\""
                                + ",\"paymentReference\":\"FT-BE19-RB-1\",\"amountReceived\":400000}"))
                .andExpect(status().isInternalServerError());

        verify(invoiceRepository).save(any(Invoice.class));
        assertNothingLeft(member, receptionist, before);
    }

    @Test
    @DisplayName("BE-19: invoice creation fails after the card payment was written: the whole desk sale rolls back")
    void deskCardSale_InvoiceFails_EverythingRollsBack() throws Exception {
        MemberProfile member = newMember();
        Long tierId = newTier();
        UserAccount receptionist = newUser("RECEPTIONIST");
        doThrow(new IllegalStateException("Simulated invoice failure"))
                .when(invoiceRepository).save(any(Invoice.class));
        Map<String, Integer> before = receiptTableCounts();

        mockMvc.perform(post("/api/v1/membership-cards/purchase")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"tierId\":" + tierId
                                + ",\"paymentMethod\":\"CASH\",\"amountReceived\":1200000}"))
                .andExpect(status().isInternalServerError());

        verify(invoiceRepository).save(any(Invoice.class));
        assertNothingLeft(member, receptionist, before);
    }

    @Test
    @DisplayName("BE-19: the audit write fails after the invoice and its line were written: all of it rolls back")
    void deskPackageSale_AuditFailsAfterInvoice_EverythingRollsBack() throws Exception {
        MemberProfile member = newMember();
        Long packageId = newPackage();
        UserAccount receptionist = newUser("RECEPTIONIST");
        doThrow(new IllegalStateException("Simulated audit failure"))
                .when(activityLogRepository)
                .save(argThat((ActivityLog log) -> log != null && AuditAction.PAYMENT_CONFIRMED.equals(log.getAction())));
        Map<String, Integer> before = receiptTableCounts();

        mockMvc.perform(post("/api/v1/packages/registrations")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"packageId\":" + packageId
                                + ",\"channel\":\"RECEPTION\",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isInternalServerError());

        // The invoice (with its line) was saved before the audit write failed, so its rows existed in the transaction.
        verify(invoiceRepository).save(any(Invoice.class));
        verify(activityLogRepository)
                .save(argThat((ActivityLog log) -> log != null && AuditAction.PAYMENT_CONFIRMED.equals(log.getAction())));
        assertNothingLeft(member, receptionist, before);
    }
}
