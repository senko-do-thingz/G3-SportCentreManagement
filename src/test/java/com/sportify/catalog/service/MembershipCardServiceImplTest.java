package com.sportify.catalog.service;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.MembershipCardTierRepository;
import com.sportify.catalog.service.impl.MembershipCardServiceImpl;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MembershipCardServiceImplTest {

    @Mock
    private MembershipCardTierRepository tierRepository;
    @Mock
    private MemberCardRepository memberCardRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CodeFormatter codeFormatter;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private MembershipCardServiceImpl membershipCardService;

    private MembershipCardTier goldTier;
    private UserAccount memberUser;
    private UserAccount receptionistUser;
    private MemberProfile memberProfile;

    @BeforeEach
    void setUp() {
        goldTier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold Card")
                .price(BigDecimal.valueOf(200000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        Role memberRole = Role.builder().id(1L).code("MEMBER").name("Member").build();
        memberUser = UserAccount.builder().id(100L).role(memberRole).fullName("Test Member").build();
        memberProfile = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-100").build();

        Role recRole = Role.builder().id(2L).code("RECEPTIONIST").name("Receptionist").build();
        receptionistUser = UserAccount.builder().id(200L).role(recRole).build();
    }

    @Test
    void getAllActiveTiers_ReturnsList() {
        when(tierRepository.findByIsActiveTrue()).thenReturn(List.of(goldTier));

        List<CardTierResponse> tiers = membershipCardService.getAllActiveTiers();

        assertEquals(1, tiers.size());
        assertEquals("GOLD", tiers.get(0).getCode());
        assertEquals(5, tiers.get(0).getDiscountPercentage());
    }

    @Test
    void purchaseCard_AsMember_IgnoresSpoofedMemberIdAndEnforcesActorId() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .memberId(888L) // Spoofed ID
                .tierId(2L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(10L);
        when(codeFormatter.formatCardCode(10L)).thenReturn("CRD-010");
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> {
            MemberCard c = i.getArgument(0);
            c.setId(1L);
            return c;
        });

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId()); // Enforced actor ID
        assertEquals("GOLD", res.getTierCode());
        assertEquals(5, res.getDiscountPercentage());
    }

    @Test
    void purchaseCard_ExtendsExistingCardConsecutively() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(2L)
                .build();

        MemberCard existingCard = MemberCard.builder()
                .id(1L)
                .tier(goldTier)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1)) // Expires next year
                .status(CardStatus.ACTIVE)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(existingCard));
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(11L);
        when(codeFormatter.formatCardCode(11L)).thenReturn("CRD-011");
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> {
            MemberCard c = i.getArgument(0);
            c.setId(2L);
            return c;
        });

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        // Extension starts from existing end date: 2027-01-01 + 12 months = 2028-01-01
        assertEquals(LocalDate.of(2028, 1, 1), res.getEndDate());
    }

    @Test
    void getApplicableDiscountPercentage_SingleVisit_ReturnsZero() {
        // Single visit (duration <= 1 day) is excluded from discount per business rules
        int discount = membershipCardService.getApplicableDiscountPercentage(100L, 1);
        assertEquals(0, discount);
    }

    @Test
    void getApplicableDiscountPercentage_MultiDayPackage_ReturnsCardDiscount() {
        MemberCard card = MemberCard.builder().id(1L).tier(goldTier).build();
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(card));

        int discount = membershipCardService.getApplicableDiscountPercentage(100L, 30);
        assertEquals(5, discount);
    }

    @Test
    void purchaseCard_OrphanCoachUser_ThrowsBusinessRuleException() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .memberId(400L)
                .tierId(2L)
                .build();

        Role coachRole = Role.builder().id(4L).code("COACH").name("Coach").build();
        UserAccount coachUser = UserAccount.builder().id(400L).email("coach@sportify.com").role(coachRole).build();

        when(memberProfileRepository.findById(400L)).thenReturn(Optional.empty());
        when(userRepository.findById(400L)).thenReturn(Optional.of(coachUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.purchaseCard(req, receptionistUser));
        assertEquals("Cannot create member profile for non-member user: 400", ex.getMessage());
    }

    @Test
    void purchaseCard_OrphanManagerUser_ThrowsBusinessRuleException() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .memberId(300L)
                .tierId(2L)
                .build();

        Role mgrRole = Role.builder().id(3L).code("MANAGER").name("Manager").build();
        UserAccount managerUser = UserAccount.builder().id(300L).email("mgr@sportify.com").role(mgrRole).build();

        when(memberProfileRepository.findById(300L)).thenReturn(Optional.empty());
        when(userRepository.findById(300L)).thenReturn(Optional.of(managerUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.purchaseCard(req, receptionistUser));
        assertEquals("Cannot create member profile for non-member user: 300", ex.getMessage());
    }
}
