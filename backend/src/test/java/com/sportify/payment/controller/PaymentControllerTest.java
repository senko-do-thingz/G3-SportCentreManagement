package com.sportify.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sportify.payment.dto.BankTransferInstructions;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails createUserDetails(Long id, String roleCode, String fullName) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        UserAccount account = UserAccount.builder()
                .id(id)
                .email(roleCode.toLowerCase() + "@sportify.com")
                .fullName(fullName)
                .role(role)
                .build();
        return new CustomUserDetails(account);
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} as owner returns 200 with details and instructions")
    void getPayment_AsMember_Returns200WithPaymentDetails() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");
        BankTransferInstructions bank = BankTransferInstructions.builder()
                .accountName("Sportify Sports Center")
                .accountNumber("0000 1041 2460")
                .bank("Demo Bank")
                .transferContent("PAY-1000")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.BANK_TRANSFER)
                .itemDescription("Football / Coach-led / 30 days")
                .originalPrice(BigDecimal.valueOf(900000.00))
                .discount(BigDecimal.valueOf(90000.00))
                .bankTransferInstructions(bank)
                .memberId(100L)
                .memberCode("MEM-0100")
                .build();

        when(paymentService.getPayment(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/1")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.code").value("PAY-1000"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(810000.00))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.itemDescription").value("Football / Coach-led / 30 days"))
                .andExpect(jsonPath("$.originalPrice").value(900000.00))
                .andExpect(jsonPath("$.discount").value(90000.00))
                .andExpect(jsonPath("$.bankTransferInstructions.accountName").value("Sportify Sports Center"))
                .andExpect(jsonPath("$.bankTransferInstructions.accountNumber").value("0000 1041 2460"))
                .andExpect(jsonPath("$.bankTransferInstructions.bank").value("Demo Bank"))
                .andExpect(jsonPath("$.bankTransferInstructions.transferContent").value("PAY-1000"))
                .andExpect(jsonPath("$.paymentTime").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} for pending MOMO omits bank instructions and has null paymentTime")
    void getPayment_WhenPendingMomo_OmitsBankTransferInstructionsAndHasNullPaymentTime() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");
        PaymentResponse response = PaymentResponse.builder()
                .id(2L)
                .code("PAY-1001")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(500000.00))
                .method(PaymentMethod.MOMO)
                .itemDescription("Gym Membership")
                .paymentTime(null)
                .bankTransferInstructions(null)
                .memberId(100L)
                .build();

        when(paymentService.getPayment(eq(2L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/2")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAY-1001"))
                .andExpect(jsonPath("$.method").value("MOMO"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentTime").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.bankTransferInstructions").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} for SUCCESS payment includes paymentTime and omits bank instructions")
    void getPayment_WhenSuccess_IncludesPaymentTimeAndOmitsBankTransferInstructions() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");
        PaymentResponse response = PaymentResponse.builder()
                .id(3L)
                .code("PAY-1002")
                .status(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(500000.00))
                .method(PaymentMethod.BANK_TRANSFER)
                .itemDescription("Gym Membership")
                .paymentTime(LocalDateTime.of(2026, 10, 12, 10, 0, 0))
                .bankTransferInstructions(null)
                .memberId(100L)
                .build();

        when(paymentService.getPayment(eq(3L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/3")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAY-1002"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentTime").value("2026-10-12T10:00:00"))
                .andExpect(jsonPath("$.bankTransferInstructions").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} as other member returns 404 Not Found (M5)")
    void getPayment_AsOtherMember_Returns404NotFound() throws Exception {
        CustomUserDetails otherMember = createUserDetails(200L, "MEMBER", "Other Member");
        when(paymentService.getPayment(eq(1L), any(UserAccount.class)))
                .thenThrow(new ResourceNotFoundException("Payment not found for id: 1"));

        mockMvc.perform(get("/api/v1/payments/1")
                        .with(user(otherMember)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} as staff returns 200")
    void getPayment_AsReceptionist_Returns200() throws Exception {
        CustomUserDetails receptionist = createUserDetails(200L, "RECEPTIONIST", "Receptionist User");
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.BANK_TRANSFER)
                .build();

        when(paymentService.getPayment(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/1")
                        .with(user(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAY-1000"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} unauthenticated returns 401")
    void getPayment_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method as owner returns 200 with updated method")
    void updatePaymentMethod_AsMember_Returns200WithUpdatedMethod() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.MOMO)
                .build();

        when(paymentService.updatePaymentMethod(eq(1L), any(UpdatePaymentMethodRequest.class), any(UserAccount.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("MOMO"));
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method as other member returns 404 Not Found (M5)")
    void updatePaymentMethod_AsOtherMember_Returns404NotFound() throws Exception {
        CustomUserDetails otherMember = createUserDetails(200L, "MEMBER", "Other Member");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        when(paymentService.updatePaymentMethod(eq(1L), any(UpdatePaymentMethodRequest.class), any(UserAccount.class)))
                .thenThrow(new ResourceNotFoundException("Payment not found for id: 1"));

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(otherMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method as staff returns 200 (Staff keeps full access)")
    void updatePaymentMethod_AsStaff_Returns200() throws Exception {
        CustomUserDetails receptionist = createUserDetails(300L, "RECEPTIONIST", "Receptionist User");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.VNPAY)
                .build();

        when(paymentService.updatePaymentMethod(eq(1L), any(UpdatePaymentMethodRequest.class), any(UserAccount.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("VNPAY"));
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method with unallowed method returns 400 Bad Request")
    void updatePaymentMethod_DisallowedMethod_Returns400BadRequest() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        when(paymentService.updatePaymentMethod(eq(1L), any(UpdatePaymentMethodRequest.class), any(UserAccount.class)))
                .thenThrow(new BusinessRuleException("Payment method CASH is not allowed for online payment"));

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment method CASH is not allowed for online payment"));
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method missing method returns 400 Bad Request")
    void updatePaymentMethod_MissingMethod_Returns400BadRequest() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} as member conceals notes (notes is null)")
    void getPayment_AsMember_ConcealsNotes() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Member User");
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.BANK_TRANSFER)
                .notes(null)
                .build();

        when(paymentService.getPayment(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/1")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} as staff includes notes")
    void getPayment_AsStaff_IncludesNotes() throws Exception {
        CustomUserDetails staff = createUserDetails(200L, "RECEPTIONIST", "Front Desk");
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.BANK_TRANSFER)
                .notes("Confirmed by user id 200")
                .build();

        when(paymentService.getPayment(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/1")
                        .with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Confirmed by user id 200"));
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method as staff allows setting CASH")
    void updatePaymentMethod_AsStaffWithCash_Returns200() throws Exception {
        CustomUserDetails staff = createUserDetails(200L, "RECEPTIONIST", "Front Desk");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .code("PAY-1000")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(810000.00))
                .method(PaymentMethod.CASH)
                .build();

        when(paymentService.updatePaymentMethod(eq(1L), any(UpdatePaymentMethodRequest.class), any(UserAccount.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("CASH"));
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method when optimistic locking conflict occurs returns 409 Conflict")
    void updatePaymentMethod_WhenOptimisticLockConflict_Returns409Conflict() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Taylor Nguyen");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        when(paymentService.updatePaymentMethod(eq(1L), any(UpdatePaymentMethodRequest.class), any(UserAccount.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(com.sportify.payment.entity.Payment.class, 1L));

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The resource was modified by another request. Please reload and try again."));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} as admin returns 403 Forbidden")
    void getPayment_AsAdmin_Returns403Forbidden() throws Exception {
        CustomUserDetails admin = createUserDetails(999L, "ADMIN", "Administrator");

        mockMvc.perform(get("/api/v1/payments/1")
                        .with(user(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/method as admin returns 403 Forbidden")
    void updatePaymentMethod_AsAdmin_Returns403Forbidden() throws Exception {
        CustomUserDetails admin = createUserDetails(999L, "ADMIN", "Administrator");
        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        mockMvc.perform(put("/api/v1/payments/1/method")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
