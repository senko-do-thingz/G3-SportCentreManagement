package com.sportify.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.*;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.entity.*;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.*;
import com.sportify.payment.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MSSQLServerContainer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own SQL Server lifecycle: all completions and monetary fixtures are synthetic and container-confined. */
@Tag("integration")
@SpringBootTest(properties = "app.finance.cash-capture-enabled=true")
@ActiveProfiles({"dev", "test"}) @AutoConfigureMockMvc
@Import(CashEventIntegrationTest.TimeConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CashEventIntegrationTest {
    static class TestClock extends Clock {
        volatile Instant value = Instant.parse("2025-12-15T03:00:00Z");
        void set(String value) { this.value = Instant.parse(value); }
        public Instant instant() { return value; }
        public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary TestClock cashTestClock() { return new TestClock(); }
        @Bean(initMethod = "start", destroyMethod = "stop")
        MSSQLServerContainer<?> cashSqlServer() {
            return new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense().withPassword("Aa1!" + UUID.randomUUID());
        }
        // Spring initializes this dependency before any datasource property can resolve.
        @Bean DynamicPropertyRegistrar cashDatabaseProperties(MSSQLServerContainer<?> sql) {
            return registry -> {
                registry.add("spring.datasource.url", () -> sql.getJdbcUrl() + ";encrypt=false;trustServerCertificate=true");
                registry.add("spring.datasource.username", sql::getUsername);
                registry.add("spring.datasource.password", sql::getPassword);
            };
        }
    }
    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PaymentService paymentService;
    @Autowired PaymentVerificationService verification;
    @Autowired CashFlowReportService reports;
    @Autowired RefundService refunds;
    @Autowired SportPackageService packages;
    @Autowired MembershipCardService cards;
    @Autowired PaymentRepository payments;
    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packageRepository;
    @Autowired SportPackageRegistrationRepository registrations;
    @Autowired MemberProfileRepository members;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UserAccount manager, legacyMember;
    Long[] legacy;
    TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    static String code() { return "CE" + UUID.randomUUID().toString().replace("-", "").substring(0,18); }
    UserAccount member() {
        return tx().execute(s -> {
            UserAccount actor = users.saveAndFlush(UserAccount.builder().email(code() + "@example.invalid")
                    .passwordHash(UUID.randomUUID().toString()).fullName("Synthetic cash member")
                    .role(roles.findByCode("MEMBER").orElseThrow()).status("ACTIVE").build());
            members.saveAndFlush(MemberProfile.builder().userAccount(actor).memberCode(code()).currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now(clock)).build());
            return actor;
        });
    }
    Long[] pending(UserAccount actor, String amount) {
        return tx().execute(s -> {
            var sport = sports.save(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).build());
            var pack = packageRepository.save(SportPackage.builder().code(code()).name("Synthetic package").sport(sport)
                    .trainingFormat(TrainingFormat.SELF_TRAINING).durationDays(30).sessionCount(10).sessionMinutes(60)
                    .priceAmount(new BigDecimal(amount)).isActive(true).build());
            var payment = paymentService.createPendingPayment(members.findById(actor.getId()).orElseThrow(), new BigDecimal(amount), PaymentMethod.BANK_TRANSFER);
            var registration = registrations.saveAndFlush(SportPackageRegistration.builder().registrationCode(code())
                    .member(members.findById(actor.getId()).orElseThrow()).sportPackage(pack).channel(RegistrationChannel.ONLINE)
                    .originalPrice(new BigDecimal(amount)).discountPercentage(0).paidAmount(new BigDecimal(amount))
                    .totalSessions(10).remainingSessions(10).startDate(LocalDate.now(clock)).endDate(LocalDate.now(clock).plusDays(29))
                    .status(PackageRegistrationStatus.PENDING_PAYMENT).createdByUser(actor).payment(payment).build());
            return new Long[]{payment.getId(), registration.getId(), pack.getId()};
        });
    }
    void confirm(Long[] ids, String amount) {
        verification.confirmPayment(ids[0], ConfirmPaymentRequest.builder().method(PaymentMethod.BANK_TRANSFER)
                .referenceCode(code()).amountReceived(new BigDecimal(amount)).build(), manager);
    }
    long approve(Long[] ids, UserAccount owner, String amount) {
        var request = refunds.submitRefund(RefundCreateRequest.builder().packageRegistrationId(ids[1])
                .amountRequested(new BigDecimal(amount)).reason("Synthetic refund").build(), owner);
        refunds.reviewRefund(request.getId(), RefundReviewRequest.builder().approved(true).amountApproved(new BigDecimal(amount)).build(), manager);
        return request.getId();
    }
    long events(long payment) { return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event WHERE payment_id = ?", Long.class, payment); }

    @BeforeAll void initializeCutoverAndLegacyFixture() {
        manager = tx().execute(s -> users.saveAndFlush(UserAccount.builder().email(code() + "@example.invalid")
                .passwordHash(UUID.randomUUID().toString()).fullName("Synthetic manager")
                .role(roles.findByCode("MANAGER").orElseThrow()).status("ACTIVE").build()));
        legacyMember = member(); legacy = pending(legacyMember, "300.00");
        jdbc.update("UPDATE dbo.payment SET payment_status='SUCCESS', payment_time='2025-12-15T10:00:00' WHERE id=?", legacy[0]);
        jdbc.update("UPDATE dbo.sport_package_registration SET status='ACTIVE' WHERE id=?", legacy[1]);
        assertThat(events(legacy[0])).isZero();
        clock.set("2026-01-01T00:00:00Z");
        tx().execute(s -> paymentService.recordDeskPayment(members.findById(legacyMember.getId()).orElseThrow(),
                new BigDecimal("1.00"), PaymentMethod.CASH, null, manager));
        assertThat(jdbc.queryForObject("SELECT cutover_utc FROM dbo.cash_coverage", java.sql.Timestamp.class).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026,1,1,0,0));
    }

    @Test void marchAprilActualSqlRestGroupsAndCsvAgreeAfterPartialRefund() throws Exception {
        clock.set("2026-03-15T03:00:00Z");
        UserAccount owner = member(); Long[] ids = pending(owner, "500000.00"); confirm(ids, "500000.00");
        var original = jdbc.queryForMap("SELECT * FROM dbo.cash_event WHERE payment_id=?", ids[0]);
        long refund = approve(ids, owner, "100000.00");
        assertThat(events(ids[0])).isEqualTo(1);
        clock.set("2026-04-02T03:00:00Z"); refunds.completeRefund(refund, manager);
        assertThat(jdbc.queryForMap("SELECT * FROM dbo.cash_event WHERE id=?", original.get("id"))).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM dbo.payment WHERE id=?", String.class, ids[0])).isEqualTo("REFUNDED");
        assertThat(reports.overview("2026-03").grossCashReceived()).isEqualByComparingTo("500000.00");
        assertThat(reports.overview("2026-04").netCashFlow()).isEqualByComparingTo("-100000.00");
        String url = "/api/v1/manager/finance/cash-flow/";
        var rest = mvc.perform(get(url + "overview?month=2026-04").with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(rest.getContentAsString()).get("netCashFlow").decimalValue()).isEqualByComparingTo("-100000.00");
        var authoritative = jdbc.queryForMap("""
                SELECT SUM(CASE WHEN event_type='COLLECTION' THEN amount ELSE 0 END) gross,
                    SUM(CASE WHEN event_type='REFUND_PAYOUT' THEN amount ELSE 0 END) payout,
                    SUM(CASE WHEN event_type='COLLECTION' THEN amount ELSE -amount END) net
                FROM dbo.cash_event WHERE event_time_utc >= '2026-02-28T17:00:00' AND event_time_utc < '2026-04-30T17:00:00'
                """);
        assertThat((BigDecimal) authoritative.get("net")).isEqualByComparingTo("400000.00");
        for (var by : RevenueGroupBy.values()) {
            var group = reports.breakdown(LocalDate.of(2026,3,1), LocalDate.of(2026,4,30), by);
            assertThat(group.grossCashReceived()).isEqualByComparingTo((BigDecimal) authoritative.get("gross"));
            assertThat(group.completedRefundCashOut()).isEqualByComparingTo((BigDecimal) authoritative.get("payout"));
            assertThat(group.rows().stream().map(r -> r.netCashFlow()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("400000.00");
            assertThat(group.collectionCount()).isEqualTo(1); assertThat(group.completedRefundCount()).isEqualTo(1);
        }
        String csv = mvc.perform(get(url + "export?from=2026-03-01&to=2026-04-30").with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var rows = csv.lines().skip(1).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(row -> new BigDecimal(row.split(",")[6])).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("400000.00");
        assertThat(rows.getFirst()).contains("COLLECTION"); assertThat(rows.getLast()).contains("REFUND_PAYOUT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.cash_capture_ticket", Integer.class)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"100.25", "500.50"})
    void fullAndPartialCompletionLeaveCollectionAndRejectSecondRefund(String approved) {
        clock.set("2027-01-10T00:00:00Z"); UserAccount owner = member(); Long[] ids = pending(owner, "500.50"); confirm(ids, "500.50");
        long refund = approve(ids, owner, approved); refunds.completeRefund(refund, manager);
        assertThat(events(ids[0])).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount FROM dbo.cash_event WHERE event_type='REFUND_PAYOUT' AND refund_request_id=?", BigDecimal.class, refund))
                .isEqualByComparingTo(approved);
        assertThatThrownBy(() -> refunds.completeRefund(refund, manager)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> refunds.submitRefund(RefundCreateRequest.builder().packageRegistrationId(ids[1]).reason("Second").build(), owner))
                .isInstanceOf(RuntimeException.class);
        assertThat(events(ids[0])).isEqualTo(2);
    }
    @Test void rejectedAndPendingRefundsNeverCreatePayout() {
        clock.set("2027-02-01T00:00:00Z"); UserAccount owner = member(); Long[] ids = pending(owner, "100.25"); confirm(ids, "100.25");
        var request = refunds.submitRefund(RefundCreateRequest.builder().packageRegistrationId(ids[1]).reason("Synthetic").build(), owner);
        assertThat(events(ids[0])).isEqualTo(1);
        refunds.reviewRefund(request.getId(), RefundReviewRequest.builder().approved(false).build(), manager);
        assertThat(events(ids[0])).isEqualTo(1);
    }
    @Test void postCutoverLegacyPayoutDoesNotInventCollection() {
        clock.set("2026-05-02T03:00:00Z"); long refund = approve(legacy, legacyMember, "100.00"); refunds.completeRefund(refund, manager);
        assertThat(events(legacy[0])).isEqualTo(1);
        var event = jdbc.queryForMap("SELECT * FROM dbo.cash_event WHERE refund_request_id=?", refund);
        assertThat(event.get("original_method")).isNull(); assertThat(event.get("original_item_type")).isNull();
        assertThat(reports.overview("2026-05").netCashFlow()).isEqualByComparingTo("-100.00");
        assertThat(reports.breakdown(LocalDate.of(2026,5,1), LocalDate.of(2026,5,31), RevenueGroupBy.METHOD).rows().getFirst().key()).isEqualTo("UNALLOCATED");
    }
    @Test void absentOrPartialHistoricalCoverageIs409AndCoveredEmptyMonthIsZero() throws Exception {
        mvc.perform(get("/api/v1/manager/finance/cash-flow/overview?month=2025-12").with(user(new CustomUserDetails(manager))))
                .andExpect(status().isConflict());
        // January begins at 2025-12-31T17:00Z, earlier than the Jan 1 UTC cutover.
        assertThatThrownBy(() -> reports.overview("2026-01")).isInstanceOf(com.sportify.core.exception.ConflictException.class);
        assertThat(reports.overview("2026-02").netCashFlow()).isEqualByComparingTo("0.00");
    }
    @Test void oldWriterAndManualLedgerInsertionAreRejectedAfterCutover() {
        Long[] ids = pending(member(), "10.25");
        assertThatThrownBy(() -> jdbc.update("UPDATE dbo.payment SET payment_status='SUCCESS', payment_time=SYSDATETIME() WHERE id=?", ids[0]))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(events(ids[0])).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dbo.cash_event(event_type,payment_id,amount,event_time_utc,provenance) VALUES('COLLECTION',?,1,SYSUTCDATETIME(),'LIVE_SERVICE')", ids[0]))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void eventAndCutoverFactsAreImmutable() {
        assertThatThrownBy(() -> jdbc.update("UPDATE dbo.cash_event SET amount=amount+1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM dbo.cash_event")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE dbo.cash_coverage SET cutover_utc=SYSUTCDATETIME()")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void sourceRollbackLeavesNoEventOrTicket() {
        clock.set("2027-03-01T00:00:00Z"); Long[] ids = pending(member(), "23.45");
        assertThatThrownBy(() -> tx().execute(s -> { paymentService.markPaymentSuccess(ids[0], manager); payments.flush(); throw new IllegalStateException("Synthetic source failure"); }))
                .hasMessageContaining("Synthetic source failure");
        assertThat(events(ids[0])).isZero();
        assertThat(jdbc.queryForObject("SELECT payment_status FROM dbo.payment WHERE id=?", String.class, ids[0])).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.cash_capture_ticket", Integer.class)).isZero();
    }
    @Test void eventInsertFailureRollsBackRefundAndPayment() {
        clock.set("2027-04-01T00:00:00Z"); UserAccount owner = member(); Long[] ids = pending(owner, "50.25"); confirm(ids, "50.25"); long refund = approve(ids, owner, "10.25");
        jdbc.execute("CREATE TRIGGER dbo.tr_cash_test_failure ON dbo.cash_event AFTER INSERT AS BEGIN SET NOCOUNT ON; THROW 51032, 'Synthetic ledger failure', 1; END");
        try {
            assertThatThrownBy(() -> refunds.completeRefund(refund, manager)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM dbo.refund_request WHERE id=?", String.class, refund)).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("SELECT payment_status FROM dbo.payment WHERE id=?", String.class, ids[0])).isEqualTo("SUCCESS");
            assertThat(events(ids[0])).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER dbo.tr_cash_test_failure"); }
    }
    @Test void concurrentCompletionProducesOnePayoutAndOneConflict() throws Exception {
        clock.set("2027-05-01T00:00:00Z"); UserAccount owner = member(); Long[] ids = pending(owner, "100.25"); confirm(ids, "100.25"); long refund = approve(ids, owner, "10.25");
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> { ready.countDown(); if (!go.await(20, TimeUnit.SECONDS)) throw new AssertionError("Start gate");
                try { refunds.completeRefund(refund, manager); return true; } catch (BusinessRuleException expected) { return false; } };
            var a = pool.submit(action); var b = pool.submit(action); assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(events(ids[0])).isEqualTo(2);
    }
    @Test void allCardPackageDeskAndActivationPathsProduceOneCollection() {
        clock.set("2027-06-01T00:00:00Z");
        var seed = pending(member(), "100.25");
        UserAccount packageOwner = member();
        var online = packages.registerPackage(PackageRegistrationRequest.builder().packageId(seed[2]).channel(RegistrationChannel.ONLINE).build(), packageOwner);
        packages.activateRegistration(online.getId(), manager, PaymentMethod.CASH);
        assertThat(events(online.getPaymentId())).isEqualTo(1);
        var desk = packages.registerPackage(PackageRegistrationRequest.builder().packageId(seed[2]).memberId(member().getId())
                .channel(RegistrationChannel.RECEPTION).paymentMethod(PaymentMethod.CASH).build(), manager);
        assertThat(events(desk.getPaymentId())).isEqualTo(1);
        long tier = jdbc.queryForObject("SELECT TOP 1 id FROM membership_card_tier WHERE price > 0 AND is_active=1 ORDER BY tier_rank", Long.class);
        UserAccount cardOwner = member(); var card = cards.purchaseCard(MemberCardPurchaseRequest.builder().tierId(tier).build(), cardOwner);
        cards.activateCard(card.getId(), manager, PaymentMethod.CASH); assertThat(events(card.getPaymentId())).isEqualTo(1);
        var cardDesk = cards.purchaseCard(MemberCardPurchaseRequest.builder().tierId(tier).memberId(member().getId()).paymentMethod(PaymentMethod.CASH).build(), manager);
        assertThat(events(cardDesk.getPaymentId())).isEqualTo(1);
        UserAccount confirmOwner = member(); var cardConfirm = cards.purchaseCard(MemberCardPurchaseRequest.builder().tierId(tier).build(), confirmOwner);
        verification.confirmPayment(cardConfirm.getPaymentId(), ConfirmPaymentRequest.builder().method(PaymentMethod.CASH).amountReceived(cardConfirm.getPricePaid()).build(), manager);
        assertThat(events(cardConfirm.getPaymentId())).isEqualTo(1);
    }
    @Test void nullLinkedLegacyCompletionIsCapturedAsUnallocatedWithoutInventedPayment() {
        clock.set("2027-12-01T00:00:00Z"); UserAccount owner=member(); Long[] ids=pending(owner,"100.25");
        jdbc.update("UPDATE dbo.sport_package_registration SET payment_id=NULL,status='ACTIVE' WHERE id=?",ids[1]);
        String code=code();
        jdbc.update("""
                INSERT INTO dbo.refund_request(refund_code,package_registration_id,member_id,amount_requested,amount_approved,reason,status,requested_by)
                VALUES(?,?,?,100.25,10.25,'Synthetic legacy completion','APPROVED',?)
                """,code,ids[1],owner.getId(),owner.getId());
        long refund=jdbc.queryForObject("SELECT id FROM dbo.refund_request WHERE refund_code=?",Long.class,code);
        refunds.completeRefund(refund,manager);
        var event=jdbc.queryForMap("SELECT * FROM dbo.cash_event WHERE refund_request_id=?",refund);
        assertThat(event.get("payment_id")).isNull(); assertThat(event.get("original_method")).isNull();
        assertThat((BigDecimal)event.get("amount")).isEqualByComparingTo("10.25");
        assertThat(events(ids[0])).isZero();
    }
    @Test void mixedInvoiceAgainstLinkedRegistrationIsNotAllocatedToFirstItem() {
        clock.set("2027-12-15T00:00:00Z"); UserAccount owner=member(); Long[] ids=pending(owner,"100.25");
        String invoice=code();
        jdbc.update("INSERT INTO dbo.invoice(invoice_number,payment_id,member_id,subtotal_amount,total_amount,status) VALUES(?,?,?,100.25,100.25,'PAID')",invoice,ids[0],owner.getId());
        long invoiceId=jdbc.queryForObject("SELECT id FROM dbo.invoice WHERE invoice_number=?",Long.class,invoice);
        jdbc.update("INSERT INTO dbo.invoice_line(invoice_id,item_type,item_reference_id,description,unit_price,line_total) VALUES(?,'PENALTY_FEE',1,'Synthetic mixed source',100.25,100.25)",invoiceId);
        tx().execute(s -> paymentService.markPaymentSuccess(ids[0],manager,PaymentMethod.CASH));
        var event=jdbc.queryForMap("SELECT * FROM dbo.cash_event WHERE payment_id=?",ids[0]);
        assertThat(event.get("original_method")).isEqualTo("CASH");
        assertThat(event.get("original_item_type")).isNull(); assertThat(event.get("original_sport_id")).isNull();
        assertThat(events(ids[0])).isEqualTo(1);
    }
}
