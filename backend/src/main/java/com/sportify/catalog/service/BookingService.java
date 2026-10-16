package com.sportify.catalog.service;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.dto.BookingWhen;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceResponse;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(BookingCreateRequest request, UserAccount actor);
    BookingResponse cancelBooking(Long bookingId, UserAccount actor);
    List<BookingResponse> getMyBookings(UserAccount currentUser);
    List<BookingResponse> getMyBookings(UserAccount currentUser, BookingWhen when);
    PageResponse<BookingResponse> getMyBookings(UserAccount currentUser, BookingWhen when, int page, int size);
    List<BookingResponse> getMemberBookings(Long memberId, BookingWhen when, BookingStatus status, UserAccount actor);
    PageResponse<BookingResponse> getMemberBookings(Long memberId, BookingWhen when, BookingStatus status,
            int page, int size, UserAccount actor);
    List<BookingResponse> getTodayBookings(Long memberId);
    SelfTrainingAttendanceResponse recordSelfTrainingAttendance(SelfTrainingAttendanceRequest request, UserAccount receptionist);
}
