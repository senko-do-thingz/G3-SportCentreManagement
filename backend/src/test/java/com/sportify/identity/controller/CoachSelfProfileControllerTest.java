package com.sportify.identity.controller;

import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.dto.CoachSelfProfileResponse;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.identity.service.CoachSelfProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoachSelfProfileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CoachSelfProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CoachSelfProfileService coachSelfProfileService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private static CustomUserDetails actor(long id, String role) {
        return new CustomUserDetails(UserAccount.builder().id(id).email(role.toLowerCase() + "@sportify.test")
                .fullName(role).status("ACTIVE").role(Role.builder().code(role).name(role).build()).build());
    }

    private static CoachSelfProfileResponse sampleProfile() {
        return new CoachSelfProfileResponse(
                7L, "Tran Minh Quan", "quan.coach@example.com", "/images/coaches/bao-le.jpg",
                "Badminton coach", "Footwork specialist", 6, "Singles, Doubles", true, 1,
                List.of(new CoachSelfProfileResponse.TaughtSport(1L, "Badminton")),
                List.of(new CoachSelfProfileResponse.Certification(10L, 1L, "Badminton", "BWF Level 1", "BWF",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2028, 1, 1), false)),
                List.of(new CoachSelfProfileResponse.CertificationOption("BWF Level 1|BWF|1", "BWF Level 1", "BWF", 1L, "Badminton"))
        );
    }

    @Test
    void coachReadsAndUpdatesOwnProfileAndCertifications() throws Exception {
        when(coachSelfProfileService.getMyProfile(any())).thenReturn(sampleProfile());
        when(coachSelfProfileService.updateMyProfile(any(), any())).thenReturn(sampleProfile());
        when(coachSelfProfileService.addCertification(any(), any())).thenReturn(sampleProfile());
        when(coachSelfProfileService.removeCertification(eq(10L), any())).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/coach/profile").with(user(actor(7L, "COACH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.certificationOptions[0].name").value("BWF Level 1"));

        mockMvc.perform(put("/api/v1/coach/profile").with(user(actor(7L, "COACH")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Badminton coach","bio":"Footwork specialist","yearsExperience":6,"specialties":"Singles, Doubles"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Badminton coach"));

        mockMvc.perform(post("/api/v1/coach/profile/certifications").with(user(actor(7L, "COACH")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BWF Level 1","issuer":"BWF","sportId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certifications[0].name").value("BWF Level 1"));

        mockMvc.perform(delete("/api/v1/coach/profile/certifications/10").with(user(actor(7L, "COACH"))))
                .andExpect(status().isOk());

        verify(coachSelfProfileService).removeCertification(eq(10L), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "MANAGER"})
    void nonCoachRolesAreForbidden(String role) throws Exception {
        mockMvc.perform(get("/api/v1/coach/profile").with(user(actor(3L, role))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(coachSelfProfileService);
    }
}
