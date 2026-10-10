package com.sportify.identity.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ManagerUserResponse;
import com.sportify.identity.dto.StaffAccountCreateRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.identity.service.ManagerUserService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerUserController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerUserControllerTest {

    private static final String BASE_URL = "/api/v1/manager/users";

    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String COACH_TOKEN = "coach.access.token";
    private static final String RECEPTIONIST_TOKEN = "receptionist.access.token";
    private static final String MEMBER_TOKEN = "member.access.token";
    private static final String INACTIVE_MANAGER_TOKEN = "inactive.manager.access.token";

    private static final Long MANAGER_ID = 1L;

    private static final String VALID_COACH_BODY = """
            {
              "fullName": "Daniel Smith",
              "email": "daniel@sportify.demo",
              "phone": "0901234568",
              "temporaryPassword": "Temp1234",
              "role": "COACH",
              "sportIds": [3, 5]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerUserService managerUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpTokens() {
        stubAccessToken(MANAGER_TOKEN, MANAGER_ID, "manager@sportify.com", "MANAGER", "ACTIVE");
        stubAccessToken(COACH_TOKEN, 2L, "coach@sportify.com", "COACH", "ACTIVE");
        stubAccessToken(RECEPTIONIST_TOKEN, 3L, "reception@sportify.com", "RECEPTIONIST", "ACTIVE");
        stubAccessToken(MEMBER_TOKEN, 4L, "member@sportify.com", "MEMBER", "ACTIVE");
        stubAccessToken(INACTIVE_MANAGER_TOKEN, 5L, "old.manager@sportify.com", "MANAGER", "INACTIVE");
    }

    private void stubAccessToken(String token, Long userId, String email, String roleCode, String status) {
        UserAccount account = UserAccount.builder()
                .id(userId)
                .email(email)
                .role(Role.builder().code(roleCode).build())
                .status(status)
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

    private static ManagerUserResponse response(Long id, String role, String status) {
        return new ManagerUserResponse(id, "Daniel Smith", "daniel@sportify.demo", "0901234568", role, status,
                LocalDateTime.of(2026, 10, 9, 9, 0));
    }

    private static String createBody(String phone, String password) {
        return """
                {
                  "fullName": "Jamie Tran",
                  "email": "jamie@sportify.demo",
                  "phone": "%s",
                  "temporaryPassword": "%s",
                  "role": "RECEPTIONIST"
                }
                """.formatted(phone, password);
    }

    // ---------------------------------------------------------------------
    // Authentication and authorization (non-manager roles get 403)
    // ---------------------------------------------------------------------

    @Test
    void list_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_COACH_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void changeStatus_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void list_asNonManagerRoles_shouldReturn403() throws Exception {
        for (String token : List.of(COACH_TOKEN, RECEPTIONIST_TOKEN, MEMBER_TOKEN)) {
            mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_asNonManagerRoles_shouldReturn403() throws Exception {
        for (String token : List.of(COACH_TOKEN, RECEPTIONIST_TOKEN, MEMBER_TOKEN)) {
            mockMvc.perform(post(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_COACH_BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(managerUserService);
    }

    @Test
    void changeStatus_asNonManagerRoles_shouldReturn403() throws Exception {
        for (String token : List.of(COACH_TOKEN, RECEPTIONIST_TOKEN, MEMBER_TOKEN)) {
            mockMvc.perform(patch(BASE_URL + "/7/status")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"INACTIVE\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(managerUserService);
    }

    @Test
    void list_withTokenOfDeactivatedManager_shouldReturn401() throws Exception {
        // A valid token of an INACTIVE account must not authenticate.
        mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer(INACTIVE_MANAGER_TOKEN)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(managerUserService);
    }

    // ---------------------------------------------------------------------
    // GET list
    // ---------------------------------------------------------------------

    @Test
    void list_asManager_shouldReturnPagedRowsWithoutSecrets() throws Exception {
        PageResponse<ManagerUserResponse> page = new PageResponse<>(
                List.of(response(11L, "COACH", "ACTIVE")), 0, 20, 1, 1);
        when(managerUserService.listUsers(null, null, null, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.content[0].fullName").value("Daniel Smith"))
                .andExpect(jsonPath("$.content[0].email").value("daniel@sportify.demo"))
                .andExpect(jsonPath("$.content[0].phone").value("0901234568"))
                .andExpect(jsonPath("$.content[0].role").value("COACH"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_shouldPassFiltersSearchAndPagingToTheService() throws Exception {
        when(managerUserService.listUsers(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("role", "COACH")
                        .param("status", "INACTIVE")
                        .param("search", "daniel")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(managerUserService).listUsers(eq("COACH"), eq(UserStatus.INACTIVE), eq("daniel"), eq(2), eq(5));
    }

    @Test
    void list_withUnknownStatus_shouldReturn400() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("status", "BANNED"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void list_withUnknownRole_shouldReturn400() throws Exception {
        when(managerUserService.listUsers(eq("ADMIN"), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessRuleException("Role must be one of MEMBER, COACH, RECEPTIONIST, MANAGER"));

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Role must be one of MEMBER, COACH, RECEPTIONIST, MANAGER"));
    }

    // ---------------------------------------------------------------------
    // POST create
    // ---------------------------------------------------------------------

    @Test
    void create_coach_shouldReturn201AndPassRequestAndActor() throws Exception {
        when(managerUserService.createStaffAccount(any(StaffAccountCreateRequest.class), any(UserAccount.class)))
                .thenReturn(response(30L, "COACH", "ACTIVE"));

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COACH_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.role").value("COACH"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        ArgumentCaptor<StaffAccountCreateRequest> requestCaptor = ArgumentCaptor.forClass(StaffAccountCreateRequest.class);
        ArgumentCaptor<UserAccount> actorCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(managerUserService).createStaffAccount(requestCaptor.capture(), actorCaptor.capture());
        assertEquals("COACH", requestCaptor.getValue().role());
        assertEquals(List.of(3L, 5L), requestCaptor.getValue().sportIds());
        assertEquals(MANAGER_ID, actorCaptor.getValue().getId());
    }

    @Test
    void create_withWeakPassword_shouldReturn400WithFieldError() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("0901234570", "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.details.temporaryPassword").exists());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withPasswordWithoutDigit_shouldReturn400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("0901234570", "OnlyLetters")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.temporaryPassword").exists());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withInvalidPhone_shouldReturn400WithFieldError() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("12ab", "Temp1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phone").exists());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withInvalidEmailAndBlankName_shouldReturn400WithFieldErrors() throws Exception {
        String body = """
                {
                  "fullName": " ",
                  "email": "not-an-email",
                  "phone": "0901234570",
                  "temporaryPassword": "Temp1234",
                  "role": "RECEPTIONIST"
                }
                """;
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").exists())
                .andExpect(jsonPath("$.details.email").exists());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withoutRole_shouldReturn400WithFieldError() throws Exception {
        String body = """
                {
                  "fullName": "Jamie Tran",
                  "email": "jamie@sportify.demo",
                  "phone": "0901234570",
                  "temporaryPassword": "Temp1234"
                }
                """;
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.role").exists());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withMalformedJson_shouldReturn400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void create_withDuplicateEmail_shouldReturn409() throws Exception {
        when(managerUserService.createStaffAccount(any(StaffAccountCreateRequest.class), any(UserAccount.class)))
                .thenThrow(new ConflictException("Email is already registered"));

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COACH_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void create_memberRole_shouldReturn400() throws Exception {
        when(managerUserService.createStaffAccount(any(StaffAccountCreateRequest.class), any(UserAccount.class)))
                .thenThrow(new BusinessRuleException("Role must be one of RECEPTIONIST, COACH, MANAGER"));

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COACH_BODY.replace("\"COACH\"", "\"MEMBER\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Role must be one of RECEPTIONIST, COACH, MANAGER"));
    }

    // ---------------------------------------------------------------------
    // PATCH status
    // ---------------------------------------------------------------------

    @Test
    void changeStatus_asManager_shouldReturn200AndPassActor() throws Exception {
        when(managerUserService.changeStatus(eq(7L), eq(UserStatus.INACTIVE), any(UserAccount.class)))
                .thenReturn(response(7L, "RECEPTIONIST", "INACTIVE"));

        mockMvc.perform(patch(BASE_URL + "/7/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        ArgumentCaptor<UserAccount> actorCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(managerUserService).changeStatus(eq(7L), eq(UserStatus.INACTIVE), actorCaptor.capture());
        assertEquals(MANAGER_ID, actorCaptor.getValue().getId());
    }

    @Test
    void changeStatus_deactivatingSelf_shouldReturn400() throws Exception {
        when(managerUserService.changeStatus(eq(MANAGER_ID), eq(UserStatus.INACTIVE), any(UserAccount.class)))
                .thenThrow(new BusinessRuleException("You cannot deactivate your own account"));

        mockMvc.perform(patch(BASE_URL + "/" + MANAGER_ID + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot deactivate your own account"));
    }

    @Test
    void changeStatus_withUnknownStatusValue_shouldReturn400() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/7/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void changeStatus_withMissingStatus_shouldReturn400WithFieldError() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/7/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void changeStatus_withNonNumericId_shouldReturn400() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/abc/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(managerUserService);
    }

    @Test
    void changeStatus_unknownUser_shouldReturn404() throws Exception {
        when(managerUserService.changeStatus(eq(404L), eq(UserStatus.ACTIVE), any(UserAccount.class)))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(patch(BASE_URL + "/404/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }
}
