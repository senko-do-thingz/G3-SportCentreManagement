package com.sportify.identity.service.impl;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.service.CoachDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoachDirectoryServiceImpl implements CoachDirectoryService {

    private final CoachProfileRepository coachProfileRepository;
    private final CoachSportRepository coachSportRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CoachOptionResponse> getActiveCoaches(Long sportId) {
        List<CoachProfile> coaches = sportId == null
                ? coachProfileRepository.findActiveCoaches()
                : coachProfileRepository.findActiveCoachesBySport(sportId);
        if (coaches.isEmpty()) {
            return List.of();
        }

        // One query for the sports of every coach in the result.
        Map<Long, List<Long>> sportsByCoach = coachSportRepository
                .findByIdCoachIdIn(coaches.stream().map(CoachProfile::getId).toList())
                .stream()
                .map(CoachSport::getId)
                .collect(Collectors.groupingBy(
                        id -> id.getCoachId(),
                        Collectors.mapping(id -> id.getSportId(), Collectors.toList())));

        return coaches.stream()
                .map(coach -> new CoachOptionResponse(
                        coach.getId(),
                        coach.getUserAccount().getFullName(),
                        coach.getHeadline(),
                        coach.getSpecialties(),
                        coach.getYearsExperience(),
                        sportsByCoach.getOrDefault(coach.getId(), List.of()).stream().sorted().toList()))
                .toList();
    }
}
