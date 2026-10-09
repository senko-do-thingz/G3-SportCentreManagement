package com.sportify.catalog.controller;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceResponse;
import com.sportify.catalog.service.BookingService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return bookingService.createBooking(request, userDetails.getUserAccount());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    public BookingResponse cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return bookingService.cancelBooking(id, userDetails.getUserAccount());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('MEMBER')")
    public List<BookingResponse> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return bookingService.getMyBookings(userDetails.getUserAccount());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public List<BookingResponse> getMemberBookings(@PathVariable Long memberId) {
        return bookingService.getMemberBookings(memberId);
    }

    @GetMapping("/today/{memberId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public List<BookingResponse> getTodayBookings(@PathVariable Long memberId) {
        return bookingService.getTodayBookings(memberId);
    }

    @PostMapping("/self-training/attendance")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public SelfTrainingAttendanceResponse recordSelfTrainingAttendance(
            @Valid @RequestBody SelfTrainingAttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return bookingService.recordSelfTrainingAttendance(request, userDetails.getUserAccount());
    }
}
