package com.sportify.catalog.controller;

import com.sportify.catalog.service.MemberTrainingService;
import com.sportify.catalog.dto.MemberTrainingResponse.*;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberTrainingController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
    JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class MemberTrainingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberTrainingService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;

    private CustomUserDetails account(String role) {
        return new CustomUserDetails(UserAccount.builder().id(42L).email("test@sportify.test")
            .fullName("Member").role(Role.builder().code(role).name(role).build()).build());
    }
    @Test void anonymousCannotReadTraining() throws Exception {
        for (String path : List.of("plans", "plans/1", "attendance", "results", "results/1", "feedback")) {
            mvc.perform(get("/api/v1/member/training/" + path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
    @Test void disabledMemberCannotReadTraining() throws Exception {
        var member = account("MEMBER");
        member.getUserAccount().setStatus("INACTIVE");
        mvc.perform(get("/api/v1/member/training/plans").with(user(member))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void staffCannotReadMemberTraining() throws Exception {
        for (String role : List.of("MANAGER", "COACH", "RECEPTIONIST")) {
            for (String path : List.of("plans", "plans/1", "attendance", "results", "results/1", "feedback")) {
                mvc.perform(get("/api/v1/member/training/" + path).with(user(account(role))))
                    .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }
    @Test void ignoresSpoofedMemberIdAndUsesPrincipal() throws Exception {
        when(service.attendance(42L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/member/training/attendance?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).attendance(42L);
        verifyNoMoreInteractions(service);
    }
    @Test void memberListsUsePrincipalAndExposeExpectedContracts() throws Exception {
        var session = trainingSession();
        when(service.plans(42L)).thenReturn(List.of(new Plan(7L, "Breathing", null, "Swimming", "Coach An")));
        when(service.attendance(42L)).thenReturn(List.of(new Attendance(1L, session, "PRESENT", null, 3L, "CONFIRMED", "PUBLISHED", "PRESENT")));
        when(service.results(42L)).thenReturn(List.of(new Result(2L, session, "Completed")));
        when(service.feedback(42L)).thenReturn(List.of(coachFeedback(session)));

        mvc.perform(get("/api/v1/member/training/plans?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Breathing"))
            .andExpect(jsonPath("$[0].coachName").value("Coach An"));
        mvc.perform(get("/api/v1/member/training/attendance?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PRESENT"))
            .andExpect(jsonPath("$[0].session.date").value("2026-10-30"))
            .andExpect(jsonPath("$[0].session.startTime").value("08:00:00"));
        mvc.perform(get("/api/v1/member/training/results?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].notes").value("Completed"));
        mvc.perform(get("/api/v1/member/training/feedback?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].comments").value("Good progress"))
            .andExpect(jsonPath("$[0].areasForImprovement").value("Breathing"));

        verify(service).plans(42L);
        verify(service).attendance(42L);
        verify(service).results(42L);
        verify(service).feedback(42L);
        verifyNoMoreInteractions(service);
    }
    @Test void memberDetailsSerializeStepsScoresAndFeedback() throws Exception {
        var session = trainingSession();
        when(service.plan(42L, 7L)).thenReturn(new PlanDetail(
            new Plan(7L, "Breathing", "Description", "Swimming", "Coach An"),
            List.of(new Step(1L, 1, "Warm up", 10, "Rotate shoulders")), List.of(coachFeedback(session))));
        when(service.result(42L, 2L)).thenReturn(new ResultDetail(new Result(2L, session, "Completed"),
            List.of(new Score(3L, "Endurance", null, new BigDecimal("75.50"), 100))));

        mvc.perform(get("/api/v1/member/training/plans/7?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.plan.id").value(7))
            .andExpect(jsonPath("$.steps[0].stepOrder").value(1))
            .andExpect(jsonPath("$.steps[0].durationMinutes").value(10))
            .andExpect(jsonPath("$.feedback[0].planId").value(7));
        mvc.perform(get("/api/v1/member/training/results/2?memberId=99").with(user(account("MEMBER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.session.id").value(5))
            .andExpect(jsonPath("$.scores[0].score").value(75.5))
            .andExpect(jsonPath("$.scores[0].maxScore").value(100))
            .andExpect(jsonPath("$.feedback").doesNotExist());

        verify(service).plan(42L, 7L);
        verify(service).result(42L, 2L);
        verifyNoMoreInteractions(service);
    }

    private Session trainingSession() {
        return new Session(5L, "Swimming", LocalDate.of(2026, 10, 30), LocalTime.of(8, 0), LocalTime.of(9, 0));
    }

    private Feedback coachFeedback(Session session) {
        return new Feedback(3L, 7L, "Breathing", "Coach An", 4, "Good progress", "Endurance", "Breathing",
            LocalDateTime.of(2026, 10, 30, 10, 0));
    }
    @Test void inaccessibleDetailsReturn404() throws Exception {
        when(service.plan(42L, 99L)).thenThrow(new ResourceNotFoundException("Training plan not found"));
        when(service.result(42L, 99L)).thenThrow(new ResourceNotFoundException("Training result not found"));
        mvc.perform(get("/api/v1/member/training/plans/99").with(user(account("MEMBER"))))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/member/training/results/99").with(user(account("MEMBER"))))
            .andExpect(status().isNotFound());
    }
    @Test void memberCannotEditPlans() throws Exception {
        mvc.perform(put("/api/v1/member/training/plans/1").with(user(account("MEMBER"))))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
