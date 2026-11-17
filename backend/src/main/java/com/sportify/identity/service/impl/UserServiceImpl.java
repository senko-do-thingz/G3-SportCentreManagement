package com.sportify.identity.service.impl;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ChangePasswordRequest;
import com.sportify.identity.dto.DeleteAccountRequest;
import com.sportify.identity.dto.ProfileCardResponse;
import com.sportify.identity.dto.UpdateProfileRequest;
import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.mapper.UserMapper;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserAvatarStore;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sportify.identity.repository.UserRefreshTokenRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRepository users;
    private final MemberProfileRepository members;
    private final MemberCardRepository cards;
    private final CoachSportRepository coachSports;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final UserAvatarStore avatarStore;
    private final UserAvatarProcessor avatarProcessor;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(CustomUserDetails userDetails) {
        return profile(currentAccount(userDetails));
    }

    @Override
    @Transactional
    public UserProfileResponse updateCurrentUserProfile(CustomUserDetails userDetails, UpdateProfileRequest request) {
        UserAccount user = currentAccount(userDetails);
        if (request.has("fullName") && (request.getFullName() == null || request.getFullName().isBlank())) {
            throw new FieldValidationException("fullName", "Full name is required");
        }
        if (request.getDateOfBirth() != null && request.getDateOfBirth().isBefore(LocalDate.of(1900, 1, 1))) {
            throw new FieldValidationException("dateOfBirth", "Date of birth is invalid");
        }
        MemberProfile member = null;
        if (request.has("mainGoal") || request.has("currentLevel")) {
            if (!"MEMBER".equals(user.getRole().getCode())) {
                throw new FieldValidationException("mainGoal", "Training preferences apply only to members");
            }
            member = members.findById(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Member profile not found"));
        }

        // Guard member-only edits too. Account field writes already use its existing @Version.
        if (member != null) {
            entityManager.lock(user, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        if (request.has("fullName")) user.setFullName(request.getFullName());
        if (request.has("phone")) user.setPhone(request.getPhone());
        if (request.has("dateOfBirth")) user.setDateOfBirth(request.getDateOfBirth());
        if (request.has("address")) user.setAddress(request.getAddress());
        if (member != null) {
            if (request.has("mainGoal")) member.setMainGoal(request.getMainGoal());
            if (request.has("currentLevel")) {
                member.setCurrentLevel(request.getCurrentLevel() == null ? "BEGINNER" : request.getCurrentLevel());
            }
        }
        users.flush();
        return profile(user);
    }

    @Override
    @Transactional
    public void changeCurrentUserPassword(CustomUserDetails userDetails, ChangePasswordRequest request) {
        UserAccount user = currentAccount(userDetails);
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new FieldValidationException("confirmPassword", "Passwords do not match");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("currentPassword", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("newPassword", "New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // Flush the versioned update first: a stale check cannot commit or emit a successful audit.
        users.flush();
        userRefreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now(clock));
        auditService.record(AuditEvent.builder().actor(user).action(AuditAction.PASSWORD_CHANGED)
                .entityType("USER_ACCOUNT").entityId(user.getId()).summary("Password changed").build());
    }

    @Override
    @Transactional
    public void deleteCurrentUserAccount(CustomUserDetails userDetails, DeleteAccountRequest request) {
        UserAccount user = currentAccount(userDetails);
        if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new FieldValidationException("password", "Password is required to confirm account deletion");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("password", "Current password is incorrect");
        }
        user.setStatus("INACTIVE");
        users.save(user);
        users.flush();
        userRefreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now(clock));
        auditService.record(AuditEvent.builder()
                .actor(user)
                .action(AuditAction.USER_DELETED)
                .entityType("USER_ACCOUNT")
                .entityId(user.getId())
                .summary("Member deleted own account")
                .details(Map.of("status", "SUCCESS", "userId", user.getId(), "email", user.getEmail()))
                .build());
    }

    @Override
    @Transactional
    public UserProfileResponse uploadCurrentUserAvatar(CustomUserDetails userDetails, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a photo to upload");
        }
        if (file.getSize() > UserAvatarProcessor.MAX_AVATAR_BYTES) {
            throw new BusinessRuleException("Photo must be at most 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("Choose a photo to upload");
        }
        return uploadCurrentUserAvatar(userDetails, bytes);
    }

    @Override
    @Transactional
    public UserProfileResponse uploadCurrentUserAvatar(CustomUserDetails userDetails, byte[] data) {
        UserAccount user = currentAccount(userDetails);
        if (data == null || data.length == 0) {
            throw new BusinessRuleException("Choose a photo to upload");
        }
        if (data.length > UserAvatarProcessor.MAX_AVATAR_BYTES) {
            throw new BusinessRuleException("Photo must be at most 5 MB");
        }

        byte[] processedWebp = avatarProcessor.process(data);
        avatarStore.save(user.getId(), UserAvatarProcessor.OUTPUT_CONTENT_TYPE, processedWebp);

        String link = "COACH".equals(user.getRole().getCode())
                ? "/api/v1/users/" + user.getId() + "/avatar?v=" + clock.millis()
                : "/api/v1/users/me/avatar?v=" + clock.millis();
        user.setAvatarUrl(link);
        users.save(user);

        auditService.record(AuditEvent.builder()
                .actor(user)
                .action(AuditAction.USER_UPDATED)
                .entityType("USER_ACCOUNT")
                .entityId(user.getId())
                .summary("Avatar uploaded")
                .details(Map.of("fields", List.of("avatarUrl"), "contentType", UserAvatarProcessor.OUTPUT_CONTENT_TYPE, "sizeBytes", processedWebp.length))
                .build());

        return profile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAvatarStore.StoredAvatar getCurrentUserAvatar(CustomUserDetails userDetails) {
        UserAccount user = currentAccount(userDetails);
        return avatarStore.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    @Override
    @Transactional
    public void deleteCurrentUserAvatar(CustomUserDetails userDetails) {
        UserAccount user = currentAccount(userDetails);
        if (user.getAvatarUrl() == null) {
            return;
        }
        avatarStore.delete(user.getId());
        user.setAvatarUrl(null);
        users.save(user);

        auditService.record(AuditEvent.builder()
                .actor(user)
                .action(AuditAction.USER_UPDATED)
                .entityType("USER_ACCOUNT")
                .entityId(user.getId())
                .summary("Avatar removed")
                .details(Map.of("fields", List.of("avatarUrl")))
                .build());
    }

    private UserAccount currentAccount(CustomUserDetails principal) {
        if (principal == null || principal.getUserAccount().getId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        UserAccount user = users.findById(principal.getUserAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new DisabledException("Account is disabled");
        }
        return user;
    }

    private UserProfileResponse profile(UserAccount user) {
        UserProfileResponse response = userMapper.toProfileResponse(user);
        if ("MEMBER".equals(user.getRole().getCode())) {
            members.findById(user.getId()).ifPresent(member -> {
                response.setMemberCode(member.getMemberCode());
                response.setMainGoal(member.getMainGoal());
                response.setCurrentLevel(member.getCurrentLevel());
                response.setMembershipCard(cards.findCurrentForOverview(user.getId(), LocalDate.now(clock), PageRequest.of(0, 1))
                        .stream().findFirst().map(card -> new ProfileCardResponse(card.getId(), card.getTier().getCode(),
                                card.getTier().getName(), card.getStatus().name(), card.getStartDate(), card.getEndDate()))
                        .orElse(null));
            });
        } else if ("COACH".equals(user.getRole().getCode())) {
            response.setSports(coachSports.findProfileSports(user.getId()));
        }
        return response;
    }
}
