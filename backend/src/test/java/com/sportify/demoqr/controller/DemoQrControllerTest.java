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

@WebMvcTest({DemoQrController.class, SimulatorCapabilityController.class})
@ActiveProfiles("qr-simulator")
@TestPropertySource(properties = {"app.payment.simulator.enabled=true", "app.payment.simulator.public-base-url=https://demo.example.test/", "app.payment.simulator.allowed-origins=https://demo.example.test"})
@Import({DemoQrAdvice.class,
        SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DemoQrControllerTest {
    private static final String BASE = "/api/v1/payments/simulated-qr/sessions";
    @Autowired MockMvc mvc;
    @MockitoBean DemoQrService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;

    private CustomUserDetails principal(String role) {
        return new CustomUserDetails(UserAccount.builder().id(2L).status("ACTIVE")
                .role(Role.builder().code(role).name(role).build()).build());
    }

    @ParameterizedTest @ValueSource(strings={"","/preview","/confirm-intent","/confirm","/id/cancel"})
    void unauthenticatedRequestsAreRejected(String suffix) throws Exception {
        mvc.perform(post(BASE+suffix).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"MANAGER","RECEPTIONIST","COACH"})
    void nonMemberRolesCannotCreate(String role) throws Exception {
        mvc.perform(post(BASE).with(user(principal(role))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":52,\"bankCode\":\"BIDV_DEMO\",\"requestId\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"{}","not-json","{\"paymentId\":0,\"bankCode\":\"BIDV_DEMO\"}","{\"token\":{\"secret\":123}}"})
    void malformedRequestsNeverEchoValues(String body) throws Exception {
        mvc.perform(post(BASE).with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATOR_INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test void datastoreErrorsAreSanitized() throws Exception {
        when(service.preview(anyString(), any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("PRIVATE DIAGNOSTICS"));
        mvc.perform(post(BASE+"/preview").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"secret\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SIMULATOR_RETRY"))
                .andExpect(header().string("Cache-Control","no-store"));
    }

    @Test void forgedFormAndUntrustedCorsAreRejected() throws Exception {
        mvc.perform(post(BASE+"/confirm").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("token=forged"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options(BASE+"/confirm").header("Origin","https://evil.example")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type"))
                .andExpect(status().isForbidden());
    }

    @Test void legacyConfirmIntentIsRejectedWithoutSettlingPayment() throws Exception {
        mvc.perform(post(BASE + "/confirm-intent").with(user(principal("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "a".repeat(43) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIMULATOR_CONFIRM_ENDPOINT_REQUIRED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"/api/v1/payments/simulated-qr/capability","/api/v1/payments/simulator-config"})
    void capabilityReportsEnabledAndSettlementEnabledForAuthenticatedMember(String path) throws Exception {
        mvc.perform(get(path).with(user(principal("MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.settlementEnabled").value(true))
                .andExpect(jsonPath("$.contractVersion").value(1));
    }
}
