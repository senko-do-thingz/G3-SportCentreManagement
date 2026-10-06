package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.dto.SportPackageCreateRequest;
import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.service.BookingService;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.RefundService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SportPackageController.class, RefundController.class, MembershipCardController.class, BookingController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
public class RefreshedCatalogAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SportPackageService sportPackageService;

    @MockitoBean
    private MembershipCardService membershipCardService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private RefundService refundService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails createUserDetails(String roleCode) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        UserAccount account = UserAccount.builder().id(100L).email("user@sportify.com").fullName("Test User").role(role).build();
        return new CustomUserDetails(account);
    }

    @Test
    void getPackages_Anonymous_ShouldReturn200() throws Exception {
        when(sportPackageService.getAllActivePackages()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/packages"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyRegistrations_Anonymous_ShouldReturn401() throws Exception {
        // Verifies SecurityConfig properly requires authentication for registrations
        mockMvc.perform(get("/api/v1/packages/registrations/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPackage_AsMember_ShouldReturn403() throws Exception {
        SportPackageCreateRequest req = SportPackageCreateRequest.builder()
                .code("TEST_PKG")
                .name("Test Package")
                .sportId(1L)
                .trainingFormat(com.sportify.catalog.entity.TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .priceAmount(BigDecimal.valueOf(100000.00))
                .build();

        mockMvc.perform(post("/api/v1/packages")
                        .with(user(createUserDetails("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPendingRefunds_AsMember_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/refunds/pending")
                        .with(user(createUserDetails("MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPendingRefunds_AsManager_ShouldReturn200() throws Exception {
        when(refundService.getPendingRefunds()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/refunds/pending")
                        .with(user(createUserDetails("MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void reviewRefund_AsMember_ShouldReturn403() throws Exception {
        RefundReviewRequest req = RefundReviewRequest.builder().approved(true).build();

        mockMvc.perform(put("/api/v1/refunds/1/review")
                        .with(user(createUserDetails("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewRefund_AsManager_ShouldReturn200() throws Exception {
        RefundReviewRequest req = RefundReviewRequest.builder().approved(true).build();
        when(refundService.reviewRefund(any(), any(), any())).thenReturn(null);

        mockMvc.perform(put("/api/v1/refunds/1/review")
                        .with(user(createUserDetails("MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void registerPackage_AsMember_ChannelReception_ReturnsPendingPaymentAndOnline() throws Exception {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .channel(RegistrationChannel.RECEPTION)
                .build();

        PackageRegistrationResponse res = PackageRegistrationResponse.builder()
                .id(1L)
                .channel(RegistrationChannel.ONLINE)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(sportPackageService.registerPackage(any(), argThat(u -> u != null && u.getRole() != null && "MEMBER".equals(u.getRole().getCode()))))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/packages/registrations")
                        .with(user(createUserDetails("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("ONLINE"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void activateRegistration_AsMember_ShouldReturn403() throws Exception {
        mockMvc.perform(put("/api/v1/packages/registrations/1/activate")
                        .with(user(createUserDetails("MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelBooking_OwnershipViolation_ShouldReturn403() throws Exception {
        when(bookingService.cancelBooking(eq(1L), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Cannot cancel another member's booking"));

        mockMvc.perform(delete("/api/v1/bookings/1")
                        .with(user(createUserDetails("MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitRefund_OwnershipViolation_ShouldReturn403() throws Exception {
        RefundCreateRequest req = RefundCreateRequest.builder()
                .packageRegistrationId(10L)
                .reason("Doctor advised against sports")
                .build();

        when(refundService.submitRefund(any(), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Cannot request refund for another member's package registration"));

        mockMvc.perform(post("/api/v1/refunds")
                        .with(user(createUserDetails("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
