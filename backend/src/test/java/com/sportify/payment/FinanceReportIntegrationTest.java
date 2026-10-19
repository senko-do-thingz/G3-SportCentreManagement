package com.sportify.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.service.RefundService;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.MembershipCardTierRepository;
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
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-22 against a real SQL Server (Testcontainers) with known payments in March 2001, a month no other test uses:
 * every figure of the overview, the revenue breakdowns (shares add up to 100), the ledger filters and the CSV export
 * are checked. PENDING, FAILED and REFUNDED payments and a payment at 2001-04-01 00:00 must not count as March revenue.
 *
 * March 2001 data:
 * P1 SUCCESS 300000  BANK_TRANSFER package (sport A)   paid 2001-03-05 10:00, invoice
 * P2 SUCCESS 500000  CASH          package (sport B)   paid 2001-03-10 09:30
 * P3 SUCCESS 1200000 MOMO          membership card     paid 2001-03-20 18:00
 * P4 PENDING 700000  BANK_TRANSFER package (sport A)   requested 2001-03-15
 * P5 FAILED  250000  CASH                              requested 2001-03-16
 * P7 REFUNDED 400000 VNPAY                             paid 2001-03-12, refund 200000 completed 2001-03-25
 *                                                      (REFUND_COMPLETED log 2001-03-25; updated_at changed later to
 *                                                      2001-04-02, so the log time must win)
 * P9 REFUNDED 150000 VNPAY   package (sport A)         paid 2001-02-20, legacy refund 50000 without a log row,
 *                                                      updated_at 2001-03-28 (fallback)
 * P6 SUCCESS 100000  CASH (February)                   paid 2001-02-14
 * P8 SUCCESS 999000  CASH (April, boundary)            paid 2001-04-01 00:00:00
 */
