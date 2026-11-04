package com.sportify.demoqr.controller;

import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.demoqr.dto.LocalDemoSettlementResponse;
import com.sportify.demoqr.service.DemoQrException;
import com.sportify.demoqr.service.LocalDemoPaymentService;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalDemoPaymentController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class, DemoQrAdvice.class})
class LocalDemoPaymentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean LocalDemoPaymentService localDemoPaymentService;

    private CustomUserDetails userDetails(String roleCode, long id) {
        return new CustomUserDetails(UserAccount.builder()
                .id(id)
                .email("test." + roleCode.toLowerCase() + "@example.com")
                .status("ACTIVE")
                .role(Role.builder().code(roleCode).name(roleCode).build())
                .build());
    }

    @Test
    void capabilityReturnsEnabledTrueWhenServiceEnabled() throws Exception {
        when(localDemoPaymentService.isEnabled()).thenReturn(true);

        mvc.perform(get("/api/v1/payments/local-demo/capability").with(user(userDetails("MEMBER", 10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void capabilityReturnsEnabledFalseWhenServiceDisabled() throws Exception {
        when(localDemoPaymentService.isEnabled()).thenReturn(false);

        mvc.perform(get("/api/v1/payments/local-demo/capability").with(user(userDetails("MEMBER", 10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void capabilityRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/v1/payments/local-demo/capability"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(post("/api/v1/payments/52/demo-confirm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmRejectsReceptionistRole() throws Exception {
        mvc.perform(post("/api/v1/payments/52/demo-confirm").with(user(userDetails("RECEPTIONIST", 2L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmRejectsManagerRole() throws Exception {
        mvc.perform(post("/api/v1/payments/52/demo-confirm").with(user(userDetails("MANAGER", 1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmReturnsUnavailableWhenServiceDisabled() throws Exception {
        when(localDemoPaymentService.isEnabled()).thenReturn(false);

        mvc.perform(post("/api/v1/payments/52/demo-confirm").with(user(userDetails("MEMBER", 10L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmSuccessReturnsAuthoritativeSettlement() throws Exception {
        when(localDemoPaymentService.isEnabled()).thenReturn(true);
        when(localDemoPaymentService.confirmDemoPayment(eq(52L), any())).thenReturn(
                new LocalDemoSettlementResponse(52L, "SUCCESS", "SIMULATED", "SPORTIFY_SIMULATOR",
                        101L, "SIM-QR-52-TEST", 201L, "INV-2026-00201")
        );

        mvc.perform(post("/api/v1/payments/52/demo-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(userDetails("MEMBER", 10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(52))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.settlementType").value("SIMULATED"))
                .andExpect(jsonPath("$.settlementProvider").value("SPORTIFY_SIMULATOR"))
                .andExpect(jsonPath("$.simulatedTransactionId").value(101))
                .andExpect(jsonPath("$.referenceCode").value("SIM-QR-52-TEST"))
                .andExpect(jsonPath("$.invoiceId").value(201))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-2026-00201"));
    }

    @Test
    void confirmPropagatesConflictWhenAlreadySettledWithActual() throws Exception {
        when(localDemoPaymentService.isEnabled()).thenReturn(true);
        when(localDemoPaymentService.confirmDemoPayment(eq(52L), any())).thenThrow(
                new DemoQrException(HttpStatus.CONFLICT, "SIMULATOR_ALREADY_SETTLED", "Payment is already settled.")
        );

        mvc.perform(post("/api/v1/payments/52/demo-confirm").with(user(userDetails("MEMBER", 10L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIMULATOR_ALREADY_SETTLED"));
    }

    @Test
    void confirmPropagatesRateLimit() throws Exception {
        when(localDemoPaymentService.isEnabled()).thenReturn(true);
        when(localDemoPaymentService.confirmDemoPayment(eq(52L), any())).thenThrow(
                new DemoQrException(HttpStatus.TOO_MANY_REQUESTS, "SIMULATOR_RATE_LIMIT", "Rate limit reached.")
        );

        mvc.perform(post("/api/v1/payments/52/demo-confirm").with(user(userDetails("MEMBER", 10L))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SIMULATOR_RATE_LIMIT"));
    }
}
