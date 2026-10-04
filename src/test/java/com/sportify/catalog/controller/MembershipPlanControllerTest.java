package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.dto.SportSummaryResponse;
import com.sportify.catalog.entity.PlanStatus;
import com.sportify.catalog.service.MembershipPlanService;
import com.sportify.core.exception.ResourceNotFoundException;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MembershipPlanController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class MembershipPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MembershipPlanService planService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    static PlanResponse samplePlan() {
        return new PlanResponse(2L, "MULTI_SPORT", "Multi-Sport", "02 / EXPLORE", "Mix classes across up to three sports.",
                new BigDecimal("550000.00"), 30, 3, true, PlanStatus.ACTIVE, 0,
                List.of("Coach-led class booking", "Personal schedule and progress"),
                List.of(new SportSummaryResponse(1L, "FOOTBALL", "Football"),
                        new SportSummaryResponse(2L, "BADMINTON", "Badminton")),
                0);
    }

    @Test
    void getPlans_withoutToken_shouldReturn200() throws Exception {
        when(planService.getActivePlans()).thenReturn(List.of(samplePlan()));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MULTI_SPORT"))
                .andExpect(jsonPath("$[0].maxSports").value(3))
                .andExpect(jsonPath("$[0].features.length()").value(2))
                .andExpect(jsonPath("$[0].eligibleSports[0].code").value("FOOTBALL"));
    }

    @Test
    void getPlanById_withoutToken_shouldReturn200() throws Exception {
        when(planService.getActivePlan(2L)).thenReturn(samplePlan());

        mockMvc.perform(get("/api/v1/plans/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getPlanById_whenNotActive_shouldReturn404() throws Exception {
        when(planService.getActivePlan(7L)).thenThrow(new ResourceNotFoundException("Membership plan not found: 7"));

        mockMvc.perform(get("/api/v1/plans/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Membership plan not found: 7"));
    }

    @Test
    void getPlanById_withNonNumericId_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/plans/abc"))
                .andExpect(status().isBadRequest());
    }
}
