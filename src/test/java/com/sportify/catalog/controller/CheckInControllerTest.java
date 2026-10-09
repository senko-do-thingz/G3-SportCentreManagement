package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.service.CheckInService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckInController.class)
@Import(SecurityConfig.class)
public class CheckInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CheckInService checkInService;

    @MockBean
    private com.sportify.identity.security.JwtService jwtService;

    @MockBean
    private com.sportify.identity.security.JwtAuthenticationFilter jwtAuthFilter;

    @MockBean
    private org.springframework.security.authentication.AuthenticationProvider authenticationProvider;

    @MockBean
    private com.sportify.identity.security.CustomAuthenticationEntryPoint authenticationEntryPoint;

    @MockBean
    private com.sportify.identity.security.CustomAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(1);
            response.sendError(401);
            return null;
        }).when(authenticationEntryPoint).commence(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(1);
            response.sendError(403);
            return null;
        }).when(accessDeniedHandler).handle(any(), any(), any());
    }

    @Test
    void recordCheckIn_NoAuth_Returns401() throws Exception {
        CheckInRequest req = new CheckInRequest();
        
        mockMvc.perform(post("/api/v1/check-ins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"MEMBER"}) // Only MANAGER or RECEPTIONIST allowed
    void recordCheckIn_WrongRole_Returns403() throws Exception {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-001");
        
        mockMvc.perform(post("/api/v1/check-ins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"RECEPTIONIST"})
    void recordCheckIn_EmptyIdentifier_Returns400() throws Exception {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("");
        
        mockMvc.perform(post("/api/v1/check-ins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
