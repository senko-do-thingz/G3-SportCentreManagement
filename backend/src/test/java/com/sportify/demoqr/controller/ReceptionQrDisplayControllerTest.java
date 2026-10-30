package com.sportify.demoqr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.demoqr.service.DemoQrService;
import com.sportify.demoqr.service.SqlQrStore;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.impl.PaymentItemResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceptionQrDisplayController.class)
@ActiveProfiles("qr-simulator")
@TestPropertySource(properties = {
        "app.payment.simulator.enabled=true",
        "app.payment.simulator.public-base-url=https://demo.example.test/",
        "app.payment.simulator.allowed-origins=https://demo.example.test"
})
@Import({
        DemoQrAdvice.class,
        SecurityConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
class ReceptionQrDisplayControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentItemResolver itemResolver;

    @MockitoBean
    private SqlQrStore store;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private UserAccount receptionist;
    private UserAccount manager;
    private UserAccount member;
    private UserAccount coach;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        Role receptionistRole = Role.builder().id(2L).code("RECEPTIONIST").name("RECEPTIONIST").build();
        receptionist = UserAccount.builder()
                .id(10L).email("receptionist@sportify.demo").role(receptionistRole).status("ACTIVE").build();

        Role managerRole = Role.builder().id(3L).code("MANAGER").name("MANAGER").build();
        manager = UserAccount.builder()
                .id(20L).email("manager@sportify.demo").role(managerRole).status("ACTIVE").build();

        Role memberRole = Role.builder().id(1L).code("MEMBER").name("MEMBER").build();
        member = UserAccount.builder()
                .id(30L).email("member@sportify.demo").role(memberRole).status("ACTIVE").build();

        Role coachRole = Role.builder().id(4L).code("COACH").name("COACH").build();
        coach = UserAccount.builder()
                .id(40L).email("coach@sportify.demo").role(coachRole).status("ACTIVE").build();

        MemberProfile profile = MemberProfile.builder()
                .userAccount(member)
                .memberCode("MEM-030")
                .build();

        samplePayment = Payment.builder()
                .id(101L)
                .paymentCode("PAY-101")
                .amount(new BigDecimal("810000.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .member(profile)
                .build();
    }

    @Test
    @DisplayName("Receptionist can get desk projection for pending payment with active session")
    void receptionistCanGetDeskProjection() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-30T14:30:00Z");
        Instant expires = now.plusSeconds(600);
        SqlQrStore.Session session = new SqlQrStore.Session(
                sessionId, 101L, 30L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.BIDV_DEMO, now.minusSeconds(30), expires,
                DemoQrService.State.PENDING, 0L
        );

        when(paymentRepository.findById(101L)).thenReturn(Optional.of(samplePayment));
        when(itemResolver.summarize(samplePayment)).thenReturn(
                new PaymentItemResolver.PaymentItemSummary("Gold Membership (1 Month)", null, null));
        when(store.findLatestByPayment(101L)).thenReturn(Optional.of(session));
        when(store.now()).thenReturn(now);

        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(101))
                .andExpect(jsonPath("$.paymentCode").value("PAY-101"))
                .andExpect(jsonPath("$.amount").value(810000.00))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.itemDescription").value("Gold Membership (1 Month)"))
                .andExpect(jsonPath("$.memberCode").value("MEM-030"))
                .andExpect(jsonPath("$.activeSession.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.activeSession.bankCode").value("BIDV_DEMO"))
                .andExpect(jsonPath("$.activeSession.bankLabel").value("BIDV (demo)"))
                .andExpect(jsonPath("$.activeSession.state").value("PENDING"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.confirmationUrl").doesNotExist());
    }

    @Test
    @DisplayName("Manager can also access desk projection")
    void managerCanAccessDeskProjection() throws Exception {
        when(paymentRepository.findById(101L)).thenReturn(Optional.of(samplePayment));
        when(itemResolver.summarize(samplePayment)).thenReturn(
                new PaymentItemResolver.PaymentItemSummary("Gold Membership (1 Month)", null, null));
        when(store.findLatestByPayment(101L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                        .with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(101))
                .andExpect(jsonPath("$.activeSession").doesNotExist());
    }

    @Test
    @DisplayName("Member is forbidden from calling desk projection endpoint")
    void memberIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                        .with(user(new CustomUserDetails(member))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coach is forbidden from calling desk projection endpoint")
    void coachIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                        .with(user(new CustomUserDetails(coach))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous caller receives 401 Unauthorized")
    void anonymousReceivesUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-existent payment returns 404 Not Found")
    void nonExistentPaymentReturns404() throws Exception {
        when(paymentRepository.findById(999999L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/999999")
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Active session past expiresAt evaluates state to EXPIRED")
    void expiredSessionEvaluatesStateToExpired() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-30T14:30:00Z");
        Instant expiredAt = now.minusSeconds(10);
        SqlQrStore.Session session = new SqlQrStore.Session(
                sessionId, 101L, 30L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.VIETCOMBANK_DEMO, now.minusSeconds(610), expiredAt,
                DemoQrService.State.PENDING, 0L
        );

        when(paymentRepository.findById(101L)).thenReturn(Optional.of(samplePayment));
        when(itemResolver.summarize(samplePayment)).thenReturn(
                new PaymentItemResolver.PaymentItemSummary("Gold Membership (1 Month)", null, null));
        when(store.findLatestByPayment(101L)).thenReturn(Optional.of(session));
        when(store.now()).thenReturn(now);

        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSession.state").value("EXPIRED"));
    }

    @Test
    @DisplayName("Zero mutations or financial side effects across multiple polling calls")
    void zeroMutationsOrFinancialSideEffectsAcrossMultiplePollingCalls() throws Exception {
        when(paymentRepository.findById(101L)).thenReturn(Optional.of(samplePayment));
        when(itemResolver.summarize(samplePayment)).thenReturn(
                new PaymentItemResolver.PaymentItemSummary("Gold Membership (1 Month)", null, null));
        when(store.findLatestByPayment(101L)).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                            .with(user(new CustomUserDetails(receptionist))))
                    .andExpect(status().isOk());
        }

        verify(paymentRepository, times(5)).findById(101L);
        verify(store, times(5)).findLatestByPayment(101L);

        // Verify zero mutation operations occur
        verify(paymentRepository, never()).save(any());
        verify(paymentRepository, never()).delete(any(Payment.class));
        verifyNoMoreInteractions(store);
    }

    @Test
    @DisplayName("Settled payment returns simulated provenance and transaction code")
    void settledPaymentReturnsSimulatedProvenanceAndTransactionCode() throws Exception {
        samplePayment.setPaymentStatus(PaymentStatus.SUCCESS);
        samplePayment.setSettlementType("SIMULATED");
        samplePayment.setSettlementProvider("SPORTIFY_SIMULATOR");
        samplePayment.setSimulatedTransactionId(9001L);

        when(paymentRepository.findById(101L)).thenReturn(Optional.of(samplePayment));
        when(itemResolver.summarize(samplePayment)).thenReturn(
                new PaymentItemResolver.PaymentItemSummary("Gold Membership (1 Month)", null, null));
        when(store.findLatestByPayment(101L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/101")
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.settlementType").value("SIMULATED"))
                .andExpect(jsonPath("$.settlementProvider").value("SPORTIFY_SIMULATOR"))
                .andExpect(jsonPath("$.simulatedTransactionId").value(9001));
    }

    @Test
    @DisplayName("Invalid zero or negative payment ID returns 404")
    void invalidPaymentIdReturns404() throws Exception {
        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/0")
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/payments/simulated-qr/desk-projection/-5")
                        .with(user(new CustomUserDetails(receptionist))))
                .andExpect(status().isNotFound());
    }
}
