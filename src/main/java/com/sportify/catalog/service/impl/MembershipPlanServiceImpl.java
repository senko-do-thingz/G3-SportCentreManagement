package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.PlanCreateRequest;
import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.dto.PlanStatusUpdateRequest;
import com.sportify.catalog.dto.PlanUpdateRequest;
import com.sportify.catalog.entity.MembershipPlan;
import com.sportify.catalog.entity.PlanStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.mapper.MembershipPlanMapper;
import com.sportify.catalog.repository.MembershipPlanRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.MembershipPlanService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Membership plan ("Membership packages") management.
 * <p>
 * Editing a plan never touches existing memberships: price, duration and selected sports are snapshotted
 * on {@code membership} / {@code membership_sport} at registration time (docs/database/02-catalog-and-membership.md).
 */
@Service
@RequiredArgsConstructor
public class MembershipPlanServiceImpl implements MembershipPlanService {

    static final String ACTION_PACKAGE_CREATED = "PACKAGE_CREATED";
    static final String ACTION_PACKAGE_UPDATED = "PACKAGE_UPDATED";
    static final String ENTITY_TYPE = "MEMBERSHIP_PLAN";

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("id"));

    private final MembershipPlanRepository planRepository;
    private final SportRepository sportRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final MembershipPlanMapper planMapper;

    // =====================================================================
    // Public
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByStatusOrderByDisplayOrderAscIdAsc(PlanStatus.ACTIVE).stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanResponse getActivePlan(Long id) {
        return planRepository.findByIdAndStatus(id, PlanStatus.ACTIVE)
                .map(planMapper::toResponse)
                .orElseThrow(() -> planNotFound(id));
    }

    // =====================================================================
    // Manager
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlanResponse> getAllPlans(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);

        // Step 1: paginate in SQL without collection fetch joins (avoids in-memory pagination).
        Page<MembershipPlan> plansPage = planRepository.findAll(pageable);
        if (plansPage.isEmpty()) {
            return PageResponse.from(plansPage.map(planMapper::toResponse));
        }

        // Step 2: fetch features and eligible sports for the ids of this page in one query.
        List<Long> ids = plansPage.getContent().stream().map(MembershipPlan::getId).toList();
        Map<Long, MembershipPlan> detailed = planRepository.findWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(MembershipPlan::getId, Function.identity()));

        return PageResponse.from(plansPage.map(plan ->
                planMapper.toResponse(detailed.getOrDefault(plan.getId(), plan))));
    }

    @Override
    @Transactional
    public PlanResponse createPlan(PlanCreateRequest request, Long actorUserId) {
        String code = request.code().trim();
        if (planRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Membership plan code '" + code + "' already exists");
        }

        MembershipPlan plan = planMapper.toEntity(request);
        plan.setCode(code);
        plan.setStatus(PlanStatus.DRAFT);
        plan.replaceEligibleSports(resolveSports(request.eligibleSportIds()));
        plan.replaceFeatures(request.features());

        // saveAndFlush so that a concurrent duplicate code surfaces here as DataIntegrityViolationException (409).
        MembershipPlan saved = planRepository.saveAndFlush(plan);

        logActivity(actorUserId, ACTION_PACKAGE_CREATED, saved,
                "Membership plan '" + saved.getName() + "' created", null);
        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(Long id, PlanUpdateRequest request, Long actorUserId) {
        MembershipPlan plan = planRepository.findWithDetailsById(id).orElseThrow(() -> planNotFound(id));
        ensureVersionMatches(plan, request.version());

        planMapper.updateEntity(request, plan);
        plan.replaceEligibleSports(resolveSports(request.eligibleSportIds()));
        plan.replaceFeatures(request.features());

        // An ACTIVE plan must keep satisfying the activation rules after an edit.
        if (plan.getStatus() == PlanStatus.ACTIVE) {
            validateActivationRules(plan);
        }

        MembershipPlan saved = planRepository.saveAndFlush(plan);

        logActivity(actorUserId, ACTION_PACKAGE_UPDATED, saved,
                "Membership plan '" + saved.getName() + "' updated", null);
        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlanResponse changeStatus(Long id, PlanStatusUpdateRequest request, Long actorUserId) {
        MembershipPlan plan = planRepository.findWithDetailsById(id).orElseThrow(() -> planNotFound(id));
        ensureVersionMatches(plan, request.version());

        PlanStatus from = plan.getStatus();
        PlanStatus to = request.status();
        if (from == to) {
            // Idempotent: nothing changes, nothing is logged.
            return planMapper.toResponse(plan);
        }
        if (to == PlanStatus.ACTIVE) {
            validateActivationRules(plan);
        }

        plan.setStatus(to);
        MembershipPlan saved = planRepository.saveAndFlush(plan);

        logActivity(actorUserId, ACTION_PACKAGE_UPDATED, saved,
                "Membership plan '" + saved.getName() + "' status changed from " + from + " to " + to,
                "{\"before\":{\"status\":\"" + from + "\"},\"after\":{\"status\":\"" + to + "\"}}");
        return planMapper.toResponse(saved);
    }

    // =====================================================================
    // Rules and helpers
    // =====================================================================

    /**
     * Rules for publishing a plan (status ACTIVE):
     * price must be at least 0, duration_days greater than 0, and the eligible sport pool must contain
     * at least max_sports sports (docs/database/02-catalog-and-membership.md, plan_eligible_sport).
     */
    void validateActivationRules(MembershipPlan plan) {
        List<String> violations = new ArrayList<>();

        BigDecimal price = plan.getPrice();
        if (price == null || price.signum() < 0) {
            violations.add("price must be greater than or equal to 0");
        }
        Integer durationDays = plan.getDurationDays();
        if (durationDays == null || durationDays <= 0) {
            violations.add("duration_days must be greater than 0");
        }
        Integer maxSports = plan.getMaxSports();
        int poolSize = plan.getEligibleSports() == null ? 0 : plan.getEligibleSports().size();
        if (maxSports == null || maxSports < 1) {
            violations.add("max_sports must be at least 1");
        } else if (poolSize < maxSports) {
            violations.add("the eligible sport pool has " + poolSize + " sport(s) but max_sports is " + maxSports
                    + "; the pool must contain at least max_sports sports");
        }

        if (!violations.isEmpty()) {
            throw new BusinessRuleException("Cannot activate membership plan: " + String.join("; ", violations));
        }
    }

    private void ensureVersionMatches(MembershipPlan plan, Integer expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(plan.getVersion())) {
            throw new ConflictException("Membership plan was modified by another request "
                    + "(expected version " + expectedVersion + ", current version " + plan.getVersion()
                    + "). Please reload and try again.");
        }
    }

    private Set<Sport> resolveSports(Set<Long> sportIds) {
        if (sportIds == null || sportIds.isEmpty()) {
            return Set.of();
        }
        List<Sport> found = sportRepository.findAllById(sportIds);
        if (found.size() != sportIds.size()) {
            Set<Long> missing = new TreeSet<>(sportIds);
            found.forEach(sport -> missing.remove(sport.getId()));
            throw new BusinessRuleException("Unknown sport id(s): " + missing);
        }
        return Set.copyOf(found);
    }

    private void logActivity(Long actorUserId, String action, MembershipPlan plan, String summary, String details) {
        ActivityLog log = new ActivityLog();
        if (actorUserId != null) {
            log.setActor(userRepository.getReferenceById(actorUserId));
        }
        log.setAction(action);
        log.setEntityType(ENTITY_TYPE);
        log.setEntityId(plan.getId());
        log.setEntityCode(plan.getCode());
        log.setSummary(truncate(summary, 255));
        log.setDetails(details);
        activityLogRepository.save(log);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static ResourceNotFoundException planNotFound(Long id) {
        return new ResourceNotFoundException("Membership plan not found: " + id);
    }
}
