package com.sportify.catalog.controller;

import com.sportify.catalog.service.FrontDeskQueueService;
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
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FrontDeskQueueController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class FrontDeskQueueControllerTest {
    private static final String[] URLS = {"/api/v1/membership-cards/pending", "/api/v1/packages/registrations/pending"};
    @Autowired private MockMvc mvc;
    @MockitoBean private FrontDeskQueueService queues;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void pendingQueues_whenUnauthenticated_return401() throws Exception {
        for (String url : URLS) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queues);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH"})
    void pendingQueues_whenRoleIsNotFrontDesk_return403(String role) throws Exception {
        for (String url : URLS) {
            mvc.perform(get(url).with(user("user").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(queues);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void pendingQueues_whenRoleIsFrontDesk_return200(String role) throws Exception {
        when(queues.pendingCards(any())).thenReturn(Page.empty());
        when(queues.pendingPackages(any())).thenReturn(Page.empty());
        for (String url : URLS) {
            mvc.perform(get(url).with(user("desk").roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        }
    }
}
