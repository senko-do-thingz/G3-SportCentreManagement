package com.sportify.catalog.service;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceResponse;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(BookingCreateRequest request, UserAccount actor);
    BookingResponse cancelBooking(Long bookingId, UserAccount actor);
    List<BookingResponse> getMyBookings(UserAccount currentUser);
    List<BookingResponse> getMemberBookings(Long memberId);
    List<BookingResponse> getTodayBookings(Long memberId);
    SelfTrainingAttendanceResponse recordSelfTrainingAttendance(SelfTrainingAttendanceRequest request, UserAccount receptionist);
}
