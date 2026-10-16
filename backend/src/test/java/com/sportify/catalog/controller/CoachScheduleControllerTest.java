package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.CoachScheduleService;
import com.sportify.catalog.service.impl.CoachScheduleServiceImpl;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CoachScheduleController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, CoachScheduleServiceImpl.class})
class CoachScheduleControllerTest {
    static final String URL = "/api/v1/coach/sessions";
    static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CoachScheduleService service;
    @MockitoBean CoachScheduleReadRepository reads;
    @MockitoBean BookingRepository bookings;
    @MockitoBean CoachProfileRepository coaches;
    @MockitoBean UserRepository users;
    @MockitoBean JwtService jwt;
    @MockitoBean UserDetailsService details;
    @MockitoBean AuthenticationProvider authenticationProvider;

    UserAccount actor(String role) {
        return UserAccount.builder().id(17L).email("be14@example.test").fullName("Coach Reader")
                .role(Role.builder().code(role).build()).build();
    }
    UserAccount token(String role, String status, String type) {
        var account = actor(role); account.setStatus(status);
        var principal = new CustomUserDetails(account);
        when(jwt.extractUsername("fixture")).thenReturn(account.getEmail());
        when(jwt.extractTokenType("fixture")).thenReturn(type);
        when(jwt.isTokenValid("fixture", principal)).thenReturn(true);
        when(details.loadUserByUsername(account.getEmail())).thenReturn(principal);
        return account;
    }
    void reader(String role) {
        var account = token(role, "ACTIVE", "access");
        when(users.findById(17L)).thenReturn(Optional.of(account));
        if (role.equals("COACH")) when(coaches.existsById(17L)).thenReturn(true);
    }
    MockHttpServletRequestBuilder calendar() {
        return get(URL).param("from", DATE.toString()).param("to", DATE.toString())
                .header("Authorization", "Bearer fixture");
    }
    MockHttpServletRequestBuilder roster() {
        return get(URL + "/91/roster").header("Authorization", "Bearer fixture");
    }
    void assigned(Long owner) {
        var row = mock(CoachScheduleReadRepository.SessionOwnership.class);
        when(row.getCoachId()).thenReturn(owner);
        when(reads.findOwnership(91L)).thenReturn(Optional.of(row));
    }

