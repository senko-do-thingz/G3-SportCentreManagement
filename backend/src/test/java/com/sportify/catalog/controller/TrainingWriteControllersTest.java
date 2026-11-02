package com.sportify.catalog.controller;

import com.sportify.catalog.dto.TrainingWriteResponse.*;
import com.sportify.catalog.service.BookingService;
import com.sportify.catalog.service.TrainingWriteService;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest({CoachTrainingController.class, ReceptionTrainingController.class, BookingController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
    JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class TrainingWriteControllersTest {
    @Autowired MockMvc mvc;
    @MockitoBean TrainingWriteService service;
    @MockitoBean BookingService bookingService;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;
    private CustomUserDetails account(String role) {
        return new CustomUserDetails(UserAccount.builder().id(10L).email("test@sportify.test").fullName(role)
            .role(Role.builder().code(role).name(role).build()).build());
    }
    private static final String PLAN = """
        {"classId":1,"memberId":42,"bookingId":1,"title":"Training","description":"Plan","steps":[{"title":"Warm up","durationMinutes":10}]}
        """;
    private static final String RESULT = """
        {"sessionId":1,"memberId":42,"notes":"Completed","scores":[{"metricId":1,"score":75.5}]}
        """;
    @Test void onlyAssignedRoleCanAccessEachWorkspace() throws Exception {
        for (String role : List.of("MEMBER", "RECEPTIONIST", "MANAGER")) {
            for (String path : List.of("classes", "classes/1/members", "plans", "plans/1", "sessions", "sessions/1/members", "metrics?sportId=1"))
                mvc.perform(get("/api/v1/coach/training/" + path).with(user(account(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/coach/training/plans").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content(PLAN)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/coach/training/results").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/coach/training/plans/1/feedback").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comments\":\"Progress\"}")).andExpect(status().isForbidden());
        }
        for (String role : List.of("MEMBER", "COACH", "MANAGER")) {
            for (String path : List.of("sessions", "sessions/1/members"))
                mvc.perform(get("/api/v1/reception/training/" + path).with(user(account(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/reception/training/attendance").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1,\"status\":\"PRESENT\"}")).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/reception/training/attendance/1").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LATE\",\"reason\":\"Correction\"}")).andExpect(status().isForbidden());
            if (!role.equals("MANAGER"))
            mvc.perform(post("/api/v1/bookings/self-training/attendance").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service, bookingService);
    }
    @Test void selfTrainingAttendanceRetainsReceptionAndManagerAccess() throws Exception {
        for (String role : List.of("RECEPTIONIST", "MANAGER")) {
            mvc.perform(post("/api/v1/bookings/self-training/attendance").with(user(account(role)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1}"))
                .andExpect(status().isOk());
        }
    }
    @Test void anonymousCannotCreateOrCorrectTrainingData() throws Exception {
        mvc.perform(post("/api/v1/coach/training/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/reception/training/attendance/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LATE\",\"reason\":\"Correction\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void disabledActorCannotUseTrainingWorkspaceEvenWithRoleAuthority() throws Exception {
        var coach = account("COACH");
        coach.getUserAccount().setStatus("INACTIVE");
        var reception = account("RECEPTIONIST");
        reception.getUserAccount().setStatus("INACTIVE");
        mvc.perform(get("/api/v1/coach/training/classes").with(user(coach))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/coach/training/plans").with(user(coach)).contentType(MediaType.APPLICATION_JSON).content(PLAN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/training/sessions").with(user(reception))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reception/training/attendance").with(user(reception)).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1,\"status\":\"PRESENT\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void inventedEnrollmentEndpointsAreNotExposed() throws Exception {
        mvc.perform(post("/api/v1/reception/training/enrollments").with(user(account("RECEPTIONIST")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"classId\":1,\"memberId\":42}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/reception/training/enrollments/1/leave").with(user(account("RECEPTIONIST"))))
            .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
    @Test void rejectsMissingFieldsInvalidNestedStepsInvalidStatusAndBlankCorrectionReason() throws Exception {
        mvc.perform(post("/api/v1/coach/training/plans").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content(PLAN.replace("\"durationMinutes\":10", "\"durationMinutes\":0"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/coach/training/plans").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/coach/training/plans/1/feedback").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":6,\"comments\":\"Progress\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reception/training/attendance").with(user(account("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1,\"status\":\"INVALID\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/reception/training/attendance/1").with(user(account("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LATE\",\"reason\":\" \"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void coachCreateUsesAuthenticatedCoachAndReturnsCreatedId() throws Exception {
        when(service.createPlan(eq(10L), any())).thenReturn(new Created(100));
        when(service.createResult(eq(10L), any())).thenReturn(new Created(200));
        when(service.feedback(eq(10L), eq(100L), any())).thenReturn(new Created(300));
        mvc.perform(post("/api/v1/coach/training/plans?coachId=99").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content(PLAN)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(100));
        mvc.perform(post("/api/v1/coach/training/results?coachId=99").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(200));
        mvc.perform(post("/api/v1/coach/training/plans/100/feedback?coachId=99").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comments\":\"Progress\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(300));
        verify(service).createPlan(eq(10L), any());
        verify(service).createResult(eq(10L), any());
        verify(service).feedback(eq(10L), eq(100L), any());
        verifyNoMoreInteractions(service);
    }
    @Test void receptionWritesUseAuthenticatedActor() throws Exception {
        when(service.attendance(eq(10L), any())).thenReturn(new Created(1));
        when(service.correctAttendance(eq(10L), eq(1L), any())).thenReturn(new Created(1));
        mvc.perform(post("/api/v1/reception/training/attendance?actorId=99").with(user(account("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1,\"status\":\"PRESENT\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(put("/api/v1/reception/training/attendance/1?actorId=99").with(user(account("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LATE\",\"reason\":\"Correction\"}")).andExpect(status().isOk());
        verify(service).attendance(eq(10L), any());
        verify(service).correctAttendance(eq(10L), eq(1L), any());
        verifyNoMoreInteractions(service);
    }
    @Test void R2_API_04_coachAttendanceAndCorrectionEnforceRole() throws Exception {
        String attendance="{\"bookingId\":1,\"status\":\"PRESENT\"}";
        String correction="{\"status\":\"LATE\",\"reason\":\"Correction\"}";
        mvc.perform(post("/api/v1/coach/training/attendance").contentType(MediaType.APPLICATION_JSON).content(attendance)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/coach/training/attendance/1").contentType(MediaType.APPLICATION_JSON).content(correction)).andExpect(status().isUnauthorized());
        for (String role : List.of("MEMBER", "MANAGER", "RECEPTIONIST")) {
            mvc.perform(post("/api/v1/coach/training/attendance").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content(attendance)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/coach/training/attendance/1").with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content(correction)).andExpect(status().isForbidden());
        }
        when(service.coachAttendance(eq(10L), any())).thenReturn(new Created(1));
        when(service.coachCorrection(eq(10L), eq(1L), any())).thenReturn(new Created(1));
        mvc.perform(post("/api/v1/coach/training/attendance").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content(attendance)).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/coach/training/attendance/1").with(user(account("COACH"))).contentType(MediaType.APPLICATION_JSON).content(correction)).andExpect(status().isOk());
    }
}
