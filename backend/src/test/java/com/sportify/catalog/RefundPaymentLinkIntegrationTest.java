package com.sportify.catalog;

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
import com.sportify.catalog.service.RefundService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-21 against a real SQL Server (Testcontainers): quote, submit with the payment link, 409 on a second refund,
 * and completion updating payment, invoice, registration and upcoming bookings in one transaction.
 * The registration is bought through the real BE-18 confirm flow, so the payment is SUCCESS with a PAID invoice.
 */
@AutoConfigureMockMvc
class RefundPaymentLinkIntegrationTest extends AbstractIntegrationTest {

    private static final String FAIL_TRIGGER = "trg_be21_force_booking_failure";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefundService refundService;
    @Autowired private PaymentVerificationService verificationService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private SportPackageRepository sportPackageRepository;
    @Autowired private SportPackageRegistrationRepository registrationRepository;
    @Autowired private MemberProfileRepository memberProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CodeFormatter codeFormatter;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private final List<Long> registrationIds = new ArrayList<>();
    private final List<Long> packageIds = new ArrayList<>();
    private final List<Long> sportIds = new ArrayList<>();

    private static final AtomicInteger SEQ = new AtomicInteger();

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    /* Rows are deleted after each test: every integration test class shares one SQL Server container. */
    @AfterEach
    void cleanUp() {
        jdbc.execute("IF OBJECT_ID('" + FAIL_TRIGGER + "', 'TR') IS NOT NULL DROP TRIGGER " + FAIL_TRIGGER);
        if (!registrationIds.isEmpty()) {
            String regs = idList(registrationIds);
            jdbc.update("DELETE FROM activity_log WHERE entity_type = 'REFUND_REQUEST' AND entity_id IN "
                    + "(SELECT id FROM refund_request WHERE package_registration_id IN (" + regs + "))");
            jdbc.update("DELETE FROM refund_request WHERE package_registration_id IN (" + regs + ")");
            jdbc.update("DELETE FROM booking WHERE package_registration_id IN (" + regs + ")");
        }
        if (!sportIds.isEmpty()) {
            jdbc.update("DELETE FROM class_session WHERE sport_id IN (" + idList(sportIds) + ")");
        }
        if (!paymentIds.isEmpty()) {
            String payments = idList(paymentIds);
            jdbc.update("DELETE FROM activity_log WHERE entity_type = 'PAYMENT' AND entity_id IN (" + payments + ")");
            jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE payment_id IN (" + payments + "))");
            jdbc.update("DELETE FROM invoice WHERE payment_id IN (" + payments + ")");
        }
        if (!userIds.isEmpty()) {
            jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + idList(userIds) + ")");
        }
        deleteByIds("sport_package_registration", registrationIds);
        deleteByIds("payment", paymentIds);
        deleteByIds("sport_package", packageIds);
        deleteByIds("sport", sportIds);
        if (!userIds.isEmpty()) {
            jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + idList(userIds) + ")");
        }
        deleteByIds("user_account", userIds);
    }

    private void deleteByIds(String table, List<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("DELETE FROM " + table + " WHERE id IN (" + idList(ids) + ")");
        }
    }

    private static String idList(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private UserAccount newUser(String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Seeded role " + roleCode + " not found"));
        UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                .email("be21-" + roleCode.toLowerCase() + "-" + System.nanoTime() + "@sportify.com")
                .passwordHash("DummyHashForTestPurposesOnly")
                .fullName("BE21 " + roleCode)
                .role(role)
                .status("ACTIVE")
                .build());
        userIds.add(user.getId());
        return user;
    }

    private UserAccount newMember() {
        return tx().execute(s -> {
            UserAccount user = newUser("MEMBER");
            memberProfileRepository.saveAndFlush(MemberProfile.builder()
                    .userAccount(user)
                    .memberCode("BE21-MEM-" + SEQ.incrementAndGet())
                    .currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now(clock))
                    .build());
            return user;
        });
    }

    /** A 10-session package registration paid 380000, confirmed through BE-18. Returns {paymentId, registrationId, sportId}. */
    private Long[] activeRegistration(UserAccount memberUser, UserAccount receptionist) {
        Long[] ids = tx().execute(s -> {
            MemberProfile member = memberProfileRepository.findById(memberUser.getId()).orElseThrow();
            Sport sport = sportRepository.save(SportTestData.aValidSport().build());
            SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                    .code("BE21-PKG-" + System.nanoTime())
                    .name("BE21 Swimming 30 Days")
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
            return new Long[]{payment.getId(), reg.getId(), sport.getId()};
        });
        verificationService.confirmPayment(ids[0], ConfirmPaymentRequest.builder()
                .method(PaymentMethod.BANK_TRANSFER)
                .referenceCode("FT" + System.nanoTime())
                .amountReceived(new BigDecimal("380000"))
                .build(), receptionist);
        return ids;
    }

    private long createSession(long sportId, LocalDate date, int bookedCount) {
        jdbc.update("""
                INSERT INTO class_session (sport_id, session_date, start_time, end_time, training_type, capacity, booked_count, status)
                VALUES (?, ?, CAST('18:00' AS TIME), CAST('19:30' AS TIME), 'SELF_TRAINING', 20, ?, 'PUBLISHED')
                """, sportId, java.sql.Date.valueOf(date), bookedCount);
        return jdbc.queryForObject("SELECT MAX(id) FROM class_session WHERE sport_id = ?", Long.class, sportId);
    }

    private long insertBooking(long sessionId, long memberId, long registrationId) {
        String code = "BE21-BK-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO booking (booking_code, session_id, member_id, package_registration_id, status)
                VALUES (?, ?, ?, ?, 'CONFIRMED')
                """, code, sessionId, memberId, registrationId);
        return jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = ?", Long.class, code);
    }

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder request, ResultMatcher expected) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(expected).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? null : objectMapper.readTree(body);
    }

    private String submitBody(Long registrationId) {
        return "{\"packageRegistrationId\":" + registrationId + ",\"reason\":\"Moving away\"}";
    }

    private String dbValue(String table, Long id, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("Full flow: quote, submit (payment linked), 409 on a second refund, approve, complete updates payment, invoice, registration, bookings")
    void fullFlow() throws Exception {
        UserAccount owner = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        UserAccount manager = newUser("MANAGER");
        Long[] ids = activeRegistration(owner, receptionist);
        Long paymentId = ids[0];
        Long regId = ids[1];
        LocalDate today = LocalDate.now(clock);

        // 3 sessions used and gone (remaining 6 of 10 after 4 bookings), 1 booking still upcoming.
        long pastSession = createSession(ids[2], today.minusDays(2), 1);
        long futureSession = createSession(ids[2], today.plusDays(5), 3);
        long pastBooking = insertBooking(pastSession, owner.getId(), regId);
        long futureBooking = insertBooking(futureSession, owner.getId(), regId);
        jdbc.update("UPDATE sport_package_registration SET remaining_sessions = 6 WHERE id = ?", regId);

        CustomUserDetails ownerDetails = new CustomUserDetails(owner);
        JsonNode quote = call(get("/api/v1/refunds/quote?registrationId=" + regId).with(user(ownerDetails)), status().isOk());
        assertThat(quote.get("remainingSessions").asInt()).isEqualTo(6);
        assertThat(quote.get("upcomingBookings").asInt()).isEqualTo(1);
        assertThat(quote.get("unusedSessions").asInt()).isEqualTo(7);
        assertThat(quote.get("suggestedAmount").decimalValue()).isEqualByComparingTo("266000.00");

        JsonNode refund = call(post("/api/v1/refunds").with(user(ownerDetails))
                .contentType(MediaType.APPLICATION_JSON).content(submitBody(regId)), status().isCreated());
        long refundId = refund.get("id").asLong();
        assertThat(refund.get("paymentId").asLong()).isEqualTo(paymentId);
        assertThat(refund.get("amountRequested").decimalValue()).isEqualByComparingTo("266000.00");
        assertThat(jdbc.queryForObject("SELECT payment_id FROM refund_request WHERE id = ?", Long.class, refundId))
                .isEqualTo(paymentId);

        JsonNode second = call(post("/api/v1/refunds").with(user(ownerDetails))
                .contentType(MediaType.APPLICATION_JSON).content(submitBody(regId)), status().isConflict());
        assertThat(second.get("code").asText()).isEqualTo("PAYMENT_ALREADY_REFUNDED");

        call(put("/api/v1/refunds/" + refundId + "/review").with(user(new CustomUserDetails(manager)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"), status().isOk());

        JsonNode done = call(put("/api/v1/refunds/" + refundId + "/complete")
                .with(user(new CustomUserDetails(receptionist))), status().isOk());
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("cancelledBookings").asInt()).isEqualTo(1);

        assertThat(dbValue("payment", paymentId, "payment_status")).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM invoice WHERE payment_id = ?", String.class, paymentId))
                .isEqualTo("REFUNDED");
        assertThat(dbValue("sport_package_registration", regId, "status")).isEqualTo("REFUNDED");
        assertThat(dbValue("booking", futureBooking, "status")).isEqualTo("CANCELLED");
        assertThat(dbValue("booking", pastBooking, "status")).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT booked_count FROM class_session WHERE id = ?", Integer.class, futureSession))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE entity_type = 'REFUND_REQUEST' "
                + "AND entity_id = ? AND action = 'REFUND_COMPLETED'", Integer.class, refundId)).isEqualTo(1);

        JsonNode third = call(post("/api/v1/refunds").with(user(ownerDetails))
                .contentType(MediaType.APPLICATION_JSON).content(submitBody(regId)), status().isConflict());
        assertThat(third.get("code").asText()).isEqualTo("PAYMENT_ALREADY_REFUNDED");
    }

    @Test
    @DisplayName("Quote for 0, half and all sessions used")
    void quote_ZeroHalfAll() throws Exception {
        UserAccount owner = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        Long regId = activeRegistration(owner, receptionist)[1];
        CustomUserDetails staff = new CustomUserDetails(receptionist);

        int[][] cases = {{10, 380000}, {5, 190000}, {0, 0}};
        for (int[] c : cases) {
            jdbc.update("UPDATE sport_package_registration SET remaining_sessions = ? WHERE id = ?", c[0], regId);
            JsonNode quote = call(get("/api/v1/refunds/quote?registrationId=" + regId).with(user(staff)), status().isOk());
            assertThat(quote.get("suggestedAmount").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(c[1]));
            assertThat(quote.get("usedSessions").asInt()).isEqualTo(10 - c[0]);
        }
    }

    @Test
    @DisplayName("Complete fails part way: payment, invoice, registration, bookings and refund all roll back")
    void complete_FailureRollsBackEverything() throws Exception {
        UserAccount owner = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        UserAccount manager = newUser("MANAGER");
        Long[] ids = activeRegistration(owner, receptionist);
        Long paymentId = ids[0];
        Long regId = ids[1];
        long futureSession = createSession(ids[2], LocalDate.now(clock).plusDays(3), 1);
        long futureBooking = insertBooking(futureSession, owner.getId(), regId);

        JsonNode refund = call(post("/api/v1/refunds").with(user(new CustomUserDetails(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(submitBody(regId)), status().isCreated());
        long refundId = refund.get("id").asLong();
        call(put("/api/v1/refunds/" + refundId + "/review").with(user(new CustomUserDetails(manager)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"), status().isOk());

        // The booking update is the last write of completion; make the database refuse it.
        jdbc.execute("CREATE TRIGGER " + FAIL_TRIGGER + " ON booking AFTER UPDATE AS "
                + "IF EXISTS (SELECT 1 FROM inserted WHERE id = " + futureBooking + ") "
                + "THROW 50021, 'BE21 forced booking failure', 1;");

        assertThatThrownBy(() -> refundService.completeRefund(refundId, receptionist)).isInstanceOf(Exception.class);

        assertThat(dbValue("payment", paymentId, "payment_status")).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM invoice WHERE payment_id = ?", String.class, paymentId))
                .isEqualTo("PAID");
        assertThat(dbValue("refund_request", refundId, "status")).isEqualTo("APPROVED");
        assertThat(dbValue("booking", futureBooking, "status")).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT booked_count FROM class_session WHERE id = ?", Integer.class, futureSession))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Another member: quote and submit are 403; amount above the payment is 400")
    void otherMemberAndAmountCap() throws Exception {
        UserAccount owner = newMember();
        UserAccount other = newMember();
        UserAccount receptionist = newUser("RECEPTIONIST");
        Long regId = activeRegistration(owner, receptionist)[1];

        call(get("/api/v1/refunds/quote?registrationId=" + regId).with(user(new CustomUserDetails(other))),
                status().isForbidden());
        call(post("/api/v1/refunds").with(user(new CustomUserDetails(other)))
                .contentType(MediaType.APPLICATION_JSON).content(submitBody(regId)), status().isForbidden());
        call(post("/api/v1/refunds").with(user(new CustomUserDetails(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageRegistrationId\":" + regId + ",\"reason\":\"x\",\"amountRequested\":380000.01}"),
                status().isBadRequest());
    }
}
