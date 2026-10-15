package com.sportify.identity.controller;

import com.sportify.core.exception.*;
import com.sportify.identity.dto.*;
import com.sportify.identity.security.*;
import com.sportify.identity.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class MemberControllerTest {
    private static final String BODY = """
            {"fullName":" Alice Smith ","phone":" 0901234567 ","email":" ALICE@EXAMPLE.COM "}
            """;
    @Autowired private MockMvc mvc;
    @MockitoBean private MemberSearchService search;
    @MockitoBean private FrontDeskMemberService members;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void endpoints_whenUnauthenticated_return401() throws Exception {
        mvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(search, members);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH"})
    void endpoints_whenRoleIsNotFrontDesk_return403(String role) throws Exception {
        mvc.perform(get("/api/v1/members").with(user("user").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/members").with(user("user").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(search, members);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "RECEPTIONIST"})
    void endpoints_whenRoleIsFrontDesk_returnSuccessWithoutHashes(String role) throws Exception {
        when(search.searchMembers(any(), any())).thenReturn(Page.empty());
        when(members.create(any())).thenReturn(new MemberCreateResponse(7L, "MEM-0007", "Alice Smith",
                "0901234567", "alice@example.com", "ACTIVE", "OneTime123!"));
        mvc.perform(get("/api/v1/members").with(user("desk").roles(role)).param("query", "Alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/v1/members").with(user("desk").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").value("OneTime123!"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(members).create(new MemberCreateRequest("Alice Smith", "0901234567", "alice@example.com"));
    }

    @Test
    void create_whenFieldsAreInvalid_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/members").with(user("desk").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"phone\":\"abc\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.details.fullName").exists()).andExpect(jsonPath("$.details.phone").exists())
                .andExpect(jsonPath("$.details.email").exists());
        verifyNoInteractions(members);
    }

    @Test
    void create_whenFieldsAreMissing_returns400() throws Exception {
        mvc.perform(post("/api/v1/members").with(user("desk").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fullName").exists())
                .andExpect(jsonPath("$.details.phone").exists()).andExpect(jsonPath("$.details.email").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Email is already registered", "Phone number is already registered"})
    void create_whenDuplicatePreCheckFails_returns409(String message) throws Exception {
        when(members.create(any())).thenThrow(new ConflictException(message));
        mvc.perform(post("/api/v1/members").with(user("desk").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void create_whenDatabaseConstraintFails_returns409() throws Exception {
        when(members.create(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));
        mvc.perform(post("/api/v1/members").with(user("desk").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void search_whenUnsupportedSort_returns400() throws Exception {
        when(search.searchMembers(any(), any())).thenThrow(new BusinessRuleException("Unsupported member sort field: passwordHash"));
        mvc.perform(get("/api/v1/members").with(user("desk").roles("MANAGER")).param("sort", "passwordHash"))
                .andExpect(status().isBadRequest());
    }
}
