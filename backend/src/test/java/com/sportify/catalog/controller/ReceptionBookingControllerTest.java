package com.sportify.catalog.controller;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.service.*;
import com.sportify.core.exception.*;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({BookingController.class, SessionController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ReceptionBookingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingService bookings;
    @MockitoBean MemberScheduleService schedule;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider provider;
    @MockitoBean UserDetailsService details;

    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void staffHistoryArrayAndPagedMappingsUseSameFiltersAndPrincipal(String role) throws Exception {
        var actor = account(role, 9);
        var item = BookingResponse.builder().id(300L).memberId(2L).packageRegistrationId(200L).build();
        when(bookings.getMemberBookings(eq(2L), eq(BookingWhen.UPCOMING), eq(BookingStatus.CONFIRMED), same(actor)))
                .thenReturn(List.of(item));
        when(bookings.getMemberBookings(eq(2L), eq(BookingWhen.UPCOMING), eq(BookingStatus.CONFIRMED), eq(0), eq(20), same(actor)))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/bookings/member/2?when=UPCOMING&status=CONFIRMED").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].memberId").value(2));
        mvc.perform(get("/api/v1/bookings/member/2?when=UPCOMING&status=CONFIRMED&page=0").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].packageRegistrationId").value(200))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1));
    }
    @Test void sizeOnlySelectsPaginationAndNoFiltersRetainArray() throws Exception {
        var actor = account("MANAGER", 9);
        when(bookings.getMemberBookings(2L, null, null, actor)).thenReturn(List.of());
        when(bookings.getMemberBookings(2L, null, null, 0, 5, actor)).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
        mvc.perform(get("/api/v1/bookings/member/2").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/v1/bookings/member/2?size=5").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.content").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"MEMBER", "COACH", "ADMIN"})
    void unsupportedRolesCannotReadAnySelectedMemberOrEligibility(String role) throws Exception {
        var actor = new CustomUserDetails(account(role, 9));
        for (String path : List.of("/api/v1/bookings/member/2", "/api/v1/bookings/member/2?page=0",
                "/api/v1/sessions/100/eligibility?memberId=2")) {
            mvc.perform(get(path).with(user(actor))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Access Denied"))
                    .andExpect(jsonPath("$.memberId").doesNotExist()).andExpect(jsonPath("$.suitableRegistrations").doesNotExist());
        }
        verifyNoInteractions(bookings, schedule);
    }
    @Test void anonymousRequestsAre401BeforeAnyService() throws Exception {
        mvc.perform(get("/api/v1/bookings/member/2")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sessions/100/eligibility?memberId=2")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":2,\"sessionId\":100,\"packageRegistrationId\":200}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(bookings, schedule);
    }
    @ParameterizedTest @ValueSource(strings = {"when=OTHER", "when=upcoming", "status=OTHER", "status=confirmed", "page=x", "size=x"})
    void malformedHistoryParametersAre400(String query) throws Exception {
        mvc.perform(get("/api/v1/bookings/member/2?" + query).with(user(new CustomUserDetails(account("MANAGER", 9)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(bookings);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/v1/sessions/100/eligibility", "/api/v1/sessions/100/eligibility?memberId=x",
            "/api/v1/sessions/x/eligibility?memberId=2", "/api/v1/bookings/member/x"})
    void missingOrMalformedIdsAre400(String path) throws Exception {
        mvc.perform(get(path).with(user(new CustomUserDetails(account("RECEPTIONIST", 9)))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bookings, schedule);
    }
    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void eligibilityUsesBE11ResponseAndValidatedPrincipal(String role) throws Exception {
        var actor = account(role, 9);
        when(schedule.getMemberEligibility(100L, 2L, actor)).thenReturn(
                new SessionDetailResponse(null, true, null, false, true, null, List.of()));
        mvc.perform(get("/api/v1/sessions/100/eligibility?memberId=2").with(user(new CustomUserDetails(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberContext").value(true))
                .andExpect(jsonPath("$.eligible").value(true)).andExpect(jsonPath("$.suitableRegistrations").isArray());
        verify(schedule).getMemberEligibility(100L, 2L, actor);
    }
    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void bookingBodyCannotSupplyActorOrRole(String role) throws Exception {
        var actor = account(role, 9);
        when(bookings.createBooking(any(), same(actor))).thenReturn(BookingResponse.builder().id(300L).memberId(2L).build());
        mvc.perform(post("/api/v1/bookings").with(user(new CustomUserDetails(actor))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":2,\"sessionId\":100,\"packageRegistrationId\":200,\"actorId\":777,\"staffId\":777,\"role\":\"MANAGER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.memberId").value(2));
        var request = ArgumentCaptor.forClass(BookingCreateRequest.class);
        verify(bookings).createBooking(request.capture(), same(actor));
        assertThat(request.getValue().getMemberId()).isEqualTo(2L);
        assertThat(request.getValue().getPackageRegistrationId()).isEqualTo(200L);
    }
    @ParameterizedTest @ValueSource(strings = {"{\"memberId\":0,\"sessionId\":100}", "{\"memberId\":-1,\"sessionId\":100}",
            "{\"memberId\":2,\"sessionId\":0}", "{\"memberId\":2,\"sessionId\":100,\"packageRegistrationId\":0}"})
    void nonpositiveBodyIdsAreRejected(String body) throws Exception {
        mvc.perform(post("/api/v1/bookings").with(user(new CustomUserDetails(account("MANAGER", 9))))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(bookings);
    }
    @Test void memberForeignBeneficiaryAndCoachBookingGet403() throws Exception {
        when(bookings.createBooking(any(), any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Foreign member"));
        mvc.perform(post("/api/v1/bookings").with(user(new CustomUserDetails(account("MEMBER", 9))))
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":2,\"sessionId\":100,\"packageRegistrationId\":200}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.memberId").doesNotExist());
        clearInvocations(bookings);
        mvc.perform(post("/api/v1/bookings").with(user(new CustomUserDetails(account("COACH", 9))))
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":2,\"sessionId\":100,\"packageRegistrationId\":200}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookings);
    }
    @ParameterizedTest @CsvSource({"FULL,409", "ALREADY_BOOKED,409", "NO_PACKAGE,400", "NO_SESSIONS_LEFT,400"})
    void bookingErrorsKeepApprovedEnvelope(String code, int expected) throws Exception {
        when(bookings.createBooking(any(), any())).thenThrow(expected == 409
                ? new ConflictException("Booking rejected", code) : new BusinessRuleException("Booking rejected", code));
        mvc.perform(post("/api/v1/bookings").with(user(new CustomUserDetails(account("MANAGER", 9))))
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":2,\"sessionId\":100,\"packageRegistrationId\":200}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.status").value(expected));
    }
    @Test void notFoundAndInvalidPaginationRetain404And400() throws Exception {
        when(bookings.getMemberBookings(eq(2L), any(), any(), any())).thenThrow(new ResourceNotFoundException("Member profile not found"));
        mvc.perform(get("/api/v1/bookings/member/2").with(user(new CustomUserDetails(account("MANAGER", 9)))))
                .andExpect(status().isNotFound());
        when(bookings.getMemberBookings(eq(2L), any(), any(), eq(-1), eq(20), any()))
                .thenThrow(new BusinessRuleException("Invalid pagination", "INVALID_PAGINATION"));
        mvc.perform(get("/api/v1/bookings/member/2?page=-1").with(user(new CustomUserDetails(account("MANAGER", 9)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }
}
