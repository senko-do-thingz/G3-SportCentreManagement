package com.sportify.payment;

import com.jayway.jsonpath.JsonPath;
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
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.MembershipCardTierRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.test.SportTestData;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.FailPaymentRequest;
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.InvoiceLine;
import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.exception.PaymentAmountMismatchException;
import com.sportify.payment.repository.InvoiceLineRepository;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.hibernate.Hibernate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-18 against a real SQL Server (Testcontainers). Not @Transactional: each step commits, so the tests see
 * what a second request would see (the mismatch note that survives the 409, the row lock on payment).
 * MockMvc drives the card and package activate endpoints that FE-05 still uses.
 */
@AutoConfigureMockMvc
class PaymentVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private PaymentVerificationService verificationService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceLineRepository invoiceLineRepository;
    @Autowired private MemberCardRepository memberCardRepository;
    @Autowired private MembershipCardTierRepository tierRepository;
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

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    /*
     * Every integration test class shares one SQL Server container, so the rows created here are deleted after each
     * test; leftovers (sports, packages, ranked tiers, member codes) would break the catalog and member tests.
     */
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private final List<Long> registrationIds = new ArrayList<>();
    private final List<Long> cardIds = new ArrayList<>();
    private final List<Long> packageIds = new ArrayList<>();
    private final List<Long> sportIds = new ArrayList<>();
    private final List<Long> tierIds = new ArrayList<>();

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
        deleteByIds("member_card", cardIds);
        deleteByIds("sport_package_registration", registrationIds);
        deleteByIds("payment", paymentIds);
        deleteByIds("sport_package", packageIds);
        deleteByIds("sport", sportIds);
        deleteByIds("membership_card_tier", tierIds);
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

    /** Member codes outside the MEM-0001 sequence range, which other test classes insert literally. */
    private static final AtomicInteger MEMBER_CODE = new AtomicInteger();

    private UserAccount newUser(String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Seeded role " + roleCode + " not found"));
        UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                .email("be18-" + roleCode.toLowerCase() + "-" + System.nanoTime() + "@sportify.com")
                .passwordHash("DummyHashForTestPurposesOnly")
                .fullName("BE18 " + roleCode)
                .role(role)
                .status("ACTIVE")
                .build());
        userIds.add(user.getId());
        return user;
    }

    private MemberProfile newMember() {
        UserAccount user = newUser("MEMBER");
        return memberProfileRepository.saveAndFlush(MemberProfile.builder()
                .userAccount(user)
                .memberCode("BE18-MEM-" + MEMBER_CODE.incrementAndGet())
                .currentLevel("BEGINNER")
                .joinedOn(LocalDate.now(clock))
                .build());
    }

    private Payment newPendingPayment(MemberProfile member, BigDecimal amount) {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paymentRepository.getNextPaymentCodeSequence()))
                .member(member)
                .amount(amount)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .build());
        paymentIds.add(payment.getId());
        return payment;
    }

    /** A registration requested three days ago, so activation must move its start date to today. */
    private Long[] pendingRegistration() {
        return tx().execute(status -> {
            MemberProfile member = newMember();
            Sport sport = sportRepository.save(SportTestData.aValidSport().build());
            SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                    .code("BE18-PKG-" + System.nanoTime())
                    .name("BE18 Swimming 30 Days")
                    .sport(sport)
                    .trainingFormat(TrainingFormat.SELF_TRAINING)
                    .durationDays(30)
                    .sessionCount(10)
                    .sessionMinutes(60)
                    .priceAmount(new BigDecimal("400000.00"))
                    .isActive(true)
                    .build());
            Payment payment = newPendingPayment(member, new BigDecimal("380000.00"));
            LocalDate requestedStart = LocalDate.now(clock).minusDays(3);
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
                    .startDate(requestedStart)
                    .endDate(requestedStart.plusDays(29))
                    .status(PackageRegistrationStatus.PENDING_PAYMENT)
                    .createdByUser(member.getUserAccount())
                    .payment(payment)
                    .build());
            sportIds.add(sport.getId());
            packageIds.add(pkg.getId());
            registrationIds.add(reg.getId());
            return new Long[]{payment.getId(), reg.getId()};
        });
    }

    private Long[] pendingCard() {
        return tx().execute(status -> {
            MemberProfile member = newMember();
            MembershipCardTier tier = tierRepository.save(MembershipCardTier.builder()
                    .code("BE18-" + System.nanoTime())
                    .name("BE18 Gold")
                    .price(new BigDecimal("1200000.00"))
                    .durationMonths(12)
                    .discountPercentage(5)
                    .tierRank(nextTierRank())
                    .isActive(true)
                    .build());
            Payment payment = newPendingPayment(member, new BigDecimal("1200000.00"));
            MemberCard card = memberCardRepository.save(MemberCard.builder()
                    .cardCode(codeFormatter.formatCardCode(memberCardRepository.getNextCardCodeSequence()))
                    .member(member)
                    .tier(tier)
                    .startDate(LocalDate.now(clock).minusDays(2))
                    .endDate(LocalDate.now(clock).minusDays(2).plusMonths(12).minusDays(1))
                    .status(CardStatus.PENDING_PAYMENT)
                    .pricePaid(new BigDecimal("1200000.00"))
                    .payment(payment)
                    .build());
            tierIds.add(tier.getId());
            cardIds.add(card.getId());
            return new Long[]{payment.getId(), card.getId()};
        });
    }

    private ConfirmPaymentRequest confirm(String amount) {
        return ConfirmPaymentRequest.builder()
                .method(PaymentMethod.BANK_TRANSFER)
                .referenceCode("FT" + System.nanoTime())
                .amountReceived(new BigDecimal(amount))
                .note("Checked bank app")
                .build();
    }

    /** Tier ranks must be unique and above 0 (ux_membership_card_tier_rank); start high to stay clear of the seed and other tests. */
    private static final AtomicInteger TIER_RANK = new AtomicInteger(5000);

    private static int nextTierRank() {
        return TIER_RANK.incrementAndGet();
    }

    /** Number of invoices of a payment, read in its own transaction. Returns int so assertThat is not ambiguous. */
    private int invoiceCount(Long paymentId) {
        Integer count = tx().execute(s -> invoiceRepository.findByPaymentId(paymentId).size());
        return count != null ? count : 0;
    }

    private int activityRows(String paymentCode, String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_log WHERE entity_type = 'PAYMENT' AND entity_code = ? AND action = ?",
                Integer.class, paymentCode, action);
        return count != null ? count : 0;
    }

    @Test
    @DisplayName("Confirm a registration: payment SUCCESS, package ACTIVE from today, one PAID invoice, activity log")
    void confirmRegistration_ExactAmount() {
        Long[] ids = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        StaffPaymentResponse response = verificationService.confirmPayment(ids[0], confirm("380000"), receptionist);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getInvoiceNumber()).startsWith("INV-");
        tx().executeWithoutResult(s -> {
            Payment payment = paymentRepository.findById(ids[0]).orElseThrow();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaymentTime()).isNotNull();
            assertThat(payment.getReferenceCode()).startsWith("FT");
            assertThat(payment.getConfirmedByUser().getId()).isEqualTo(receptionist.getId());

            SportPackageRegistration reg = registrationRepository.findById(ids[1]).orElseThrow();
            LocalDate today = LocalDate.now(clock);
            assertThat(reg.getStatus()).isEqualTo(PackageRegistrationStatus.ACTIVE);
            assertThat(reg.getStartDate()).isEqualTo(today);
            assertThat(reg.getEndDate()).isEqualTo(today.plusDays(29));

            List<Invoice> invoices = invoiceRepository.findByPaymentId(ids[0]);
            assertThat(invoices).hasSize(1);
            Invoice invoice = invoices.get(0);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo("400000");
            assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("20000");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("380000");
            List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(invoice.getId());
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getItemType()).isEqualTo(InvoiceItemType.SPORT_PACKAGE);
            assertThat(lines.get(0).getItemReferenceId()).isEqualTo(ids[1]);
            assertThat(lines.get(0).getUnitPrice()).isEqualByComparingTo("400000");
            assertThat(lines.get(0).getLineTotal()).isEqualByComparingTo("400000");
            assertThat(activityRows(payment.getPaymentCode(), "PAYMENT_CONFIRMED")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Confirm a card: card ACTIVE with dates from today, invoice line MEMBERSHIP_CARD")
    void confirmCard_ExactAmount() {
        Long[] ids = pendingCard();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        verificationService.confirmPayment(ids[0], confirm("1200000.00"), receptionist);

        tx().executeWithoutResult(s -> {
            MemberCard card = memberCardRepository.findById(ids[1]).orElseThrow();
            LocalDate today = LocalDate.now(clock);
            assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
            assertThat(card.getStartDate()).isEqualTo(today);
            assertThat(card.getEndDate()).isEqualTo(today.plusMonths(12).minusDays(1));
            Invoice invoice = invoiceRepository.findByPaymentId(ids[0]).get(0);
            assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("0");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1200000");
            assertThat(invoiceLineRepository.findByInvoiceId(invoice.getId()).get(0).getItemType())
                    .isEqualTo(InvoiceItemType.MEMBERSHIP_CARD);
        });
    }

    @Test
    @DisplayName("Wrong amount: 409, payment stays PENDING, note and one PAYMENT_AMOUNT_MISMATCH row committed, nothing else changes")
    void confirm_AmountMismatch_CommitsNoteOnly() {
        Long[] ids = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));
        LocalDate startBefore = tx().execute(s -> registrationRepository.findById(ids[1]).orElseThrow().getStartDate());

        assertThatThrownBy(() -> verificationService.confirmPayment(ids[0], confirm("350000"), receptionist))
                .isInstanceOf(PaymentAmountMismatchException.class);

        tx().executeWithoutResult(s -> {
            Payment payment = paymentRepository.findById(ids[0]).orElseThrow();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPaymentTime()).isNull();
            assertThat(payment.getReferenceCode()).isNull();
            assertThat(payment.getConfirmedByUser()).isNull();
            assertThat(payment.getNotes()).contains("Amount mismatch: received 350000");
            SportPackageRegistration reg = registrationRepository.findById(ids[1]).orElseThrow();
            assertThat(reg.getStatus()).isEqualTo(PackageRegistrationStatus.PENDING_PAYMENT);
            assertThat(reg.getStartDate()).isEqualTo(startBefore);
            assertThat(reg.getActivatedAt()).isNull();
            assertThat(invoiceRepository.findByPaymentId(ids[0])).isEmpty();
            assertThat(activityRows(payment.getPaymentCode(), "PAYMENT_AMOUNT_MISMATCH")).isEqualTo(1);
            assertThat(activityRows(payment.getPaymentCode(), "PAYMENT_CONFIRMED")).isZero();
            String details = jdbcTemplate.queryForObject(
                    "SELECT details FROM activity_log WHERE entity_type = 'PAYMENT' AND entity_code = ? AND action = ?",
                    String.class, payment.getPaymentCode(), "PAYMENT_AMOUNT_MISMATCH");
            assertThat(details).contains("\"amountReceived\":350000").contains("\"method\":\"BANK_TRANSFER\"");
        });

        // The member can still be served: the right amount confirms the same payment afterwards.
        StaffPaymentResponse response = verificationService.confirmPayment(ids[0], confirm("380000"), receptionist);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Confirming twice returns 409 and leaves one invoice")
    void confirm_Twice_Conflict() {
        Long[] ids = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));
        verificationService.confirmPayment(ids[0], confirm("380000"), receptionist);

        assertThatThrownBy(() -> verificationService.confirmPayment(ids[0], confirm("380000"), receptionist))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_NOT_PENDING"));
        assertThat(invoiceCount(ids[0])).isEqualTo(1);
    }

    @Test
    @DisplayName("A stale copy of the payment cannot be saved after another confirmation (@Version)")
    void staleVersion_IsRejected() {
        Long[] ids = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            Payment stale = paymentRepository.findById(ids[0]).orElseThrow();
            // A second receptionist confirms in their own transaction while this one still holds the old version.
            new TransactionTemplate(transactionManager, propagationRequiresNew())
                    .executeWithoutResult(inner -> verificationService.confirmPayment(ids[0], confirm("380000"), receptionist));
            stale.setNotes("Stale edit");
            paymentRepository.saveAndFlush(stale);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(invoiceCount(ids[0])).isEqualTo(1);
    }

    @Test
    @DisplayName("Two receptionists confirm at the same time: one succeeds, the other gets 409 PAYMENT_NOT_PENDING, one invoice")
    void concurrentConfirm_OnlyOneSucceeds() throws Exception {
        Long[] ids = pendingRegistration();
        UserAccount first = tx().execute(s -> newUser("RECEPTIONIST"));
        UserAccount second = tx().execute(s -> newUser("RECEPTIONIST"));

        List<Throwable> outcomes = runTogether(List.of(
                () -> verificationService.confirmPayment(ids[0], confirm("380000"), first),
                () -> verificationService.confirmPayment(ids[0], confirm("380000"), second)));

        assertOneWinnerOneNotPending(outcomes);
        assertThat(invoiceCount(ids[0])).isEqualTo(1);
    }

    @Test
    @DisplayName("Confirm and fail at the same time: one succeeds, the other gets 409 PAYMENT_NOT_PENDING")
    void concurrentConfirmAndFail_OnlyOneSucceeds() throws Exception {
        Long[] ids = pendingCard();
        UserAccount first = tx().execute(s -> newUser("RECEPTIONIST"));
        UserAccount second = tx().execute(s -> newUser("RECEPTIONIST"));

        List<Throwable> outcomes = runTogether(List.of(
                () -> verificationService.confirmPayment(ids[0], confirm("1200000"), first),
                () -> verificationService.failPayment(ids[0],
                        FailPaymentRequest.builder().reason("No transfer found").build(), second)));

        assertOneWinnerOneNotPending(outcomes);
        tx().executeWithoutResult(s -> {
            Payment payment = paymentRepository.findById(ids[0]).orElseThrow();
            MemberCard card = memberCardRepository.findById(ids[1]).orElseThrow();
            int invoices = invoiceRepository.findByPaymentId(ids[0]).size();
            if (outcomes.get(0) == null) {
                assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
                assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
                assertThat(invoices).isEqualTo(1);
            } else {
                assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
                assertThat(card.getStatus()).isEqualTo(CardStatus.CANCELLED);
                assertThat(invoices).isZero();
            }
        });
    }

    @Test
    @DisplayName("FE-05 card activate and PUT /payments/{id}/confirm at the same time: one 200, the other 400 or 409, never 500, one invoice")
    void concurrentActivateEndpointAndConfirm_NoDeadlock() throws Exception {
        Long[] ids = pendingCard();
        UserAccount first = tx().execute(s -> newUser("RECEPTIONIST"));
        UserAccount second = tx().execute(s -> newUser("RECEPTIONIST"));

        List<MvcResult> results = performTogether(List.of(
                put("/api/v1/membership-cards/" + ids[1] + "/activate")
                        .with(user(new CustomUserDetails(first)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"),
                put("/api/v1/payments/" + ids[0] + "/confirm")
                        .with(user(new CustomUserDetails(second)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CASH\",\"amountReceived\":1200000}")));

        assertOneOkOneBusinessError(results);
        tx().executeWithoutResult(s -> {
            assertThat(paymentRepository.findById(ids[0]).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(memberCardRepository.findById(ids[1]).orElseThrow().getStatus()).isEqualTo(CardStatus.ACTIVE);
            assertThat(invoiceRepository.findByPaymentId(ids[0])).hasSize(1);
            String code = paymentRepository.findById(ids[0]).orElseThrow().getPaymentCode();
            assertThat(activityRows(code, "PAYMENT_CONFIRMED")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Member cancels a card request while a receptionist confirms it: one wins, never 500, states consistent")
    void concurrentMemberCancelAndConfirm_ConsistentOutcome() throws Exception {
        Long[] ids = pendingCard();
        UserAccount owner = cardOwner(ids[1]);
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        List<MvcResult> results = performTogether(List.of(
                put("/api/v1/membership-cards/" + ids[1] + "/cancel")
                        .with(user(new CustomUserDetails(owner))),
                put("/api/v1/payments/" + ids[0] + "/confirm")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CASH\",\"amountReceived\":1200000}")));

        assertOneOkOneBusinessError(results);
        boolean cancelWon = results.get(0).getResponse().getStatus() == 200;
        tx().executeWithoutResult(s -> {
            Payment payment = paymentRepository.findById(ids[0]).orElseThrow();
            MemberCard card = memberCardRepository.findById(ids[1]).orElseThrow();
            int invoices = invoiceRepository.findByPaymentId(ids[0]).size();
            if (cancelWon) {
                assertThat(card.getStatus()).isEqualTo(CardStatus.CANCELLED);
                assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
                assertThat(invoices).isZero();
            } else {
                assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
                assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
                assertThat(invoices).isEqualTo(1);
            }
        });
    }

    @Test
    @DisplayName("PUT /membership-cards/{id}/activate (FE-05) issues one invoice and one PAYMENT_CONFIRMED row")
    void activateCardEndpoint_IssuesReceipt() throws Exception {
        Long[] ids = pendingCard();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        mockMvc.perform(put("/api/v1/membership-cards/" + ids[1] + "/activate")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertReceiptIssued(ids[0], InvoiceItemType.MEMBERSHIP_CARD, ids[1], "1200000");
    }

    @Test
    @DisplayName("PUT /packages/registrations/{id}/activate (FE-05) issues one invoice and one PAYMENT_CONFIRMED row")
    void activateRegistrationEndpoint_IssuesReceipt() throws Exception {
        Long[] ids = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        mockMvc.perform(put("/api/v1/packages/registrations/" + ids[1] + "/activate")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertReceiptIssued(ids[0], InvoiceItemType.SPORT_PACKAGE, ids[1], "380000");
        mockMvc.perform(get("/api/v1/invoices/by-payment/" + ids[0])
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    @DisplayName("Fail cancels the card request, payment FAILED with the reason, activity log written")
    void failCard_CancelsRequest() {
        Long[] ids = pendingCard();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        StaffPaymentResponse response = verificationService.failPayment(ids[0],
                FailPaymentRequest.builder().reason("No transfer found").build(), receptionist);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        tx().executeWithoutResult(s -> {
            Payment payment = paymentRepository.findById(ids[0]).orElseThrow();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getNotes()).endsWith("No transfer found");
            assertThat(memberCardRepository.findById(ids[1]).orElseThrow().getStatus()).isEqualTo(CardStatus.CANCELLED);
            assertThat(activityRows(payment.getPaymentCode(), "PAYMENT_FAILED")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Fail a registration: the receptionist's reason is stored as the registration cancel reason")
    void failRegistration_StoresReason() {
        Long[] ids = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));

        verificationService.failPayment(ids[0], FailPaymentRequest.builder().reason("No transfer found").build(), receptionist);

        tx().executeWithoutResult(s -> {
            SportPackageRegistration reg = registrationRepository.findById(ids[1]).orElseThrow();
            assertThat(reg.getStatus()).isEqualTo(PackageRegistrationStatus.CANCELLED);
            assertThat(reg.getCancelReason()).isEqualTo("No transfer found");
            assertThat(paymentRepository.findById(ids[0]).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        });
    }

    @Test
    @DisplayName("The list shows PENDING payments before confirmed ones and finds a member by code")
    void list_PendingFirst() {
        Long[] confirmed = pendingRegistration();
        Long[] pending = pendingRegistration();
        UserAccount receptionist = tx().execute(s -> newUser("RECEPTIONIST"));
        verificationService.confirmPayment(confirmed[0], confirm("380000"), receptionist);

        List<StaffPaymentResponse> rows = verificationService
                .listPayments(null, null, null, null, null, 0, 100).content();
        int firstNonPending = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getStatus() != PaymentStatus.PENDING) {
                firstNonPending = i;
                break;
            }
        }
        if (firstNonPending >= 0) {
            assertThat(rows.subList(firstNonPending, rows.size()))
                    .allMatch(r -> r.getStatus() != PaymentStatus.PENDING);
        }

        String memberCode = tx().execute(s -> paymentRepository.findById(pending[0]).orElseThrow().getMember().getMemberCode());
        List<StaffPaymentResponse> found = verificationService
                .listPayments(PaymentStatus.PENDING, null, memberCode, LocalDate.now(clock).minusDays(1),
                        LocalDate.now(clock), 0, 20).content();
        assertThat(found).extracting(StaffPaymentResponse::getId).contains(pending[0]);
        assertThat(found).allMatch(r -> r.getStatus() == PaymentStatus.PENDING);
        assertThat(found.get(0).getMemberFullName()).isEqualTo("BE18 MEMBER");
        assertThat(found.get(0).getItemType()).isEqualTo(InvoiceItemType.SPORT_PACKAGE);
    }

    private void assertReceiptIssued(Long paymentId, InvoiceItemType type, Long itemId, String total) {
        tx().executeWithoutResult(s -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
            List<Invoice> invoices = invoiceRepository.findByPaymentId(paymentId);
            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoices.get(0).getTotalAmount()).isEqualByComparingTo(total);
            List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(invoices.get(0).getId());
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getItemType()).isEqualTo(type);
            assertThat(lines.get(0).getItemReferenceId()).isEqualTo(itemId);
            assertThat(activityRows(payment.getPaymentCode(), "PAYMENT_CONFIRMED")).isEqualTo(1);
        });
    }

    /** The member who owns a card, with the role loaded so it can be used outside a transaction. */
    private UserAccount cardOwner(Long cardId) {
        return tx().execute(s -> {
            Long memberId = memberCardRepository.findById(cardId).orElseThrow().getMember().getId();
            UserAccount account = userRepository.findById(memberId).orElseThrow();
            Hibernate.initialize(account.getRole());
            return account;
        });
    }

    /** Sends every request at the same moment and returns the results in the same order. */
    private List<MvcResult> performTogether(List<MockHttpServletRequestBuilder> requests) throws Exception {
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (MockHttpServletRequestBuilder request : requests) {
            calls.add(() -> mockMvc.perform(request).andReturn());
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Exactly one 200; the other is 400 or 409 with a business message. A 500 (for example a deadlock victim) fails. */
    private static void assertOneOkOneBusinessError(List<MvcResult> results) throws Exception {
        List<Integer> statuses = results.stream().<Integer>map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
        MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() != 200).findFirst().orElseThrow();
        assertThat(loser.getResponse().getStatus()).isIn(400, 409);
        assertThat(loser.getResponse().getContentAsString()).contains("\"message\"");
        assertThat(loser.getResponse().getContentAsString()).doesNotContain("Internal server error");
    }

    /** Starts every call at the same moment; the result holds null for a call that returned, else its exception. */
    private List<Throwable> runTogether(List<Callable<?>> calls) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    outcomes.add(null);
                } catch (ExecutionException ex) {
                    outcomes.add(ex.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Lock order rule: every path takes the payment row lock before it reads or writes the card or registration.
     * The only read before that lock is a single-value query for the payment id, whose shared lock is released at
     * once, so a waiting caller holds no lock the winner needs and the calls cannot deadlock. The second caller
     * waits for the first to commit and then reads a payment that is no longer PENDING.
     */
    private static void assertOneWinnerOneNotPending(List<Throwable> outcomes) {
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes).filteredOn(o -> o == null).hasSize(1);
        Throwable loser = outcomes.stream().filter(o -> o != null).findFirst().orElseThrow();
        assertThat(loser).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) loser).getCode()).isEqualTo("PAYMENT_NOT_PENDING");
    }

    private static TransactionDefinition propagationRequiresNew() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return definition;
    }

    // ---------- BE-19: payment at the desk (F1-13, F1-14) ----------

    /** An active 400.000 package of a new sport, tracked for the cleanup. */
    private Long newPackage() {
        return tx().execute(st -> {
            Sport sport = sportRepository.save(SportTestData.aValidSport().build());
            SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                    .code("BE19-PKG-" + System.nanoTime())
                    .name("BE19 Swimming 30 Days")
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

    /** An active 1.200.000 ranked tier, tracked for the cleanup. */
    private Long newTier() {
        return tx().execute(st -> {
            MembershipCardTier tier = tierRepository.save(MembershipCardTier.builder()
                    .code("BE19-" + System.nanoTime())
                    .name("BE19 Gold")
                    .price(new BigDecimal("1200000.00"))
                    .durationMonths(12)
                    .discountPercentage(5)
                    .tierRank(nextTierRank())
                    .isActive(true)
                    .build());
            tierIds.add(tier.getId());
            return tier.getId();
        });
    }

    private static long longAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    /** Invoice totals: the sum of the lines minus the discount plus the tax equals the total. */
    private void assertInvoiceTotalsAddUp(Long invoiceId, String expectedTotal) {
        tx().executeWithoutResult(st -> {
            Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
            BigDecimal lines = invoiceLineRepository.findByInvoiceId(invoiceId).stream()
                    .map(InvoiceLine::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo(lines);
            assertThat(lines.subtract(invoice.getDiscountAmount()).add(invoice.getTaxAmount()))
                    .isEqualByComparingTo(invoice.getTotalAmount());
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        });
    }

    @Test
    @DisplayName("BE-19: desk package sale by transfer records a SUCCESS payment, activates it and returns the receipt")
    void deskPackageSale_ByTransfer_RecordsPaymentAndReceipt() throws Exception {
        MemberProfile member = tx().execute(st -> newMember());
        Long packageId = newPackage();
        UserAccount receptionist = tx().execute(st -> newUser("RECEPTIONIST"));

        String body = mockMvc.perform(post("/api/v1/packages/registrations")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"packageId\":" + packageId
                                + ",\"channel\":\"RECEPTION\",\"paymentMethod\":\"BANK_TRANSFER\""
                                + ",\"paymentReference\":\"FT-BE19-1\",\"amountReceived\":400000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.channel").value("RECEPTION"))
                .andReturn().getResponse().getContentAsString();
        long registrationId = longAt(body, "$.id");
        long paymentId = longAt(body, "$.paymentId");
        long invoiceId = longAt(body, "$.invoiceId");
        registrationIds.add(registrationId);
        paymentIds.add(paymentId);
        assertThat((String) JsonPath.read(body, "$.invoiceNumber")).startsWith("INV-");

        tx().executeWithoutResult(st -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(payment.getReferenceCode()).isEqualTo("FT-BE19-1");
            assertThat(payment.getAmount()).isEqualByComparingTo("400000");
            assertThat(payment.getPaymentTime()).isNotNull();
            assertThat(payment.getConfirmedByUser().getId()).isEqualTo(receptionist.getId());
            assertThat(registrationRepository.findById(registrationId).orElseThrow().getPayment().getId()).isEqualTo(paymentId);
            assertThat(invoiceRepository.findByPaymentId(paymentId)).extracting(Invoice::getId).containsExactly(invoiceId);
            assertThat(activityRows(payment.getPaymentCode(), "PAYMENT_CONFIRMED")).isEqualTo(1);
        });
        assertInvoiceTotalsAddUp(invoiceId, "400000");
    }

    @Test
    @DisplayName("BE-19: desk card sale in cash with more money keeps the change in the notes and returns the receipt")
    void deskCardSale_CashWithChange_RecordsPaymentAndReceipt() throws Exception {
        MemberProfile member = tx().execute(st -> newMember());
        Long tierId = newTier();
        UserAccount receptionist = tx().execute(st -> newUser("RECEPTIONIST"));

        String body = mockMvc.perform(post("/api/v1/membership-cards/purchase")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"tierId\":" + tierId
                                + ",\"method\":\"CASH\",\"amountReceived\":1500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        cardIds.add(longAt(body, "$.id"));
        long paymentId = longAt(body, "$.paymentId");
        long invoiceId = longAt(body, "$.invoiceId");
        paymentIds.add(paymentId);

        tx().executeWithoutResult(st -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(payment.getAmount()).isEqualByComparingTo("1200000");
            assertThat(payment.getNotes()).contains("Cash received 1500000, change 300000.00");
        });
        assertInvoiceTotalsAddUp(invoiceId, "1200000");
    }

    @Test
    @DisplayName("BE-19: desk sale with less cash than the price answers 409 and creates nothing")
    void deskSale_ShortCash_ConflictAndNothingCreated() throws Exception {
        MemberProfile member = tx().execute(st -> newMember());
        Long packageId = newPackage();
        UserAccount receptionist = tx().execute(st -> newUser("RECEPTIONIST"));

        mockMvc.perform(post("/api/v1/packages/registrations")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"packageId\":" + packageId
                                + ",\"channel\":\"RECEPTION\",\"paymentMethod\":\"CASH\",\"amountReceived\":300000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        Integer registrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ?", Integer.class, member.getId());
        Integer payments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE member_id = ?", Integer.class, member.getId());
        assertThat(registrations).isZero();
        assertThat(payments).isZero();
    }

    @Test
    @DisplayName("BE-19: a member reads their own payment and invoice; another member gets 403 for both")
    void memberSeesOnlyOwnPaymentAndInvoice() throws Exception {
        MemberProfile owner = tx().execute(st -> newMember());
        MemberProfile other = tx().execute(st -> newMember());
        Long packageId = newPackage();
        UserAccount receptionist = tx().execute(st -> newUser("RECEPTIONIST"));

        String body = mockMvc.perform(post("/api/v1/packages/registrations")
                        .with(user(new CustomUserDetails(receptionist)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + owner.getId() + ",\"packageId\":" + packageId
                                + ",\"channel\":\"RECEPTION\",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        registrationIds.add(longAt(body, "$.id"));
        long paymentId = longAt(body, "$.paymentId");
        paymentIds.add(paymentId);

        CustomUserDetails ownerLogin = new CustomUserDetails(owner.getUserAccount());
        CustomUserDetails otherLogin = new CustomUserDetails(other.getUserAccount());
        mockMvc.perform(get("/api/v1/payments/" + paymentId).with(user(ownerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        mockMvc.perform(get("/api/v1/invoices/by-payment/" + paymentId).with(user(ownerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));
        mockMvc.perform(get("/api/v1/payments/" + paymentId).with(user(otherLogin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/invoices/by-payment/" + paymentId).with(user(otherLogin)))
                .andExpect(status().isForbidden());
    }
}
