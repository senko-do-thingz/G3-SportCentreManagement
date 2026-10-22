package com.sportify.identity.controller;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.identity.service.CoachDirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCoachController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class PublicCoachControllerTest {

    private static final String URL = "/api/v1/coaches/public";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoachDirectoryService coachDirectoryService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void guestReadsActiveCoachesWithoutContactData() throws Exception {
        when(coachDirectoryService.getActiveCoaches(null)).thenReturn(List.of(
                new CoachOptionResponse(7L, "Pham Thu Ha", "Swimming coach", "Stroke technique", 6, List.of(5L))));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].fullName").value("Pham Thu Ha"))
                .andExpect(jsonPath("$[0].sportIds[0]").value(5))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void sportFilterIsPassedToTheDirectory() throws Exception {
        when(coachDirectoryService.getActiveCoaches(2L)).thenReturn(List.of());

        mockMvc.perform(get(URL).param("sportId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(coachDirectoryService).getActiveCoaches(2L);
    }

    @Test
    void writeMethodsStayClosedToGuests() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(coachDirectoryService);
    }
}
