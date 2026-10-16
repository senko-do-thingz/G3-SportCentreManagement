package com.sportify.catalog.service;

import com.sportify.catalog.dto.CoachRosterResponse;
import com.sportify.catalog.dto.CoachSessionResponse;
import java.util.List;

public interface CoachScheduleService {
    List<CoachSessionResponse> listSessions(String from, String to, Long coachId);
    List<CoachRosterResponse> roster(Long sessionId);
}
