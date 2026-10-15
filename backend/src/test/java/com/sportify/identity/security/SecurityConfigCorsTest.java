package com.sportify.identity.security;

import com.sportify.catalog.controller.SportController;
import com.sportify.catalog.service.SportService;
import com.sportify.identity.controller.UserController;
import com.sportify.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS behaviour of SecurityConfig for the React frontend (BE-00).
 * The allowed origins are pinned here so the test does not depend on the active profile.
 */
@WebMvcTest(
        controllers = {SportController.class, UserController.class},
        properties = "app.cors.allowed-origins=http://localhost:5173,http://localhost:3000"
)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class SecurityConfigCorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String SECOND_ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String UNKNOWN_ORIGIN = "http://evil.example.com";

    private static final String PUBLIC_ENDPOINT = "/api/v1/sports";
    private static final String PROTECTED_ENDPOINT = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SportService sportService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    // ---------- Allowed origin ----------

    @Test
    void simpleRequest_fromAllowedOrigin_shouldReturnAllowOriginHeader() throws Exception {
        when(sportService.getActiveSports()).thenReturn(List.of());

        mockMvc.perform(get(PUBLIC_ENDPOINT).header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void simpleRequest_fromSecondAllowedOrigin_shouldReturnAllowOriginHeader() throws Exception {
        when(sportService.getActiveSports()).thenReturn(List.of());

        mockMvc.perform(get(PUBLIC_ENDPOINT).header("Origin", SECOND_ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SECOND_ALLOWED_ORIGIN));
    }

    @Test
    void simpleRequest_fromAllowedOrigin_shouldNotAllowCredentials() throws Exception {
        when(sportService.getActiveSports()).thenReturn(List.of());

        mockMvc.perform(get(PUBLIC_ENDPOINT).header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void protectedEndpoint_fromAllowedOriginWithoutToken_shouldReturn401WithAllowOriginHeader() throws Exception {
        // The browser must be able to read the 401 body, so the CORS header has to be present.
        mockMvc.perform(get(PROTECTED_ENDPOINT).header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void request_withoutOriginHeader_shouldNotReturnCorsHeaders() throws Exception {
        when(sportService.getActiveSports()).thenReturn(List.of());

        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---------- Unknown origin ----------

    @Test
    void simpleRequest_fromUnknownOrigin_shouldBeRejected() throws Exception {
        mockMvc.perform(get(PUBLIC_ENDPOINT).header("Origin", UNKNOWN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflight_fromUnknownOrigin_shouldBeRejected() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", UNKNOWN_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---------- Preflight ----------

    @Test
    void preflight_onProtectedEndpointWithoutToken_shouldReturn200() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void preflight_shouldAdvertiseAllowedMethodsHeadersAndMaxAge() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("DELETE")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("OPTIONS")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("content-type")))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflight_forEachAllowedMethod_shouldReturn200() throws Exception {
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
            mockMvc.perform(options(PROTECTED_ENDPOINT)
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", method))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void preflight_withMethodNotInAllowList_shouldBeRejected() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflight_withHeaderNotInAllowList_shouldBeRejected() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "x-custom-header"))
                .andExpect(status().isForbidden());
    }
}
