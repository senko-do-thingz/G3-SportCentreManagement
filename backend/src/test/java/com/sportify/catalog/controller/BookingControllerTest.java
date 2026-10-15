package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.service.BookingService;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
public class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails createUserDetails(Long id, String roleCode, String fullName) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        UserAccount account = UserAccount.builder()
                .id(id)
                .email(roleCode.toLowerCase() + "@sportify.com")
                .fullName(fullName)
                .role(role)
                .build();
        return new CustomUserDetails(account);
    }

    @Test
    void cancelBooking_AsMember_Returns200WithCancelledByNameAndRole() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Member User");
        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .bookingCode("BK-001")
                .status(BookingStatus.CANCELLED)
                .cancelledByName("Member User")
                .cancelledByRole("MEMBER")
                .build();

        when(bookingService.cancelBooking(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(delete("/api/v1/bookings/1")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByName").value("Member User"))
                .andExpect(jsonPath("$.cancelledByRole").value("MEMBER"));
    }

    @Test
    void cancelBooking_AsReceptionist_Returns200WithCancelledByNameAndRole() throws Exception {
        CustomUserDetails receptionist = createUserDetails(200L, "RECEPTIONIST", "Receptionist Staff");
        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .bookingCode("BK-001")
                .status(BookingStatus.CANCELLED)
                .cancelledByName("Receptionist Staff")
                .cancelledByRole("RECEPTIONIST")
                .build();

        when(bookingService.cancelBooking(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(delete("/api/v1/bookings/1")
                        .with(user(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByName").value("Receptionist Staff"))
                .andExpect(jsonPath("$.cancelledByRole").value("RECEPTIONIST"));
    }

    @Test
    void cancelBooking_AsManager_Returns200WithCancelledByNameAndRole() throws Exception {
        CustomUserDetails manager = createUserDetails(300L, "MANAGER", "Center Manager");
        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .bookingCode("BK-001")
                .status(BookingStatus.CANCELLED)
                .cancelledByName("Center Manager")
                .cancelledByRole("MANAGER")
                .build();

        when(bookingService.cancelBooking(eq(1L), any(UserAccount.class))).thenReturn(response);

        mockMvc.perform(delete("/api/v1/bookings/1")
                        .with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByName").value("Center Manager"))
                .andExpect(jsonPath("$.cancelledByRole").value("MANAGER"));
    }

    @Test
    void createBooking_DuplicateBooking_Returns409ConflictWithMessage() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Member User");
        BookingCreateRequest request = BookingCreateRequest.builder()
                .sessionId(10L)
                .build();

        when(bookingService.createBooking(any(BookingCreateRequest.class), any(UserAccount.class)))
                .thenThrow(new ConflictException("Member already has a confirmed booking for this session"));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Member already has a confirmed booking for this session"));
    }

    @Test
    void createBooking_DataIntegrityViolationUxBookingActive_Returns409ConflictWithMessage() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Member User");
        BookingCreateRequest request = BookingCreateRequest.builder()
                .sessionId(10L)
                .build();

        when(bookingService.createBooking(any(BookingCreateRequest.class), any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("Violation of unique index 'ux_booking_active'"));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Member already has a confirmed booking for this session"));
    }

    @Test
    void createBooking_Success_Returns201Created() throws Exception {
        CustomUserDetails member = createUserDetails(100L, "MEMBER", "Member User");
        BookingCreateRequest request = BookingCreateRequest.builder()
                .sessionId(10L)
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .bookingCode("BK-001")
                .sessionId(10L)
                .sessionDate(LocalDate.of(2026, 10, 20))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .sportName("Badminton")
                .status(BookingStatus.CONFIRMED)
                .bookedAt(LocalDateTime.of(2026, 10, 10, 10, 0))
                .build();

        when(bookingService.createBooking(any(BookingCreateRequest.class), any(UserAccount.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.bookingCode").value("BK-001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
