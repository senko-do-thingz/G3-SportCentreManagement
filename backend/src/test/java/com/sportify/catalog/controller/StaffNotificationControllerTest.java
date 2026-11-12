package com.sportify.catalog.controller;

import com.sportify.catalog.service.OperationalNotificationService;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffNotificationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class StaffNotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OperationalNotificationService notifications;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private static CustomUserDetails actor(long id, String role) {
        return new CustomUserDetails(UserAccount.builder().id(id).email(role.toLowerCase() + "@sportify.test").fullName(role)
                .status("ACTIVE").role(Role.builder().code(role).name(role).build()).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "COACH"})
    void staffReadsOnlyTheirOwnNotifications(String role) throws Exception {
        when(notifications.own(7L)).thenReturn(List.of(new OperationalNotificationService.Notice(1L, "Title", "Body", "INFO", false,
                LocalDateTime.of(2026, 11, 12, 8, 0), "E:1", null, null)));
        mockMvc.perform(get("/api/v1/staff/notifications").with(user(actor(7L, role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Title"))
                .andExpect(jsonPath("$[0].read").value(false));
        verify(notifications).own(7L);
    }

    @Test
    void markReadIsScopedToTheCaller() throws Exception {
        mockMvc.perform(put("/api/v1/staff/notifications/4/read").with(user(actor(9L, "COACH")))).andExpect(status().isNoContent());
        verify(notifications).read(9L, 4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "RECEPTIONIST"})
    void otherRolesAreForbidden(String role) throws Exception {
        mockMvc.perform(get("/api/v1/staff/notifications").with(user(actor(3L, role)))).andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }
}
