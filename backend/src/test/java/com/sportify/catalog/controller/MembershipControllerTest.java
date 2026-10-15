package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.MembershipRegistrationRequest;
import com.sportify.catalog.entity.RegistrationType;
import com.sportify.catalog.service.MembershipService;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MembershipController.class)
@Import(SecurityConfig.class)
public class MembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MembershipService membershipService;

    @MockBean
    private JwtService jwtService;

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
    void register_NoAuth_Returns401() throws Exception {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        
        mockMvc.perform(post("/api/v1/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"COACH"})
    void register_WrongRole_Returns403() throws Exception {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(1L);
        req.setSportIds(java.util.List.of(1L));
        
        mockMvc.perform(post("/api/v1/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"MEMBER"})
    void register_NullPlanId_Returns400() throws Exception {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(null);
        req.setSportIds(java.util.List.of(1L));
        
        mockMvc.perform(post("/api/v1/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"MEMBER"})
    void register_EmptySports_Returns400() throws Exception {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(1L);
        req.setSportIds(java.util.Collections.emptyList());
        
        mockMvc.perform(post("/api/v1/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
