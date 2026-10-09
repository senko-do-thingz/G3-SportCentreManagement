package com.sportify.catalog.controller;

import com.sportify.catalog.dto.SportResponse;
import com.sportify.catalog.entity.SportVenueType;
import com.sportify.catalog.service.SportService;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SportController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class SportControllerTest {

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
    void getSports_withoutToken_shouldReturn200() throws Exception {
        when(sportService.getActiveSports()).thenReturn(List.of(
                new SportResponse(1L, "FOOTBALL", "Football", SportVenueType.OUTDOOR, null, null, 0),
                new SportResponse(5L, "SWIMMING", "Swimming", SportVenueType.POOL, null, null, 0)));

        mockMvc.perform(get("/api/v1/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("FOOTBALL"))
                .andExpect(jsonPath("$[1].venueType").value("POOL"));
    }

    @Test
    void postSports_withoutToken_shouldReturn401() throws Exception {
        // Only GET is public.
        mockMvc.perform(post("/api/v1/sports"))
                .andExpect(status().isUnauthorized());
    }
}
