package com.sportify.payment.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.payment.dto.InvoiceLineResponse;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.dto.MemberPaymentResponse;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.ReceiptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-20: GET /api/v1/payments/my, GET /api/v1/invoices/{id}, GET /api/v1/invoices/by-payment/{paymentId}.
 */
@WebMvcTest({InvoiceController.class, MemberPaymentController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receiptService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails userWithRole(String roleCode) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        UserAccount account = UserAccount.builder()
                .id(100L)
                .email(roleCode.toLowerCase() + "@sportify.com")
                .fullName("Test " + roleCode)
                .role(role)
                .build();
        return new CustomUserDetails(account);
    }

    private MemberPaymentResponse paidRow() {
        return MemberPaymentResponse.builder()
                .id(1L).code("PAY-1001").status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("380000.00")).method(PaymentMethod.BANK_TRANSFER)
                .itemType(InvoiceItemType.SPORT_PACKAGE).itemDescription("Swimming 30 Days")
                .time(LocalDateTime.of(2026, 10, 14, 10, 0))
                .invoiceId(55L).invoiceNumber("INV-1001")
                .build();
    }

    private InvoiceResponse invoice() {
        return InvoiceResponse.builder()
                .id(55L).invoiceNumber("INV-1001").status(InvoiceStatus.PAID)
                .issueDate(LocalDateTime.of(2026, 10, 14, 10, 0))
                .paymentId(1L).paymentCode("PAY-1001").paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentReference("FT2610140001")
                .memberId(100L).memberCode("MEM-0100").memberFullName("Minh Member")
                .subtotalAmount(new BigDecimal("400000.00")).discountAmount(new BigDecimal("20000.00"))
                .taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("380000.00"))
                .lines(List.of(InvoiceLineResponse.builder().id(1L).itemType(InvoiceItemType.SPORT_PACKAGE)
                        .itemReferenceId(20L).description("Swimming 30 Days (REG-PKG-1020)").quantity(1)
                        .unitPrice(new BigDecimal("400000.00")).lineTotal(new BigDecimal("400000.00")).build()))
                .build();
    }

    @Test
    @DisplayName("GET /payments/my as a member returns a page of their payments")
    void myPayments_AsMember_Returns200() throws Exception {
        when(receiptService.listMyPayments(isNull(), eq(0), eq(20), any()))
                .thenReturn(new PageResponse<>(List.of(paidRow()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/payments/my").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PAY-1001"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.content[0].itemDescription").value("Swimming 30 Days"))
                .andExpect(jsonPath("$.content[0].invoiceId").value(55))
                .andExpect(jsonPath("$.content[0].invoiceNumber").value("INV-1001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /payments/my passes status, page and size through")
    void myPayments_PassesFilters() throws Exception {
        when(receiptService.listMyPayments(eq(PaymentStatus.PENDING), eq(2), eq(5), any()))
                .thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/payments/my?status=PENDING&page=2&size=5").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
        verify(receiptService).listMyPayments(eq(PaymentStatus.PENDING), eq(2), eq(5), any());
    }

    @Test
    @DisplayName("GET /payments/my with an unknown status is 400")
    void myPayments_BadStatus_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/payments/my?status=DONE").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(receiptService);
    }

    @Test
    @DisplayName("GET /payments/my with a size the service rejects is 400")
    void myPayments_BadSize_Returns400() throws Exception {
        when(receiptService.listMyPayments(any(), eq(0), eq(500), any()))
                .thenThrow(new BusinessRuleException("Size must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/payments/my?size=500").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER", "COACH"})
    @DisplayName("GET /payments/my as a non-member is 403")
    void myPayments_NonMember_Returns403(String role) throws Exception {
        mockMvc.perform(get("/api/v1/payments/my").with(user(userWithRole(role))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(receiptService);
    }

    @Test
    @DisplayName("GET /payments/my without a token is 401")
    void myPayments_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/my"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "MANAGER"})
    @DisplayName("GET /invoices/{id} returns header and lines to the owner and staff")
    void invoice_Returns200(String role) throws Exception {
        when(receiptService.getInvoice(eq(55L), any())).thenReturn(invoice());

        mockMvc.perform(get("/api/v1/invoices/55").with(user(userWithRole(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-1001"))
                .andExpect(jsonPath("$.issueDate").value("2026-10-14T10:00:00"))
                .andExpect(jsonPath("$.memberFullName").value("Minh Member"))
                .andExpect(jsonPath("$.memberCode").value("MEM-0100"))
                .andExpect(jsonPath("$.paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.paymentReference").value("FT2610140001"))
                .andExpect(jsonPath("$.subtotalAmount").value(400000.00))
                .andExpect(jsonPath("$.discountAmount").value(20000.00))
                .andExpect(jsonPath("$.taxAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(380000.00))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.lines[0].description").value("Swimming 30 Days (REG-PKG-1020)"))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(400000.00));
    }

    @Test
    @DisplayName("GET /invoices/{id} of another member is 403")
    void invoice_OtherMember_Returns403() throws Exception {
        when(receiptService.getInvoice(eq(55L), any())).thenThrow(new AccessDeniedException("other member"));

        mockMvc.perform(get("/api/v1/invoices/55").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /invoices/{id} with an unknown id is 404")
    void invoice_NotFound_Returns404() throws Exception {
        when(receiptService.getInvoice(eq(999L), any())).thenThrow(new ResourceNotFoundException("Invoice not found"));

        mockMvc.perform(get("/api/v1/invoices/999").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /invoices/{id} as a coach is 403")
    void invoice_Coach_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/55").with(user(userWithRole("COACH"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(receiptService);
    }

    @Test
    @DisplayName("GET /invoices/by-payment/{paymentId} returns the receipt")
    void invoiceByPayment_Returns200() throws Exception {
        when(receiptService.getInvoiceByPayment(eq(1L), any())).thenReturn(invoice());

        mockMvc.perform(get("/api/v1/invoices/by-payment/1").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.paymentId").value(1));
    }

    @Test
    @DisplayName("GET /invoices/by-payment/{paymentId} of another member is 403")
    void invoiceByPayment_OtherMember_Returns403() throws Exception {
        when(receiptService.getInvoiceByPayment(eq(1L), any())).thenThrow(new AccessDeniedException("other member"));

        mockMvc.perform(get("/api/v1/invoices/by-payment/1").with(user(userWithRole("MEMBER"))))
                .andExpect(status().isForbidden());
    }
}
