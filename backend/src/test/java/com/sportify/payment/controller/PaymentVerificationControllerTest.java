package com.sportify.payment.controller;

import com.sportify.catalog.dto.PageResponse;
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
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.exception.PaymentAmountMismatchException;
import com.sportify.payment.service.PaymentVerificationService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentVerificationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class PaymentVerificationControllerTest {

    private static final String CONFIRM_BODY = """
            {"method":"BANK_TRANSFER","referenceCode":"FT2610140001","amountReceived":380000,"note":"ok"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentVerificationService paymentVerificationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails userWithRole(String roleCode) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        UserAccount account = UserAccount.builder()
                .id(7L)
                .email(roleCode.toLowerCase() + "@sportify.com")
                .fullName("Test " + roleCode)
                .role(role)
                .build();
        return new CustomUserDetails(account);
    }

    private StaffPaymentResponse confirmedPayment() {
        return StaffPaymentResponse.builder()
                .id(1L)
                .code("PAY-1001")
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("380000.00"))
                .method(PaymentMethod.BANK_TRANSFER)
                .referenceCode("FT2610140001")
                .itemType(InvoiceItemType.SPORT_PACKAGE)
                .itemCode("REG-PKG-1020")
                .invoiceId(55L)
                .invoiceNumber("INV-1001")
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    @DisplayName("PUT /payments/{id}/confirm as staff returns 200 with the invoice number")
    void confirm_AsStaff_Returns200(String role) throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any())).thenReturn(confirmedPayment());

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole(role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-1001"))
                .andExpect(jsonPath("$.referenceCode").value("FT2610140001"))
                .andExpect(jsonPath("$.paymentReference").value("FT2610140001"));
    }

    @Test
    @DisplayName("Confirm accepts the FE-12 field names paymentMethod and paymentReference")
    void confirm_AcceptsAliasFieldNames() throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any())).thenReturn(confirmedPayment());

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"paymentReference\":null,\"amountReceived\":380000,\"note\":null}"))
                .andExpect(status().isOk());

        verify(paymentVerificationService).confirmPayment(eq(1L),
                argThat(r -> r.getMethod() == PaymentMethod.CASH
                        && r.getAmountReceived().compareTo(new BigDecimal("380000")) == 0),
                any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH"})
    @DisplayName("MEMBER and COACH get 403 on list, confirm and fail")
    void nonStaff_Returns403(String role) throws Exception {
        mockMvc.perform(get("/api/v1/payments").with(user(userWithRole(role))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole(role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/payments/1/fail")
                        .with(user(userWithRole(role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No transfer\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentVerificationService);
    }

    @Test
    @DisplayName("Without a token the confirm endpoint returns 401")
    void confirm_Anonymous_Returns401() throws Exception {
        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Amount mismatch returns 409 with code PAYMENT_AMOUNT_MISMATCH")
    void confirm_Mismatch_Returns409WithCode() throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any()))
                .thenThrow(new PaymentAmountMismatchException("Amount received 350000 does not match"));

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Amount received 350000 does not match"));
    }

    @Test
    @DisplayName("Confirming a payment that is no longer PENDING returns 409")
    void confirm_NotPending_Returns409() throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any()))
                .thenThrow(new ConflictException("Payment PAY-1001 cannot be confirmed", "PAYMENT_NOT_PENDING"));

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
    }

    @Test
    @DisplayName("AccessDeniedException thrown by the service answers 403")
    void confirm_ServiceDenies_Returns403() throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any()))
                .thenThrow(new AccessDeniedException("Only staff users can verify payments"));

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A concurrent update returns 409 with code PAYMENT_CONCURRENT_UPDATE")
    void confirm_ConcurrentUpdate_Returns409WithCode() throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any()))
                .thenThrow(new ConflictException("The payment was changed by another request. Reload it and try again.",
                        "PAYMENT_CONCURRENT_UPDATE"));

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONCURRENT_UPDATE"));
    }

    @Test
    @DisplayName("Bank transfer without a reference returns 400")
    void confirm_MissingReference_Returns400() throws Exception {
        when(paymentVerificationService.confirmPayment(eq(1L), any(), any()))
                .thenThrow(new BusinessRuleException("Reference code is required for payment method BANK_TRANSFER"));

        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"BANK_TRANSFER\",\"amountReceived\":380000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reference code is required for payment method BANK_TRANSFER"));
    }

    @Test
    @DisplayName("Missing method and amount fail bean validation with 400")
    void confirm_InvalidBody_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/payments/1/confirm")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceCode\":\"FT1\",\"amountReceived\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.method").exists())
                .andExpect(jsonPath("$.details.amountReceived").exists());
        verifyNoInteractions(paymentVerificationService);
    }

    @Test
    @DisplayName("Fail without a reason returns 400")
    void fail_BlankReason_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/payments/1/fail")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").exists());
    }

    @Test
    @DisplayName("GET /payments passes filters and reads query as another name for search")
    void list_PassesFilters() throws Exception {
        when(paymentVerificationService.listPayments(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(confirmedPayment()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/payments")
                        .with(user(userWithRole("MANAGER")))
                        .param("status", "PENDING")
                        .param("method", "BANK_TRANSFER")
                        .param("query", "MEM-0100")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-14")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("PAY-1001"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(paymentVerificationService).listPayments(PaymentStatus.PENDING, PaymentMethod.BANK_TRANSFER, "MEM-0100",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), 0, 10);
    }

    @Test
    @DisplayName("GET /payments without parameters uses page 0 and size 20")
    void list_Defaults() throws Exception {
        when(paymentVerificationService.listPayments(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/payments").with(user(userWithRole("RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /payments with a malformed date returns 400 in the standard error body")
    void list_MalformedDate_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/payments").with(user(userWithRole("RECEPTIONIST"))).param("to", "2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'to'"));
        verifyNoInteractions(paymentVerificationService);
    }

    @Test
    @DisplayName("GET /payments with to=9999-12-31 reaches the service and returns 200")
    void list_LastSupportedDate_Returns200() throws Exception {
        when(paymentVerificationService.listPayments(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/payments").with(user(userWithRole("RECEPTIONIST"))).param("to", "9999-12-31"))
                .andExpect(status().isOk());

        verify(paymentVerificationService).listPayments(null, null, null, null, LocalDate.of(9999, 12, 31), 0, 20);
    }

    @Test
    @DisplayName("An unknown status value returns 400")
    void list_UnknownStatus_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/payments").with(user(userWithRole("RECEPTIONIST"))).param("status", "PAID"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentVerificationService);
    }

    @Test
    @DisplayName("Fail as receptionist returns 200")
    void fail_AsStaff_Returns200() throws Exception {
        StaffPaymentResponse failed = StaffPaymentResponse.builder().id(1L).code("PAY-1001")
                .status(PaymentStatus.FAILED).build();
        when(paymentVerificationService.failPayment(anyLong(), any(), any())).thenReturn(failed);

        mockMvc.perform(put("/api/v1/payments/1/fail")
                        .with(user(userWithRole("RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No transfer found\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }
}
