package com.sportify.identity.service.impl;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ChangePasswordRequest;
import com.sportify.identity.dto.ProfileCardResponse;
import com.sportify.identity.dto.UpdateProfileRequest;
import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.mapper.UserMapper;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.repository.MemberProfileRepository;
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

import java.time.Clock;
import java.time.LocalDate;

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
        auditService.record(AuditEvent.builder().actor(user).action(AuditAction.PASSWORD_CHANGED)
                .entityType("USER_ACCOUNT").entityId(user.getId()).summary("Password changed").build());
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
