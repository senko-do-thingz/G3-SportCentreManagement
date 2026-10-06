package com.sportify.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.identity.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportify.identity.security.JwtService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(AuthenticationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;
    
    @MockitoBean
    private JwtService jwtService;
    
    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void login_withBadCredentials_shouldReturn401() throws Exception {
        AuthRequest req = new AuthRequest("test@sportify.com", "wrong");
        when(authenticationService.authenticate(any())).thenThrow(new BadCredentialsException("Bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpoint_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/some-protected-endpoint"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void register_withInvalidEmail_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "invalid-email", "Password123", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void register_withWeakPassword_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "weak", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }
    
    @Test
    void register_withInvalidPhone_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password123", "abc");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phone").exists());
    }

    @Test
    void register_withDuplicateEmail_shouldReturn409() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password123", "0123456789");
        when(authenticationService.register(any())).thenThrow(new com.sportify.core.exception.ConflictException("Email is already registered"));
        
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void register_withBlankFullName_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("", "test@sportify.com", "Password123", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").exists());
    }

    @Test
    void register_withOversizedFullName_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("A".repeat(101), "test@sportify.com", "Password123", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").exists());
    }

    @Test
    void register_withOversizedEmail_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "A".repeat(247) + "@a.com", "Password123", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void register_withPasswordWithoutDigit_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void register_withPasswordWithoutLetter_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "12345678", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void register_withOversizedPassword_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "A".repeat(64) + "1", "0123456789");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void register_withOversizedPhone_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password123", "0".repeat(21));
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phone").exists());
    }

    @Test
    void register_withShortPhone_shouldReturn400() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password123", "+1234567");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phone").exists());
    }

    @Test
    void register_withValidPhone_shouldReturn200() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password123", "+84901234567");
        when(authenticationService.register(any())).thenReturn(com.sportify.identity.dto.AuthResponse.builder().accessToken("token").build());
        
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"));
    }

    @Test
    void register_withGenericException_shouldReturn500WithoutInternalDetails() throws Exception {
        com.sportify.identity.dto.RegisterRequest req = new com.sportify.identity.dto.RegisterRequest("Test User", "test@sportify.com", "Password123", "0123456789");
        when(authenticationService.register(any())).thenThrow(new IllegalStateException("Secret internal detail"));
        
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret internal detail"))))
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
