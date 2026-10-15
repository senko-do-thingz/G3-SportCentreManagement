package com.sportify.identity.controller;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.identity.service.CoachDirectoryService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoachController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class CoachControllerTest {

    private static final String BASE_URL = "/api/v1/coaches";

    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String RECEPTIONIST_TOKEN = "receptionist.access.token";
    private static final String MEMBER_TOKEN = "member.access.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoachDirectoryService coachDirectoryService;
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
    void getCoaches_asManagerWithoutSportId_shouldReturn200() throws Exception {
        when(coachDirectoryService.getActiveCoaches(null)).thenReturn(List.of(
                new CoachOptionResponse(10L, "Daniel Smith", "Head Coach", "Passing", 6, List.of(1L, 2L))
        ));

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].fullName").value("Daniel Smith"))
                .andExpect(jsonPath("$[0].sportIds[0]").value(1));

        verify(coachDirectoryService).getActiveCoaches(null);
    }

    @Test
    void getCoaches_asReceptionistWithSportId_shouldPassSportId() throws Exception {
        when(coachDirectoryService.getActiveCoaches(1L)).thenReturn(List.of(
                new CoachOptionResponse(10L, "Daniel Smith", "Head Coach", "Passing", 6, List.of(1L))
        ));

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN))
                        .param("sportId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(coachDirectoryService).getActiveCoaches(1L);
    }

    @Test
    void getCoaches_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(coachDirectoryService);
    }

    @Test
    void getCoaches_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }
}
