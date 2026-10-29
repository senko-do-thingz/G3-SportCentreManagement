package com.sportify.demoqr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.demoqr.config.DemoQrConfiguration;
import com.sportify.demoqr.service.DemoPaymentReader;
import com.sportify.demoqr.service.DemoQrService;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemoQrController.class)
@ActiveProfiles("qr-demo")
@TestPropertySource(properties = {"app.demo.qr.enabled=true", "app.demo.qr.public-base-url=https://demo.example.test/", "app.demo.qr.allowed-origins=https://demo.example.test"})
@Import({DemoQrConfiguration.class, DemoQrService.class, DemoPaymentReader.class, DemoQrAdvice.class,
        SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DemoQrControllerTest {
    private static final String BASE = "/api/v1/demo/qr/sessions";
    private static final java.util.concurrent.atomic.AtomicLong TICK = new java.util.concurrent.atomic.AtomicLong();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean PaymentRepository payments;
    @MockitoBean MemberCardRepository cards;
    @MockitoBean SportPackageRegistrationRepository registrations;
    @MockitoBean Clock clock;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;
    private Payment payment;
    private CustomUserDetails owner;

    private static CustomUserDetails principal(long id, String role) {
        return new CustomUserDetails(UserAccount.builder().id(id).status("ACTIVE").email("test@example.test")
                .role(Role.builder().code(role).name(role).build()).build());
    }

    @BeforeEach
    void setup() {
        // Advance each test beyond the prior rate-limit window while retaining a real service/store.
        when(clock.instant()).thenReturn(Instant.parse("2026-10-29T08:00:00Z").plusSeconds(TICK.incrementAndGet() * 1000));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        owner = principal(2, "MEMBER");
        payment = Payment.builder().id(52L).member(MemberProfile.builder().id(2L).build())
                .amount(new BigDecimal("810000.00")).paymentStatus(PaymentStatus.PENDING).version(4).build();
        when(payments.findById(52L)).thenReturn(Optional.of(payment));
    }

    private DemoQrService.Created create() throws Exception {
        var result = mvc.perform(post(BASE).with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":52,\"bankCode\":\"BIDV_DEMO\",\"amount\":1,\"ownerId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.session.amount").value(810000.00))
                .andExpect(jsonPath("$.session.mode").value("NON_PAYABLE")).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), DemoQrService.Created.class);
    }

    @Test
    void realServiceJourneyPreviewConfirmReplayRefreshAndCancelNeverMutateFinancialRepository() throws Exception {
        var created = create();
        String token = created.confirmationUrl().split("#token=")[1];
        String body = mapper.writeValueAsString(Map.of("token", token));
        mvc.perform(post(BASE + "/preview").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING"));
        for (int i = 0; i < 2; i++) mvc.perform(post(BASE + "/confirm").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"));
        mvc.perform(get(BASE + "/" + created.session().id()).with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"));
        mvc.perform(post(BASE + "/" + created.session().id() + "/cancel").with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"));
        verify(payments, times(2)).findById(52L);
        verifyNoMoreInteractions(payments);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getVersion()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/preview", "/confirm", "/00000000-0000-0000-0000-000000000000/cancel"})
    void unauthenticatedPostDenied(String suffix) throws Exception {
        mvc.perform(post(BASE + suffix).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }

    @Test
    void unauthenticatedReadDenied() throws Exception {
        mvc.perform(get(BASE + "/00000000-0000-0000-0000-000000000000")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "RECEPTIONIST", "COACH"})
    void nonMemberRolesCannotCreate(String role) throws Exception {
        mvc.perform(post(BASE).with(user(principal(2, role))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":52,\"bankCode\":\"BIDV_DEMO\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void crossOwnerHasSameResponseAsUnknownTokenAndCannotEnumerate() throws Exception {
        var created = create();
        var other = principal(3, "MEMBER");
        String token = created.confirmationUrl().split("#token=")[1];
        var denied = mvc.perform(post(BASE + "/confirm").with(user(other)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", token)))).andExpect(status().isNotFound()).andReturn();
        var unknown = mvc.perform(post(BASE + "/confirm").with(user(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"unknown\"}")).andExpect(status().isNotFound()).andReturn();
        assertThat(denied.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        mvc.perform(get(BASE + "/" + created.session().id()).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/" + created.session().id() + "/cancel").with(user(other))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"paymentId\":0,\"bankCode\":\"BIDV_DEMO\"}", "{\"paymentId\":52,\"bankCode\":\"REAL_BANK_SECRET\"}", "{\"paymentId\":52}", "not-json"})
    void invalidCreateBodyHasSanitizedError(String body) throws Exception {
        mvc.perform(post(BASE).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"code\":\"DEMO_INVALID_REQUEST\",\"message\":\"Invalid demo request.\"}"));
        verifyNoInteractions(payments);
    }

    @Test
    void tokenNeverReturnedByReadsAndInvalidBodyNeverEchoed() throws Exception {
        var created = create();
        mvc.perform(get(BASE + "/" + created.session().id()).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.confirmationUrl").doesNotExist());
        mvc.perform(post(BASE + "/confirm").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"token\":{\"secret\":123}}"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"code\":\"DEMO_INVALID_REQUEST\",\"message\":\"Invalid demo request.\"}"));
        assertThat(new DemoQrController.TokenRequest("secret").toString()).doesNotContain("secret");
    }

    @Test
    void forgedHtmlFormWithoutBearerCannotConfirmAndUntrustedCorsCannotRead() throws Exception {
        mvc.perform(post(BASE + "/confirm").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("token=forged"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options(BASE + "/confirm").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isForbidden());
    }
}
