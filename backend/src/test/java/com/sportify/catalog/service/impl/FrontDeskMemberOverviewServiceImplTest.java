package com.sportify.catalog.service.impl;

import com.sportify.catalog.entity.*;
import com.sportify.catalog.mapper.FrontDeskMapper;
import com.sportify.catalog.repository.*;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.*;
import com.sportify.identity.mapper.MemberSearchMapper;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FrontDeskMemberOverviewServiceImplTest {
    @Mock private MemberProfileRepository members;
    @Mock private MemberCardRepository cards;
    @Mock private SportPackageRegistrationRepository registrations;
    @Mock private CheckInRepository checkIns;
    private FrontDeskMemberOverviewServiceImpl service;
    private final LocalDate today = LocalDate.of(2026, 10, 15);
    private MemberProfile profile;

    @BeforeEach
    void setUp() {
        service = new FrontDeskMemberOverviewServiceImpl(members, cards, registrations, checkIns,
                Mappers.getMapper(FrontDeskMapper.class), Mappers.getMapper(MemberSearchMapper.class),
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        profile = MemberProfile.builder().id(7L).memberCode("MEM-0007").userAccount(
                UserAccount.builder().id(7L).fullName("Alice Smith").email("alice@example.com")
                        .phone("0901234567").build()).build();
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
