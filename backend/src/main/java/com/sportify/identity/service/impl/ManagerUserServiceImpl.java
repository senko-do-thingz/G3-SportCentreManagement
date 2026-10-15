package com.sportify.identity.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ManagerUserResponse;
import com.sportify.identity.dto.StaffAccountCreateRequest;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import com.sportify.identity.mapper.ManagerUserMapper;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.repository.UserSpecifications;
import com.sportify.identity.service.ManagerUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerUserServiceImpl implements ManagerUserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final String ROLE_COACH = "COACH";
    private static final Set<String> ALL_ROLES = Set.of("MEMBER", "COACH", "RECEPTIONIST", "MANAGER");
    private static final Set<String> STAFF_ROLES = Set.of("RECEPTIONIST", "COACH", "MANAGER");

    private static final String ENTITY_TYPE = "USER_ACCOUNT";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final CoachSportRepository coachSportRepository;
    private final SportRepository sportRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ManagerUserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ManagerUserResponse> listUsers(String role, UserStatus status, String search, int page, int size) {
        String roleCode = normalizeRoleFilter(role);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);

        Page<UserAccount> users = userRepository.findAll(
                UserSpecifications.withFilters(roleCode, status, search), pageable);
        return PageResponse.from(users.map(userMapper::toResponse));
    }

    @Override
    @Transactional
    public ManagerUserResponse createStaffAccount(StaffAccountCreateRequest request, UserAccount actor) {
        String roleCode = request.role().trim().toUpperCase(Locale.ROOT);
        if (!STAFF_ROLES.contains(roleCode)) {
            throw new BusinessRuleException("Role must be one of RECEPTIONIST, COACH, MANAGER");
        }

        List<Long> sportIds = request.sportIds() == null ? List.of() : request.sportIds().stream().distinct().toList();
        boolean isCoach = ROLE_COACH.equals(roleCode);
        if (isCoach) {
            validateCoachSports(sportIds);
        } else if (!sportIds.isEmpty()) {
            throw new BusinessRuleException("Sports can only be assigned to a COACH");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone number is already registered");
        }

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));

        UserAccount user = UserAccount.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.temporaryPassword()))
                .role(role)
                .status(UserStatus.ACTIVE.name())
                .build();

        UserAccount saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race against another request that used the same email or phone.
            throw new ConflictException("Email or phone number is already registered");
        }

        if (isCoach) {
            createCoachRows(saved, sportIds);
        }

        Map<String, Object> details = isCoach
                ? Map.of("role", roleCode, "sportIds", sportIds)
                : Map.of("role", roleCode);
        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.USER_CREATED)
                .entityType(ENTITY_TYPE)
                .entityId(saved.getId())
                .summary("Staff account created with role " + roleCode)
                .details(details)
                .build());

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ManagerUserResponse changeStatus(Long userId, UserStatus status, UserAccount actor) {
        if (status == UserStatus.INACTIVE && actor != null && userId.equals(actor.getId())) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String previous = user.getStatus();
        if (status.name().equals(previous)) {
            // Nothing to change: no write and no activity log row.
            return userMapper.toResponse(user);
        }

        user.setStatus(status.name());
        UserAccount saved = userRepository.save(user);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.USER_STATUS_CHANGED)
                .entityType(ENTITY_TYPE)
                .entityId(saved.getId())
                .summary("User status changed from " + previous + " to " + status.name())
                .details(Map.of("from", String.valueOf(previous), "to", status.name()))
                .build());

        return userMapper.toResponse(saved);
    }

    private String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String code = role.trim().toUpperCase(Locale.ROOT);
        if (!ALL_ROLES.contains(code)) {
            throw new BusinessRuleException("Role must be one of MEMBER, COACH, RECEPTIONIST, MANAGER");
        }
        return code;
    }

    private void validateCoachSports(List<Long> sportIds) {
        if (sportIds.isEmpty()) {
            throw new BusinessRuleException("At least one sport is required for a coach");
        }
        Set<Long> activeIds = sportRepository.findAllById(sportIds).stream()
                .filter(sport -> Boolean.TRUE.equals(sport.getIsActive()))
                .map(Sport::getId)
                .collect(Collectors.toSet());
        List<Long> invalid = sportIds.stream().filter(id -> !activeIds.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new BusinessRuleException("Unknown or inactive sport id(s): " + invalid);
        }
    }

    private void createCoachRows(UserAccount coach, List<Long> sportIds) {
        coachProfileRepository.save(CoachProfile.builder()
                .userAccount(coach)
                .primarySportId(sportIds.get(0))
                .build());
        coachSportRepository.saveAll(sportIds.stream()
                .map(sportId -> CoachSport.of(coach.getId(), sportId))
                .toList());
    }
}
