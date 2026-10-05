package com.sportify.catalog.service;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.BookingServiceImpl;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
public class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ClassSessionRepository sessionRepository;
    @Mock
    private SportPackageRegistrationRepository registrationRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private CodeFormatter codeFormatter;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Sport sport;
    private ClassSession session;
    private SportPackageRegistration packageReg;
    private UserAccount memberUser;
    private MemberProfile memberProfile;

    @BeforeEach
    void setUp() {
        sport = Sport.builder().id(1L).name("Badminton").code("BADMINTON").build();
        session = ClassSession.builder()
                .id(20L)
                .sport(sport)
                .sessionDate(LocalDate.of(2026, 10, 6))
                .capacity(10)
                .bookedCount(2)
                .build();

        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        memberUser = UserAccount.builder().id(100L).role(memberRole).fullName("Member User").build();
        memberProfile = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-100").build();

        SportPackage pkg = SportPackage.builder().id(5L).sport(sport).sessionCount(8).build();
        packageReg = SportPackageRegistration.builder()
                .id(50L)
                .member(memberProfile)
                .sportPackage(pkg)
                .status(PackageRegistrationStatus.ACTIVE)
                .totalSessions(8)
                .remainingSessions(5)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .build();
    }

    @Test
    void createBooking_Success_DecrementsRemainingSessionsAndIncrementsBookedCount() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatBookingCode(1L)).thenReturn("BKG-001");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookingResponse res = bookingService.createBooking(req, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CONFIRMED, res.getStatus());
        assertEquals(4, packageReg.getRemainingSessions()); // Remaining sessions decremented from 5 to 4
        assertEquals(3, session.getBookedCount()); // Session booked count incremented from 2 to 3
        verify(registrationRepository).save(packageReg);
        verify(sessionRepository).save(session);
    }

    @Test
    void createBooking_AsMember_IgnoresSpoofedMemberIdAndEnforcesActorId() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .memberId(999L) // Spoofed member ID
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(2L);
        when(codeFormatter.formatBookingCode(2L)).thenReturn("BKG-002");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(2L);
            return b;
        });

        BookingResponse res = bookingService.createBooking(req, memberUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId()); // Enforced 100L
    }

    @Test
    void createBooking_SessionFull_ThrowsBusinessRuleException() {
        session.setBookedCount(10); // Full capacity

        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void createBooking_AlreadyBooked_ThrowsBusinessRuleException() {
        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();
        Booking existing = Booking.builder().id(10L).status(BookingStatus.CONFIRMED).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.of(existing));

        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void cancelBooking_AsOwner_Success_RestoresSessionAndIncrementsRemainingSessions() {
        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals(1, session.getBookedCount()); // Decremented session booked count (seat restored)
        assertEquals(6, packageReg.getRemainingSessions()); // Incremented package remaining sessions (session restored)
    }

    @Test
    void cancelBooking_ByDifferentMember_ThrowsBusinessRuleException() {
        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        UserAccount otherMember = UserAccount.builder().id(999L).role(memberRole).build();

        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile) // Belongs to member 100L
                .session(session)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(BusinessRuleException.class, () -> bookingService.cancelBooking(1L, otherMember));
    }
}
