package com.sportify.catalog.service;

import com.sportify.catalog.dto.ManagerSessionResponse;
import com.sportify.catalog.dto.SessionCancelRequest;
import com.sportify.catalog.dto.SessionCreateRequest;
import com.sportify.catalog.dto.SessionUpdateRequest;
import com.sportify.identity.entity.UserAccount;

import java.time.LocalDate;
import java.util.List;

public interface ManagerSessionService {
    List<ManagerSessionResponse> listSessions(LocalDate from, LocalDate to, Long classId, Long coachId, Long facilityId);
    List<ManagerSessionResponse> createSessions(Long classId, SessionCreateRequest request, UserAccount actor);
    ManagerSessionResponse updateSession(Long id, SessionUpdateRequest request, UserAccount actor);
    ManagerSessionResponse cancelSession(Long id, SessionCancelRequest request, UserAccount actor);
}
