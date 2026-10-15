package com.sportify.catalog.controller;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.service.MemberScheduleService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.controller.CoachController;
import com.sportify.identity.controller.CoachPublicProfileController;
import com.sportify.identity.dto.CoachPublicProfileResponse;
import com.sportify.identity.security.*;
import com.sportify.identity.service.CoachDirectoryService;
import com.sportify.identity.service.CoachPublicProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.stream.Stream;

import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SessionController.class, CoachPublicProfileController.class, CoachController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class MemberScheduleControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberScheduleService service;
    @MockitoBean CoachPublicProfileService profiles;
    @MockitoBean CoachDirectoryService directory;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;

    static Stream<Arguments> allowed() {
        return Stream.of("/api/v1/sessions", "/api/v1/sessions/100", "/api/v1/coaches/20").flatMap(path ->
                Stream.of("MEMBER", "RECEPTIONIST", "MANAGER").map(role -> Arguments.of(path, role)));
    }

    @ParameterizedTest @MethodSource("allowed")
    void approvedRolesCanReadEveryEndpoint(String path, String role) throws Exception {
        var actor = account(role, 2);
        if (path.equals("/api/v1/sessions")) when(service.listSessions(any(), eq(actor))).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        else if (path.contains("coaches")) when(profiles.getProfile(20L, actor)).thenReturn(new CoachPublicProfileResponse(20L,
                "Coach", null, null, null, null, List.of(), List.of(), new PageResponse<>(List.of(), 0, 20, 0, 0)));
        else when(service.getSession(100L, actor)).thenReturn(new SessionDetailResponse(null, role.equals("MEMBER"), null,
                role.equals("MEMBER") ? false : null, role.equals("MEMBER") ? false : null, null, List.of()));
        mvc.perform(get(path).with(user(new CustomUserDetails(actor)))).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/sessions", "/api/v1/sessions/100", "/api/v1/coaches/20"})
    void anonymousIs401AndUnsupportedCoachRoleIs403(String path) throws Exception {
        mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user(new CustomUserDetails(account("COACH", 20))))).andExpect(status().isForbidden());
        verifyNoInteractions(service, profiles);
    }

    @Test void allFiltersAndPaginationUseTypedBindingAndDoNotAcceptMemberIdAsContext() throws Exception {
        var actor = account("MEMBER", 2);
        var q = new SessionSearchQuery(10L, TODAY, TODAY.plusDays(2), TrainingFormat.COACH_LED, ClassLevel.BEGINNER, 20L, 1, 10);
        when(service.listSessions(q, actor)).thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));
        mvc.perform(get("/api/v1/sessions").param("sportId", "10").param("from", TODAY.toString()).param("to", TODAY.plusDays(2).toString())
                        .param("format", "COACH_LED").param("level", "BEGINNER").param("coachId", "20")
                        .param("page", "1").param("size", "10").param("memberId", "999").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(10));
        verify(service).listSessions(q, actor);
    }

    static Stream<Arguments> malformed() {
        return Stream.of(Arguments.of("from", "bad-date"), Arguments.of("to", "2026-02-30"), Arguments.of("sportId", "x"),
                Arguments.of("format", "INVALID"), Arguments.of("level", "EXPERT"), Arguments.of("coachId", "x"),
                Arguments.of("page", "1.5"), Arguments.of("size", "99999999999999999999"));
    }

    @ParameterizedTest @MethodSource("malformed")
    void malformedParametersAre400BeforeService(String key, String value) throws Exception {
        mvc.perform(get("/api/v1/sessions").param(key, value).with(user(new CustomUserDetails(account("MEMBER", 2)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service, profiles);
    }

    @Test void queryValidationAndHiddenSessionErrorsUseExistingEnvelope() throws Exception {
        var actor = account("MEMBER", 2);
        when(service.listSessions(any(), eq(actor))).thenThrow(new BusinessRuleException("Invalid page"));
        mvc.perform(get("/api/v1/sessions").param("page", "-1").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid page"));
        when(service.getSession(100L, actor)).thenThrow(new ResourceNotFoundException("Session not found"));
        mvc.perform(get("/api/v1/sessions/100").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.timestamp").exists());
    }

    @Test void existingCoachDropdownRemainsStaffOnly() throws Exception {
        when(directory.getActiveCoaches(10L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/coaches").param("sportId", "10").with(user(new CustomUserDetails(account("MANAGER", 3)))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/coaches").with(user(new CustomUserDetails(account("MEMBER", 2)))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles, service);
    }

    @Test void detailNeverPassesAnInjectedMemberIdToService() throws Exception {
        var actor = account("MEMBER", 2);
        mvc.perform(get("/api/v1/sessions/100").param("memberId", "999").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk());
        verify(service).getSession(100L, actor);
    }
}
