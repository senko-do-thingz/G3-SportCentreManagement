package com.sportify.identity.service.impl;

import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.CoachSelfCertificationCreateRequest;
import com.sportify.identity.dto.CoachSelfProfileResponse;
import com.sportify.identity.dto.CoachSelfProfileUpdateRequest;
import com.sportify.identity.dto.ManagerCoachProfileUpdateRequest;
import com.sportify.identity.entity.CoachCertification;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachCertificationRepository;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.service.CoachSelfProfileService;
import com.sportify.identity.service.ManagerCoachProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoachSelfProfileServiceImpl implements CoachSelfProfileService {

    private static final String ENTITY_TYPE = "USER_ACCOUNT";

    private final CoachProfileRepository coachProfileRepository;
    private final CoachSportRepository coachSportRepository;
    private final CoachCertificationRepository certificationRepository;
    private final SportRepository sportRepository;
    private final ManagerCoachProfileService managerCoachProfileService;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CoachSelfProfileResponse getMyProfile(UserAccount actor) {
        CoachProfile profile = requireCoachProfile(actor);
        return buildResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoachSelfProfileResponse.CertificationOption> getCertificationOptions(UserAccount actor) {
        requireCoachProfile(actor);
        return loadCertificationOptions(sportNameMap());
    }

    @Override
    @Transactional
    public CoachSelfProfileResponse updateMyProfile(CoachSelfProfileUpdateRequest request, UserAccount actor) {
        CoachProfile profile = requireCoachProfile(actor);
        String avatarUrl = request.avatarUrl() != null ? request.avatarUrl() : profile.getUserAccount().getAvatarUrl();
        // Tai su dung ManagerCoachProfileService de xac thuc va luu ho so ma khong thay doi isPublic va displayOrder
        managerCoachProfileService.updateProfile(
                profile.getId(),
                new ManagerCoachProfileUpdateRequest(
                        avatarUrl,
                        request.headline(),
                        request.bio(),
                        request.yearsExperience(),
                        request.specialties(),
                        Boolean.TRUE.equals(profile.getIsPublic()),
                        profile.getDisplayOrder() == null ? 0 : profile.getDisplayOrder()
                ),
                actor
        );
        return buildResponse(requireCoachProfile(actor));
    }

    @Override
    @Transactional
    public CoachSelfProfileResponse uploadMyPhoto(byte[] data, UserAccount actor) {
        CoachProfile profile = requireCoachProfile(actor);
        managerCoachProfileService.uploadPhoto(profile.getId(), data, actor);
        return buildResponse(requireCoachProfile(actor));
    }

    @Override
    @Transactional
    public CoachSelfProfileResponse addCertification(CoachSelfCertificationCreateRequest request, UserAccount actor) {
        CoachProfile profile = requireCoachProfile(actor);
        String name = clean(request.name());
        String issuer = clean(request.issuer());
        Long sportId = request.sportId();
        if (name == null) {
            throw new FieldValidationException("name", "Choose a certification from the list");
        }
        if (request.issuedOn() != null && request.expiresOn() != null && request.expiresOn().isBefore(request.issuedOn())) {
            throw new FieldValidationException("expiresOn", "Expiry date must not be before the issue date");
        }

        Map<Long, String> sportNames = sportNameMap();
        List<CoachSelfProfileResponse.CertificationOption> options = loadCertificationOptions(sportNames);
        // Kiem tra chung chi phai nam trong danh sach lua chon chung chi co san cua trung tam
        CoachSelfProfileResponse.CertificationOption matched = options.stream()
                .filter(opt -> opt.name().equalsIgnoreCase(name)
                        && Objects.equals(clean(opt.issuer()), issuer)
                        && Objects.equals(opt.sportId(), sportId))
                .findFirst()
                .orElseGet(() -> options.stream()
                        .filter(opt -> opt.name().equalsIgnoreCase(name)
                                && (issuer == null || Objects.equals(clean(opt.issuer()), issuer)))
                        .findFirst()
                        .orElse(null));
        if (matched == null) {
            throw new BusinessRuleException("Select an available certification from the center catalog");
        }

        List<CoachCertification> current = certificationRepository.findByCoachIdOrderByIdAsc(profile.getId());
        boolean duplicate = current.stream().anyMatch(c ->
                c.getName().equalsIgnoreCase(matched.name())
                        && Objects.equals(clean(c.getIssuer()), clean(matched.issuer()))
                        && Objects.equals(c.getSportId(), matched.sportId()));
        if (duplicate) {
            throw new ConflictException("This certification is already listed on your profile");
        }

        CoachCertification created = CoachCertification.builder()
                .coach(profile)
                .sportId(matched.sportId())
                .name(matched.name())
                .issuer(matched.issuer())
                .issuedOn(request.issuedOn())
                .expiresOn(request.expiresOn())
                .isVerified(false)
                .createdAt(LocalDateTime.now(clock))
                .build();
        CoachCertification saved = certificationRepository.save(created);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.USER_UPDATED)
                .entityType(ENTITY_TYPE)
                .entityId(profile.getId())
                .summary("Coach certification added")
                .details(Map.of("certificationId", saved.getId(), "name", saved.getName()))
                .build());

        return buildResponse(profile);
    }

    @Override
    @Transactional
    public CoachSelfProfileResponse removeCertification(Long certificationId, UserAccount actor) {
        CoachProfile profile = requireCoachProfile(actor);
        if (certificationId == null || certificationId <= 0) {
            throw new BusinessRuleException("Certification id must be positive");
        }
        CoachCertification cert = certificationRepository.findByIdAndCoachId(certificationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found"));
        certificationRepository.delete(cert);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.USER_UPDATED)
                .entityType(ENTITY_TYPE)
                .entityId(profile.getId())
                .summary("Coach certification removed")
                .details(Map.of("certificationId", certificationId, "name", cert.getName()))
                .build());

        return buildResponse(profile);
    }

    private CoachProfile requireCoachProfile(UserAccount actor) {
        if (actor == null || actor.getId() == null || actor.getRole() == null
                || !"COACH".equals(actor.getRole().getCode()) || !"ACTIVE".equals(actor.getStatus())) {
            throw new AccessDeniedException("An active coach account is required");
        }
        return coachProfileRepository.findWithAccountById(actor.getId())
                .filter(p -> p.getUserAccount() != null && p.getUserAccount().getRole() != null
                        && "COACH".equals(p.getUserAccount().getRole().getCode()))
                .orElseThrow(() -> new ResourceNotFoundException("Coach profile not found"));
    }

    private CoachSelfProfileResponse buildResponse(CoachProfile profile) {
        UserAccount account = profile.getUserAccount();
        Map<Long, String> sportNames = sportNameMap();
        List<Long> sportIds = coachSportRepository.findByIdCoachIdIn(List.of(profile.getId())).stream()
                .map(cs -> cs.getId().getSportId())
                .distinct()
                .sorted()
                .toList();
        List<CoachSelfProfileResponse.TaughtSport> taughtSports = sportIds.stream()
                .filter(sportNames::containsKey)
                .map(id -> new CoachSelfProfileResponse.TaughtSport(id, sportNames.get(id)))
                .toList();
        List<CoachSelfProfileResponse.Certification> certifications = certificationRepository
                .findByCoachIdOrderByIdAsc(profile.getId()).stream()
                .map(c -> new CoachSelfProfileResponse.Certification(
                        c.getId(),
                        c.getSportId(),
                        c.getSportId() == null ? null : sportNames.get(c.getSportId()),
                        c.getName(),
                        c.getIssuer(),
                        c.getIssuedOn(),
                        c.getExpiresOn(),
                        Boolean.TRUE.equals(c.getIsVerified())
                ))
                .toList();
        List<CoachSelfProfileResponse.CertificationOption> options = loadCertificationOptions(sportNames);
        return new CoachSelfProfileResponse(
                profile.getId(),
                account.getFullName(),
                account.getEmail(),
                account.getAvatarUrl(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getYearsExperience(),
                profile.getSpecialties(),
                Boolean.TRUE.equals(profile.getIsPublic()),
                profile.getDisplayOrder() == null ? 0 : profile.getDisplayOrder(),
                taughtSports,
                certifications,
                options
        );
    }

    private Map<Long, String> sportNameMap() {
        return sportRepository.findAll().stream()
                .collect(Collectors.toMap(Sport::getId, Sport::getName, (a, b) -> a));
    }

    // Tong hop danh sach chung chi tu cac ban ghi coach_certification hien co trong he thong
    private List<CoachSelfProfileResponse.CertificationOption> loadCertificationOptions(Map<Long, String> sportNames) {
        Map<String, CoachSelfProfileResponse.CertificationOption> unique = new LinkedHashMap<>();
        for (CoachCertification row : certificationRepository.findAllOrdered()) {
            String name = clean(row.getName());
            if (name == null) continue;
            String issuer = clean(row.getIssuer());
            Long sportId = row.getSportId();
            String key = optionKey(name, issuer, sportId);
            unique.putIfAbsent(key, new CoachSelfProfileResponse.CertificationOption(
                    key,
                    name,
                    issuer,
                    sportId,
                    sportId == null ? null : sportNames.get(sportId)
            ));
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(CoachSelfProfileResponse.CertificationOption::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(opt -> opt.issuer() == null ? "" : opt.issuer(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String optionKey(String name, String issuer, Long sportId) {
        return name + "|" + (issuer == null ? "" : issuer) + "|" + (sportId == null ? "" : sportId);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
