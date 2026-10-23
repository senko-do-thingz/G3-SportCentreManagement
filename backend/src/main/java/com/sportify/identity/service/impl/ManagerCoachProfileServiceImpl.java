package com.sportify.identity.service.impl;

import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ManagerCoachProfileResponse;
import com.sportify.identity.dto.ManagerCoachProfileUpdateRequest;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.UserAvatarStore;
import com.sportify.identity.service.ManagerCoachProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ManagerCoachProfileServiceImpl implements ManagerCoachProfileService {

    private static final String ENTITY_TYPE = "USER_ACCOUNT";
    static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    private final CoachProfileRepository coachProfileRepository;
    private final UserAvatarStore avatarStore;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ManagerCoachProfileResponse getProfile(Long userId) {
        return toResponse(find(userId));
    }

    @Override
    @Transactional
    public ManagerCoachProfileResponse updateProfile(Long userId, ManagerCoachProfileUpdateRequest request, UserAccount actor) {
        CoachProfile profile = find(userId);
        UserAccount account = profile.getUserAccount();

        String avatarUrl = clean(request.avatarUrl());
        if (avatarUrl != null && !Objects.equals(avatarUrl, account.getAvatarUrl())
                && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
            try {
                URI uri = URI.create(avatarUrl);
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535) {
                    throw new IllegalArgumentException("Invalid public photo URL");
                }
            } catch (IllegalArgumentException ex) {
                throw new FieldValidationException("avatarUrl", "Use a valid http(s) photo link without credentials");
            }
        }
        if (avatarUrl != null && avatarUrl.startsWith("/api/v1/users/")
                && (!isUploaded(avatarUrl, userId) || !Objects.equals(avatarUrl, account.getAvatarUrl()))) {
            // Reject cross-account, fabricated and stale uploaded links, even when called outside MVC validation.
            throw new FieldValidationException("avatarUrl", "Use the current photo uploaded for this coach");
        }
        String headline = clean(request.headline());
        String bio = clean(request.bio());
        String specialties = clean(request.specialties());

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(account.getAvatarUrl(), avatarUrl)) changed.add("avatarUrl");
        if (!Objects.equals(profile.getHeadline(), headline)) changed.add("headline");
        if (!Objects.equals(profile.getBio(), bio)) changed.add("bio");
        if (!Objects.equals(profile.getYearsExperience(), request.yearsExperience())) changed.add("yearsExperience");
        if (!Objects.equals(profile.getSpecialties(), specialties)) changed.add("specialties");
        if (!Objects.equals(profile.getIsPublic(), request.isPublic())) changed.add("isPublic");
        if (!Objects.equals(profile.getDisplayOrder(), request.displayOrder())) changed.add("displayOrder");
        if (changed.isEmpty()) {
            // Nothing to change: no write and no activity log row.
            return toResponse(profile);
        }

        if (isUploaded(account.getAvatarUrl(), userId) && !isUploaded(avatarUrl, userId)) {
            // The uploaded photo is no longer used: remove it instead of keeping unreachable data.
            avatarStore.delete(userId);
        }
        account.setAvatarUrl(avatarUrl);
        profile.setHeadline(headline);
        profile.setBio(bio);
        profile.setYearsExperience(request.yearsExperience());
        profile.setSpecialties(specialties);
        profile.setIsPublic(request.isPublic());
        profile.setDisplayOrder(request.displayOrder());
        CoachProfile saved = coachProfileRepository.save(profile);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.USER_UPDATED)
                .entityType(ENTITY_TYPE)
                .entityId(userId)
                .summary("Coach profile updated")
                .details(Map.of("fields", changed))
                .build());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ManagerCoachProfileResponse uploadPhoto(Long userId, byte[] data, UserAccount actor) {
        CoachProfile profile = find(userId);
        if (data == null || data.length == 0) {
            throw new BusinessRuleException("Choose a photo to upload");
        }
        if (data.length > MAX_PHOTO_BYTES) {
            throw new BusinessRuleException("Photo must be at most 2 MB");
        }
        String contentType = CoachPhotoValidator.validate(data);

        avatarStore.save(userId, contentType, data);
        // Keep the FE URL grammar; the serving endpoint also disables caching and rechecks publication.
        String link = "/api/v1/users/" + userId + "/avatar?v=" + clock.millis();
        profile.getUserAccount().setAvatarUrl(link);
        CoachProfile saved = coachProfileRepository.save(profile);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.USER_UPDATED)
                .entityType(ENTITY_TYPE)
                .entityId(userId)
                .summary("Coach photo uploaded")
                .details(Map.of("fields", List.of("avatarUrl"), "contentType", contentType, "sizeBytes", data.length))
                .build());

        return toResponse(saved);
    }

    private static boolean isUploaded(String avatarUrl, Long userId) {
        return avatarUrl != null && avatarUrl.matches("/api/v1/users/" + userId + "/avatar(?:\\?v=\\d+)?");
    }

    private CoachProfile find(Long userId) {
        return coachProfileRepository.findWithAccountById(userId)
                .filter(profile -> profile.getUserAccount().getRole() != null
                        && "COACH".equals(profile.getUserAccount().getRole().getCode()))
                .orElseThrow(() -> new ResourceNotFoundException("Coach profile not found"));
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ManagerCoachProfileResponse toResponse(CoachProfile profile) {
        UserAccount account = profile.getUserAccount();
        return new ManagerCoachProfileResponse(
                profile.getId(),
                account.getFullName(),
                account.getAvatarUrl(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getYearsExperience(),
                profile.getSpecialties(),
                Boolean.TRUE.equals(profile.getIsPublic()),
                profile.getDisplayOrder() == null ? 0 : profile.getDisplayOrder());
    }
}
