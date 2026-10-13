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

import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.PaymentService;
import com.sportify.catalog.dto.SportPackageUpdateRequest;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private PaymentService paymentService;
    @Mock
    private CodeFormatter codeFormatter;
    @Mock
    private AuditService auditService;
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
        lenient().when(paymentService.createPendingPayment(any(MemberProfile.class), any(BigDecimal.class), any()))
                .thenAnswer(i -> Payment.builder()
                        .id(777L)
                        .paymentCode("PAY-1077")
                        .amount(i.getArgument(1))
                        .paymentMethod(i.getArgument(2) != null ? i.getArgument(2) : PaymentMethod.BANK_TRANSFER)
                        .paymentStatus(PaymentStatus.PENDING)
                        .build());

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
    void registerPackage_ThirtyDayPackage_EndsOnTheThirtiethDay() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .startDate(LocalDate.of(2026, 11, 1))
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(eq(100L), eq(30))).thenReturn(0);
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(110L);
        when(codeFormatter.formatPackageRegCode(110L)).thenReturn("REG-PKG-110");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, memberUser);

        assertEquals(LocalDate.of(2026, 11, 1), res.getStartDate());
        assertEquals(LocalDate.of(2026, 11, 30), res.getEndDate()); // 1 to 30 November = 30 days, not 31
    }

    @Test
    void registerPackage_SingleVisit_EndsOnTheSameDay() {
        SportPackage singleVisit = SportPackage.builder()
                .id(11L)
                .code("BADMINTON_SELF_SINGLE")
                .name("Badminton Single Visit")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(1)
                .sessionCount(1)
                .priceAmount(BigDecimal.valueOf(50000.00))
                .isActive(true)
                .build();
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(11L)
                .startDate(LocalDate.of(2026, 10, 20))
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(11L)).thenReturn(Optional.of(singleVisit));
        when(membershipCardService.getApplicableDiscountPercentage(eq(100L), eq(1))).thenReturn(0);
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(111L);
        when(codeFormatter.formatPackageRegCode(111L)).thenReturn("REG-PKG-111");
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, memberUser);

        assertEquals(LocalDate.of(2026, 10, 20), res.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 20), res.getEndDate()); // valid on the visit date only
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
        assertEquals(LocalDate.of(2026, 11, 4), res.getEndDate()); // Recomputed: 30 days counting today, 06/10 to 04/11
    }

    @Test
    void activateRegistration_NotFound_ThrowsResourceNotFoundException() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sportPackageService.activateRegistration(999L, receptionistUser));
    }

    @Test
    void registerPackage_AsMember_CreatesPendingPaymentAndSetsPaymentId() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(registrationRepository.getNextRegistrationCodeSequence()).thenReturn(77L);
        when(codeFormatter.formatPackageRegCode(77L)).thenReturn("REG-PKG-0077");
        when(paymentService.createPendingPayment(eq(memberProfile), any(BigDecimal.class), eq(PaymentMethod.VNPAY)))
                .thenReturn(Payment.builder().id(777L).paymentCode("PAY-1077").paymentStatus(PaymentStatus.PENDING).build());
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.registerPackage(req, memberUser);

        assertEquals(PackageRegistrationStatus.PENDING_PAYMENT, res.getStatus());
        assertEquals(RegistrationChannel.ONLINE, res.getChannel());
        assertEquals(777L, res.getPaymentId());
        assertEquals("PAY-1077", res.getPaymentCode());
    }

    @Test
    void registerPackage_Online_WithZeroPrice_ThrowsBusinessRuleException() {
        SportPackage freePkg = SportPackage.builder()
                .id(15L)
                .code("FREE-PKG")
                .name("Free Package")
                .priceAmount(BigDecimal.ZERO)
                .isActive(true)
                .durationDays(30)
                .sessionCount(5)
                .build();

        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(15L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(15L)).thenReturn(Optional.of(freePkg));

        assertThrows(BusinessRuleException.class, () -> sportPackageService.registerPackage(req, memberUser));
        verify(registrationRepository, never()).getNextRegistrationCodeSequence();
    }

    @Test
    void registerPackage_Online_With100PercentDiscount_ThrowsBusinessRuleException() {
        PackageRegistrationRequest req = PackageRegistrationRequest.builder()
                .packageId(10L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(sportPackage));
        when(membershipCardService.getApplicableDiscountPercentage(100L, sportPackage.getDurationDays())).thenReturn(100);

        assertThrows(BusinessRuleException.class, () -> sportPackageService.registerPackage(req, memberUser));
        verify(registrationRepository, never()).getNextRegistrationCodeSequence();
    }

    @Test
    void activateRegistration_WithPendingPayment_CallsMarkPaymentSuccess() {
        Payment payment = Payment.builder()
                .id(88L)
                .paymentCode("PAY-0088")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(88L)
                .registrationCode("REG-PKG-0088")
                .member(memberProfile)
                .sportPackage(sportPackage)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .payment(payment)
                .startDate(LocalDate.now(clock))
                .endDate(LocalDate.now(clock).plusDays(29))
                .build();

        when(registrationRepository.findById(88L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.activateRegistration(88L, receptionistUser);

        assertEquals(PackageRegistrationStatus.ACTIVE, res.getStatus());
        verify(paymentService).markPaymentSuccess(88L, receptionistUser, null);
    }

    @Test
    void activateRegistration_WithActualPaymentMethodCash_PassesCashToPaymentService() {
        Payment payment = Payment.builder()
                .id(88L)
                .paymentCode("PAY-0088")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(88L)
                .registrationCode("REG-PKG-0088")
                .member(memberProfile)
                .sportPackage(sportPackage)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .payment(payment)
                .startDate(LocalDate.now(clock))
                .endDate(LocalDate.now(clock).plusDays(29))
                .build();

        when(registrationRepository.findById(88L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.activateRegistration(88L, receptionistUser, PaymentMethod.CASH);

        assertEquals(PackageRegistrationStatus.ACTIVE, res.getStatus());
        verify(paymentService).markPaymentSuccess(88L, receptionistUser, PaymentMethod.CASH);
    }

    @Test
    void cancelRegistration_AsMember_PendingRegistration_CancelsAndSetsPaymentToFailed() {
        Payment payment = Payment.builder()
                .id(88L)
                .paymentCode("PAY-0088")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(88L)
                .registrationCode("REG-PKG-0088")
                .member(memberProfile)
                .sportPackage(sportPackage)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .payment(payment)
                .originalPrice(sportPackage.getPriceAmount())
                .paidAmount(sportPackage.getPriceAmount())
                .build();

        when(registrationRepository.findById(88L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.cancelRegistration(88L, memberUser);

        assertEquals(PackageRegistrationStatus.CANCELLED, res.getStatus());
        verify(paymentService).markPaymentFailed(eq(88L), org.mockito.ArgumentMatchers.contains("member user id 100"));
    }

    @Test
    void cancelRegistration_ByReceptionist_RecordsStaffInNote() {
        Payment payment = Payment.builder()
                .id(88L)
                .paymentCode("PAY-0088")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(88L)
                .registrationCode("REG-PKG-0088")
                .member(memberProfile)
                .sportPackage(sportPackage)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .payment(payment)
                .originalPrice(sportPackage.getPriceAmount())
                .paidAmount(sportPackage.getPriceAmount())
                .build();
        receptionistUser.setFullName("Front Desk");

        when(registrationRepository.findById(88L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(SportPackageRegistration.class))).thenAnswer(i -> i.getArgument(0));

        PackageRegistrationResponse res = sportPackageService.cancelRegistration(88L, receptionistUser);

        assertEquals(PackageRegistrationStatus.CANCELLED, res.getStatus());
        verify(paymentService).markPaymentFailed(eq(88L), org.mockito.ArgumentMatchers.contains("staff user id 200"));
    }

    @Test
    void cancelRegistration_AsNonOwner_ThrowsResourceNotFoundException() {
        UserAccount otherUser = UserAccount.builder().id(999L).role(memberUser.getRole()).build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(89L)
                .member(memberProfile)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findById(89L)).thenReturn(Optional.of(reg));

        assertThrows(ResourceNotFoundException.class, () -> sportPackageService.cancelRegistration(89L, otherUser));
    }

    @Test
    void cancelRegistration_ActiveRegistration_ThrowsBusinessRuleException() {
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(90L)
                .member(memberProfile)
                .status(PackageRegistrationStatus.ACTIVE)
                .build();

        when(registrationRepository.findById(90L)).thenReturn(Optional.of(reg));

        assertThrows(BusinessRuleException.class, () -> sportPackageService.cancelRegistration(90L, memberUser));
    }

    @Test
    void activateRegistration_WithActualMethod_WhenNoPaymentLinked_ThrowsBusinessRuleException() {
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(91L)
                .member(memberProfile)
                .sportPackage(sportPackage)
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .payment(null)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .build();

        when(registrationRepository.findById(91L)).thenReturn(Optional.of(reg));

        assertThrows(BusinessRuleException.class,
                () -> sportPackageService.activateRegistration(91L, receptionistUser, PaymentMethod.CASH));
    }

    @Test
    void updatePackage_success_recordsAuditLog() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Old Name")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(12)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(200000.00))
                .isActive(true)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));
        when(sportPackageRepository.saveAndFlush(any(SportPackage.class))).thenAnswer(inv -> inv.getArgument(0));

        SportPackageUpdateRequest updateReq = SportPackageUpdateRequest.builder()
                .name("New Yoga Flow")
                .price(BigDecimal.valueOf(250000.00))
                .sessionCount(15)
                .durationDays(30)
                .sessionMinutes(90)
                .active(true)
                .description("Updated description")
                .build();

        UserAccount manager = UserAccount.builder().id(99L).email("mgr@sportify.com").fullName("Manager").build();
        SportPackageResponse res = sportPackageService.updatePackage(10L, updateReq, manager);

        assertNotNull(res);
        assertEquals("New Yoga Flow", res.getName());
        assertEquals(BigDecimal.valueOf(250000.00), res.getPriceAmount());
        assertEquals(90, res.getSessionMinutes());
        assertEquals(15, res.getSessionCount());

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditAction.PACKAGE_UPDATED, event.getAction());
        assertEquals("SPORT_PACKAGE", event.getEntityType());
        assertEquals(10L, event.getEntityId());
        assertEquals("PKG-10", event.getEntityCode());
        assertEquals(manager, event.getActor());
        assertNotNull(event.getDetails());
        assertTrue(event.getDetails().containsKey("before"));
        assertTrue(event.getDetails().containsKey("after"));
    }

    @Test
    void updatePackage_invalidSessionMinutes_throwsBusinessRuleException() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Old Name")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(12)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(200000.00))
                .isActive(true)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));

        SportPackageUpdateRequest req45 = SportPackageUpdateRequest.builder()
                .name("New Name")
                .price(BigDecimal.valueOf(250000.00))
                .sessionCount(15)
                .durationDays(30)
                .sessionMinutes(45)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> sportPackageService.updatePackage(10L, req45, null));
        verify(sportPackageRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void updatePackage_zeroOrNegativePrice_throwsBusinessRuleException() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Old Name")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(12)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(200000.00))
                .isActive(true)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));

        SportPackageUpdateRequest zeroPriceReq = SportPackageUpdateRequest.builder()
                .name("New Name")
                .price(BigDecimal.ZERO)
                .sessionCount(15)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> sportPackageService.updatePackage(10L, zeroPriceReq, null));

        SportPackageUpdateRequest negPriceReq = SportPackageUpdateRequest.builder()
                .name("New Name")
                .price(BigDecimal.valueOf(-50000))
                .sessionCount(15)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> sportPackageService.updatePackage(10L, negPriceReq, null));
    }

    @Test
    void updatePackage_blankDescription_clearsDescription() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Yoga Flow")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(200000.00))
                .description("Initial description")
                .isActive(true)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));
        when(sportPackageRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SportPackageUpdateRequest req = SportPackageUpdateRequest.builder()
                .name("Yoga Flow")
                .price(BigDecimal.valueOf(200000.00))
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .description("   ")
                .build();

        SportPackageResponse res = sportPackageService.updatePackage(10L, req, null);
        assertNull(res.getDescription());
        assertNull(pkg.getDescription());
    }

    @Test
    void updatePackage_nullDescription_clearsDescription() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Yoga Flow")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(200000.00))
                .description("Initial description")
                .isActive(true)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));
        when(sportPackageRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SportPackageUpdateRequest req = SportPackageUpdateRequest.builder()
                .name("Yoga Flow")
                .price(BigDecimal.valueOf(200000.00))
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .description(null)
                .build();

        SportPackageResponse res = sportPackageService.updatePackage(10L, req, null);
        assertNull(res.getDescription());
        assertNull(pkg.getDescription());
    }

    @Test
    void updatePackage_unknownId_throwsResourceNotFoundException() {
        when(sportPackageRepository.findById(999L)).thenReturn(Optional.empty());

        SportPackageUpdateRequest req = SportPackageUpdateRequest.builder()
                .name("New Name")
                .price(BigDecimal.valueOf(250000.00))
                .sessionCount(15)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> sportPackageService.updatePackage(999L, req, null));
    }

    @Test
    void updatePackage_existingRegistrationsRetainSnapshotPrice() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Yoga Basic")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(100000.00))
                .isActive(true)
                .build();

        SportPackageRegistration existingRegistration = SportPackageRegistration.builder()
                .id(50L)
                .registrationCode("REG-PKG-0050")
                .member(memberProfile)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(BigDecimal.valueOf(100000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(100000.00))
                .status(PackageRegistrationStatus.ACTIVE)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));
        when(sportPackageRepository.saveAndFlush(any(SportPackage.class))).thenAnswer(inv -> inv.getArgument(0));

        // Manager increases package price from 100,000 to 200,000
        SportPackageUpdateRequest updateReq = SportPackageUpdateRequest.builder()
                .name("Yoga Basic Updated")
                .price(BigDecimal.valueOf(200000.00))
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        sportPackageService.updatePackage(10L, updateReq, null);

        // Verify package price is updated
        assertEquals(BigDecimal.valueOf(200000.00), pkg.getPriceAmount());

        // Verify the existing registration keeps its original snapshot price
        assertEquals(BigDecimal.valueOf(100000.00), existingRegistration.getOriginalPrice());
        assertEquals(BigDecimal.valueOf(100000.00), existingRegistration.getPaidAmount());
        assertEquals(0, existingRegistration.getDiscountPercentage());
        verifyNoInteractions(registrationRepository);
    }

    @Test
    void memberImmediatelySeesNewPrice() {
        SportPackage pkg = SportPackage.builder()
                .id(10L)
                .code("PKG-10")
                .name("Yoga Basic")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(100000.00))
                .isActive(true)
                .build();

        when(sportPackageRepository.findById(10L)).thenReturn(Optional.of(pkg));
        when(sportPackageRepository.saveAndFlush(any(SportPackage.class))).thenAnswer(inv -> inv.getArgument(0));

        // Manager updates price
        SportPackageUpdateRequest updateReq = SportPackageUpdateRequest.builder()
                .name("Yoga Basic")
                .price(BigDecimal.valueOf(180000.00))
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        sportPackageService.updatePackage(10L, updateReq, null);

        // Active catalog queries immediately reflect the updated package price
        when(sportPackageRepository.findByIsActiveTrue()).thenReturn(List.of(pkg));
        when(sportPackageRepository.findBySportIdAndIsActiveTrue(1L)).thenReturn(List.of(pkg));

        List<SportPackageResponse> activePackages = sportPackageService.getAllActivePackages();
        assertEquals(1, activePackages.size());
        assertEquals(BigDecimal.valueOf(180000.00), activePackages.get(0).getPriceAmount());

        List<SportPackageResponse> sportPackages = sportPackageService.getPackagesBySport(1L);
        assertEquals(1, sportPackages.size());
        assertEquals(BigDecimal.valueOf(180000.00), sportPackages.get(0).getPriceAmount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getManagerPackages_queriesWithFilters() {
        SportPackage pkg1 = SportPackage.builder()
                .id(10L)
                .code("PKG-1")
                .name("Package 1")
                .sport(sport)
                .trainingFormat(TrainingFormat.COACH_LED)
                .durationDays(60)
                .sessionCount(20)
                .sessionMinutes(90)
                .priceAmount(BigDecimal.valueOf(500000.00))
                .isActive(false)
                .build();

        when(sportPackageRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(pkg1));

        List<SportPackageResponse> result = sportPackageService.getManagerPackages(1L, TrainingFormat.COACH_LED, false);

        assertEquals(1, result.size());
        assertEquals("PKG-1", result.get(0).getCode());
        assertEquals(false, result.get(0).getIsActive());
    }
}

