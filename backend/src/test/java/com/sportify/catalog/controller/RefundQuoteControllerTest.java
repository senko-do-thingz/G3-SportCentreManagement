package com.sportify.catalog.controller;

import com.sportify.catalog.dto.RefundQuoteResponse;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.service.RefundService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-21: GET /api/v1/refunds/quote, the 409 codes of submit and complete, the amount validation on submit. */
@WebMvcTest(RefundController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class RefundQuoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RefundService refundService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private CustomUserDetails userWithRole(String roleCode) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        return new CustomUserDetails(UserAccount.builder().id(100L).email(roleCode.toLowerCase() + "@sportify.com")
                .fullName("Test " + roleCode).role(role).build());
    }

    private RefundQuoteResponse quote() {
        return RefundQuoteResponse.builder()
                .registrationId(50L).registrationCode("REG-PKG-0050").packageName("Swimming 30 Days")
                .paymentId(70L).paymentCode("PAY-0070").paymentAmount(new BigDecimal("900000.00"))
                .paidAmount(new BigDecimal("900000.00")).totalSessions(10).remainingSessions(4)
                .upcomingBookings(1).unusedSessions(5).usedSessions(5)
                .suggestedAmount(new BigDecimal("450000.00"))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "MANAGER"})
    @DisplayName("GET /refunds/quote returns the quote to members and staff")
    void quote_Returns200(String role) throws Exception {
        when(refundService.quoteRefund(eq(50L), any())).thenReturn(quote());

        mockMvc.perform(get("/api/v1/refunds/quote?registrationId=50").with(user(userWithRole(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedAmount").value(450000.00))
                .andExpect(jsonPath("$.unusedSessions").value(5))
                .andExpect(jsonPath("$.usedSessions").value(5))
                .andExpect(jsonPath("$.upcomingBookings").value(1))
                .andExpect(jsonPath("$.paymentCode").value("PAY-0070"));
    }

    @Test
    @DisplayName("GET /refunds/quote as COACH is 403")
    void quote_Coach_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/refunds/quote?registrationId=50").with(user(userWithRole("COACH"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refundService);
    }

    @Test
    @DisplayName("GET /refunds/quote without registrationId is 400")
    void quote_MissingRegistration_Returns400() throws Exception {
        when(refundService.quoteRefund(isNull(), any())).thenThrow(new BusinessRuleException("Package registration ID is required"));

        mockMvc.perform(get("/api/v1/refunds/quote").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /refunds/quote of another member's registration is 403")
    void quote_OtherMember_Returns403() throws Exception {
        when(refundService.quoteRefund(eq(50L), any())).thenThrow(new AccessDeniedException("other member"));

        mockMvc.perform(get("/api/v1/refunds/quote?registrationId=50").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /refunds/quote without token is 401")
    void quote_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/refunds/quote?registrationId=50"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /refunds on a payment that already has a refund is 409 with code PAYMENT_ALREADY_REFUNDED")
    void submit_SecondRefund_Returns409() throws Exception {
        when(refundService.submitRefund(any(), any()))
                .thenThrow(new ConflictException("already refunded", "PAYMENT_ALREADY_REFUNDED"));

        mockMvc.perform(post("/api/v1/refunds").with(user(userWithRole("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageRegistrationId\":50,\"reason\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REFUNDED"));
    }

    @Test
    @DisplayName("POST /refunds with amountRequested 0 or 3 decimals is 400 before the service")
    void submit_InvalidAmount_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/refunds").with(user(userWithRole("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageRegistrationId\":50,\"reason\":\"x\",\"amountRequested\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/refunds").with(user(userWithRole("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageRegistrationId\":50,\"reason\":\"x\",\"amountRequested\":10.123}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(refundService);
    }

    @Test
    @DisplayName("POST /refunds with a valid amountRequested is 201 and returns the payment link")
    void submit_WithAmount_Returns201() throws Exception {
        when(refundService.submitRefund(any(), any())).thenReturn(RefundResponse.builder()
                .id(10L).refundCode("RFD-0001").paymentId(70L).paymentCode("PAY-0070")
                .amountRequested(new BigDecimal("400000.00")).status(RefundStatus.PENDING).build());

        mockMvc.perform(post("/api/v1/refunds").with(user(userWithRole("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageRegistrationId\":50,\"reason\":\"x\",\"amountRequested\":400000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(70))
                .andExpect(jsonPath("$.paymentCode").value("PAY-0070"));
    }

    @Test
    @DisplayName("PUT /refunds/{id}/complete returns COMPLETED and the number of cancelled bookings")
    void complete_Returns200() throws Exception {
        when(refundService.completeRefund(eq(10L), any())).thenReturn(RefundResponse.builder()
                .id(10L).refundCode("RFD-0001").status(RefundStatus.COMPLETED).cancelledBookings(2).build());

        mockMvc.perform(put("/api/v1/refunds/10/complete").with(user(userWithRole("RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cancelledBookings").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "100.005", "0", "-1"})
    void review_InvalidAmount_Returns400BeforeService(String amount) throws Exception {
        mockMvc.perform(put("/api/v1/refunds/10/review").with(user(userWithRole("MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"amountApproved\":" + amount + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(refundService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.00", "100"})
    void review_ValidAmount_ReachesService(String amount) throws Exception {
        when(refundService.reviewRefund(eq(10L), any(), any())).thenReturn(RefundResponse.builder()
                .id(10L).amountApproved(new BigDecimal(amount)).status(RefundStatus.APPROVED).build());

        mockMvc.perform(put("/api/v1/refunds/10/review").with(user(userWithRole("MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"amountApproved\":" + amount + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amountApproved").value(100));
        verify(refundService).reviewRefund(eq(10L), any(RefundReviewRequest.class), any());
    }
}
