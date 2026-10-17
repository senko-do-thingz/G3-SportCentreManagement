package com.sportify.catalog.service;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.identity.entity.UserAccount;

import java.util.List;
import java.time.LocalDate;

public interface CheckInService {
    CheckInResponse recordCheckIn(CheckInRequest request, UserAccount receptionist);
    List<CheckInResponse> getCheckInHistory(Long memberId);
    List<CheckInResponse> getCheckInsOn(LocalDate date, Long memberId);
}
