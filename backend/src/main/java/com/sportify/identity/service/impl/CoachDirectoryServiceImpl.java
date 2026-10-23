package com.sportify.identity.service.impl;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.dto.PublicCoachResponse;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.service.CoachDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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
        List<CoachProfile> coaches = findCoaches(sportId);
        Map<Long, List<Long>> sportsByCoach = sportsByCoach(coaches);
        return coaches.stream()
                .map(coach -> new CoachOptionResponse(
                        coach.getId(),
                        coach.getUserAccount().getFullName(),
                        coach.getHeadline(),
                        coach.getSpecialties(),
                        coach.getYearsExperience(),
                        sportsByCoach.getOrDefault(coach.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicCoachResponse> getPublicCoaches(Long sportId) {
        // Only profiles marked public, in the manager-defined display order, then by name.
        List<CoachProfile> coaches = findCoaches(sportId).stream()
                .filter(coach -> Boolean.TRUE.equals(coach.getIsPublic()))
                .sorted(Comparator.comparing((CoachProfile coach) -> coach.getDisplayOrder() == null ? 0 : coach.getDisplayOrder()))
                .toList();
        Map<Long, List<Long>> sportsByCoach = sportsByCoach(coaches);
        return coaches.stream()
                .map(coach -> new PublicCoachResponse(
                        coach.getId(),
                        coach.getUserAccount().getFullName(),
                        coach.getHeadline(),
                        coach.getSpecialties(),
                        coach.getYearsExperience(),
                        coach.getBio(),
                        coach.getUserAccount().getAvatarUrl(),
                        sportsByCoach.getOrDefault(coach.getId(), List.of())))
                .toList();
    }

    private List<CoachProfile> findCoaches(Long sportId) {
        return sportId == null
                ? coachProfileRepository.findActiveCoaches()
                : coachProfileRepository.findActiveCoachesBySport(sportId);
    }

    /** One query for the sports of every coach in the result, each list sorted by sport id. */
    private Map<Long, List<Long>> sportsByCoach(List<CoachProfile> coaches) {
        if (coaches.isEmpty()) {
            return Map.of();
        }
        return coachSportRepository
                .findByIdCoachIdIn(coaches.stream().map(CoachProfile::getId).toList())
                .stream()
                .map(CoachSport::getId)
                .collect(Collectors.groupingBy(
                        id -> id.getCoachId(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(id -> id.getSportId(), Collectors.toList()),
                                ids -> ids.stream().sorted().toList())));
    }
}
