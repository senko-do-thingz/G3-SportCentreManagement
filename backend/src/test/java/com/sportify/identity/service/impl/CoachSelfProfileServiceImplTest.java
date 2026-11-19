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
import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.entity.CoachSportId;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachCertificationRepository;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import com.sportify.identity.service.ManagerCoachProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachSelfProfileServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock private CoachProfileRepository coachProfileRepository;
    @Mock private CoachSportRepository coachSportRepository;
    @Mock private CoachCertificationRepository certificationRepository;
    @Mock private SportRepository sportRepository;
    @Mock private ManagerCoachProfileService managerCoachProfileService;
    @Mock private AuditService auditService;

    private CoachSelfProfileServiceImpl service;
    private UserAccount coachAccount;
    private CoachProfile coachProfile;

    @BeforeEach
    void setUp() {
        service = new CoachSelfProfileServiceImpl(
                coachProfileRepository,
                coachSportRepository,
                certificationRepository,
                sportRepository,
                managerCoachProfileService,
                auditService,
                CLOCK
        );
        coachAccount = UserAccount.builder()
                .id(7L)
                .fullName("Tran Minh Quan")
                .email("quan.coach@example.com")
                .avatarUrl("/images/coaches/bao-le.jpg")
                .role(Role.builder().code("COACH").build())
                .status("ACTIVE")
                .build();
        coachProfile = CoachProfile.builder()
                .id(7L)
                .userAccount(coachAccount)
                .headline("Badminton coach")
                .bio("Footwork and singles tactics.")
                .yearsExperience(5)
                .specialties("Singles, Footwork")
                .isPublic(true)
                .displayOrder(2)
                .build();
    }

    private void stubProfileAndOptions() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coachProfile));
        when(sportRepository.findAll()).thenReturn(List.of(
                Sport.builder().id(1L).code("BADMINTON").name("Badminton").isActive(true).build(),
                Sport.builder().id(2L).code("SWIMMING").name("Swimming").isActive(true).build()
        ));
        when(coachSportRepository.findByIdCoachIdIn(List.of(7L))).thenReturn(List.of(
                CoachSport.of(7L, 1L)
        ));
    }

    @Test
    void getMyProfile_returnsCoachDetailsCertificationsAndDistinctCenterOptions() {
        stubProfileAndOptions();
        CoachCertification ownCert = CoachCertification.builder()
                .id(10L).coach(coachProfile).sportId(1L).name("BWF Level 1").issuer("BWF").isVerified(true).build();
        CoachCertification peerCert = CoachCertification.builder()
                .id(11L).sportId(2L).name("ASCA Level 2").issuer("ASCA").isVerified(true).build();
        CoachCertification duplicateCert = CoachCertification.builder()
                .id(12L).sportId(1L).name("BWF Level 1").issuer("BWF").isVerified(false).build();
        when(certificationRepository.findByCoachIdOrderByIdAsc(7L)).thenReturn(List.of(ownCert));
        when(certificationRepository.findAllOrdered()).thenReturn(List.of(ownCert, peerCert, duplicateCert));

        CoachSelfProfileResponse response = service.getMyProfile(coachAccount);

        assertEquals(7L, response.userId());
        assertEquals("Tran Minh Quan", response.fullName());
        assertEquals("Badminton coach", response.headline());
        assertEquals(1, response.sports().size());
        assertEquals("Badminton", response.sports().getFirst().name());
        assertEquals(1, response.certifications().size());
        assertTrue(response.certifications().getFirst().verified());
        // Danh sach lua chon chung chi duoc gop cac ban ghi trung lap
        assertEquals(2, response.certificationOptions().size());
        assertEquals("ASCA Level 2", response.certificationOptions().get(0).name());
        assertEquals("BWF Level 1", response.certificationOptions().get(1).name());
    }

    @Test
    void updateMyProfile_delegatesToManagerServiceWhilePreservingIsPublicAndDisplayOrder() {
        stubProfileAndOptions();
        when(certificationRepository.findByCoachIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(certificationRepository.findAllOrdered()).thenReturn(List.of());

        service.updateMyProfile(new CoachSelfProfileUpdateRequest(
                "/images/coaches/bao-le.jpg",
                "National badminton coach",
                "Updated bio",
                8,
                "Doubles, Defense"
        ), coachAccount);

        ArgumentCaptor<ManagerCoachProfileUpdateRequest> captor = ArgumentCaptor.forClass(ManagerCoachProfileUpdateRequest.class);
        verify(managerCoachProfileService).updateProfile(eq(7L), captor.capture(), eq(coachAccount));
        assertEquals("National badminton coach", captor.getValue().headline());
        assertEquals(8, captor.getValue().yearsExperience());
        assertTrue(captor.getValue().isPublic());
        assertEquals(2, captor.getValue().displayOrder());
    }

    @Test
    void addCertification_acceptsOptionFromCatalogAndDefaultsVerifiedFalse() {
        stubProfileAndOptions();
        CoachCertification catalogCert = CoachCertification.builder()
                .id(11L).sportId(1L).name("BWF Level 2").issuer("Badminton World Federation").isVerified(true).build();
        when(certificationRepository.findAllOrdered()).thenReturn(List.of(catalogCert));
        when(certificationRepository.findByCoachIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(certificationRepository.save(any(CoachCertification.class))).thenAnswer(inv -> {
            CoachCertification saved = inv.getArgument(0);
            saved.setId(55L);
            return saved;
        });

        service.addCertification(new CoachSelfCertificationCreateRequest(
                "BWF Level 2",
                "Badminton World Federation",
                1L,
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2028, 1, 10)
        ), coachAccount);

        ArgumentCaptor<CoachCertification> certCaptor = ArgumentCaptor.forClass(CoachCertification.class);
        verify(certificationRepository).save(certCaptor.capture());
        assertEquals("BWF Level 2", certCaptor.getValue().getName());
        assertEquals("Badminton World Federation", certCaptor.getValue().getIssuer());
        assertEquals(1L, certCaptor.getValue().getSportId());
        assertFalse(certCaptor.getValue().getIsVerified());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals(AuditAction.USER_UPDATED, auditCaptor.getValue().getAction());
    }

    @Test
    void addCertification_rejectsUnlistedCertificationDuplicateAndInvalidDates() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coachProfile));
        when(sportRepository.findAll()).thenReturn(List.of());
        CoachCertification existing = CoachCertification.builder()
                .id(11L).sportId(1L).name("BWF Level 1").issuer("BWF").isVerified(true).build();
        when(certificationRepository.findAllOrdered()).thenReturn(List.of(existing));

        // Ngay het han truoc ngay cap bi tu choi
        assertThrows(FieldValidationException.class, () -> service.addCertification(
                new CoachSelfCertificationCreateRequest("BWF Level 1", "BWF", 1L,
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1)), coachAccount));

        // Chung chi khong co trong danh muc trung tam bi tu choi
        assertThrows(BusinessRuleException.class, () -> service.addCertification(
                new CoachSelfCertificationCreateRequest("Arbitrary Unlisted Cert", "Unknown", null, null, null), coachAccount));

        // Chung chi da co tren ho so cua Coach bi tu choi
        when(certificationRepository.findByCoachIdOrderByIdAsc(7L)).thenReturn(List.of(existing));
        assertThrows(ConflictException.class, () -> service.addCertification(
                new CoachSelfCertificationCreateRequest("BWF Level 1", "BWF", 1L, null, null), coachAccount));
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void removeCertification_deletesOnlyOwnCertificationAndRejectsNonCoach() {
        stubProfileAndOptions();
        CoachCertification ownCert = CoachCertification.builder()
                .id(25L).coach(coachProfile).name("BWF Level 1").build();
        when(certificationRepository.findByIdAndCoachId(25L, 7L)).thenReturn(Optional.of(ownCert));
        when(certificationRepository.findByCoachIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(certificationRepository.findAllOrdered()).thenReturn(List.of());

        service.removeCertification(25L, coachAccount);
        verify(certificationRepository).delete(ownCert);

        when(certificationRepository.findByIdAndCoachId(99L, 7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.removeCertification(99L, coachAccount));

        UserAccount member = UserAccount.builder().id(8L).role(Role.builder().code("MEMBER").build()).status("ACTIVE").build();
        assertThrows(AccessDeniedException.class, () -> service.getMyProfile(member));
    }
}
