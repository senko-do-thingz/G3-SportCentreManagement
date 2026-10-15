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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerUserServiceImplTest {

    private static final Long MANAGER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private CoachSportRepository coachSportRepository;
    @Mock
    private SportRepository sportRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Spy
    private ManagerUserMapper userMapper = Mappers.getMapper(ManagerUserMapper.class);

    @InjectMocks
    private ManagerUserServiceImpl service;

    private UserAccount actor;

    @BeforeEach
    void setUp() {
        actor = UserAccount.builder()
                .id(MANAGER_ID)
                .email("manager@sportify.com")
                .role(role("MANAGER"))
                .status("ACTIVE")
                .build();
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static Role role(String code) {
        return Role.builder().id(10L).code(code).name(code).build();
    }

    private static Sport sport(Long id, boolean active) {
        return Sport.builder().id(id).code("S" + id).name("Sport " + id).isActive(active).build();
    }

    private static UserAccount user(Long id, String email, String roleCode, String status) {
        return UserAccount.builder()
                .id(id)
                .email(email)
                .fullName("User " + id)
                .phone("09000000" + id)
                .role(role(roleCode))
                .status(status)
                .build();
    }

    private static StaffAccountCreateRequest request(String role, List<Long> sportIds) {
        return new StaffAccountCreateRequest("Daniel Smith", "  Daniel@Sportify.com ", "0901234568",
                "Temp1234", role, sportIds);
    }

    /** Makes save() return the entity with an id and a created date, like the database would. */
    private void stubSaveAssignsId(long id) {
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            saved.setId(id);
            saved.setCreatedAt(LocalDateTime.of(2026, 10, 9, 9, 0));
            return saved;
        });
    }

    private void stubCreateDependencies(String roleCode) {
        when(userRepository.existsByEmailIgnoreCase("daniel@sportify.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234568")).thenReturn(false);
        when(roleRepository.findByCode(roleCode)).thenReturn(Optional.of(role(roleCode)));
        when(passwordEncoder.encode("Temp1234")).thenReturn("encoded-hash");
    }

    // ---------------------------------------------------------------------
    // listUsers
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_shouldMapRowsAndUseNewestFirstSort() {
        UserAccount account = user(5L, "linh@sportify.demo", "MEMBER", "INACTIVE");
        account.setCreatedAt(LocalDateTime.of(2026, 10, 1, 8, 30));
        Page<UserAccount> page = new PageImpl<>(List.of(account), org.springframework.data.domain.PageRequest.of(2, 10), 41);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ManagerUserResponse> response = service.listUsers("member", UserStatus.INACTIVE, "linh", 2, 10);

        assertEquals(1, response.content().size());
        ManagerUserResponse row = response.content().get(0);
        assertEquals(5L, row.id());
        assertEquals("linh@sportify.demo", row.email());
        assertEquals("MEMBER", row.role());
        assertEquals("INACTIVE", row.status());
        assertEquals(LocalDateTime.of(2026, 10, 1, 8, 30), row.createdAt());
        assertEquals(2, response.page());
        assertEquals(10, response.size());
        assertEquals(41, response.totalElements());
        assertEquals(5, response.totalPages());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), captor.capture());
        Sort.Order createdAt = captor.getValue().getSort().getOrderFor("createdAt");
        assertNotNull(createdAt);
        assertEquals(Sort.Direction.DESC, createdAt.getDirection());
        assertNotNull(captor.getValue().getSort().getOrderFor("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_shouldClampPageAndSize() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.listUsers(null, null, null, -5, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_withNonPositiveSize_shouldUseSizeOne() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.listUsers(null, null, null, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(1, captor.getValue().getPageSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_withBlankRole_shouldNotFilterByRole() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<ManagerUserResponse> response = service.listUsers("  ", null, null, 0, 20);

        assertTrue(response.content().isEmpty());
    }

    @Test
    void listUsers_withUnknownRole_shouldThrowBusinessRule() {
        assertThrows(BusinessRuleException.class, () -> service.listUsers("ADMIN", null, null, 0, 20));
        verifyNoInteractions(userRepository);
    }

    // ---------------------------------------------------------------------
    // createStaffAccount - happy paths
    // ---------------------------------------------------------------------

    @Test
    void createStaffAccount_receptionist_shouldSaveEncodedAccountAndWriteActivityLog() {
        stubCreateDependencies("RECEPTIONIST");
        stubSaveAssignsId(20L);

        ManagerUserResponse response = service.createStaffAccount(request("RECEPTIONIST", null), actor);

        assertEquals(20L, response.id());
        assertEquals("daniel@sportify.com", response.email());
        assertEquals("RECEPTIONIST", response.role());
        assertEquals("ACTIVE", response.status());
        assertEquals(LocalDateTime.of(2026, 10, 9, 9, 0), response.createdAt());

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(userCaptor.capture());
        UserAccount saved = userCaptor.getValue();
        assertEquals("encoded-hash", saved.getPasswordHash());
        assertEquals("Daniel Smith", saved.getFullName());
        assertEquals("0901234568", saved.getPhone());

        verify(coachProfileRepository, never()).save(any());
        verify(coachSportRepository, never()).saveAll(anyIterable());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertEquals(AuditAction.USER_CREATED, event.getAction());
        assertEquals("USER_ACCOUNT", event.getEntityType());
        assertEquals(20L, event.getEntityId());
        assertEquals(actor, event.getActor());
        assertEquals("RECEPTIONIST", event.getDetails().get("role"));
    }

    @Test
    void createStaffAccount_manager_shouldBeAllowed() {
        stubCreateDependencies("MANAGER");
        stubSaveAssignsId(21L);

        ManagerUserResponse response = service.createStaffAccount(request("manager", List.of()), actor);

        assertEquals("MANAGER", response.role());
        verify(coachProfileRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createStaffAccount_coach_shouldCreateCoachProfileAndCoachSportRows() {
        stubCreateDependencies("COACH");
        stubSaveAssignsId(22L);
        when(sportRepository.findAllById(List.of(3L, 5L))).thenReturn(List.of(sport(3L, true), sport(5L, true)));

        ManagerUserResponse response = service.createStaffAccount(request(" coach ", List.of(3L, 5L)), actor);

        assertEquals("COACH", response.role());

        ArgumentCaptor<CoachProfile> profileCaptor = ArgumentCaptor.forClass(CoachProfile.class);
        verify(coachProfileRepository).save(profileCaptor.capture());
        assertEquals(22L, profileCaptor.getValue().getUserAccount().getId());
        assertEquals(3L, profileCaptor.getValue().getPrimarySportId());

        ArgumentCaptor<Iterable<CoachSport>> rowsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(coachSportRepository).saveAll(rowsCaptor.capture());
        List<CoachSport> rows = new java.util.ArrayList<>();
        rowsCaptor.getValue().forEach(rows::add);
        assertEquals(2, rows.size());
        assertEquals(22L, rows.get(0).getId().getCoachId());
        assertEquals(3L, rows.get(0).getId().getSportId());
        assertEquals(22L, rows.get(1).getId().getCoachId());
        assertEquals(5L, rows.get(1).getId().getSportId());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals(AuditAction.USER_CREATED, auditCaptor.getValue().getAction());
        assertEquals(List.of(3L, 5L), auditCaptor.getValue().getDetails().get("sportIds"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createStaffAccount_coach_shouldIgnoreDuplicateSportIds() {
        stubCreateDependencies("COACH");
        stubSaveAssignsId(23L);
        when(sportRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(sport(1L, true), sport(2L, true)));

        service.createStaffAccount(request("COACH", List.of(1L, 1L, 2L)), actor);

        ArgumentCaptor<Iterable<CoachSport>> rowsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(coachSportRepository).saveAll(rowsCaptor.capture());
        int count = 0;
        for (CoachSport ignored : rowsCaptor.getValue()) {
            count++;
        }
        assertEquals(2, count);
    }

    // ---------------------------------------------------------------------
    // createStaffAccount - rules
    // ---------------------------------------------------------------------

    @Test
    void createStaffAccount_member_shouldBeRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createStaffAccount(request("MEMBER", null), actor));

        assertTrue(ex.getMessage().contains("RECEPTIONIST"));
        verifyNoInteractions(userRepository, auditService);
    }

    @Test
    void createStaffAccount_unknownRole_shouldBeRejected() {
        assertThrows(BusinessRuleException.class, () -> service.createStaffAccount(request("ADMIN", null), actor));
        verifyNoInteractions(userRepository, auditService);
    }

    @Test
    void createStaffAccount_coachWithoutSports_shouldBeRejected() {
        assertThrows(BusinessRuleException.class, () -> service.createStaffAccount(request("COACH", null), actor));
        assertThrows(BusinessRuleException.class, () -> service.createStaffAccount(request("COACH", List.of()), actor));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createStaffAccount_coachWithUnknownSport_shouldBeRejectedAndNameTheId() {
        when(sportRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(sport(1L, true)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createStaffAccount(request("COACH", List.of(1L, 99L)), actor));

        assertTrue(ex.getMessage().contains("99"));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createStaffAccount_coachWithInactiveSport_shouldBeRejected() {
        when(sportRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(sport(1L, true), sport(2L, false)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createStaffAccount(request("COACH", List.of(1L, 2L)), actor));

        assertTrue(ex.getMessage().contains("2"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createStaffAccount_receptionistWithSports_shouldBeRejected() {
        assertThrows(BusinessRuleException.class,
                () -> service.createStaffAccount(request("RECEPTIONIST", List.of(1L)), actor));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createStaffAccount_duplicateEmail_shouldThrowConflict() {
        when(userRepository.existsByEmailIgnoreCase("daniel@sportify.com")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createStaffAccount(request("RECEPTIONIST", null), actor));

        assertEquals("Email is already registered", ex.getMessage());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService, coachProfileRepository, coachSportRepository);
    }

    @Test
    void createStaffAccount_duplicatePhone_shouldThrowConflict() {
        when(userRepository.existsByEmailIgnoreCase("daniel@sportify.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234568")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createStaffAccount(request("RECEPTIONIST", null), actor));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createStaffAccount_whenInsertRacesWithAnotherRequest_shouldThrowConflict() {
        stubCreateDependencies("RECEPTIONIST");
        when(userRepository.save(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(ConflictException.class, () -> service.createStaffAccount(request("RECEPTIONIST", null), actor));

        verifyNoInteractions(auditService, coachProfileRepository, coachSportRepository);
    }

    // ---------------------------------------------------------------------
    // changeStatus
    // ---------------------------------------------------------------------

    @Test
    void changeStatus_toInactive_shouldUpdateAndWriteActivityLog() {
        UserAccount target = user(7L, "jamie@sportify.demo", "RECEPTIONIST", "ACTIVE");
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        ManagerUserResponse response = service.changeStatus(7L, UserStatus.INACTIVE, actor);

        assertEquals("INACTIVE", response.status());
        assertEquals("INACTIVE", target.getStatus());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditAction.USER_STATUS_CHANGED, event.getAction());
        assertEquals("USER_ACCOUNT", event.getEntityType());
        assertEquals(7L, event.getEntityId());
        assertEquals(actor, event.getActor());
        assertEquals("ACTIVE", event.getDetails().get("from"));
        assertEquals("INACTIVE", event.getDetails().get("to"));
    }

    @Test
    void changeStatus_toActive_shouldUpdateAndWriteActivityLog() {
        UserAccount target = user(8L, "linh@sportify.demo", "MEMBER", "INACTIVE");
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        ManagerUserResponse response = service.changeStatus(8L, UserStatus.ACTIVE, actor);

        assertEquals("ACTIVE", response.status());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertEquals("INACTIVE", captor.getValue().getDetails().get("from"));
        assertEquals("ACTIVE", captor.getValue().getDetails().get("to"));
    }

    @Test
    void changeStatus_deactivatingOwnAccount_shouldThrowBusinessRule() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(MANAGER_ID, UserStatus.INACTIVE, actor));

        assertEquals("You cannot deactivate your own account", ex.getMessage());
        verifyNoInteractions(userRepository, auditService);
    }

    @Test
    void changeStatus_keepingOwnAccountActive_shouldBeANoOp() {
        UserAccount self = user(MANAGER_ID, "manager@sportify.com", "MANAGER", "ACTIVE");
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(self));

        ManagerUserResponse response = service.changeStatus(MANAGER_ID, UserStatus.ACTIVE, actor);

        assertEquals("ACTIVE", response.status());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void changeStatus_toSameStatus_shouldNotWriteOrLog() {
        UserAccount target = user(9L, "chris@sportify.demo", "COACH", "INACTIVE");
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));

        ManagerUserResponse response = service.changeStatus(9L, UserStatus.INACTIVE, actor);

        assertEquals("INACTIVE", response.status());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void changeStatus_unknownUser_shouldThrowNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.changeStatus(404L, UserStatus.INACTIVE, actor));

        verifyNoInteractions(auditService);
    }
}
