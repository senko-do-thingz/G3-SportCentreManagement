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
import org.springframework.security.access.AccessDeniedException;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private MembershipCardTier vipTier;
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

        vipTier = MembershipCardTier.builder()
                .id(3L)
                .code("VIP")
                .name("VIP Card")
                .price(BigDecimal.valueOf(600000.00))
                .durationMonths(12)
                .discountPercentage(10)
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
        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus()); // online request waits for the front desk
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
        // Renewal starts the day after the current card ends (2027-01-02) and lasts 12 months: to 2028-01-01
        assertEquals(LocalDate.of(2027, 1, 2), res.getStartDate());
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

    // ------------------------------------------------------------------ one card per member, pay before the discount

    private void stubNewCard(long seq) {
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(seq);
        when(codeFormatter.formatCardCode(seq)).thenReturn("CARD-" + seq);
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));
    }

    private MemberCard paidCard(MembershipCardTier tier, LocalDate start, LocalDate end) {
        return MemberCard.builder().id(7L).cardCode("CARD-7").member(memberProfile).tier(tier)
                .startDate(start).endDate(end).status(CardStatus.ACTIVE).pricePaid(tier.getPrice()).build();
    }

    @Test
    void purchaseCard_AsMember_WaitsForPaymentAndLastsTwelveMonths() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().tierId(2L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        stubNewCard(20L);

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus()); // no discount until the front desk confirms the payment
        assertEquals(LocalDate.of(2026, 10, 6), res.getStartDate());
        assertEquals(LocalDate.of(2027, 10, 5), res.getEndDate()); // 12 months counting today, not 12 months + 1 day
        assertEquals(0, BigDecimal.valueOf(200000).compareTo(res.getPricePaid()));
    }

    @Test
    void purchaseCard_AsReceptionist_IsPaidAtTheDeskAndActive() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().memberId(100L).tierId(2L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        stubNewCard(21L);

        MemberCardResponse res = membershipCardService.purchaseCard(req, receptionistUser);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
    }

    @Test
    void purchaseCard_RequestAlreadyWaitingForPayment_ThrowsBusinessRuleException() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().tierId(3L).build();
        MemberCard pending = MemberCard.builder().id(5L).cardCode("CARD-5").member(memberProfile).tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(vipTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of(pending));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> membershipCardService.purchaseCard(req, memberUser));
        assertTrue(ex.getMessage().contains("CARD-5"));
    }

    @Test
    void purchaseCard_InactiveTier_ThrowsBusinessRuleException() {
        goldTier.setIsActive(false);
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().tierId(2L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));

        assertThrows(BusinessRuleException.class, () -> membershipCardService.purchaseCard(req, memberUser));
    }

    @Test
    void purchaseCard_UpgradeGoldToVip_PaysTheDifferenceAndKeepsTheEndDate() {
        MemberCard gold = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().tierId(3L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(vipTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of(gold));
        stubNewCard(22L);

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertEquals(0, BigDecimal.valueOf(400000).compareTo(res.getPricePaid())); // 600.000 - 200.000
        assertEquals(LocalDate.of(2026, 10, 6), res.getStartDate());
        assertEquals(LocalDate.of(2027, 2, 28), res.getEndDate());
        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        assertEquals(CardStatus.ACTIVE, gold.getStatus()); // replaced only when the payment is confirmed
    }

    @Test
    void purchaseCard_UpgradeAtTheDesk_ReplacesTheCurrentCard() {
        MemberCard gold = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().memberId(100L).tierId(3L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(vipTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of(gold));
        stubNewCard(23L);

        MemberCardResponse res = membershipCardService.purchaseCard(req, receptionistUser);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(CardStatus.REPLACED, gold.getStatus());
    }

    @Test
    void purchaseCard_CheaperTier_StartsTheDayAfterTheCurrentCardEnds() {
        MemberCard vip = paidCard(vipTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().tierId(2L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of(vip));
        stubNewCard(24L);

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertEquals(LocalDate.of(2027, 3, 1), res.getStartDate());
        assertEquals(LocalDate.of(2028, 2, 29), res.getEndDate());
        assertEquals(0, BigDecimal.valueOf(200000).compareTo(res.getPricePaid()));
        assertEquals(CardStatus.ACTIVE, vip.getStatus());
    }

    @Test
    void purchaseCard_UpgradeWhenTheCardIsAlreadyRenewed_ThrowsBusinessRuleException() {
        MemberCard gold = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        MemberCard renewal = paidCard(goldTier, LocalDate.of(2027, 3, 1), LocalDate.of(2028, 2, 29));
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder().tierId(3L).build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(vipTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of(gold, renewal));

        assertThrows(BusinessRuleException.class, () -> membershipCardService.purchaseCard(req, memberUser));
    }

    // ------------------------------------------------------------------ activation at the front desk

    @Test
    void activateCard_PendingRequest_BecomesActiveFromToday() {
        MemberCard pending = MemberCard.builder().id(30L).cardCode("CARD-30").member(memberProfile).tier(goldTier)
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2027, 9, 30))
                .status(CardStatus.PENDING_PAYMENT).pricePaid(goldTier.getPrice()).build();
        when(memberCardRepository.findById(30L)).thenReturn(Optional.of(pending));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(30L, receptionistUser);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(LocalDate.of(2026, 10, 6), res.getStartDate()); // the 12 months start on the payment date
        assertEquals(LocalDate.of(2027, 10, 5), res.getEndDate());
    }

    @Test
    void activateCard_Upgrade_ReplacesTheCurrentCard() {
        MemberCard gold = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        MemberCard upgrade = MemberCard.builder().id(31L).cardCode("CARD-31").member(memberProfile).tier(vipTier)
                .startDate(LocalDate.of(2026, 10, 4)).endDate(LocalDate.of(2027, 2, 28))
                .status(CardStatus.PENDING_PAYMENT).pricePaid(BigDecimal.valueOf(400000.00)).build();
        when(memberCardRepository.findById(31L)).thenReturn(Optional.of(upgrade));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of(gold));
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(31L, receptionistUser);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(LocalDate.of(2027, 2, 28), res.getEndDate());
        assertEquals(CardStatus.REPLACED, gold.getStatus());
    }

    @Test
    void activateCard_UpgradeButTheCurrentCardHasEnded_ThrowsBusinessRuleException() {
        MemberCard upgrade = MemberCard.builder().id(32L).cardCode("CARD-32").member(memberProfile).tier(vipTier)
                .startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2026, 10, 1))
                .status(CardStatus.PENDING_PAYMENT).pricePaid(BigDecimal.valueOf(400000.00)).build();
        when(memberCardRepository.findById(32L)).thenReturn(Optional.of(upgrade));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());

        assertThrows(BusinessRuleException.class, () -> membershipCardService.activateCard(32L, receptionistUser));
    }

    @Test
    void activateCard_AlreadyActive_ThrowsBusinessRuleException() {
        MemberCard active = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        when(memberCardRepository.findById(7L)).thenReturn(Optional.of(active));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> membershipCardService.activateCard(7L, receptionistUser));
        assertEquals("Cannot activate membership card with status: ACTIVE. Only PENDING_PAYMENT cards can be activated.", ex.getMessage());
    }

    // ------------------------------------------------------------------ cancelling an unpaid request

    @Test
    void cancelCard_OwnPendingRequest_IsCancelled() {
        MemberCard pending = MemberCard.builder().id(40L).cardCode("CARD-40").member(memberProfile).tier(goldTier)
                .startDate(LocalDate.of(2026, 10, 6)).endDate(LocalDate.of(2027, 10, 5))
                .status(CardStatus.PENDING_PAYMENT).pricePaid(goldTier.getPrice()).build();
        when(memberCardRepository.findById(40L)).thenReturn(Optional.of(pending));
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.cancelCard(40L, memberUser);

        assertEquals(CardStatus.CANCELLED, res.getStatus());
    }

    @Test
    void cancelCard_AnotherMembersRequest_ThrowsAccessDeniedException() {
        UserAccount otherUser = UserAccount.builder().id(101L).role(memberUser.getRole()).fullName("Other").build();
        MemberProfile otherProfile = MemberProfile.builder().id(101L).userAccount(otherUser).memberCode("MEM-101").build();
        MemberCard pending = MemberCard.builder().id(41L).cardCode("CARD-41").member(otherProfile).tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT).pricePaid(goldTier.getPrice()).build();
        when(memberCardRepository.findById(41L)).thenReturn(Optional.of(pending));

        assertThrows(AccessDeniedException.class, () -> membershipCardService.cancelCard(41L, memberUser));
    }

    @Test
    void cancelCard_PaidCard_ThrowsBusinessRuleException() {
        MemberCard active = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        when(memberCardRepository.findById(7L)).thenReturn(Optional.of(active));

        assertThrows(BusinessRuleException.class, () -> membershipCardService.cancelCard(7L, memberUser));
    }

    @Test
    void getApplicableDiscountPercentage_CardThatHasNotStartedYet_ReturnsZero() {
        MemberCard nextTerm = paidCard(goldTier, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of(nextTerm));

        assertEquals(0, membershipCardService.getApplicableDiscountPercentage(100L, 30));
    }
}
