package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PublicFacilityResponse;
import com.sportify.catalog.entity.FacilityType;
import com.sportify.catalog.service.FacilityService;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PublicFacilityController.class, FacilityController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class PublicFacilityControllerTest {

    private static final String URL = "/api/v1/facilities/public";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void guestReadsActiveFacilitiesWithTypeSportAndCapacity() throws Exception {
        when(facilityService.getPublicFacilities()).thenReturn(List.of(
                new PublicFacilityResponse(3L, "Indoor Pool", FacilityType.POOL, 5L, 30)));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Indoor Pool"))
                .andExpect(jsonPath("$[0].facilityType").value("POOL"))
                .andExpect(jsonPath("$[0].sportId").value(5))
                .andExpect(jsonPath("$[0].capacity").value(30))
                .andExpect(jsonPath("$[0].code").doesNotExist());
    }

    @Test
    void managerDirectoryAndWritesStayClosedToGuests() throws Exception {
        mockMvc.perform(get("/api/v1/facilities")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(facilityService);
    }
}
