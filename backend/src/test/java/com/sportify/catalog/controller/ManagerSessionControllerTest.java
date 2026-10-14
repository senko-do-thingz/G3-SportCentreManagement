package com.sportify.catalog.controller;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.exception.ScheduleConflictException;
import com.sportify.catalog.service.ManagerSessionService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagerSessionController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerSessionControllerTest {
    private static final String BASE = "/api/v1/manager/sessions";
    private static final String CREATE = "/api/v1/manager/classes/10/sessions";
    private static final String BODY = """
            {"date":"2026-10-19","startTime":"10:00:00","endTime":"11:00:00",
             "facilityId":30,"coachId":20,"capacity":16,"trainingType":"COACH_LED"}
            """;
    private static final String UPDATE = """
            {"date":"2026-10-19","startTime":"10:00:00","endTime":"11:00:00",
             "facilityId":30,"coachId":20,"capacity":16}
            """;
    @Autowired private MockMvc mvc;
    @MockitoBean private ManagerSessionService service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private static ManagerSessionResponse sample() {
        return new ManagerSessionResponse(100L, LocalDate.of(2026, 10, 19), LocalTime.of(10, 0), LocalTime.of(11, 0),
                new ManagerSessionResponse.ClassSummary(10L, "CL-1000", "Football Fundamentals"),
                new ManagerSessionResponse.ResourceSummary(20L, "Daniel Smith"),
                new ManagerSessionResponse.ResourceSummary(30L, "Football Pitch 1"),
                16, 2, TrainingFormat.COACH_LED, SessionStatus.PUBLISHED, null, null, null);
    }

    private static MockHttpServletRequestBuilder endpoint(String operation) {
        return switch (operation) {
            case "list" -> get(BASE).param("from", "2026-10-19").param("to", "2026-10-20");
            case "create" -> post(CREATE).contentType(MediaType.APPLICATION_JSON).content(BODY);
            case "update" -> put(BASE + "/100").contentType(MediaType.APPLICATION_JSON).content(UPDATE);
            case "cancel" -> put(BASE + "/100/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Weather\"}");
            default -> throw new IllegalArgumentException(operation);
        };
    }

    static Stream<Arguments> forbiddenRoles() {
        return Stream.of("list", "create", "update", "cancel").flatMap(operation ->
                Stream.of("RECEPTIONIST", "COACH", "MEMBER").map(role -> Arguments.of(operation, role)));
    }

    @ParameterizedTest
    @MethodSource("forbiddenRoles")
    void everyEndpoint_rejectsEveryNonManagerRole(String operation, String role) throws Exception {
        mvc.perform(endpoint(operation).with(user("staff").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "create", "update", "cancel"})
    void everyEndpoint_anonymousReturns401(String operation) throws Exception {
        mvc.perform(endpoint(operation).with(anonymous())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void manager_listsCalendarWithOptionalFiltersAndNestedResources() throws Exception {
        when(service.listSessions(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20), 10L, 20L, 30L))
                .thenReturn(List.of(sample()));
        mvc.perform(endpoint("list").param("classId", "10").param("coachId", "20").param("facilityId", "30")
                        .with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].class.code").value("CL-1000"))
                .andExpect(jsonPath("$[0].coach.name").value("Daniel Smith"))
                .andExpect(jsonPath("$[0].facility.name").value("Football Pitch 1"))
                .andExpect(jsonPath("$[0].bookedCount").value(2));
    }

    @Test
    void manager_createsSessionsAndReturns201Array() throws Exception {
        when(service.createSessions(eq(10L), any(), nullable(UserAccount.class))).thenReturn(List.of(sample()));
        mvc.perform(endpoint("create").with(user("manager").roles("MANAGER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void manager_updatesSessionAndReturns200() throws Exception {
        when(service.updateSession(eq(100L), any(), nullable(UserAccount.class))).thenReturn(sample());
        mvc.perform(endpoint("update").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(100));
    }

    @Test
    void manager_cancelsSessionAndTrimsReason() throws Exception {
        when(service.cancelSession(eq(100L), any(), nullable(UserAccount.class))).thenReturn(sample());
        mvc.perform(put(BASE + "/100/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  Weather  \"}")
                        .with(user("manager").roles("MANAGER"))).andExpect(status().isOk());
        verify(service).cancelSession(eq(100L), eq(new SessionCancelRequest("Weather")), nullable(UserAccount.class));
    }

    static Stream<String> invalidCreateBodies() {
        return Stream.of("{}", BODY.replace("\"capacity\":16", "\"capacity\":0"),
                BODY.replace("\"capacity\":16", "\"capacity\":null"),
                BODY.replace("\"facilityId\":30", "\"facilityId\":null"),
                BODY.replace("\"startTime\":\"10:00:00\"", "\"startTime\":null"),
                BODY.replace("\"endTime\":\"11:00:00\"", "\"endTime\":null"),
                BODY.replace("\"trainingType\":\"COACH_LED\"", "\"trainingType\":null"),
                BODY.replace("COACH_LED", "INVALID"), BODY.replace("2026-10-19", "bad-date"),
                BODY.replace("\"date\":\"2026-10-19\"", "\"daysOfWeek\":[\"FUNDAY\"],\"fromDate\":\"2026-10-19\",\"toDate\":\"2026-10-20\""),
                BODY.replace("\"date\":\"2026-10-19\"", "\"date\":\"2026-10-19\",\"status\":\"INVALID\""));
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    void create_invalidBodyReturns400WithoutCallingService(String body) throws Exception {
        mvc.perform(post(CREATE).with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<String> invalidUpdateBodies() {
        return Stream.of("{}", UPDATE.replace("\"date\":\"2026-10-19\"", "\"date\":null"),
                UPDATE.replace("\"capacity\":16", "\"capacity\":0"),
                UPDATE.replace("\"facilityId\":30", "\"facilityId\":null"),
                UPDATE.replace("\"startTime\":\"10:00:00\"", "\"startTime\":null"),
                UPDATE.replace("\"endTime\":\"11:00:00\"", "\"endTime\":null"));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateBodies")
    void update_invalidBodyReturns400WithoutCallingService(String body) throws Exception {
        mvc.perform(put(BASE + "/100").with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<String> invalidCancelBodies() {
        return Stream.of("{}", "{\"reason\":\"\"}", "{\"reason\":\"  \"}", "{\"reason\":null}",
                "{\"reason\":\"" + "x".repeat(256) + "\"}");
    }

    @ParameterizedTest
    @MethodSource("invalidCancelBodies")
    void cancel_invalidReasonReturns400WithoutCallingService(String body) throws Exception {
        mvc.perform(put(BASE + "/100/cancel").with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "cancel", "list"})
    void businessRuleFailures_useExisting400Envelope(String operation) throws Exception {
        var exception = new BusinessRuleException("Business rule rejected");
        switch (operation) {
            case "create" -> when(service.createSessions(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
            case "update" -> when(service.updateSession(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
            case "cancel" -> when(service.cancelSession(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
            case "list" -> when(service.listSessions(any(), any(), any(), any(), any())).thenThrow(exception);
        }
        mvc.perform(endpoint(operation).with(user("manager").roles("MANAGER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").value("Business rule rejected"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "cancel"})
    void missingResourceFailures_return404(String operation) throws Exception {
        var exception = new ResourceNotFoundException("Resource not found");
        switch (operation) {
            case "create" -> when(service.createSessions(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
            case "update" -> when(service.updateSession(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
            case "cancel" -> when(service.cancelSession(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
        }
        mvc.perform(endpoint(operation).with(user("manager").roles("MANAGER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void scheduleConflicts_return409WithStructuredListInsideExistingEnvelope(String operation) throws Exception {
        var conflict = new SessionConflictResponse(101L, sample().date(), sample().date(), sample().startTime(), sample().endTime(),
                sample().sportClass(), sample().coach(), sample().facility(), SessionConflictResponse.ConflictType.BOTH);
        var exception = new ScheduleConflictException(List.of(conflict));
        if (operation.equals("create")) {
            when(service.createSessions(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
        } else {
            when(service.updateSession(anyLong(), any(), nullable(UserAccount.class))).thenThrow(exception);
        }
        mvc.perform(endpoint(operation).with(user("manager").roles("MANAGER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details.conflicts[0].sessionId").value(101))
                .andExpect(jsonPath("$.details.conflicts[0].requestedDate").value("2026-10-19"))
                .andExpect(jsonPath("$.details.conflicts[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.details.conflicts[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$.details.conflicts[0].endTime").value("11:00:00"))
                .andExpect(jsonPath("$.details.conflicts[0].class.code").value("CL-1000"))
                .andExpect(jsonPath("$.details.conflicts[0].coach.name").value("Daniel Smith"))
                .andExpect(jsonPath("$.details.conflicts[0].facility.name").value("Football Pitch 1"))
                .andExpect(jsonPath("$.details.conflicts[0].conflictType").value("BOTH"));
    }

    @Test
    void calendar_missingRangeIsDelegatedForBusinessValidation() throws Exception {
        when(service.listSessions(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenThrow(new BusinessRuleException("From and to are required"));
        mvc.perform(get(BASE).with(user("manager").roles("MANAGER"))).andExpect(status().isBadRequest());
    }

    @Test
    void calendar_invalidDateReturns400() throws Exception {
        mvc.perform(get(BASE).param("from", "not-a-date").param("to", "2026-10-19")
                        .with(user("manager").roles("MANAGER"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
