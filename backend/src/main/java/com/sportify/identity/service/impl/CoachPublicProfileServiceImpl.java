package com.sportify.identity.service.impl;

import com.sportify.catalog.dto.SessionSearchQuery;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.MemberScheduleService;
import com.sportify.core.common.PagingDefaults;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.CoachPublicProfileResponse;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachCertificationRepository;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.service.CoachPublicProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachPublicProfileServiceImpl implements CoachPublicProfileService {
    private final CoachProfileRepository coaches;
    private final CoachSportRepository coachSports;
    private final CoachCertificationRepository certifications;
    private final SportRepository sports;
    private final MemberScheduleService schedule;

    @Override
    public CoachPublicProfileResponse getProfile(Long id, UserAccount actor) {
        if (actor == null || actor.getId() == null || !"ACTIVE".equals(actor.getStatus())
                || !(SecurityUtils.isMember(actor) || SecurityUtils.isStaff(actor))) {
            throw new AccessDeniedException("An authenticated member or staff account is required");
        }
        if (id == null || id <= 0) throw new BusinessRuleException("Coach id must be positive");
        var coach = coaches.findWithAccountById(id)
                .filter(c -> Boolean.TRUE.equals(c.getIsPublic()))
                .filter(c -> c.getUserAccount() != null && c.getUserAccount().getRole() != null
                        && "COACH".equals(c.getUserAccount().getRole().getCode())
                        && "ACTIVE".equals(c.getUserAccount().getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Coach profile not found: " + id));
        var sportIds = coachSports.findByIdCoachIdIn(List.of(id)).stream().map(c -> c.getId().getSportId()).distinct().toList();
        var taughtSports = sportIds.isEmpty() ? List.<CoachPublicProfileResponse.TaughtSport>of() :
                sports.findAllById(sportIds).stream().sorted(Comparator.comparing(com.sportify.catalog.entity.Sport::getId))
                        .map(s -> new CoachPublicProfileResponse.TaughtSport(s.getId(), s.getName())).toList();
        var publicCertifications = certifications.findByCoachIdOrderByIdAsc(id).stream()
                .map(c -> new CoachPublicProfileResponse.Certification(c.getId(), c.getSportId(), c.getName(),
                        c.getIssuer(), c.getIssuedOn(), c.getExpiresOn(), Boolean.TRUE.equals(c.getIsVerified()))).toList();
        var upcoming = schedule.listSessions(new SessionSearchQuery(null, null, null, null, null, id,
                0, PagingDefaults.DEFAULT_PAGE_SIZE), actor);
        return new CoachPublicProfileResponse(id, coach.getUserAccount().getFullName(), coach.getHeadline(),
                coach.getBio(), coach.getYearsExperience(), coach.getSpecialties(), taughtSports, publicCertifications, upcoming);
    }
}