    @ParameterizedTest @EnumSource(SessionStatus.class)
    void coachScheduleJsonKeepsAllSessionStatuses(SessionStatus status) throws Exception {
        reader("COACH");
        var row = new CoachSessionResponse(91L, 10L, "Class", 20L, "Sport", 17L, "Coach Reader", 30L, "Court",
                DATE, LocalTime.NOON, LocalTime.of(13, 0), 10, status);
        when(reads.findTeachingSessions(DATE, DATE, 17L)).thenReturn(List.of(row));
        var count = mock(BookingRepository.SessionBookingCount.class);
        when(count.getSessionId()).thenReturn(91L); when(count.getBookingCount()).thenReturn(3L);
        when(bookings.countForSessionCalendar(DATE, DATE, null, 17L, null, BookingStatus.CONFIRMED)).thenReturn(List.of(count));
        mvc.perform(calendar()).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(91))
                .andExpect(jsonPath("$[0].classId").value(10)).andExpect(jsonPath("$[0].className").value("Class"))
                .andExpect(jsonPath("$[0].sportId").value(20)).andExpect(jsonPath("$[0].sportName").value("Sport"))
                .andExpect(jsonPath("$[0].coachId").value(17)).andExpect(jsonPath("$[0].coachName").value("Coach Reader"))
                .andExpect(jsonPath("$[0].facilityId").value(30)).andExpect(jsonPath("$[0].facilityName").value("Court"))
                .andExpect(jsonPath("$[0].sessionDate").value("2026-10-16"))
                .andExpect(jsonPath("$[0].startTime").value("12:00:00")).andExpect(jsonPath("$[0].endTime").value("13:00:00"))
                .andExpect(jsonPath("$[0].capacity").value(10)).andExpect(jsonPath("$[0].bookedCount").value(3))
                .andExpect(jsonPath("$[0].status").value(status.name())).andExpect(jsonPath("$[0].memberPhone").doesNotExist());
    }

    @Test void managerFilterAndOmittedFilterContract() throws Exception {
        reader("MANAGER"); when(coaches.existsById(19L)).thenReturn(true);
        when(reads.findTeachingSessions(DATE, DATE, 19L)).thenReturn(List.of());
        when(reads.findTeachingSessions(DATE, DATE, null)).thenReturn(List.of());
        mvc.perform(calendar().param("coachId", "19")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(calendar()).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(reads).findTeachingSessions(DATE, DATE, 19L);
        verify(reads).findTeachingSessions(DATE, DATE, null);
        verify(coaches, never()).existsById(17L);
    }

    @Test void confirmedRosterJsonUsesOnlyOperationalFields() throws Exception {
        reader("COACH"); assigned(17L);
        var row = new CoachRosterResponse(81L, "MEM-EXAMPLE", "Nguy\u1ec5n", "0900000000", 71L, 61L,
                "Selected package", DATE.minusDays(2).atTime(9, 0));
        when(reads.findRoster(91L, BookingStatus.CONFIRMED, false, 17L)).thenReturn(List.of(row));
        var response = mvc.perform(roster()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberCode").value("MEM-EXAMPLE"))
                .andExpect(jsonPath("$[0].memberName").value("Nguy\u1ec5n"))
                .andExpect(jsonPath("$[0].memberPhone").value("0900000000"))
                .andExpect(jsonPath("$[0].packageRegistrationId").value(71))
                .andExpect(jsonPath("$[0].packageId").value(61)).andExpect(jsonPath("$[0].packageName").value("Selected package"))
                .andExpect(jsonPath("$[0].bookedAt").value("2026-10-14T09:00:00"))
                .andReturn().getResponse();
        var fields = new HashSet<String>();
        json.readTree(response.getContentAsByteArray()).get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("bookingId", "memberCode", "memberName", "memberPhone",
                "packageRegistrationId", "packageId", "packageName", "bookedAt");
    }

    @Test void managerReadsCoachlessLegacyRosterWithNullPhoneAndPackage() throws Exception {
        reader("MANAGER");
        when(reads.findOwnership(91L)).thenReturn(Optional.of(mock(CoachScheduleReadRepository.SessionOwnership.class)));
        when(reads.findRoster(91L, BookingStatus.CONFIRMED, true, null)).thenReturn(List.of(
                new CoachRosterResponse(81L, "MEM-EXAMPLE", "Member", null, null, null, null, DATE.atStartOfDay())));
        mvc.perform(roster()).andExpect(status().isOk()).andExpect(jsonPath("$[0].memberPhone").isEmpty())
                .andExpect(jsonPath("$[0].packageRegistrationId").isEmpty()).andExpect(jsonPath("$[0].packageName").isEmpty());
        verifyNoInteractions(coaches);
    }

    @Test void ownRosterEmpty200() throws Exception {
        reader("COACH"); assigned(17L);
        when(reads.findRoster(91L, BookingStatus.CONFIRMED, false, 17L)).thenReturn(List.of());
        mvc.perform(roster()).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {19L})
    void foreignOrUnassignedRoster403DoesNotQueryPrivateRows(Long owner) throws Exception {
        reader("COACH"); assigned(owner);
        mvc.perform(roster().param("coachId", "17").header("X-Coach-Id", "19"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access Denied")).andExpect(jsonPath("$.memberPhone").doesNotExist());
        verify(reads, never()).findRoster(anyLong(), any(), anyBoolean(), any());
    }

    @Test void coachCannotOverrideScheduleOwner() throws Exception {
        reader("COACH"); mvc.perform(calendar().param("coachId", "19")).andExpect(status().isForbidden());
        verifyNoInteractions(reads, bookings);
    }

    @Test void missingSessionAndUnknownManagerCoach404() throws Exception {
        reader("MANAGER"); when(reads.findOwnership(91L)).thenReturn(Optional.empty());
        mvc.perform(roster()).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Session not found"));
        mvc.perform(calendar().param("coachId", "999")).andExpect(status().isNotFound());
        verify(reads, never()).findRoster(anyLong(), any(), anyBoolean(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "ADMIN", "coach"})
    void unsupportedRoles403OnBothEndpoints(String role) throws Exception {
        token(role, "ACTIVE", "access");
        mvc.perform(calendar()).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(roster()).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(users, reads, bookings, coaches);
    }

    @ParameterizedTest @ValueSource(strings = {"", "/91/roster"})
    void anonymous401(String suffix) throws Exception {
        mvc.perform(get(URL + suffix).param("from", DATE.toString()).param("to", DATE.toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(users, reads, bookings, coaches);
    }

    @ParameterizedTest @CsvSource({"INACTIVE,access", "ACTIVE,refresh"})
    void inactiveOrRefreshCannotRead(String state, String type) throws Exception {
        token("COACH", state, type);
        mvc.perform(calendar()).andExpect(status().isUnauthorized());
        mvc.perform(roster()).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, reads, bookings, coaches);
    }

    @ParameterizedTest @CsvSource({"from,2026-02-30", "from,0000-01-01", "from,2026-10-17",
            "from,2026-01-01", "to,bad", "coachId,0", "coachId,-1", "coachId,bad"})
    void malformedScheduleParameters400(String field, String value) throws Exception {
        reader("MANAGER");
        var request = get(URL).header("Authorization", "Bearer fixture")
                .param("from", field.equals("from") ? value : DATE.toString())
                .param("to", field.equals("to") ? value : DATE.toString());
        if (field.equals("coachId")) request.param(field, value);
        mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(reads, bookings, coaches);
    }

    @ParameterizedTest @ValueSource(strings = {"from", "to", "both"})
    void requiredCalendarBounds(String missing) throws Exception {
        token("MANAGER", "ACTIVE", "access");
        var request = get(URL).header("Authorization", "Bearer fixture");
        if (!missing.equals("from") && !missing.equals("both")) request.param("from", DATE.toString());
        if (!missing.equals("to") && !missing.equals("both")) request.param("to", DATE.toString());
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(users, reads, bookings, coaches);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "bad"})
    void invalidRosterId400(String id) throws Exception {
        reader("MANAGER");
        mvc.perform(get(URL + "/" + id + "/roster").header("Authorization", "Bearer fixture"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(reads, bookings, coaches);
    }

    @Test void directServiceProxyRejectsMember() {
        var principal = new CustomUserDetails(actor("MEMBER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        try {
            assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> service.listSessions(DATE.toString(), DATE.toString(), null))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(users, reads, bookings, coaches);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
