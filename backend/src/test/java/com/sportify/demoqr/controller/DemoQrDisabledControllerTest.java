package com.sportify.demoqr.controller;

import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoQrController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
abstract class DisabledDemoQrContract {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;

    @ParameterizedTest
    @ValueSource(strings = {"", "/preview", "/confirm", "/00000000-0000-0000-0000-000000000000/cancel", "/read"})
    void everyOperationIsAbsentEvenWithAuthenticatedOwner(String suffix) throws Exception {
        var owner = new CustomUserDetails(UserAccount.builder().id(2L).status("ACTIVE")
                .role(Role.builder().code("MEMBER").name("MEMBER").build()).build());
        var request = suffix.equals("/read") ? get("/api/v1/demo/qr/sessions/00000000-0000-0000-0000-000000000000")
                : post("/api/v1/demo/qr/sessions" + suffix).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":52,\"bankCode\":\"BIDV_DEMO\",\"token\":\"invalid\"}");
        mvc.perform(request.with(user(owner))).andExpect(status().isNotFound());
    }
}

@ActiveProfiles("dev")
@TestPropertySource(properties = "app.demo.qr.enabled=false")
class DemoQrDisabledControllerTest extends DisabledDemoQrContract { }

@ActiveProfiles("prod")
@TestPropertySource(properties = "app.demo.qr.enabled=true")
class DemoQrProductionControllerTest extends DisabledDemoQrContract { }
