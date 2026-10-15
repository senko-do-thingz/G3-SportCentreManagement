package com.sportify.catalog.service.impl;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.mapper.FrontDeskMapper;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrontDeskQueueServiceImplTest {
    @Mock private MemberCardRepository cards;
    @Mock private SportPackageRegistrationRepository registrations;

    @Test
    void pendingCards_whenCallerRequestsDescendingOrder_forcesOldestFirstAndCapsPageSize() {
        var member = MemberProfile.builder().memberCode("MEM-0001")
                .userAccount(UserAccount.builder().fullName("Alice").build()).build();
        var card = MemberCard.builder().id(1L).member(member)
                .tier(MembershipCardTier.builder().name("Gold").build())
                .pricePaid(BigDecimal.TEN).createdAt(LocalDateTime.of(2026, 10, 15, 9, 0)).build();
        when(cards.findByStatus(eq(CardStatus.PENDING_PAYMENT), any())).thenReturn(new PageImpl<>(List.of(card)));
        var service = new FrontDeskQueueServiceImpl(cards, registrations, Mappers.getMapper(FrontDeskMapper.class));
        var result = service.pendingCards(PageRequest.of(2, 500, Sort.by(Sort.Direction.DESC, "createdAt")));
        verify(cards).findByStatus(CardStatus.PENDING_PAYMENT, PageRequest.of(2, 100, Sort.by("createdAt", "id")));
        assertEquals("Alice", result.getContent().getFirst().memberName());
        assertEquals("Gold", result.getContent().getFirst().tierName());
        assertEquals(BigDecimal.TEN, result.getContent().getFirst().amount());
    }

    @Test
    void pendingPackages_whenCalled_queriesOnlyPendingPaymentInStableOldestOrder() {
        when(registrations.findByStatus(eq(PackageRegistrationStatus.PENDING_PAYMENT), any())).thenReturn(Page.empty());
        var service = new FrontDeskQueueServiceImpl(cards, registrations, Mappers.getMapper(FrontDeskMapper.class));
        assertTrue(service.pendingPackages(PageRequest.of(0, 20, Sort.by("passwordHash"))).isEmpty());
        verify(registrations).findByStatus(PackageRegistrationStatus.PENDING_PAYMENT,
                PageRequest.of(0, 20, Sort.by("createdAt", "id")));
    }
}
