package com.sportify.catalog.controller;

import com.sportify.catalog.dto.ClassStatusUpdateRequest;
import com.sportify.catalog.dto.ManagerClassResponse;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SportClassRequest;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.service.ManagerClassService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerClassController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerClassControllerTest {

    private static final String BASE_URL = "/api/v1/manager/classes";

    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String COACH_TOKEN = "coach.access.token";
    private static final String RECEPTIONIST_TOKEN = "receptionist.access.token";
    private static final String MEMBER_TOKEN = "member.access.token";

    private static final Long MANAGER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerClassService managerClassService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpTokens() {
        stubAccessToken(MANAGER_TOKEN, MANAGER_ID, "manager@sportify.com", "MANAGER");
        stubAccessToken(COACH_TOKEN, 2L, "coach@sportify.com", "COACH");
        stubAccessToken(RECEPTIONIST_TOKEN, 3L, "reception@sportify.com", "RECEPTIONIST");
        stubAccessToken(MEMBER_TOKEN, 4L, "member@sportify.com", "MEMBER");
    }

    private void stubAccessToken(String token, Long userId, String email, String roleCode) {
        UserAccount account = UserAccount.builder()
                .id(userId)
                .email(email)
                .fullName("Test User")
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

    private static ManagerClassResponse sampleResponse(Long id) {
        return new ManagerClassResponse(
                id, "CL-1000", "Football Fundamentals", 1L, "Football",
                4L, "Ages 16+", "BEGINNER", 20L, "Daniel Smith",
                16, 2L, true,
                LocalDateTime.of(2026, 10, 11, 10, 0),
                null);
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/manager/classes
    // ---------------------------------------------------------------------

    @Test
    void listClasses_asManager_shouldReturn200AndPage() throws Exception {
        when(managerClassService.listClasses(1L, ClassLevel.BEGINNER, 20L, true, "football", 0, 20))
                .thenReturn(new PageResponse<>(List.of(sampleResponse(100L)), 0, 20, 1, 1));

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("sportId", "1")
                        .param("level", "BEGINNER")
                        .param("coachId", "20")
                        .param("active", "true")
                        .param("search", "football")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("CL-1000"))
                .andExpect(jsonPath("$.content[0].name").value("Football Fundamentals"))
                .andExpect(jsonPath("$.content[0].upcomingSessions").value(2));
    }

    @Test
    void listClasses_asReceptionist_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(managerClassService);
    }

    @Test
    void listClasses_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listClasses_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/manager/classes/{id}
    // ---------------------------------------------------------------------

    @Test
    void getClass_asManager_shouldReturn200() throws Exception {
        when(managerClassService.getClass(100L)).thenReturn(sampleResponse(100L));

        mockMvc.perform(get(BASE_URL + "/100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.code").value("CL-1000"));
    }

    @Test
    void getClass_whenNotFound_shouldReturn404() throws Exception {
        when(managerClassService.getClass(999L)).thenThrow(new ResourceNotFoundException("Class not found: 999"));

        mockMvc.perform(get(BASE_URL + "/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClass_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/manager/classes
    // ---------------------------------------------------------------------

    @Test
    void createClass_asManagerWithValidBody_shouldReturn201() throws Exception {
        String body = """
                {
                  "name": "Football Fundamentals",
                  "sportId": 1,
                  "ageGroupId": 4,
                  "level": "BEGINNER",
                  "maxMembers": 16,
                  "coachId": 20
                }
                """;
        when(managerClassService.createClass(any(SportClassRequest.class), any()))
                .thenReturn(sampleResponse(100L));

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.code").value("CL-1000"));

        ArgumentCaptor<SportClassRequest> captor = ArgumentCaptor.forClass(SportClassRequest.class);
        verify(managerClassService).createClass(captor.capture(), any());
        assertEquals("Football Fundamentals", captor.getValue().name());
        assertEquals(ClassLevel.BEGINNER, captor.getValue().level());
    }

    @Test
    void createClass_withValidationErrors_shouldReturn400() throws Exception {
        String invalidBody = """
                {
                  "name": "",
                  "sportId": null,
                  "ageGroupId": 4,
                  "level": "BEGINNER",
                  "maxMembers": 0,
                  "coachId": null
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.sportId").exists())
                .andExpect(jsonPath("$.details.maxMembers").exists())
                .andExpect(jsonPath("$.details.coachId").exists());

        verifyNoInteractions(managerClassService);
    }

    @Test
    void createClass_whenCoachDoesNotTeachSport_shouldReturn400() throws Exception {
        String body = """
                {
                  "name": "Football Fundamentals",
                  "sportId": 1,
                  "ageGroupId": 4,
                  "level": "BEGINNER",
                  "maxMembers": 16,
                  "coachId": 20
                }
                """;
        when(managerClassService.createClass(any(), any()))
                .thenThrow(new BusinessRuleException("Coach 'Daniel Smith' does not teach Football. Choose a coach qualified for this sport."));

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Coach 'Daniel Smith' does not teach Football. Choose a coach qualified for this sport."));
    }

    @Test
    void createClass_asReceptionist_shouldReturn403() throws Exception {
        String body = """
                {
                  "name": "Football Fundamentals",
                  "sportId": 1,
                  "ageGroupId": 4,
                  "level": "BEGINNER",
                  "maxMembers": 16,
                  "coachId": 20
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // PUT /api/v1/manager/classes/{id}
    // ---------------------------------------------------------------------

    @Test
    void updateClass_asManager_shouldReturn200() throws Exception {
        String body = """
                {
                  "name": "Football Skills Updated",
                  "sportId": 1,
                  "ageGroupId": 4,
                  "level": "INTERMEDIATE",
                  "maxMembers": 20,
                  "coachId": 20
                }
                """;
        when(managerClassService.updateClass(eq(100L), any(SportClassRequest.class), any()))
                .thenReturn(sampleResponse(100L));

        mockMvc.perform(put(BASE_URL + "/100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));

        verify(managerClassService).updateClass(eq(100L), any(SportClassRequest.class), any());
    }

    @Test
    void updateClass_asCoach_shouldReturn403() throws Exception {
        String body = """
                {
                  "name": "Football Skills Updated",
                  "sportId": 1,
                  "ageGroupId": 4,
                  "level": "INTERMEDIATE",
                  "maxMembers": 20,
                  "coachId": 20
                }
                """;

        mockMvc.perform(put(BASE_URL + "/100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(COACH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // PATCH /api/v1/manager/classes/{id}/status
    // ---------------------------------------------------------------------

    @Test
    void changeStatus_asManager_shouldReturn200() throws Exception {
        when(managerClassService.changeStatus(eq(100L), eq(false), any()))
                .thenReturn(sampleResponse(100L));

        mockMvc.perform(patch(BASE_URL + "/100/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk());

        verify(managerClassService).changeStatus(eq(100L), eq(false), any());
    }

    @Test
    void changeStatus_deactivationBlockedByFutureBookings_shouldReturn400() throws Exception {
        when(managerClassService.changeStatus(eq(100L), eq(false), any()))
                .thenThrow(new BusinessRuleException("Cannot deactivate a class that has 3 confirmed booking(s) on upcoming sessions. Cancel or complete those sessions first."));

        mockMvc.perform(patch(BASE_URL + "/100/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot deactivate a class that has 3 confirmed booking(s) on upcoming sessions. Cancel or complete those sessions first."));
    }

    @Test
    void changeStatus_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/100/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isForbidden());
    }
}
