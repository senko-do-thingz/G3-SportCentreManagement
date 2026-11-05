package com.sportify.catalog.controller;

import com.sportify.catalog.service.ManagerAttendanceReadService;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerAttendanceReadController.class)
@Import({SecurityConfig.class,CustomAuthenticationEntryPoint.class,CustomAccessDeniedHandler.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class ManagerAttendanceReadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ManagerAttendanceReadService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;
    private static final String OVERVIEW="/api/v1/manager/training/attendance/overview?from=2026-11-01&to=2026-11-05";
    @Test void unauthorizedReadersNeverReachTheService() throws Exception {
        for(String role:List.of("MEMBER","COACH","RECEPTIONIST")) {
            var actor=new CustomUserDetails(UserAccount.builder().id(10L).email("read@test.invalid").fullName(role).role(Role.builder().code(role).name(role).build()).build());
            for(String path:List.of(OVERVIEW,"/api/v1/manager/training/attendance/sessions/051126FB01"))
                mvc.perform(get(path).with(user(actor))).andExpect(status().isForbidden());
        }
        mvc.perform(get(OVERVIEW)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
