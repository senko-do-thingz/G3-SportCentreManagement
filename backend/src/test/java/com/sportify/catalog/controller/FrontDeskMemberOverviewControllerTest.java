package com.sportify.catalog.controller;

import com.sportify.catalog.dto.MemberOverviewResponse;
import com.sportify.catalog.service.FrontDeskMemberOverviewService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FrontDeskMemberOverviewController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class FrontDeskMemberOverviewControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private FrontDeskMemberOverviewService service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void overview_whenUnauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/members/7/overview")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH"})
    void overview_whenRoleIsNotFrontDesk_returns403(String role) throws Exception {
        mvc.perform(get("/api/v1/members/7/overview").with(user("staff").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "RECEPTIONIST"})
    void overview_whenRoleIsFrontDesk_keepsExistingJsonShape(String role) throws Exception {
        when(service.overview(7L)).thenReturn(new MemberOverviewResponse(
                MemberSearchResponse.builder().userId(7L).fullName("Alice").status("ACTIVE").build(),
                null, null, List.of(), List.of(), List.of()));
        mvc.perform(get("/api/v1/members/7/overview").with(user("staff").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.fullName").value("Alice"))
                .andExpect(jsonPath("$.currentCard").isEmpty()).andExpect(jsonPath("$.pendingCardRequest").isEmpty())
                .andExpect(jsonPath("$.activePackageRegistrations").isArray())
                .andExpect(jsonPath("$.pendingPackageRegistrations").isArray())
                .andExpect(jsonPath("$.recentCheckIns").isArray());
    }

    @Test
    void overview_whenUnknownMember_returns404() throws Exception {
        when(service.overview(99L)).thenThrow(new ResourceNotFoundException("Member not found"));
        mvc.perform(get("/api/v1/members/99/overview").with(user("staff").roles("RECEPTIONIST")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Member not found"));
    }
}
