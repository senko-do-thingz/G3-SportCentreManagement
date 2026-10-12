package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.exception.BusinessRuleException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SportPackageController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
public class SportPackageRegistrationCancelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SportPackageService sportPackageService;

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
    @DisplayName("PUT /api/v1/packages/registrations/{id}/cancel unauthenticated returns 401")
    void cancelRegistration_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(put("/api/v1/packages/registrations/10/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/packages/registrations/{id}/cancel as owner member returns 200")
    void cancelRegistration_AsOwner_Returns200() throws Exception {
        CustomUserDetails owner = createUserDetails(100L, "MEMBER", "Owner Member");
        PackageRegistrationResponse response = PackageRegistrationResponse.builder()
                .id(10L)
                .status(PackageRegistrationStatus.CANCELLED)
                .build();

        when(sportPackageService.cancelRegistration(eq(10L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/packages/registrations/10/cancel")
                        .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/packages/registrations/{id}/cancel as staff (RECEPTIONIST) returns 200")
    void cancelRegistration_AsReceptionist_Returns200() throws Exception {
        CustomUserDetails receptionist = createUserDetails(200L, "RECEPTIONIST", "Desk Staff");
        PackageRegistrationResponse response = PackageRegistrationResponse.builder()
                .id(10L)
                .status(PackageRegistrationStatus.CANCELLED)
                .build();

        when(sportPackageService.cancelRegistration(eq(10L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/packages/registrations/10/cancel")
                        .with(user(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/packages/registrations/{id}/cancel as staff (ADMIN) returns 200")
    void cancelRegistration_AsAdmin_Returns200() throws Exception {
        CustomUserDetails admin = createUserDetails(300L, "ADMIN", "Center Admin");
        PackageRegistrationResponse response = PackageRegistrationResponse.builder()
                .id(10L)
                .status(PackageRegistrationStatus.CANCELLED)
                .build();

        when(sportPackageService.cancelRegistration(eq(10L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/packages/registrations/10/cancel")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/packages/registrations/{id}/cancel as other member returns 403 Forbidden")
    void cancelRegistration_AsOtherMember_Returns403Forbidden() throws Exception {
        CustomUserDetails otherMember = createUserDetails(400L, "MEMBER", "Other Member");
        when(sportPackageService.cancelRegistration(eq(10L), any(UserAccount.class)))
                .thenThrow(new AccessDeniedException("Cannot cancel another member's registration request"));

        mockMvc.perform(put("/api/v1/packages/registrations/10/cancel")
                        .with(user(otherMember)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/packages/registrations/{id}/cancel with active status returns 400 Bad Request")
    void cancelRegistration_ActiveStatus_Returns400BadRequest() throws Exception {
        CustomUserDetails owner = createUserDetails(100L, "MEMBER", "Owner Member");
        when(sportPackageService.cancelRegistration(eq(10L), any(UserAccount.class)))
                .thenThrow(new BusinessRuleException("Only a package registration that is waiting for payment can be cancelled."));

        mockMvc.perform(put("/api/v1/packages/registrations/10/cancel")
                        .with(user(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only a package registration that is waiting for payment can be cancelled."));
    }
}