@AutoConfigureMockMvc
class FinanceReportIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private SportPackageRepository sportPackageRepository;
    @Autowired private SportPackageRegistrationRepository registrationRepository;
    @Autowired private MemberCardRepository memberCardRepository;
    @Autowired private MembershipCardTierRepository tierRepository;
    @Autowired private MemberProfileRepository memberProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CodeFormatter codeFormatter;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RefundService refundService;
    @Autowired private Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private final List<Long> registrationIds = new ArrayList<>();
    private final List<Long> cardIds = new ArrayList<>();
    private final List<Long> packageIds = new ArrayList<>();
    private final List<Long> sportIds = new ArrayList<>();
    private final List<Long> tierIds = new ArrayList<>();

    private final Map<String, Long> payments = new HashMap<>();
    private UserAccount manager;
    private String codePrefix;
    private String sportAName;
    private String sportBName;
    private Long sportAId;
    private Long sportBId;
    private String invoiceNumber;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        if (!paymentIds.isEmpty()) {
            String ids = idList(paymentIds);
            jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE payment_id IN (" + ids + "))");
            jdbc.update("DELETE FROM invoice WHERE payment_id IN (" + ids + ")");
        }
        if (!registrationIds.isEmpty()) {
            jdbc.update("DELETE FROM refund_request WHERE package_registration_id IN (" + idList(registrationIds) + ")");
        }
        deleteByIds("member_card", cardIds);
        deleteByIds("sport_package_registration", registrationIds);
        deleteByIds("payment", paymentIds);
        deleteByIds("sport_package", packageIds);
        deleteByIds("sport", sportIds);
        deleteByIds("membership_card_tier", tierIds);
        if (!userIds.isEmpty()) {
            jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + idList(userIds) + ")");
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

    private UserAccount newUser(String roleCode, String fullName) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Seeded role " + roleCode + " not found"));
        UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                .email("be22-" + roleCode.toLowerCase() + "-" + System.nanoTime() + "@example.invalid")
                .passwordHash(java.util.UUID.randomUUID().toString())
                .fullName(fullName)
                .role(role)
                .status("ACTIVE")
                .build());
        userIds.add(user.getId());
        return user;
    }

    private MemberProfile newMember(String fullName) {
        UserAccount user = newUser("MEMBER", fullName);
        return memberProfileRepository.saveAndFlush(MemberProfile.builder()
                .userAccount(user)
                .memberCode(codePrefix + "-" + SEQ.incrementAndGet())
                .currentLevel("BEGINNER")
                .joinedOn(LocalDate.of(2001, 1, 1))
                .build());
    }

    private Payment payment(String key, MemberProfile member, String amount, PaymentMethod method, PaymentStatus status,
                            LocalDateTime paidAt) {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paymentRepository.getNextPaymentCodeSequence()))
                .member(member)
                .amount(new BigDecimal(amount))
                .paymentMethod(method)
                .paymentStatus(status)
                .paymentTime(paidAt)
                .referenceCode(key.equals("P1") ? "FT-" + codePrefix : null)
                .build());
        paymentIds.add(payment.getId());
        payments.put(key, payment.getId());
        return payment;
    }

    private void setCreatedAt(String key, LocalDateTime createdAt) {
        jdbc.update("UPDATE payment SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), payments.get(key));
    }

    private SportPackage sportPackage(Sport sport) {
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("BE22-PKG-" + System.nanoTime())
                .name("BE22 Package " + sport.getName())
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(new BigDecimal("300000.00"))
                .isActive(true)
                .build());
        packageIds.add(pkg.getId());
        return pkg;
    }

    private SportPackageRegistration registration(MemberProfile member, SportPackage pkg, Payment payment,
                                                  PackageRegistrationStatus status) {
        SportPackageRegistration reg = registrationRepository.save(SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(registrationRepository.getNextRegistrationCodeSequence()))
                .member(member)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(payment.getAmount())
                .discountPercentage(0)
                .paidAmount(payment.getAmount())
                .totalSessions(10)
                .remainingSessions(10)
                .startDate(LocalDate.of(2001, 3, 1))
                .endDate(LocalDate.of(2001, 3, 30))
                .status(status)
                .createdByUser(member.getUserAccount())
                .payment(payment)
                .build());
        registrationIds.add(reg.getId());
        return reg;
    }

    @BeforeEach
    void seed() {
        // member_code is NVARCHAR(20): a short prefix, unique per test, that the ledger search can match.
        codePrefix = "B22" + Math.floorMod(System.nanoTime(), 1_000_000_000L);
        manager = newUser("MANAGER", "BE22 Manager");
        tx().executeWithoutResult(s -> {
            // Vietnamese test data is written with Unicode escapes: scripts/ci/check-conventions.sh rejects
            // accented characters in Java lines. The values are still real Vietnamese names at run time.
            MemberProfile anh = newMember("Nguy\u1ec5n V\u0103n \u00c1nh");
            MemberProfile bich = newMember("Tr\u1ea7n Th\u1ecb B\u00edch, \"VIP\"");

            Sport sportA = sportRepository.save(SportTestData.aValidSport().name("B\u01a1i l\u1ed9i " + SEQ.incrementAndGet()).build());
            Sport sportB = sportRepository.save(SportTestData.aValidSport().name("C\u1ea7u l\u00f4ng " + SEQ.incrementAndGet()).build());
            sportIds.add(sportA.getId());
            sportIds.add(sportB.getId());
            sportAId = sportA.getId();
            sportBId = sportB.getId();
            sportAName = sportA.getName();
            sportBName = sportB.getName();
            SportPackage pkgA = sportPackage(sportA);
            SportPackage pkgB = sportPackage(sportB);

            Payment p1 = payment("P1", anh, "300000.00", PaymentMethod.BANK_TRANSFER, PaymentStatus.SUCCESS,
                    LocalDateTime.of(2001, 3, 5, 10, 0));
            registration(anh, pkgA, p1, PackageRegistrationStatus.ACTIVE);
            Payment p2 = payment("P2", bich, "500000.00", PaymentMethod.CASH, PaymentStatus.SUCCESS,
                    LocalDateTime.of(2001, 3, 10, 9, 30));
            registration(bich, pkgB, p2, PackageRegistrationStatus.ACTIVE);

            Payment p3 = payment("P3", anh, "1200000.00", PaymentMethod.MOMO, PaymentStatus.SUCCESS,
                    LocalDateTime.of(2001, 3, 20, 18, 0));
            MembershipCardTier tier = tierRepository.save(MembershipCardTier.builder()
                    .code("BE22-" + System.nanoTime())
                    .name("BE22 Gold")
                    .price(new BigDecimal("1200000.00"))
                    .durationMonths(12)
                    .discountPercentage(5)
                    .tierRank(7000 + SEQ.incrementAndGet())
                    .isActive(true)
                    .build());
            tierIds.add(tier.getId());
            MemberCard card = memberCardRepository.save(MemberCard.builder()
                    .cardCode(codeFormatter.formatCardCode(memberCardRepository.getNextCardCodeSequence()))
                    .member(anh)
                    .tier(tier)
                    .startDate(LocalDate.of(2001, 3, 20))
                    .endDate(LocalDate.of(2002, 3, 19))
                    .status(CardStatus.ACTIVE)
                    .pricePaid(new BigDecimal("1200000.00"))
                    .payment(p3)
                    .build());
            cardIds.add(card.getId());

            Payment p4 = payment("P4", bich, "700000.00", PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING, null);
            registration(bich, pkgA, p4, PackageRegistrationStatus.PENDING_PAYMENT);
            payment("P5", anh, "250000.00", PaymentMethod.CASH, PaymentStatus.FAILED, null);
            Payment p7 = payment("P7", bich, "400000.00", PaymentMethod.VNPAY, PaymentStatus.REFUNDED,
                    LocalDateTime.of(2001, 3, 12, 8, 0));
            SportPackageRegistration refundedReg = registration(bich, pkgB, p7, PackageRegistrationStatus.REFUNDED);
            Payment p9 = payment("P9", anh, "150000.00", PaymentMethod.VNPAY, PaymentStatus.REFUNDED,
                    LocalDateTime.of(2001, 2, 20, 10, 0));
            SportPackageRegistration legacyRefundedReg = registration(anh, pkgA, p9, PackageRegistrationStatus.REFUNDED);
            payment("P6", anh, "100000.00", PaymentMethod.CASH, PaymentStatus.SUCCESS, LocalDateTime.of(2001, 2, 14, 12, 0));
            payment("P8", anh, "999000.00", PaymentMethod.CASH, PaymentStatus.SUCCESS, LocalDateTime.of(2001, 4, 1, 0, 0));

            jdbc.update("""
                    INSERT INTO refund_request (refund_code, payment_id, package_registration_id, member_id, amount_requested,
                        amount_approved, reason, status, requested_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 400000.00, 200000.00, N'Chuy\u1ec3n nh\u00e0', 'COMPLETED', ?, ?, ?)
                    """, "RF-" + codePrefix.substring(3), p7.getId(), refundedReg.getId(), bich.getId(),
                    bich.getId(), Timestamp.valueOf(LocalDateTime.of(2001, 3, 13, 9, 0)),
                    Timestamp.valueOf(LocalDateTime.of(2001, 4, 2, 9, 0)));
            // P7 was completed on 2001-03-25 (REFUND_COMPLETED log, as BE-21 writes it); updated_at above stands for a
            // later change to the row, which must not move the refund into April.
            Long p7RefundId = jdbc.queryForObject("SELECT id FROM refund_request WHERE refund_code = ?", Long.class,
                    "RF-" + codePrefix.substring(3));
            jdbc.update("""
                    INSERT INTO activity_log (actor_user_id, action, entity_type, entity_id, entity_code, summary, created_at)
                    VALUES (?, 'REFUND_COMPLETED', 'REFUND_REQUEST', ?, ?, N'Refund completed', ?)
                    """, manager.getId(), p7RefundId, "RF-" + codePrefix.substring(3),
                    Timestamp.valueOf(LocalDateTime.of(2001, 3, 25, 9, 0)));
            // P9: legacy COMPLETED refund without a log row; its updated_at (2001-03-28) is the completion time.
            jdbc.update("""
                    INSERT INTO refund_request (refund_code, payment_id, package_registration_id, member_id, amount_requested,
                        amount_approved, reason, status, requested_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 150000.00, 50000.00, N'Legacy refund', 'COMPLETED', ?, ?, ?)
                    """, "RL-" + codePrefix.substring(3), p9.getId(), legacyRefundedReg.getId(), anh.getId(),
                    anh.getId(), Timestamp.valueOf(LocalDateTime.of(2001, 2, 21, 9, 0)),
                    Timestamp.valueOf(LocalDateTime.of(2001, 3, 28, 9, 0)));

            invoiceNumber = "INV-" + codePrefix.substring(3);
            jdbc.update("""
                    INSERT INTO invoice (invoice_number, payment_id, member_id, subtotal_amount, discount_amount, tax_amount,
                        total_amount, status, issue_date, created_at)
                    VALUES (?, ?, ?, 300000.00, 0, 0, 300000.00, 'PAID', ?, ?)
                    """, invoiceNumber, p1.getId(), anh.getId(),
                    Timestamp.valueOf(LocalDateTime.of(2001, 3, 5, 10, 0)), Timestamp.valueOf(LocalDateTime.of(2001, 3, 5, 10, 0)));
        });
        setCreatedAt("P4", LocalDateTime.of(2001, 3, 15, 14, 0));
        setCreatedAt("P5", LocalDateTime.of(2001, 3, 16, 14, 0));
        // created_at of the others in the same month too, so the ledger range below is exact.
        setCreatedAt("P1", LocalDateTime.of(2001, 3, 5, 9, 0));
        setCreatedAt("P2", LocalDateTime.of(2001, 3, 10, 9, 0));
        setCreatedAt("P3", LocalDateTime.of(2001, 3, 20, 17, 0));
        setCreatedAt("P7", LocalDateTime.of(2001, 3, 12, 7, 0));
        setCreatedAt("P6", LocalDateTime.of(2001, 2, 14, 11, 0));
        setCreatedAt("P9", LocalDateTime.of(2001, 2, 20, 9, 0));
        setCreatedAt("P8", LocalDateTime.of(2001, 3, 31, 23, 0));
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @CsvSource({"P1,100000.00,1700000.00", "P2,500000.00,1500000.00"})
    @DisplayName("Real BE-21 partial and full refunds preserve SUCCESS-only finance semantics")
    void financeAfterRealRefundApprovalAndCompletion(String key, String approved, String remainingRevenue) throws Exception {
        Long paymentId = payments.get(key);
        Long registrationId = jdbc.queryForObject(
                "SELECT id FROM sport_package_registration WHERE payment_id = ?", Long.class, paymentId);
        String completionMonth = YearMonth.now(clock).toString();
        BigDecimal refundedBefore = dec(getJson("/api/v1/manager/finance/overview?month=" + completionMonth), "refundedAmount");
        var submitted = refundService.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(registrationId).amountRequested(new BigDecimal(approved))
                .reason("Synthetic finance regression").build(), manager);
        refundService.reviewRefund(submitted.getId(), RefundReviewRequest.builder()
                .approved(true).amountApproved(new BigDecimal(approved)).build(), manager);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id = ?", String.class, paymentId))
                .isEqualTo("SUCCESS");
        assertThat(dec(getJson("/api/v1/manager/finance/overview?month=2001-03"), "paidRevenue"))
                .isEqualByComparingTo("2000000.00");
        assertThat(dec(getJson("/api/v1/manager/finance/overview?month=" + completionMonth), "refundedAmount"))
                .isEqualByComparingTo(refundedBefore);

        refundService.completeRefund(submitted.getId(), manager);
        var paymentBeforeRead = jdbc.queryForMap("SELECT * FROM payment WHERE id = ?", paymentId);
        var refundBeforeRead = jdbc.queryForMap("SELECT * FROM refund_request WHERE id = ?", submitted.getId());
        assertThat(paymentBeforeRead.get("payment_status")).isEqualTo("REFUNDED");
        assertThat(refundBeforeRead.get("status")).isEqualTo("COMPLETED");
        // Even a partial refund removes the whole original payment, not only the approved amount.
        assertThat(dec(getJson("/api/v1/manager/finance/overview?month=2001-03"), "paidRevenue"))
                .isEqualByComparingTo(remainingRevenue);
        assertThat(dec(getJson("/api/v1/manager/finance/revenue?from=2001-03-01&to=2001-03-31&groupBy=METHOD"), "totalRevenue"))
                .isEqualByComparingTo(remainingRevenue);
        assertThat(dec(getJson("/api/v1/manager/finance/overview?month=" + completionMonth), "refundedAmount"))
                .isEqualByComparingTo(refundedBefore.add(new BigDecimal(approved)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE entity_type = 'REFUND_REQUEST' "
                + "AND entity_id = ? AND action = 'REFUND_COMPLETED'", Integer.class, submitted.getId())).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM payment WHERE id = ?", paymentId)).isEqualTo(paymentBeforeRead);
        assertThat(jdbc.queryForMap("SELECT * FROM refund_request WHERE id = ?", submitted.getId())).isEqualTo(refundBeforeRead);
    }

    private static BigDecimal dec(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }

    @Test
    @DisplayName("Overview of March 2001: every figure; PENDING, FAILED, REFUNDED and the April boundary payment are not revenue")
    void overview() throws Exception {
        JsonNode o = getJson("/api/v1/manager/finance/overview?month=2001-03");

        assertThat(o.get("month").asText()).isEqualTo("2001-03");
        assertThat(dec(o, "paidRevenue")).isEqualByComparingTo("2000000");
        assertThat(o.get("paidTransactions").asLong()).isEqualTo(3);
        assertThat(dec(o, "averagePayment")).isEqualByComparingTo("666666.67");
        assertThat(dec(o, "pendingAmount")).isEqualByComparingTo("700000");
        assertThat(o.get("pendingTransactions").asLong()).isEqualTo(1);
        // P7 (log time 2001-03-25, updated_at later moved to April) and P9 (legacy, updated_at 2001-03-28).
        assertThat(dec(o, "refundedAmount")).isEqualByComparingTo("250000");
        assertThat(o.get("refundedTransactions").asLong()).isEqualTo(2);
        assertThat(o.get("previousMonth").asText()).isEqualTo("2001-02");
        assertThat(dec(o, "previousPaidRevenue")).isEqualByComparingTo("100000");
        assertThat(o.get("previousPaidTransactions").asLong()).isEqualTo(1);
        assertThat(dec(o, "revenueChange")).isEqualByComparingTo("1900000");
        assertThat(dec(o, "revenueChangePercent")).isEqualByComparingTo("1900.00");

        JsonNode april = getJson("/api/v1/manager/finance/overview?month=2001-04");
        assertThat(dec(april, "paidRevenue")).isEqualByComparingTo("999000");
        assertThat(dec(april, "averagePayment")).isEqualByComparingTo("999000");
        assertThat(dec(april, "refundedAmount")).isEqualByComparingTo("0");
        assertThat(april.get("refundedTransactions").asLong()).isZero();

        JsonNode february = getJson("/api/v1/manager/finance/overview?month=2001-02");
        assertThat(dec(february, "paidRevenue")).isEqualByComparingTo("100000");
        assertThat(dec(february, "refundedAmount")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Revenue by item type, method and sport for March 2001: totals equal the overview and shares add up to 100")
    void revenueBreakdowns() throws Exception {
        String range = "from=2001-03-01&to=2001-03-31";

        JsonNode byType = getJson("/api/v1/manager/finance/revenue?" + range + "&groupBy=ITEM_TYPE");
        assertThat(dec(byType, "totalRevenue")).isEqualByComparingTo("2000000");
        assertThat(byType.get("totalTransactions").asLong()).isEqualTo(3);
        assertRows(byType, new String[][]{
                {"MEMBERSHIP_CARD", "1200000", "1", "60.00"},
                {"SPORT_PACKAGE", "800000", "2", "40.00"}});

        JsonNode byMethod = getJson("/api/v1/manager/finance/revenue?" + range + "&groupBy=METHOD");
        assertRows(byMethod, new String[][]{
                {"MOMO", "1200000", "1", "60.00"},
                {"CASH", "500000", "1", "25.00"},
                {"BANK_TRANSFER", "300000", "1", "15.00"}});

        JsonNode bySport = getJson("/api/v1/manager/finance/revenue?" + range + "&groupBy=SPORT");
        assertRows(bySport, new String[][]{
                {null, "1200000", "1", "60.00"},
                {String.valueOf(sportBId), "500000", "1", "25.00"},
                {String.valueOf(sportAId), "300000", "1", "15.00"}});
        assertThat(bySport.get("rows").get(1).get("label").asText()).isEqualTo(sportBName);
        assertThat(bySport.get("rows").get(2).get("label").asText()).isEqualTo(sportAName);

        for (JsonNode report : List.of(byType, byMethod, bySport)) {
            BigDecimal shares = BigDecimal.ZERO;
            BigDecimal revenue = BigDecimal.ZERO;
            for (JsonNode row : report.get("rows")) {
                shares = shares.add(dec(row, "sharePercent"));
                revenue = revenue.add(dec(row, "revenue"));
            }
            assertThat(shares).isEqualByComparingTo("100.00");
            assertThat(revenue).isEqualByComparingTo(dec(report, "totalRevenue"));
        }
    }

    private static void assertRows(JsonNode report, String[][] expected) {
        JsonNode rows = report.get("rows");
        assertThat(rows.size()).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            JsonNode row = rows.get(i);
            if (expected[i][0] == null) {
                assertThat(row.get("key").isNull()).isTrue();
            } else {
                assertThat(row.get("key").asText()).isEqualTo(expected[i][0]);
            }
            assertThat(dec(row, "revenue")).isEqualByComparingTo(expected[i][1]);
            assertThat(row.get("transactions").asLong()).isEqualTo(Long.parseLong(expected[i][2]));
            assertThat(dec(row, "sharePercent")).isEqualByComparingTo(expected[i][3]);
        }
    }

    @Test
    @DisplayName("Ledger: every status, newest first, filters by status, type, method and search")
    void ledger() throws Exception {
        String base = "/api/v1/manager/finance/transactions?from=2001-02-01&to=2001-04-30&search=" + codePrefix;

        JsonNode all = getJson(base + "&size=100");
        assertThat(all.get("totalElements").asLong()).isEqualTo(9);
        List<Long> order = new ArrayList<>();
        all.get("content").forEach(r -> order.add(r.get("id").asLong()));
        assertThat(order).containsExactly(payments.get("P8"), payments.get("P3"), payments.get("P5"), payments.get("P4"),
                payments.get("P7"), payments.get("P2"), payments.get("P1"), payments.get("P9"), payments.get("P6"));

        assertThat(getJson(base + "&status=PENDING").get("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson(base + "&status=FAILED").get("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson(base + "&status=REFUNDED").get("totalElements").asLong()).isEqualTo(2);
        JsonNode cards = getJson(base + "&type=MEMBERSHIP_CARD");
        assertThat(cards.get("totalElements").asLong()).isEqualTo(1);
        assertThat(cards.get("content").get(0).get("id").asLong()).isEqualTo(payments.get("P3"));
        assertThat(getJson(base + "&type=SPORT_PACKAGE").get("totalElements").asLong()).isEqualTo(5);
        assertThat(getJson(base + "&method=CASH").get("totalElements").asLong()).isEqualTo(4);

        JsonNode p1 = getJson("/api/v1/manager/finance/transactions?search=" + invoiceNumber).get("content").get(0);
        assertThat(p1.get("id").asLong()).isEqualTo(payments.get("P1"));
        assertThat(p1.get("memberName").asText()).isEqualTo("Nguy\u1ec5n V\u0103n \u00c1nh");
        assertThat(p1.get("itemType").asText()).isEqualTo("SPORT_PACKAGE");
        assertThat(p1.get("sportName").asText()).isEqualTo(sportAName);

        JsonNode page2 = getJson(base + "&page=1&size=3");
        assertThat(page2.get("content").size()).isEqualTo(3);
        assertThat(page2.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page2.get("content").get(0).get("id").asLong()).isEqualTo(payments.get("P4"));
    }

    @Test
    @DisplayName("CSV export: BOM, header, paid March rows only, Vietnamese names in UTF-8, quoted comma and quotes")
    void csvExport() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/manager/finance/revenue/export?from=2001-03-01&to=2001-03-31")
                        .with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk()).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment").contains("revenue_2001-03-01_2001-03-31.csv");
        assertThat(Arrays.copyOf(body, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        List<String> lines = Arrays.asList(text.split("\r\n"));
        assertThat(lines.get(0)).isEqualTo("Payment code,Paid at,Invoice number,Member code,Member name,Item type,Item code,Item,Sport,Method,Amount");
        // Rows of other tests (paid now) are outside March 2001; only the 3 paid March rows remain.
        assertThat(lines).hasSize(4);
        assertThat(lines.get(1)).contains("2001-03-05 10:00:00").contains(invoiceNumber).contains("Nguy\u1ec5n V\u0103n \u00c1nh")
                .contains(sportAName).contains("BANK_TRANSFER").endsWith(",300000");
        assertThat(lines.get(2)).contains("\"Tr\u1ea7n Th\u1ecb B\u00edch, \"\"VIP\"\"\"").endsWith(",500000");
        assertThat(lines.get(3)).contains("MEMBERSHIP_CARD").contains("MOMO").endsWith(",1200000");
        // The PENDING (P4), FAILED (P5) and REFUNDED (P7, P9) payments and the April payment (P8) are not exported.
        assertThat(lines.stream().noneMatch(l -> l.endsWith(",700000") || l.endsWith(",250000")
                || l.endsWith(",400000") || l.endsWith(",150000") || l.endsWith(",999000"))).isTrue();
    }

    @Test
    @DisplayName("Supported dates: 9999-12-30 reaches SQL Server DATETIME2 and inclusive bounds hold; later or earlier dates are 400, not 500")
    void dateBoundaries() throws Exception {
        String ledger = "/api/v1/manager/finance/transactions?search=" + codePrefix;

        // The last supported date: its exclusive bound 9999-12-31 00:00 is a valid DATETIME2 value.
        assertThat(getJson(ledger + "&from=1900-01-01&to=9999-12-30").get("totalElements").asLong()).isEqualTo(9);
        JsonNode farRevenue = getJson("/api/v1/manager/finance/revenue?from=9999-01-01&to=9999-12-30&groupBy=METHOD");
        assertThat(dec(farRevenue, "totalRevenue")).isEqualByComparingTo("0");
        assertThat(farRevenue.get("rows").size()).isZero();
        MvcResult farExport = mockMvc.perform(get("/api/v1/manager/finance/revenue/export?from=9999-12-30&to=9999-12-30")
                        .with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk()).andReturn();
        byte[] farBody = farExport.getResponse().getContentAsByteArray();
        assertThat(new String(farBody, 3, farBody.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("Payment code,Paid at,Invoice number,Member code,Member name,Item type,Item code,Item,Sport,Method,Amount\r\n");
        assertThat(dec(getJson("/api/v1/manager/finance/overview?month=9999-11"), "paidRevenue")).isEqualByComparingTo("0");

        // Inclusive dates on SQL Server: P8 paid exactly 2001-04-01 00:00 belongs to to=2001-04-01, not to to=2001-03-31.
        JsonNode aprilFirst = getJson(ledger + "&from=2001-04-01&to=2001-04-01");
        assertThat(aprilFirst.get("totalElements").asLong()).isEqualTo(1);
        assertThat(aprilFirst.get("content").get(0).get("id").asLong()).isEqualTo(payments.get("P8"));
        assertThat(getJson(ledger + "&from=2001-03-31&to=2001-03-31").get("totalElements").asLong()).isZero();

        // Unsupported dates: 400 with the range message, never 500.
        for (String url : List.of(
                ledger + "&to=9999-12-31",
                ledger + "&to=%2B10000-01-01",
                ledger + "&to=%2B999999999-12-31",
                ledger + "&from=1899-12-31",
                "/api/v1/manager/finance/revenue?from=9999-12-31&to=9999-12-31",
                "/api/v1/manager/finance/revenue?to=%2B999999999-12-31",
                "/api/v1/manager/finance/revenue/export?from=9999-12-31&to=9999-12-31",
                "/api/v1/manager/finance/revenue/export?from=%2B999999999-12-31&to=%2B999999999-12-31",
                "/api/v1/manager/finance/overview?month=9999-12",
                "/api/v1/manager/finance/overview?month=1899-12")) {
            MvcResult result = mockMvc.perform(get(java.net.URI.create(url)).with(user(new CustomUserDetails(manager))))
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .as(url).containsPattern("between (1900-01-01 and 9999-12-30|1900-01 and 9999-11)");
        }
    }

    @Test
    @DisplayName("Trend: the requested number of months, ending with the current month")
    void trend() throws Exception {
        JsonNode trend = getJson("/api/v1/manager/finance/trend?months=6");
        assertThat(trend.size()).isEqualTo(6);
        assertThat(trend.get(5).get("month").asText()).isEqualTo(java.time.YearMonth.now(clock).toString());
    }

    @Test
    @DisplayName("Non-manager roles get 403 on the finance endpoints")
    void nonManagerForbidden() throws Exception {
        UserAccount receptionist = newUser("RECEPTIONIST", "BE22 Reception");
        mockMvc.perform(get("/api/v1/manager/finance/overview?month=2001-03").with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/manager/finance/revenue/export").with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isForbidden());
    }
}
