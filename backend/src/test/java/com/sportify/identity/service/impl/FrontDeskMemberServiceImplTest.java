package com.sportify.identity.service.impl;

import com.sportify.catalog.entity.*;
import com.sportify.catalog.mapper.FrontDeskMapper;
import com.sportify.catalog.repository.*;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.entity.*;
import com.sportify.identity.mapper.FrontDeskMemberMapper;
import com.sportify.identity.mapper.MemberSearchMapper;
import com.sportify.identity.repository.*;
import com.sportify.identity.service.TemporaryPasswordGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FrontDeskMemberServiceImplTest {
    @Mock private MemberProfileRepository members;
    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private MemberCardRepository cards;
    @Mock private SportPackageRegistrationRepository registrations;
    @Mock private CheckInRepository checkIns;
    @Mock private TemporaryPasswordGenerator passwords;
    @Mock private PasswordEncoder encoder;
    private FrontDeskMemberServiceImpl service;
    private final LocalDate today = LocalDate.of(2026, 10, 15);
    private final MemberCreateRequest request = new MemberCreateRequest(" Alice Smith ", " 0901234567 ", " ALICE@EXAMPLE.COM ");
    private MemberProfile profile;

    @BeforeEach
    void setUp() {
        service = new FrontDeskMemberServiceImpl(members, users, roles, cards, registrations, checkIns,
                Mappers.getMapper(FrontDeskMapper.class), Mappers.getMapper(FrontDeskMemberMapper.class),
                Mappers.getMapper(MemberSearchMapper.class), passwords, encoder, new CodeFormatter(),
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        profile = MemberProfile.builder().id(7L).memberCode("MEM-0007").userAccount(
                UserAccount.builder().id(7L).fullName("Alice Smith").email("alice@example.com")
                        .phone("0901234567").passwordHash("never-return-this-hash").build()).build();
    }

    @Test
    void create_whenEmailExists_rejectsBeforeGeneratingCredentials() {
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(request));
        verifyNoInteractions(passwords, encoder, roles);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void create_whenPhoneExists_rejectsBeforeGeneratingCredentials() {
        when(users.existsByPhone("0901234567")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(request));
        verify(users).existsByEmailIgnoreCase("alice@example.com");
        verifyNoInteractions(passwords, encoder, roles);
    }

    private void stubCreation() {
        when(roles.findByCode("MEMBER")).thenReturn(Optional.of(Role.builder().code("MEMBER").build()));
        when(passwords.generate()).thenReturn("StrongTemporary123!");
        when(encoder.encode("StrongTemporary123!")).thenReturn("encoded-password");
        when(members.getNextMemberCode()).thenReturn(7L);
    }

    @Test
    void create_whenValid_normalizesUsesSequenceHashesPasswordAndReturnsItOnce() {
        stubCreation();
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            UserAccount user = call.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(members.saveAndFlush(any())).thenAnswer(call -> {
            MemberProfile member = call.getArgument(0);
            member.setId(member.getUserAccount().getId());
            return member;
        });
        var response = service.create(request);
        assertEquals("MEM-0007", response.memberCode());
        assertEquals("alice@example.com", response.email());
        assertEquals("0901234567", response.phone());
        assertEquals("Alice Smith", response.fullName());
        assertEquals("StrongTemporary123!", response.temporaryPassword());
        assertFalse(response.toString().contains(response.temporaryPassword()));
        ArgumentCaptor<UserAccount> user = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).saveAndFlush(user.capture());
        assertEquals("encoded-password", user.getValue().getPasswordHash());
        assertEquals("MEMBER", user.getValue().getRole().getCode());
        ArgumentCaptor<MemberProfile> member = ArgumentCaptor.forClass(MemberProfile.class);
        verify(members).saveAndFlush(member.capture());
        assertEquals(today, member.getValue().getJoinedOn());
        assertEquals("BEGINNER", member.getValue().getCurrentLevel());
    }

    @Test
    void create_whenConcurrentAccountConstraintFails_returnsConflict() {
        stubCreation();
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique email or phone"));
        assertThrows(ConflictException.class, () -> service.create(request));
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    void create_whenMemberConstraintFails_returnsConflict() {
        stubCreation();
        when(users.saveAndFlush(any())).thenReturn(profile.getUserAccount());
        when(members.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique member code"));
        assertThrows(ConflictException.class, () -> service.create(request));
    }

    private void stubOverview() {
        when(members.findWithUserById(7L)).thenReturn(Optional.of(profile));
        when(cards.findCurrentForOverview(eq(7L), eq(today), any())).thenReturn(List.of());
        when(cards.findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc(7L, CardStatus.PENDING_PAYMENT))
                .thenReturn(Optional.empty());
        when(registrations.findForOverview(7L, today)).thenReturn(List.of());
        when(checkIns.findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(7L)).thenReturn(List.of());
    }

    @Test
    void overview_whenMemberIsUnknown_returnsNotFoundWithoutOtherQueries() {
        assertThrows(ResourceNotFoundException.class, () -> service.overview(99L));
        verifyNoInteractions(cards, registrations, checkIns);
    }

    @Test
    void overview_whenNoCardOrPendingRequest_returnsNullAndEmptyLists() {
        stubOverview();
        var response = service.overview(7L);
        assertEquals("Alice Smith", response.profile().getFullName());
        assertNull(response.currentCard());
        assertNull(response.pendingCardRequest());
        assertNull(response.profile().getCardTier());
        assertTrue(response.activePackageRegistrations().isEmpty());
        assertTrue(response.pendingPackageRegistrations().isEmpty());
        assertTrue(response.recentCheckIns().isEmpty());
    }

    @Test
    void overview_whenPendingItemsAndTwoCheckIns_assemblesAllMappedParts() {
        when(members.findWithUserById(7L)).thenReturn(Optional.of(profile));
        var tier = MembershipCardTier.builder().code("GOLD").name("Gold").build();
        var card = MemberCard.builder().id(11L).member(profile).tier(tier).status(CardStatus.ACTIVE)
                .startDate(today).pricePaid(BigDecimal.TEN).build();
        var pending = MemberCard.builder().id(12L).member(profile).tier(tier).status(CardStatus.PENDING_PAYMENT)
                .createdAt(today.atStartOfDay()).pricePaid(BigDecimal.TEN).build();
        when(cards.findCurrentForOverview(eq(7L), eq(today), any())).thenReturn(List.of(card));
        when(cards.findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc(7L, CardStatus.PENDING_PAYMENT))
                .thenReturn(Optional.of(pending));
        var sportPackage = SportPackage.builder().id(5L).name("Swimming").build();
        when(registrations.findForOverview(7L, today)).thenReturn(List.of(
                SportPackageRegistration.builder().id(20L).member(profile).sportPackage(sportPackage)
                        .status(PackageRegistrationStatus.ACTIVE).paidAmount(BigDecimal.TEN).build(),
                SportPackageRegistration.builder().id(21L).member(profile).sportPackage(sportPackage)
                        .status(PackageRegistrationStatus.PENDING_PAYMENT).paidAmount(BigDecimal.TEN).build()));
        when(checkIns.findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(7L)).thenReturn(List.of(
                CheckIn.builder().id(2L).checkedInAt(today.atTime(10, 0)).result(CheckInResult.ALLOWED).build(),
                CheckIn.builder().id(1L).checkedInAt(today.atTime(9, 0)).result(CheckInResult.DENIED).build()));
        var response = service.overview(7L);
        assertEquals("GOLD", response.profile().getCardTier());
        assertEquals(11L, response.currentCard().id());
        assertEquals(12L, response.pendingCardRequest().id());
        assertEquals("Swimming", response.activePackageRegistrations().getFirst().packageName());
        assertEquals(21L, response.pendingPackageRegistrations().getFirst().id());
        assertEquals(List.of(2L, 1L), response.recentCheckIns().stream().map(r -> r.id()).toList());
        assertEquals("Alice Smith", response.pendingCardRequest().memberName());
        assertEquals(BigDecimal.TEN, response.pendingCardRequest().amount());
    }
}
