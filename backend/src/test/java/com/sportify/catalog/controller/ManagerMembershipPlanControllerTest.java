package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.PlanCreateRequest;
import com.sportify.catalog.dto.PlanStatusUpdateRequest;
import com.sportify.catalog.dto.PlanUpdateRequest;
import com.sportify.catalog.entity.PlanStatus;
import com.sportify.catalog.service.MembershipPlanService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerMembershipPlanController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerMembershipPlanControllerTest {

    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String MEMBER_TOKEN = "member.access.token";

    private static final String VALID_CREATE_BODY = """
            {
              "code": "FAMILY",
              "name": "Family Pass",
              "price": 450000,
              "durationDays": 30,
              "maxSports": 2,
              "features": ["Coach-led class booking"],
              "eligibleSportIds": [1, 2]
            }
            """;

    private static final String VALID_UPDATE_BODY = """
            {
              "name": "Multi-Sport",
              "price": 600000,
              "durationDays": 30,
              "maxSports": 3,
              "isFeatured": true,
              "displayOrder": 1,
              "features": [],
              "eligibleSportIds": [1, 2, 3],
              "version": 0
            }
            """;

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

    @BeforeEach
    void setUpTokens() {
        stubAccessToken(MANAGER_TOKEN, 1L, "manager@sportify.com", "MANAGER");
        stubAccessToken(MEMBER_TOKEN, 2L, "member@sportify.com", "MEMBER");
    }

    private void stubAccessToken(String token, Long userId, String email, String roleCode) {
        UserAccount account = UserAccount.builder()
                .id(userId)
                .email(email)
                .role(Role.builder().code(roleCode).build())
                .status("ACTIVE")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(account);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(jwtService.extractTokenType(token)).thenReturn("access");
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------------------------------------------------------------------
    // Authentication / authorization matrix
    // ---------------------------------------------------------------------

    @Test
    void listPlans_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/manager/plans"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(planService);
    }

    @Test
    void listPlans_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/manager/plans").header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(planService);
    }

    @Test
    void listPlans_asManager_shouldReturn200() throws Exception {
        when(planService.getAllPlans(0, 20)).thenReturn(
                new PageResponse<>(List.of(MembershipPlanControllerTest.samplePlan()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/manager/plans").header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("MULTI_SPORT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createPlan_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/manager/plans").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(planService);
    }

    @Test
    void createPlan_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/manager/plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planService);
    }

    @Test
    void createPlan_asManager_shouldReturn201WithActorId() throws Exception {
        when(planService.createPlan(any(PlanCreateRequest.class), eq(1L)))
                .thenReturn(MembershipPlanControllerTest.samplePlan());

        mockMvc.perform(post("/api/v1/manager/plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MULTI_SPORT"));
        verify(planService).createPlan(any(PlanCreateRequest.class), eq(1L));
    }

    @Test
    void updatePlan_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(put("/api/v1/manager/plans/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePlan_asManager_shouldReturn200() throws Exception {
        when(planService.updatePlan(eq(2L), any(PlanUpdateRequest.class), eq(1L)))
                .thenReturn(MembershipPlanControllerTest.samplePlan());

        mockMvc.perform(put("/api/v1/manager/plans/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void changeStatus_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(patch("/api/v1/manager/plans/2/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeStatus_asManager_shouldReturn200() throws Exception {
        when(planService.changeStatus(eq(2L), any(PlanStatusUpdateRequest.class), eq(1L)))
                .thenReturn(MembershipPlanControllerTest.samplePlan());

        mockMvc.perform(patch("/api/v1/manager/plans/2/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---------------------------------------------------------------------
    // Validation and error mapping
    // ---------------------------------------------------------------------

    @Test
    void createPlan_withInvalidBody_shouldReturn400WithFieldErrors() throws Exception {
        String body = """
                {"code": "", "name": "X", "price": -1, "durationDays": 0, "maxSports": 0, "features": [" "]}
                """;

        mockMvc.perform(post("/api/v1/manager/plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.details.code").exists())
                .andExpect(jsonPath("$.details.price").exists())
                .andExpect(jsonPath("$.details.durationDays").exists())
                .andExpect(jsonPath("$.details.maxSports").exists());
        verify(planService, never()).createPlan(any(), any());
    }

    @Test
    void changeStatus_withUnknownStatusValue_shouldReturn400() throws Exception {
        mockMvc.perform(patch("/api/v1/manager/plans/2/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPlan_withDuplicateCode_shouldReturn409() throws Exception {
        when(planService.createPlan(any(), any())).thenThrow(new ConflictException("Membership plan code 'FAMILY' already exists"));

        mockMvc.perform(post("/api/v1/manager/plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void changeStatus_whenActivationRuleViolated_shouldReturn400() throws Exception {
        when(planService.changeStatus(eq(2L), any(), any()))
                .thenThrow(new BusinessRuleException("Cannot activate membership plan: the eligible sport pool has 1 sport(s) but max_sports is 3"));

        mockMvc.perform(patch("/api/v1/manager/plans/2/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("max_sports is 3")));
    }

    @Test
    void updatePlan_whenOptimisticLockFails_shouldReturn409WithoutInternalDetails() throws Exception {
        when(planService.updatePlan(eq(2L), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("com.sportify.catalog.entity.MembershipPlan", 2L));

        mockMvc.perform(put("/api/v1/manager/plans/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("com.sportify"))));
    }

    @Test
    void listPlans_asManager_shouldPassPagingParameters() throws Exception {
        when(planService.getAllPlans(anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/manager/plans?page=2&size=5").header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk());
        verify(planService).getAllPlans(2, 5);
    }
}
