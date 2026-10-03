package com.sportify.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.security.*;
import com.sportify.identity.service.UserService;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getCurrentUser_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUser_withValidAccessToken_shouldReturn200() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(1L)
                .email("test@sportify.com")
                .fullName("Test User")
                .phone("0900000000")
                .role("MEMBER")
                .status("ACTIVE")
                .build();
        
        UserAccount userAccount = UserAccount.builder()
                .email("test@sportify.com")
                .role(Role.builder().code("MEMBER").build())
                .status("ACTIVE")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(userAccount);
        
        when(jwtService.extractUsername("valid.access.token")).thenReturn("test@sportify.com");
        when(jwtService.extractTokenType("valid.access.token")).thenReturn("access");
        when(userDetailsService.loadUserByUsername("test@sportify.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid.access.token", userDetails)).thenReturn(true);
        when(userService.getCurrentUserProfile(any())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.access.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@sportify.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getCurrentUser_withRefreshToken_shouldReturn401() throws Exception {
        UserAccount userAccount = UserAccount.builder()
                .email("test@sportify.com")
                .role(Role.builder().code("MEMBER").build())
                .status("ACTIVE")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(userAccount);
        
        when(jwtService.extractUsername("valid.refresh.token")).thenReturn("test@sportify.com");
        when(jwtService.extractTokenType("valid.refresh.token")).thenReturn("refresh");
        when(userDetailsService.loadUserByUsername("test@sportify.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid.refresh.token", userDetails)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.refresh.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notFoundEndpoint_withValidToken_shouldReturn404() throws Exception {
        UserAccount userAccount = UserAccount.builder()
                .email("test@sportify.com")
                .role(Role.builder().code("MEMBER").build())
                .status("ACTIVE")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(userAccount);
        
        when(jwtService.extractUsername("valid.access.token")).thenReturn("test@sportify.com");
        when(jwtService.extractTokenType("valid.access.token")).thenReturn("access");
        when(userDetailsService.loadUserByUsername("test@sportify.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid.access.token", userDetails)).thenReturn(true);

        mockMvc.perform(get("/api/v1/not-found-endpoint")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.access.token"))
                .andExpect(status().isNotFound());
    }
}
