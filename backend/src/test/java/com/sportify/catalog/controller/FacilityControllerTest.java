package com.sportify.catalog.controller;

import com.sportify.catalog.dto.FacilityResponse;
import com.sportify.catalog.service.FacilityService;
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
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FacilityController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class})
class FacilityControllerTest {
    private static final String URL = "/api/v1/facilities";
    @Autowired private MockMvc mvc;
    @MockitoBean private FacilityService service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void manager_receivesOnlyFrontendFieldsIncludingInactiveFacilities() throws Exception {
        when(service.getFacilities()).thenReturn(List.of(new FacilityResponse(12L, "Court A", true),
                new FacilityResponse(20L, "Old pool", false)));
        mvc.perform(get(URL).with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":12,"name":"Court A","active":true},
                         {"id":20,"name":"Old pool","active":false}]
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void emptyDirectory_returns200AndEmptyArray() throws Exception {
        when(service.getFacilities()).thenReturn(List.of());
        mvc.perform(get(URL).with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void anonymous_returnsExisting401EnvelopeBeforeService() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH", "RECEPTIONIST"})
    void nonManager_returns403BeforeService(String role) throws Exception {
        mvc.perform(get(URL).with(user("other").roles(role))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void writeMethods_areNotSupported(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), URL).with(user("manager").roles("MANAGER")))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
