package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.PlanCreateRequest;
import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.dto.PlanStatusUpdateRequest;
import com.sportify.catalog.dto.PlanUpdateRequest;
import com.sportify.catalog.entity.MembershipPlan;
import com.sportify.catalog.entity.PlanStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportVenueType;
import com.sportify.catalog.mapper.MembershipPlanMapper;
import com.sportify.catalog.repository.MembershipPlanRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipPlanServiceImplTest {

    private static final Long ACTOR_ID = 99L;

    @Mock
    private MembershipPlanRepository planRepository;
    @Mock
    private SportRepository sportRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private UserRepository userRepository;

    private final MembershipPlanMapper planMapper = Mappers.getMapper(MembershipPlanMapper.class);

    private MembershipPlanServiceImpl service;

    private List<Sport> sports;

    @BeforeEach
    void setUp() {
        service = new MembershipPlanServiceImpl(planRepository, sportRepository, activityLogRepository,
                userRepository, planMapper);
        sports = new ArrayList<>();
        String[] codes = {"FOOTBALL", "BADMINTON", "BASKETBALL", "VOLLEYBALL", "SWIMMING", "TENNIS"};
        for (int i = 0; i < codes.length; i++) {
            sports.add(Sport.builder().id((long) i + 1).code(codes[i]).name(codes[i])
                    .venueType(SportVenueType.INDOOR).displayOrder(0).build());
        }
    }

    private MembershipPlan plan(PlanStatus status, String price, int durationDays, int maxSports, int poolSize) {
        MembershipPlan plan = MembershipPlan.builder()
                .id(10L)
                .code("MULTI_SPORT")
                .name("Multi-Sport")
                .price(new BigDecimal(price))
                .durationDays(durationDays)
                .maxSports(maxSports)
                .status(status)
                .version(3)
                .build();
        plan.setEligibleSports(new HashSet<>(sports.subList(0, poolSize)));
        return plan;
    }

    private void stubSaveAndFlushEcho() {
        when(planRepository.saveAndFlush(any(MembershipPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------------
    // Activation rules
    // ---------------------------------------------------------------------

    @Test
    void changeStatus_toActive_whenPoolSmallerThanMaxSports_shouldBeRejected() {
        MembershipPlan draft = plan(PlanStatus.DRAFT, "550000", 30, 3, 2);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, null), ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pool has 2 sport(s) but max_sports is 3");

        assertThat(draft.getStatus()).isEqualTo(PlanStatus.DRAFT);
        verify(planRepository, never()).saveAndFlush(any());
        verify(activityLogRepository, never()).save(any());
    }

    @Test
    void changeStatus_toActive_withNegativePrice_shouldBeRejected() {
        MembershipPlan draft = plan(PlanStatus.DRAFT, "-1", 30, 1, 6);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, null), ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("price must be greater than or equal to 0");
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeStatus_toActive_withNonPositiveDuration_shouldBeRejected() {
        MembershipPlan draft = plan(PlanStatus.DRAFT, "300000", 0, 1, 6);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, null), ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("duration_days must be greater than 0");
    }

    @Test
    void changeStatus_toActive_whenRulesSatisfied_shouldActivateAndLogPackageUpdated() {
        MembershipPlan draft = plan(PlanStatus.DRAFT, "0", 30, 3, 3);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(draft));
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(new UserAccount());
        stubSaveAndFlushEcho();

        PlanResponse response = service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, 3), ACTOR_ID);

        assertThat(response.status()).isEqualTo(PlanStatus.ACTIVE);
        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("PACKAGE_UPDATED");
        assertThat(logCaptor.getValue().getEntityType()).isEqualTo("MEMBERSHIP_PLAN");
        assertThat(logCaptor.getValue().getEntityCode()).isEqualTo("MULTI_SPORT");
        assertThat(logCaptor.getValue().getDetails()).contains("\"DRAFT\"").contains("\"ACTIVE\"");
    }

    @Test
    void changeStatus_toArchived_shouldNotRequireActivationRules() {
        MembershipPlan active = plan(PlanStatus.ACTIVE, "300000", 30, 3, 1);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(active));
        stubSaveAndFlushEcho();

        PlanResponse response = service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ARCHIVED, null), null);

        assertThat(response.status()).isEqualTo(PlanStatus.ARCHIVED);
    }

    @Test
    void changeStatus_withStaleVersion_shouldReturnConflict() {
        MembershipPlan draft = plan(PlanStatus.DRAFT, "300000", 30, 1, 6);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, 2), ACTOR_ID))
                .isInstanceOf(ConflictException.class);
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeStatus_sameStatus_shouldBeNoOpWithoutLog() {
        MembershipPlan active = plan(PlanStatus.ACTIVE, "300000", 30, 1, 6);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(active));

        PlanResponse response = service.changeStatus(10L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, null), ACTOR_ID);

        assertThat(response.status()).isEqualTo(PlanStatus.ACTIVE);
        verify(planRepository, never()).saveAndFlush(any());
        verify(activityLogRepository, never()).save(any());
    }

    @Test
    void changeStatus_unknownPlan_shouldReturnNotFound() {
        when(planRepository.findWithDetailsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(404L, new PlanStatusUpdateRequest(PlanStatus.ACTIVE, null), ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------

    private PlanCreateRequest createRequest(String code, Set<Long> sportIds) {
        return new PlanCreateRequest(code, "Family Pass", null, "Desc", new BigDecimal("450000"), 30, 2,
                null, null, List.of("Coach-led class booking", "Personal schedule and progress"), sportIds);
    }

    @Test
    void createPlan_withDuplicateCode_shouldReturnConflict() {
        when(planRepository.existsByCodeIgnoreCase("STARTER")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlan(createRequest("STARTER", Set.of(1L)), ACTOR_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("STARTER");
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPlan_shouldCreateDraftWithFeaturesAndPoolAndLogPackageCreated() {
        when(planRepository.existsByCodeIgnoreCase("FAMILY")).thenReturn(false);
        when(sportRepository.findAllById(anyCollection())).thenReturn(List.of(sports.get(0), sports.get(1)));
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(new UserAccount());
        stubSaveAndFlushEcho();

        PlanResponse response = service.createPlan(createRequest(" FAMILY ", Set.of(1L, 2L)), ACTOR_ID);

        assertThat(response.code()).isEqualTo("FAMILY");
        assertThat(response.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(response.isFeatured()).isFalse();
        assertThat(response.displayOrder()).isZero();
        assertThat(response.features()).containsExactly("Coach-led class booking", "Personal schedule and progress");
        assertThat(response.eligibleSports()).extracting("code").containsExactly("FOOTBALL", "BADMINTON");

        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("PACKAGE_CREATED");
    }

    @Test
    void createPlan_withUnknownSportId_shouldBeRejected() {
        when(planRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(sportRepository.findAllById(anyCollection())).thenReturn(List.of(sports.get(0)));

        assertThatThrownBy(() -> service.createPlan(createRequest("FAMILY", Set.of(1L, 777L)), ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("777");
        verify(planRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------------
    // Update
    // ---------------------------------------------------------------------

    private PlanUpdateRequest updateRequest(int maxSports, Set<Long> sportIds, Integer version) {
        return new PlanUpdateRequest("Multi-Sport", "02 / EXPLORE", "Mix classes", new BigDecimal("600000"), 30,
                maxSports, true, 2, List.of("Feature A"), sportIds, version);
    }

    @Test
    void updatePlan_withStaleVersion_shouldReturnConflict() {
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(plan(PlanStatus.DRAFT, "1", 30, 1, 1)));

        assertThatThrownBy(() -> service.updatePlan(10L, updateRequest(1, Set.of(1L), 1), ACTOR_ID))
                .isInstanceOf(ConflictException.class);
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePlan_activePlan_breakingPoolRule_shouldBeRejected() {
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(plan(PlanStatus.ACTIVE, "1", 30, 1, 6)));
        when(sportRepository.findAllById(anyCollection())).thenReturn(List.of(sports.get(0)));

        assertThatThrownBy(() -> service.updatePlan(10L, updateRequest(3, Set.of(1L), null), ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePlan_shouldReplaceFieldsAndLogPackageUpdated() {
        MembershipPlan draft = plan(PlanStatus.DRAFT, "1", 30, 1, 6);
        when(planRepository.findWithDetailsById(10L)).thenReturn(Optional.of(draft));
        when(sportRepository.findAllById(anyCollection())).thenReturn(List.of(sports.get(2)));
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(new UserAccount());
        stubSaveAndFlushEcho();

        PlanResponse response = service.updatePlan(10L, updateRequest(1, Set.of(3L), 3), ACTOR_ID);

        assertThat(response.code()).isEqualTo("MULTI_SPORT");
        assertThat(response.price()).isEqualByComparingTo("600000");
        assertThat(response.tagline()).isEqualTo("02 / EXPLORE");
        assertThat(response.isFeatured()).isTrue();
        assertThat(response.features()).containsExactly("Feature A");
        assertThat(response.eligibleSports()).extracting("code").containsExactly("BASKETBALL");
        assertThat(response.status()).isEqualTo(PlanStatus.DRAFT);

        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("PACKAGE_UPDATED");
    }

    // ---------------------------------------------------------------------
    // Public visibility
    // ---------------------------------------------------------------------

    @Test
    void getActivePlans_shouldQueryActiveStatusOnly() {
        when(planRepository.findByStatusOrderByDisplayOrderAscIdAsc(PlanStatus.ACTIVE))
                .thenReturn(List.of(plan(PlanStatus.ACTIVE, "300000", 30, 1, 6)));

        List<PlanResponse> result = service.getActivePlans();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(PlanStatus.ACTIVE);
        verify(planRepository).findByStatusOrderByDisplayOrderAscIdAsc(PlanStatus.ACTIVE);
        verify(planRepository, never()).findByStatusOrderByDisplayOrderAscIdAsc(PlanStatus.DRAFT);
        verify(planRepository, never()).findByStatusOrderByDisplayOrderAscIdAsc(PlanStatus.ARCHIVED);
    }

    @Test
    void getActivePlan_forDraftOrArchivedPlan_shouldReturnNotFound() {
        // The repository only matches ACTIVE plans; a DRAFT or ARCHIVED plan id yields no row.
        when(planRepository.findByIdAndStatus(5L, PlanStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActivePlan(5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(planRepository).findByIdAndStatus(5L, PlanStatus.ACTIVE);
    }

    @Test
    void getAllPlans_shouldClampPageSizeAndFetchDetails() {
        MembershipPlan p = plan(PlanStatus.DRAFT, "1", 30, 1, 1);
        when(planRepository.findAll(any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(0);
            assertThat(pageable.getPageSize()).isEqualTo(100);
            assertThat(pageable.getPageNumber()).isZero();
            return new PageImpl<>(List.of(p), pageable, 1);
        });
        when(planRepository.findWithDetailsByIdIn(List.of(10L))).thenReturn(List.of(p));

        var result = service.getAllPlans(-3, 5000);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(planRepository).findWithDetailsByIdIn(List.of(10L));
    }
}
