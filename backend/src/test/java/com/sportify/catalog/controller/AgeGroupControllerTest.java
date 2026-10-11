package com.sportify.catalog.controller;

import com.sportify.catalog.dto.AgeGroupResponse;
import com.sportify.catalog.service.AgeGroupService;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgeGroupController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class AgeGroupControllerTest {

    private static final String BASE_URL = "/api/v1/age-groups";

    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String RECEPTIONIST_TOKEN = "receptionist.access.token";
    private static final String MEMBER_TOKEN = "member.access.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgeGroupService ageGroupService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpTokens() {
        stubAccessToken(MANAGER_TOKEN, 1L, "manager@sportify.com", "MANAGER");
        stubAccessToken(RECEPTIONIST_TOKEN, 2L, "reception@sportify.com", "RECEPTIONIST");
        stubAccessToken(MEMBER_TOKEN, 3L, "member@sportify.com", "MEMBER");
    }

    private void stubAccessToken(String token, Long userId, String email, String roleCode) {
        UserAccount account = UserAccount.builder()
                .id(userId)
                .email(email)
                .fullName("Test User")
                .role(Role.builder().code(roleCode).build())
                .status("ACTIVE")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(account);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(jwtService.extractTokenType(token)).thenReturn("access");
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void getAgeGroups_asManager_shouldReturn200() throws Exception {
        when(ageGroupService.getActiveAgeGroups()).thenReturn(List.of(
                new AgeGroupResponse(1L, "KIDS_6_12", "Kids 6-12", 6, 12),
                new AgeGroupResponse(2L, "AGES_16_PLUS", "Ages 16+", 16, null)
        ));

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("KIDS_6_12"))
                .andExpect(jsonPath("$[1].code").value("AGES_16_PLUS"));
    }

    @Test
    void getAgeGroups_asReceptionist_shouldReturn200() throws Exception {
        when(ageGroupService.getActiveAgeGroups()).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void getAgeGroups_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ageGroupService);
    }

    @Test
    void getAgeGroups_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }
}
