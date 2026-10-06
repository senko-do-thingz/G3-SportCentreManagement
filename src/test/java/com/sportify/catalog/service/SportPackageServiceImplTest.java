package com.sportify.catalog.service;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.dto.SportPackageCreateRequest;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.impl.SportPackageServiceImpl;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SportPackageServiceImplTest {

    @Mock
    private SportPackageRepository sportPackageRepository;
    @Mock
    private SportPackageRegistrationRepository registrationRepository;
    @Mock
    private SportRepository sportRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MembershipCardService membershipCardService;
    @Mock
    private CodeFormatter codeFormatter;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private SportPackageServiceImpl sportPackageService;

    private Sport sport;
    private SportPackage sportPackage;
    private UserAccount memberUser;
    private UserAccount receptionistUser;
    private UserAccount managerUser;
    private MemberProfile memberProfile;

    @BeforeEach
    void setUp() {
        sport = Sport.builder().id(1L).code("BADMINTON").name("Badminton").build();
        sportPackage = SportPackage.builder()
                .id(10L)
                .code("BADMINTON_30D")
                .name("Badminton 30 Days")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(8)
                .priceAmount(BigDecimal.valueOf(350000.00))
                .isActive(true)
                .build();

        Role memberRole = Role.builder().id(1L).code("MEMBER").name("Member").build();
        memberUser = UserAccount.builder().id(100L).email("member@example.com").fullName("Member One").role(memberRole).build();
        memberProfile = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-100").build();

        Role recRole = Role.builder().id(2L).code("RECEPTIONIST").name("Receptionist").build();
        receptionistUser = UserAccount.builder().id(200L).email("rec@sportify.com").role(recRole).build();

        Role mgrRole = Role.builder().id(3L).code("MANAGER").name("Manager").build();
        managerUser = UserAccount.builder().id(300L).email("mgr@sportify.com").role(mgrRole).build();
    }

    @Test
    void getAllActivePackages_ReturnsMappedList() {
        when(sportPackageRepository.findByIsActiveTrue()).thenReturn(List.of(sportPackage));

        List<SportPackageResponse> result = sportPackageService.getAllActivePackages();

        assertEquals(1, result.size());
        assertEquals("BADMINTON_30D", result.get(0).getCode());
    }

    @Test
    void createPackage_Success() {
        SportPackageCreateRequest req = SportPackageCreateRequest.builder()
                .code("NEW_PKG")
                .name("New Package")
                .sportId(1L)
                .trainingFormat(TrainingFormat.COACH_LED)
                .durationDays(30)
                .sessionCount(10)
                .priceAmount(BigDecimal.valueOf(500000.00))
                .build();

        when(sportRepository.findById(1L)).thenReturn(Optional.of(sport));
        when(sportPackageRepository.save(any(SportPackage.class))).thenAnswer(i -> {
            SportPackage p = i.getArgument(0);
            p.setId(15L);
            return p;
        });

        SportPackageResponse res = sportPackageService.createPackage(req, receptionistUser);

        assertNotNull(res);
        assertEquals("NEW_PKG", res.getCode());
    }

    @Test
    void registerPackage_AsMember_IgnoresSpoofedMemberIdAndEnforcesActorId_SetsPendingPayment() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .memberId(999L) // Attacker member attempts IDOR
                .packageId(10L)
                .channel(RegistrationChannel.ONLINE)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(eq(100L), eq(30))).thenReturn(5); // Gold card 5%
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(101L);
        when(codeFormatter.formatPackageRegCode(101L)).thenReturn("REG-PKG-101");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> {
            SportPackageRegistration r = i.getArgument(0);
            r.setId(500L);
            return r;
        });

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, memberUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId()); // Verified target was actor 100L, NOT 999L
        assertEquals(PackageRegistrationStatus.PENDING_PAYMENT, res.getStatus()); // Online member starts as PENDING_PAYMENT
        assertEquals(5, res.getDiscountPercentage());
    }

    @Test
    void registerPackage_AsMember_WithChannelReception_ForcesOnlineAndPendingPayment() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .channel(RegistrationChannel.RECEPTION) // Member attempts to self-activate via reception channel
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(eq(100L), eq(30))).thenReturn(0);
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(103L);
        when(codeFormatter.formatPackageRegCode(103L)).thenReturn("REG-PKG-103");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> {
            SportPackageRegistration r = i.getArgument(0);
            r.setId(502L);
            return r;
        });

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, memberUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId());
        assertEquals(RegistrationChannel.ONLINE, res.getChannel()); // Channel forced to ONLINE
        assertEquals(PackageRegistrationStatus.PENDING_PAYMENT, res.getStatus()); // Status forced to PENDING_PAYMENT
    }

    @Test
    void registerPackage_AsReceptionist_UsesProvidedMemberId_SetsActive() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .memberId(100L)
                .packageId(10L)
                .channel(RegistrationChannel.RECEPTION)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(eq(100L), eq(30))).thenReturn(0);
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(102L);
        when(codeFormatter.formatPackageRegCode(102L)).thenReturn("REG-PKG-102");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> {
            SportPackageRegistration r = i.getArgument(0);
            r.setId(501L);
            return r;
        });

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, receptionistUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId());
        assertEquals(RegistrationChannel.RECEPTION, res.getChannel());
        assertEquals(PackageRegistrationStatus.ACTIVE, res.getStatus()); // Front desk registration is activated immediately
    }

    @Test
    void registerPackage_AsManager_UsesProvidedMemberId_SetsActive() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .memberId(100L)
                .packageId(10L)
                .channel(RegistrationChannel.RECEPTION)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(eq(100L), eq(30))).thenReturn(0);
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(104L);
        when(codeFormatter.formatPackageRegCode(104L)).thenReturn("REG-PKG-104");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> {
            SportPackageRegistration r = i.getArgument(0);
            r.setId(503L);
            return r;
        });

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, managerUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId());
        assertEquals(RegistrationChannel.RECEPTION, res.getChannel());
        assertEquals(PackageRegistrationStatus.ACTIVE, res.getStatus()); // Front desk registration by manager is activated immediately
    }

    @Test
    void registerPackage_OrphanUser_AutoCreatesMemberProfile() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.empty());
        when(userRepository.findById(100L)).thenReturn(Optional.of(memberUser));
        when(memberProfileRepository.getNextMemberCode()).thenReturn(55L);
        when(codeFormatter.formatMemberCode(55L)).thenReturn("MEM-055");
        when(memberProfileRepository.save(any(MemberProfile.class))).thenReturn(memberProfile);
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(any(), eq(30))).thenReturn(0);
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(103L);
        when(codeFormatter.formatPackageRegCode(103L)).thenReturn("REG-PKG-103");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, memberUser);

        assertNotNull(res);
        verify(memberProfileRepository).save(any(MemberProfile.class));
    }

    @Test
    void registerPackage_OrphanCoachUser_ThrowsBusinessRuleException() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .memberId(400L)
                .packageId(10L)
                .channel(RegistrationChannel.RECEPTION)
                .build();

        Role coachRole = Role.builder().id(4L).code("COACH").name("Coach").build();
        UserAccount coachUser = UserAccount.builder().id(400L).email("coach@sportify.com").role(coachRole).build();

        when(memberProfileRepository.findById(400L)).thenReturn(Optional.empty());
        when(userRepository.findById(400L)).thenReturn(Optional.of(coachUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> sportPackageService.registerPackage(req, receptionistUser));
        assertEquals("Cannot create member profile for non-member user: 400", ex.getMessage());
    }

    @Test
    void registerPackage_OrphanManagerUser_ThrowsBusinessRuleException() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .memberId(300L)
                .packageId(10L)
                .channel(RegistrationChannel.RECEPTION)
                .build();

        when(memberProfileRepository.findById(300L)).thenReturn(Optional.empty());
        when(userRepository.findById(300L)).thenReturn(Optional.of(managerUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> sportPackageService.registerPackage(req, receptionistUser));
        assertEquals("Cannot create member profile for non-member user: 300", ex.getMessage());
    }

    @Test
    void registerPackage_PastStartDate_ThrowsBusinessRuleException() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .startDate(LocalDate.of(2026, 10, 1)) // Past date
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));

        assertThrows(BusinessRuleException.class, () -> sportPackageService.registerPackage(req, memberUser));
    }

    @Test
    void activateRegistration_PendingPayment_Success() {
        LocalDate futureStart = LocalDate.of(2026, 10, 10);
        LocalDate futureEnd = futureStart.plusDays(30);
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(50L)
                .member(memberProfile)
                .sportPackage(sportPackage)
                .startDate(futureStart)
                .endDate(futureEnd)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findById(50L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.activateRegistration(50L, receptionistUser);

        assertEquals(PackageRegistrationStatus.ACTIVE, res.getStatus());
        assertNotNull(res.getActivatedAt());
        assertEquals(futureStart, res.getStartDate());
        assertEquals(futureEnd, res.getEndDate());
    }

    @Test
    void activateRegistration_AlreadyActive_ThrowsBusinessRuleException() {
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(51L)
                .member(memberProfile)
                .sportPackage(sportPackage)
                .startDate(LocalDate.of(2026, 10, 6))
                .endDate(LocalDate.of(2026, 11, 5))
                .status(PackageRegistrationStatus.ACTIVE)
                .build();

        when(registrationRepository.findById(51L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> sportPackageService.activateRegistration(51L, receptionistUser));
        assertEquals("Cannot activate package registration with status: ACTIVE. Only PENDING_PAYMENT registrations can be activated.", ex.getMessage());
    }

    @Test
    void activateRegistration_Cancelled_ThrowsBusinessRuleException() {
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(52L)
                .member(memberProfile)
                .sportPackage(sportPackage)
                .startDate(LocalDate.of(2026, 10, 6))
                .endDate(LocalDate.of(2026, 11, 5))
                .status(PackageRegistrationStatus.CANCELLED)
                .build();

        when(registrationRepository.findById(52L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> sportPackageService.activateRegistration(52L, receptionistUser));
        assertEquals("Cannot activate package registration with status: CANCELLED. Only PENDING_PAYMENT registrations can be activated.", ex.getMessage());
    }

    @Test
    void activateRegistration_Refunded_ThrowsBusinessRuleException() {
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(53L)
                .member(memberProfile)
                .sportPackage(sportPackage)
                .startDate(LocalDate.of(2026, 10, 6))
                .endDate(LocalDate.of(2026, 11, 5))
                .status(PackageRegistrationStatus.REFUNDED)
                .build();

        when(registrationRepository.findById(53L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> sportPackageService.activateRegistration(53L, receptionistUser));
        assertEquals("Cannot activate package registration with status: REFUNDED. Only PENDING_PAYMENT registrations can be activated.", ex.getMessage());
    }

    @Test
    void activateRegistration_Expired_ThrowsBusinessRuleException() {
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(54L)
                .member(memberProfile)
                .sportPackage(sportPackage)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 10, 1))
                .status(PackageRegistrationStatus.EXPIRED)
                .build();

        when(registrationRepository.findById(54L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> sportPackageService.activateRegistration(54L, receptionistUser));
        assertEquals("Cannot activate package registration with status: EXPIRED. Only PENDING_PAYMENT registrations can be activated.", ex.getMessage());
    }

    @Test
    void activateRegistration_PastStartDate_RecomputesDates() {
        LocalDate pastStart = LocalDate.of(2026, 10, 1); // Clock is 2026-10-06
        LocalDate pastEnd = pastStart.plusDays(30);
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(55L)
                .member(memberProfile)
                .sportPackage(sportPackage) // durationDays = 30
                .startDate(pastStart)
                .endDate(pastEnd)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findById(55L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.activateRegistration(55L, receptionistUser);

        assertEquals(PackageRegistrationStatus.ACTIVE, res.getStatus());
        assertEquals(LocalDate.of(2026, 10, 6), res.getStartDate()); // Recomputed to today
        assertEquals(LocalDate.of(2026, 10, 6).plusDays(30), res.getEndDate()); // Recomputed to today + durationDays
    }

    @Test
    void activateRegistration_NotFound_ThrowsResourceNotFoundException() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sportPackageService.activateRegistration(999L, receptionistUser));
    }
}
