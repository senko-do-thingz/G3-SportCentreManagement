package com.sportify.identity.security;

import com.sportify.catalog.controller.SportController;
import com.sportify.catalog.service.SportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An empty app.cors.allowed-origins (the prod default when the variable is missing)
 * must reject every cross-origin browser request.
 */
@WebMvcTest(
        controllers = SportController.class,
        properties = "app.cors.allowed-origins="
)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class SecurityConfigCorsEmptyOriginsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SportService sportService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void simpleRequest_withEmptyAllowList_shouldRejectAnyOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/sports").header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflight_withEmptyAllowList_shouldRejectAnyOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/sports")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
