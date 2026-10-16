package com.sportify.catalog.controller;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.service.BookingService;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class BookingRulesControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider provider;
    @MockitoBean UserDetailsService detailsService;

    private CustomUserDetails actor(String role) {
        return new CustomUserDetails(UserAccount.builder().id(30L).role(Role.builder().code(role).build()).build());
    }
    private BookingResponse response() {
        return BookingResponse.builder().id(3L).sessionId(1L).packageRegistrationId(2L)
                .classId(4L).className("Fundamentals").coachId(null).coachName(null).facilityId(5L).facilityName("Court 1")
                .packageName("Purchased package").status(BookingStatus.CANCELLED).cancelledAt(LocalDateTime.of(2026, 10, 16, 10, 0))
                .cancelledById(30L).cancelledByName("Member").cancelledByRole("MEMBER").cancelReason("Cancelled by user").build();
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "MANAGER", "COACH"})
    void existingCreationCancellationAndMemberOnlyHistoryRoleMatrixIsPreserved(String role) throws Exception {
        when(service.createBooking(any(), any())).thenReturn(response());
        doReturn(response()).when(service).cancelBooking(any(), any());
        when(service.getMyBookings(any(UserAccount.class))).thenReturn(List.of());
        when(service.getMyBookings(any(), any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(post("/api/v1/bookings").with(user(actor(role))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"packageRegistrationId\":2}"))
                .andExpect(status().is(role.equals("COACH") ? 403 : 201));
        mvc.perform(delete("/api/v1/bookings/3").with(user(actor(role))))
                .andExpect(status().is(role.equals("COACH") ? 403 : 200));
        mvc.perform(get("/api/v1/bookings/my").with(user(actor(role))))
                .andExpect(status().is(role.equals("MEMBER") ? 200 : 403));
        mvc.perform(get("/api/v1/bookings/my?page=0").with(user(actor(role))))
                .andExpect(status().is(role.equals("MEMBER") ? 200 : 403));
        if (role.equals("COACH")) verifyNoInteractions(service);
    }

    @Test void anonymousRequestsReturn401ForAllBookingRoutes() throws Exception {
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/bookings/3")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/my?when=PAST&page=0")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void whenOnlyRetainsDashboardArrayAndExplicitPageUsesPageResponse() throws Exception {
        when(service.getMyBookings(any(), eq(BookingWhen.UPCOMING))).thenReturn(List.of(response()));
        when(service.getMyBookings(any(), eq(BookingWhen.UPCOMING), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/bookings/my?when=UPCOMING").with(user(actor("MEMBER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].packageName").value("Purchased package"));
        mvc.perform(get("/api/v1/bookings/my?when=UPCOMING&page=0").with(user(actor("MEMBER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void sizeOnlyAlsoSelectsPaginationWithDefaultPage() throws Exception {
        when(service.getMyBookings(any(), isNull(), eq(0), eq(5))).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
        mvc.perform(get("/api/v1/bookings/my?size=5").with(user(actor("MEMBER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.page").value(0));
    }

    @ParameterizedTest @ValueSource(strings = {"when=OTHER", "when=upcoming&page=0", "page=bad", "size=bad"})
    void malformedQueryIs400BeforeService(String query) throws Exception {
        mvc.perform(get("/api/v1/bookings/my?" + query).with(user(actor("MEMBER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"sessionId\":0}", "{\"sessionId\":-1}", "{\"sessionId\":1,\"packageRegistrationId\":0}"})
    void invalidBookingBodyNeverCallsService(String body) throws Exception {
        mvc.perform(post("/api/v1/bookings").with(user(actor("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation Error"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"NO_PACKAGE", "NO_SESSIONS_LEFT", "PACKAGE_EXPIRED_BY_THEN", "PACKAGE_NOT_STARTED_BY_THEN", "CAPACITY_UNAVAILABLE"})
    void ineligibilityKeepsEnvelopeAndAdds400MachineCode(String code) throws Exception {
        when(service.createBooking(any(), any())).thenThrow(new BusinessRuleException("Booking is not eligible", code));
        mvc.perform(post("/api/v1/bookings").with(user(actor("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Booking is not eligible")).andExpect(jsonPath("$.code").value(code));
    }

    @ParameterizedTest @ValueSource(strings = {"FULL", "ALREADY_BOOKED"})
    void bookingConflictsAre409WithMachineReason(String code) throws Exception {
        when(service.createBooking(any(), any())).thenThrow(new ConflictException("Booking conflict", code));
        mvc.perform(post("/api/v1/bookings").with(user(actor("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
    }

    @Test void cutoffUses400AndCancellationExposesSupportedMetadataWithoutAccountContacts() throws Exception {
        when(service.cancelBooking(any(), any())).thenThrow(new BusinessRuleException("Cutoff reached", "CANCELLATION_CUTOFF"));
        mvc.perform(delete("/api/v1/bookings/3").with(user(actor("MEMBER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANCELLATION_CUTOFF"));
        doReturn(response()).when(service).cancelBooking(any(), any());
        mvc.perform(delete("/api/v1/bookings/3").with(user(actor("MEMBER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledById").value(30))
                .andExpect(jsonPath("$.cancelledAt").value("2026-10-16T10:00:00"))
                .andExpect(jsonPath("$.cancelReason").value("Cancelled by user"))
                .andExpect(jsonPath("$.coachId").isEmpty()).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void notFoundAndForeignOwnershipPreserve404And403Policy() throws Exception {
        when(service.cancelBooking(any(), any())).thenThrow(new ResourceNotFoundException("Booking not found"));
        mvc.perform(delete("/api/v1/bookings/3").with(user(actor("MEMBER")))).andExpect(status().isNotFound());
        doThrow(new org.springframework.security.access.AccessDeniedException("Foreign booking")).when(service).cancelBooking(any(), any());
        mvc.perform(delete("/api/v1/bookings/3").with(user(actor("MEMBER")))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }
}
