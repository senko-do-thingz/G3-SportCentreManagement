package com.sportify.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.test.SportTestData;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-20 against a real SQL Server (Testcontainers): the member payment history, the receipt endpoints, the 403 for
 * another member, and the totals rule on an invoice issued by the real confirm flow (BE-18).
 */
@AutoConfigureMockMvc
class ReceiptIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentVerificationService verificationService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private SportPackageRepository sportPackageRepository;
    @Autowired private SportPackageRegistrationRepository registrationRepository;
    @Autowired private MemberProfileRepository memberProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CodeFormatter codeFormatter;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private final List<Long> registrationIds = new ArrayList<>();
    private final List<Long> packageIds = new ArrayList<>();
    private final List<Long> sportIds = new ArrayList<>();

    private static final AtomicInteger MEMBER_CODE = new AtomicInteger();

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    /* Rows are deleted after each test: every integration test class shares one SQL Server container. */
    @AfterEach
    void cleanUp() {
        if (!paymentIds.isEmpty()) {
            String payments = idList(paymentIds);
            jdbcTemplate.update("DELETE FROM activity_log WHERE entity_type = 'PAYMENT' AND entity_id IN (" + payments + ")");
            jdbcTemplate.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE payment_id IN (" + payments + "))");
            jdbcTemplate.update("DELETE FROM invoice WHERE payment_id IN (" + payments + ")");
        }
        if (!userIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM activity_log WHERE actor_user_id IN (" + idList(userIds) + ")");
        }
        deleteByIds("sport_package_registration", registrationIds);
        deleteByIds("payment", paymentIds);
        deleteByIds("sport_package", packageIds);
        deleteByIds("sport", sportIds);
        if (!userIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM member_profile WHERE user_id IN (" + idList(userIds) + ")");
        }
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
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Seeded role " + roleCode + " not found"));
        UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                .email("be20-" + roleCode.toLowerCase() + "-" + System.nanoTime() + "@sportify.com")
                .passwordHash("DummyHashForTestPurposesOnly")
                .fullName("BE20 " + roleCode)
                .role(role)
                .status("ACTIVE")
                .build());
        userIds.add(user.getId());
        return user;
    }

    /** Returns the member's user account, with its role loaded, ready for MockMvc. */
    private UserAccount newMember() {
        return tx().execute(s -> {
            UserAccount user = newUser("MEMBER");
            memberProfileRepository.saveAndFlush(MemberProfile.builder()
                    .userAccount(user)
                    .memberCode("BE20-MEM-" + MEMBER_CODE.incrementAndGet())
                    .currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now(clock))
                    .build());
            return user;
        });
    }

    /** A pending package registration of the member: original 400000, paid 380000. Returns {paymentId, registrationId}. */
    private Long[] pendingRegistration(UserAccount memberUser) {
        return tx().execute(s -> {
            MemberProfile member = memberProfileRepository.findById(memberUser.getId()).orElseThrow();
            Sport sport = sportRepository.save(SportTestData.aValidSport().build());
            SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                    .code("BE20-PKG-" + System.nanoTime())
                    .name("BE20 Swimming 30 Days")
                    .sport(sport)
                    .trainingFormat(TrainingFormat.SELF_TRAINING)
                    .durationDays(30)
                    .sessionCount(10)
                    .sessionMinutes(60)
                    .priceAmount(new BigDecimal("400000.00"))
                    .isActive(true)
                    .build());
            Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                    .paymentCode(codeFormatter.formatPaymentCode(paymentRepository.getNextPaymentCodeSequence()))
                    .member(member)
                    .amount(new BigDecimal("380000.00"))
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentStatus(PaymentStatus.PENDING)
                    .build());
            LocalDate today = LocalDate.now(clock);
            SportPackageRegistration reg = registrationRepository.save(SportPackageRegistration.builder()
                    .registrationCode(codeFormatter.formatPackageRegCode(registrationRepository.getNextRegistrationCodeSequence()))
                    .member(member)
                    .sportPackage(pkg)
                    .channel(RegistrationChannel.ONLINE)
                    .originalPrice(new BigDecimal("400000.00"))
                    .discountPercentage(5)
                    .paidAmount(new BigDecimal("380000.00"))
                    .totalSessions(10)
                    .remainingSessions(10)
                    .startDate(today)
                    .endDate(today.plusDays(29))
                    .status(PackageRegistrationStatus.PENDING_PAYMENT)
                    .createdByUser(member.getUserAccount())
                    .payment(payment)
                    .build());
            sportIds.add(sport.getId());
            packageIds.add(pkg.getId());
            paymentIds.add(payment.getId());
            registrationIds.add(reg.getId());
            return new Long[]{payment.getId(), reg.getId()};
        });
    }

    /** Confirms the payment through the real BE-18 flow and returns the issued invoice id. */
    private Long confirm(Long paymentId, UserAccount receptionist) {
        StaffPaymentResponse response = verificationService.confirmPayment(paymentId, ConfirmPaymentRequest.builder()
                .method(PaymentMethod.BANK_TRANSFER)
                .referenceCode("FT" + System.nanoTime())
                .amountReceived(new BigDecimal("380000"))
                .build(), receptionist);
        return response.getInvoiceId();
    }

    private JsonNode getJson(String url, UserAccount actor) throws Exception {
        MvcResult result = mockMvc.perform(get(url).with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET /payments/my: only the signed-in member's payments, newest first, invoice set once confirmed")
    void myPayments_OwnOnlyNewestFirst() throws Exception {
        UserAccount owner = newMember();
        UserAccount other = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        Long[] first = pendingRegistration(owner);
        Long[] second = pendingRegistration(owner);
        pendingRegistration(other);
        Long invoiceId = confirm(first[0], receptionist);

        JsonNode page = getJson("/api/v1/payments/my", owner);

        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        JsonNode rows = page.get("content");
        assertThat(rows.get(0).get("id").asLong()).isEqualTo(second[0]);
        assertThat(rows.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(rows.get(0).get("invoiceId").isNull()).isTrue();
        assertThat(rows.get(1).get("id").asLong()).isEqualTo(first[0]);
        assertThat(rows.get(1).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(rows.get(1).get("invoiceId").asLong()).isEqualTo(invoiceId);
        assertThat(rows.get(1).get("invoiceNumber").asText()).startsWith("INV-");
        assertThat(rows.get(1).get("itemDescription").asText()).isEqualTo("BE20 Swimming 30 Days");

        JsonNode pending = getJson("/api/v1/payments/my?status=PENDING", owner);
        assertThat(pending.get("totalElements").asLong()).isEqualTo(1);
        assertThat(pending.get("content").get(0).get("id").asLong()).isEqualTo(second[0]);
    }

    @Test
    @DisplayName("GET /invoices/{id}: owner and staff read it, total = lines - discount + tax, another member gets 403")
    void invoice_OwnerStaffAndOtherMember() throws Exception {
        UserAccount owner = newMember();
        UserAccount other = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        Long[] ids = pendingRegistration(owner);
        Long invoiceId = confirm(ids[0], receptionist);

        JsonNode invoice = getJson("/api/v1/invoices/" + invoiceId, owner);
        assertThat(invoice.get("invoiceNumber").asText()).startsWith("INV-");
        assertThat(invoice.get("status").asText()).isEqualTo("PAID");
        assertThat(invoice.get("memberFullName").asText()).isEqualTo("BE20 MEMBER");
        assertThat(invoice.get("memberCode").asText()).startsWith("BE20-MEM-");
        assertThat(invoice.get("paymentMethod").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(invoice.get("paymentReference").asText()).startsWith("FT");
        BigDecimal linesTotal = BigDecimal.ZERO;
        for (JsonNode line : invoice.get("lines")) {
            linesTotal = linesTotal.add(line.get("lineTotal").decimalValue());
        }
        BigDecimal discount = invoice.get("discountAmount").decimalValue();
        BigDecimal tax = invoice.get("taxAmount").decimalValue();
        BigDecimal total = invoice.get("totalAmount").decimalValue();
        assertThat(invoice.get("subtotalAmount").decimalValue()).isEqualByComparingTo(linesTotal);
        assertThat(linesTotal.subtract(discount).add(tax)).isEqualByComparingTo(total);
        assertThat(total).isEqualByComparingTo("380000");

        assertThat(getJson("/api/v1/invoices/" + invoiceId, receptionist).get("id").asLong()).isEqualTo(invoiceId);
        assertThat(getJson("/api/v1/invoices/by-payment/" + ids[0], owner).get("id").asLong()).isEqualTo(invoiceId);

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId).with(user(new CustomUserDetails(other))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/invoices/by-payment/" + ids[0]).with(user(new CustomUserDetails(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /invoices/{id} unknown id is 404; by-payment of a PENDING payment is 404")
    void invoice_NotFoundCases() throws Exception {
        UserAccount owner = newMember();
        Long[] ids = pendingRegistration(owner);

        mockMvc.perform(get("/api/v1/invoices/999999999").with(user(new CustomUserDetails(owner))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/invoices/by-payment/" + ids[0]).with(user(new CustomUserDetails(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /payments/my as staff is 403; GET /payments/{id} still works next to /my")
    void myPayments_StaffForbidden_AndPathDoesNotClash() throws Exception {
        UserAccount owner = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        Long[] ids = pendingRegistration(owner);

        mockMvc.perform(get("/api/v1/payments/my").with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isForbidden());
        assertThat(getJson("/api/v1/payments/" + ids[0], owner).get("id").asLong()).isEqualTo(ids[0]);
    }
}
