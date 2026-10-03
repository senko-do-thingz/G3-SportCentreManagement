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
}
