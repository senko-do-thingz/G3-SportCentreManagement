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
import org.springframework.security.access.AccessDeniedException;

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
import com.sportify.catalog.dto.MembershipCardTierUpdateRequest;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.payment.service.PaymentService;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private PaymentService paymentService;
    @Mock
    private CodeFormatter codeFormatter;
    @Mock
    private AuditService auditService;
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
        lenient().when(paymentService.createPendingPayment(any(MemberProfile.class), any(BigDecimal.class), any()))
                .thenAnswer(i -> Payment.builder()
                        .id(50L)
                        .paymentCode("PAY-1000")
                        .amount(i.getArgument(1))
                        .paymentMethod(i.getArgument(2) != null ? i.getArgument(2) : PaymentMethod.BANK_TRANSFER)
                        .paymentStatus(PaymentStatus.PENDING)
                        .build());
        lenient().when(memberCardRepository.findByMemberIdAndStatus(anyLong(), any())).thenReturn(List.of());

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
                .status(CardStatus.PENDING_PAYMENT).pricePaid(BigDecimal.valueOf(400000.00))
                .upgradeRequest(true).replacesCard(gold).build();
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
                .status(CardStatus.PENDING_PAYMENT).pricePaid(BigDecimal.valueOf(400000.00))
                .upgradeRequest(true).build();
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
    void cancelCard_AnotherMembersRequest_ThrowsResourceNotFoundException() {
        UserAccount otherUser = UserAccount.builder().id(101L).role(memberUser.getRole()).fullName("Other").build();
        MemberProfile otherProfile = MemberProfile.builder().id(101L).userAccount(otherUser).memberCode("MEM-101").build();
        MemberCard pending = MemberCard.builder().id(41L).cardCode("CARD-41").member(otherProfile).tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT).pricePaid(goldTier.getPrice()).build();
        when(memberCardRepository.findById(41L)).thenReturn(Optional.of(pending));

        assertThrows(ResourceNotFoundException.class, () -> membershipCardService.cancelCard(41L, memberUser));
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

    @Test
    void purchaseCard_AsMember_CreatesPendingPaymentAndSetsPaymentId() {
        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(2L)
                .paymentMethod(PaymentMethod.MOMO)
                .build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        stubNewCard(25L);
        when(paymentService.createPendingPayment(eq(memberProfile), eq(goldTier.getPrice()), eq(PaymentMethod.MOMO)))
                .thenReturn(Payment.builder().id(50L).paymentCode("PAY-1005").paymentStatus(PaymentStatus.PENDING).build());

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        assertEquals(50L, res.getPaymentId());
        assertEquals("PAY-1005", res.getPaymentCode());
    }

    @Test
    void purchaseCard_Online_WithZeroPrice_ThrowsBusinessRuleException() {
        MembershipCardTier freeTier = MembershipCardTier.builder()
                .id(9L)
                .code("FREE-12M")
                .name("Free 12M")
                .price(BigDecimal.ZERO)
                .durationMonths(12)
                .isActive(true)
                .build();

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(9L)
                .build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(9L)).thenReturn(Optional.of(freeTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());

        assertThrows(BusinessRuleException.class, () -> membershipCardService.purchaseCard(req, memberUser));
    }

    @Test
    void purchaseCard_Online_WithEqualPriceTier_CreatesPendingCardStartingAfterCurrentEnd() {
        // Current card is VIP (600k) ending 2027-09-30. Buying another tier with same price (600k)
        // must not be treated as upgrade; it starts the day after last card ends at full tier price.
        MembershipCardTier equalPriceTier = MembershipCardTier.builder()
                .id(4L)
                .code("VIP-EQUAL")
                .name("VIP Equal")
                .price(BigDecimal.valueOf(600000.00))
                .durationMonths(12)
                .isActive(true)
                .build();

        MemberCard currentVip = paidCard(vipTier, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(4L)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(4L)).thenReturn(Optional.of(equalPriceTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(currentVip));
        stubNewCard(26L);
        when(paymentService.createPendingPayment(eq(memberProfile), eq(equalPriceTier.getPrice()), eq(PaymentMethod.BANK_TRANSFER)))
                .thenReturn(Payment.builder().id(51L).paymentCode("PAY-1006").paymentStatus(PaymentStatus.PENDING).build());

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        assertEquals(LocalDate.of(2027, 10, 1), res.getStartDate());
        assertEquals(LocalDate.of(2028, 9, 30), res.getEndDate());
        assertEquals(BigDecimal.valueOf(600000.00), res.getPricePaid());
        assertEquals(51L, res.getPaymentId());
        assertEquals("PAY-1006", res.getPaymentCode());
        // Verify current card was NOT replaced
        assertEquals(CardStatus.ACTIVE, currentVip.getStatus());
    }

    @Test
    void purchaseCard_AtDesk_WithEqualPriceTier_DoesNotReplaceCurrentCard() {
        // Staff at desk buying an equal-price tier for a member with an active card
        MembershipCardTier equalPriceTier = MembershipCardTier.builder()
                .id(4L)
                .code("VIP-EQUAL")
                .name("VIP Equal")
                .price(BigDecimal.valueOf(600000.00))
                .durationMonths(12)
                .isActive(true)
                .build();

        MemberCard currentVip = paidCard(vipTier, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .memberId(100L)
                .tierId(4L)
                .build();
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(4L)).thenReturn(Optional.of(equalPriceTier));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(currentVip));
        stubNewCard(27L);

        MemberCardResponse res = membershipCardService.purchaseCard(req, receptionistUser);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(LocalDate.of(2027, 10, 1), res.getStartDate());
        assertEquals(LocalDate.of(2028, 9, 30), res.getEndDate());
        assertEquals(BigDecimal.valueOf(600000.00), res.getPricePaid());
        // The current card must NOT be REPLACED
        assertEquals(CardStatus.ACTIVE, currentVip.getStatus());
    }

    @Test
    void activateCard_WithPendingPayment_CallsMarkPaymentSuccess() {
        Payment payment = Payment.builder()
                .id(99L)
                .paymentCode("PAY-0099")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        MemberCard pending = MemberCard.builder()
                .id(42L)
                .cardCode("CARD-42")
                .member(memberProfile)
                .tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT)
                .payment(payment)
                .pricePaid(goldTier.getPrice())
                .build();
        when(memberCardRepository.findById(42L)).thenReturn(Optional.of(pending));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(42L, receptionistUser);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
        verify(paymentService).markPaymentSuccess(99L, receptionistUser, null);
    }

    @Test
    void activateCard_WithActualPaymentMethodCash_PassesCashToPaymentService() {
        Payment payment = Payment.builder()
                .id(99L)
                .paymentCode("PAY-0099")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        MemberCard pending = MemberCard.builder()
                .id(42L)
                .cardCode("CARD-42")
                .member(memberProfile)
                .tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT)
                .payment(payment)
                .pricePaid(goldTier.getPrice())
                .build();
        when(memberCardRepository.findById(42L)).thenReturn(Optional.of(pending));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(42L, receptionistUser, PaymentMethod.CASH);

        assertEquals(CardStatus.ACTIVE, res.getStatus());
        verify(paymentService).markPaymentSuccess(99L, receptionistUser, PaymentMethod.CASH);
    }

    @Test
    void cancelCard_WithPendingPayment_CallsMarkPaymentFailedWithActorNote() {
        Payment payment = Payment.builder()
                .id(99L)
                .paymentCode("PAY-0099")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        MemberCard pending = MemberCard.builder()
                .id(42L)
                .cardCode("CARD-42")
                .member(memberProfile)
                .tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT)
                .payment(payment)
                .pricePaid(goldTier.getPrice())
                .build();
        when(memberCardRepository.findById(42L)).thenReturn(Optional.of(pending));
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.cancelCard(42L, memberUser);

        assertEquals(CardStatus.CANCELLED, res.getStatus());
        verify(paymentService).markPaymentFailed(eq(99L), org.mockito.ArgumentMatchers.contains("member user id 100"));
    }

    @Test
    void cancelCard_ByReceptionist_RecordsStaffInNote() {
        Payment payment = Payment.builder()
                .id(99L)
                .paymentCode("PAY-0099")
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        MemberCard pending = MemberCard.builder()
                .id(42L)
                .cardCode("CARD-42")
                .member(memberProfile)
                .tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT)
                .payment(payment)
                .pricePaid(goldTier.getPrice())
                .build();
        receptionistUser.setFullName("Front Desk");
        when(memberCardRepository.findById(42L)).thenReturn(Optional.of(pending));
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.cancelCard(42L, receptionistUser);

        assertEquals(CardStatus.CANCELLED, res.getStatus());
        verify(paymentService).markPaymentFailed(eq(99L), org.mockito.ArgumentMatchers.contains("staff user id 200"));
    }

    @Test
    void activateCard_WithActualMethod_WhenNoPaymentLinked_ThrowsBusinessRuleException() {
        MemberCard pending = MemberCard.builder()
                .id(42L)
                .cardCode("CARD-42")
                .member(memberProfile)
                .tier(goldTier)
                .status(CardStatus.PENDING_PAYMENT)
                .payment(null)
                .pricePaid(goldTier.getPrice())
                .build();
        when(memberCardRepository.findById(42L)).thenReturn(Optional.of(pending));

        assertThrows(BusinessRuleException.class,
                () -> membershipCardService.activateCard(42L, receptionistUser, PaymentMethod.CASH));
    }

    @Test
    void updateTier_success_recordsAuditLog() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold Member")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .description("Gold tier")
                .isActive(true)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(tierRepository.saveAndFlush(any(MembershipCardTier.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(350000.00))
                .durationMonths(12)
                .discountPercentage(8)
                .description("Updated Gold benefits")
                .active(true)
                .build();

        UserAccount manager = UserAccount.builder().id(99L).email("mgr@sportify.com").fullName("Manager").build();
        CardTierResponse res = membershipCardService.updateTier(2L, req, manager);

        assertNotNull(res);
        assertEquals("Gold Member", res.getName());
        assertEquals(BigDecimal.valueOf(350000.00), res.getPrice());
        assertEquals(8, res.getDiscountPercentage());

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditAction.CARD_TIER_UPDATED, event.getAction());
        assertEquals("MEMBERSHIP_CARD_TIER", event.getEntityType());
        assertEquals(2L, event.getEntityId());
        assertEquals("GOLD", event.getEntityCode());
        assertEquals(manager, event.getActor());
        assertNotNull(event.getDetails());
        assertTrue(event.getDetails().containsKey("before"));
        assertTrue(event.getDetails().containsKey("after"));
    }


    @Test
    void updateTier_paidTierZeroOrNegativePrice_throwsBusinessRuleException() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold Member")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));

        MembershipCardTierUpdateRequest zeroReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> membershipCardService.updateTier(2L, zeroReq, null));

        MembershipCardTierUpdateRequest negReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(-1000))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> membershipCardService.updateTier(2L, negReq, null));
    }

    @Test
    void updateTier_standardTierPriceChangeAttempt_throwsBusinessRuleException() {
        MembershipCardTier standard = MembershipCardTier.builder()
                .id(1L)
                .code("STANDARD")
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .isActive(true)
                .build();

        when(tierRepository.findById(1L)).thenReturn(Optional.of(standard));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(100000.00))
                .durationMonths(0)
                .discountPercentage(0)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> membershipCardService.updateTier(1L, req, null));
        verify(tierRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTier_standardTierDeactivationAttempt_throwsBusinessRuleException() {
        MembershipCardTier standard = MembershipCardTier.builder()
                .id(1L)
                .code("STANDARD")
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .isActive(true)
                .build();

        when(tierRepository.findById(1L)).thenReturn(Optional.of(standard));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .active(false)
                .build();

        assertThrows(BusinessRuleException.class, () -> membershipCardService.updateTier(1L, req, null));
        verify(tierRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTier_standardTierNonZeroDiscount_throwsBusinessRuleException() {
        MembershipCardTier standard = MembershipCardTier.builder()
                .id(1L)
                .code("STANDARD")
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .isActive(true)
                .build();

        when(tierRepository.findById(1L)).thenReturn(Optional.of(standard));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(10)
                .active(true)
                .build();

        assertThrows(BusinessRuleException.class, () -> membershipCardService.updateTier(1L, req, null));
        verify(tierRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTier_paidTierDurationZero_throwsBusinessRuleException() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(0)
                .discountPercentage(5)
                .active(true)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.updateTier(2L, req, null));
        assertEquals("Duration in months must be at least 1 for paid tiers", ex.getMessage());
        verify(tierRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTier_standardTierDurationNonZero_throwsBusinessRuleException() {
        MembershipCardTier standard = MembershipCardTier.builder()
                .id(1L)
                .code("STANDARD")
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .isActive(true)
                .build();

        when(tierRepository.findById(1L)).thenReturn(Optional.of(standard));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(12)
                .discountPercentage(0)
                .active(true)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.updateTier(1L, req, null));
        assertEquals("The STANDARD tier duration must be 0 months", ex.getMessage());
        verify(tierRepository, never()).saveAndFlush(any());
    }

    @Test
    void purchaseCard_afterValidEditOfPaidTier_createsPendingPaymentCard() {
        MembershipCardTier paidTier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(6)
                .discountPercentage(5)
                .isActive(true)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(paidTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class))).thenReturn(List.of());
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(99L);
        when(codeFormatter.formatCardCode(99L)).thenReturn("CARD-0099");
        when(paymentService.createPendingPayment(eq(memberProfile), any(BigDecimal.class), eq(PaymentMethod.VNPAY)))
                .thenReturn(Payment.builder().id(501L).paymentCode("PAY-0501").build());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(2L)
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        assertEquals("CARD-0099", res.getCardCode());
        assertEquals(BigDecimal.valueOf(300000.00), res.getPricePaid());
        assertEquals(501L, res.getPaymentId());
    }

    @Test
    void updateTier_standardTierValidUpdate_succeeds() {
        MembershipCardTier standard = MembershipCardTier.builder()
                .id(1L)
                .code("STANDARD")
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .description("Old standard description")
                .isActive(true)
                .build();

        when(tierRepository.findById(1L)).thenReturn(Optional.of(standard));
        when(tierRepository.saveAndFlush(any(MembershipCardTier.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .description("Updated free standard membership card description")
                .active(true)
                .build();

        CardTierResponse res = membershipCardService.updateTier(1L, req, null);
        assertNotNull(res);
        assertEquals("Standard", res.getName());
        assertEquals(BigDecimal.ZERO, res.getPrice());
        assertEquals(0, res.getDiscountPercentage());
        assertEquals(true, res.getIsActive());
    }

    @Test
    void updateTier_blankDescription_clearsDescription() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .description("Initial description")
                .isActive(true)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(tierRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .description("   ")
                .build();

        CardTierResponse res = membershipCardService.updateTier(2L, req, null);
        assertNull(res.getDescription());
        assertNull(tier.getDescription());
    }

    @Test
    void updateTier_nullDescription_clearsDescription() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .description("Initial description")
                .isActive(true)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(tierRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .description(null)
                .build();

        CardTierResponse res = membershipCardService.updateTier(2L, req, null);
        assertNull(res.getDescription());
        assertNull(tier.getDescription());
    }

    @Test
    void updateTier_unknownId_throwsResourceNotFoundException() {
        when(tierRepository.findById(999L)).thenReturn(Optional.empty());

        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> membershipCardService.updateTier(999L, req, null));
    }

    @Test
    void updateTier_existingCardsRetainSnapshotPrice() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold Member")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        MemberCard existingCard = MemberCard.builder()
                .id(100L)
                .cardCode("CARD-0100")
                .member(memberProfile)
                .tier(tier)
                .pricePaid(BigDecimal.valueOf(300000.00))
                .status(CardStatus.ACTIVE)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(tierRepository.saveAndFlush(any(MembershipCardTier.class))).thenAnswer(inv -> inv.getArgument(0));

        // Manager updates tier price from 300,000 to 450,000
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(450000.00))
                .durationMonths(12)
                .discountPercentage(8)
                .active(true)
                .build();

        membershipCardService.updateTier(2L, req, null);

        // Verify tier price is updated
        assertEquals(BigDecimal.valueOf(450000.00), tier.getPrice());

        // Verify the existing card retains its original snapshot price paid
        assertEquals(BigDecimal.valueOf(300000.00), existingCard.getPricePaid());
        verifyNoInteractions(memberCardRepository);
    }

    @Test
    void memberImmediatelySeesNewTierPrice() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold Member")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        when(tierRepository.findById(2L)).thenReturn(Optional.of(tier));
        when(tierRepository.saveAndFlush(any(MembershipCardTier.class))).thenAnswer(inv -> inv.getArgument(0));

        // Manager updates tier price
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(400000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();

        membershipCardService.updateTier(2L, req, null);

        // Public active tiers immediately reflect the new price
        when(tierRepository.findByIsActiveTrue()).thenReturn(List.of(tier));

        List<CardTierResponse> activeTiers = membershipCardService.getAllActiveTiers();
        assertEquals(1, activeTiers.size());
        assertEquals(BigDecimal.valueOf(400000.00), activeTiers.get(0).getPrice());
    }

    @Test
    void getAllTiersForManager_returnsAllTiersIncludingInactive() {
        MembershipCardTier activeTier = MembershipCardTier.builder()
                .id(1L)
                .code("STANDARD")
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .isActive(true)
                .build();

        MembershipCardTier inactiveTier = MembershipCardTier.builder()
                .id(4L)
                .code("PLATINUM")
                .name("Platinum")
                .price(BigDecimal.valueOf(1000000.00))
                .durationMonths(12)
                .discountPercentage(15)
                .isActive(false)
                .build();

        when(tierRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(activeTier, inactiveTier));

        List<CardTierResponse> result = membershipCardService.getAllTiersForManager();
        assertEquals(2, result.size());
        assertEquals("STANDARD", result.get(0).getCode());
        assertEquals(true, result.get(0).getIsActive());
        assertEquals("PLATINUM", result.get(1).getCode());
        assertEquals(false, result.get(1).getIsActive());
    }

    @Test
    void activateCard_pendingCardAtOldPrice_activatesSuccessfullyAndRetainsOldPrice() {
        // (1) create a PENDING_PAYMENT card, raise tier price, activate -> succeeds and retains old pricePaid
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(450000.00)) // Live price raised to 450k
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        MemberCard pendingCard = MemberCard.builder()
                .id(101L)
                .cardCode("CARD-0101")
                .member(memberProfile)
                .tier(tier)
                .startDate(LocalDate.now(clock))
                .endDate(LocalDate.now(clock).plusMonths(12).minusDays(1))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(300000.00)) // Requested at old price 300k
                .upgradeRequest(false)
                .payment(Payment.builder().id(201L).build())
                .build();

        when(memberCardRepository.findById(101L)).thenReturn(Optional.of(pendingCard));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(101L, receptionistUser);

        assertNotNull(res);
        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(BigDecimal.valueOf(300000.00), res.getPricePaid());
        verify(paymentService).markPaymentSuccess(eq(201L), eq(receptionistUser), isNull());
    }

    @Test
    void activateCard_pendingUpgradeCard_whenTierPricesChange_activatesSuccessfully() {
        // (2) create a pending upgrade card, change prices, activate -> succeeds
        MembershipCardTier goldTier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(600000.00)) // Live price changed to 600k
                .durationMonths(12)
                .discountPercentage(10)
                .isActive(true)
                .build();

        MemberCard activeBronzeCard = MemberCard.builder()
                .id(90L)
                .cardCode("CARD-0090")
                .member(memberProfile)
                .tier(MembershipCardTier.builder().id(10L).code("BRONZE").price(BigDecimal.valueOf(250000.00)).durationMonths(12).build())
                .startDate(LocalDate.now(clock).minusMonths(1))
                .endDate(LocalDate.now(clock).plusMonths(11))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(200000.00))
                .build();

        MemberCard pendingUpgradeCard = MemberCard.builder()
                .id(102L)
                .cardCode("CARD-0102")
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now(clock))
                .endDate(activeBronzeCard.getEndDate())
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(300000.00)) // Difference when Gold was 500k
                .upgradeRequest(true)
                .replacesCard(activeBronzeCard)
                .payment(Payment.builder().id(202L).build())
                .build();

        when(memberCardRepository.findById(102L)).thenReturn(Optional.of(pendingUpgradeCard));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeBronzeCard));
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(102L, receptionistUser);

        assertNotNull(res);
        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(BigDecimal.valueOf(300000.00), res.getPricePaid());
        assertEquals(CardStatus.REPLACED, activeBronzeCard.getStatus());
        verify(paymentService).markPaymentSuccess(eq(202L), eq(receptionistUser), isNull());
    }

    @Test
    void purchaseCard_upgradeDifference_calculatedFromPricePaid_notLivePrice() {
        // (3) member holds ACTIVE card, manager edits that tier price, member requests upgrade -> difference is based on pricePaid
        MembershipCardTier silverTier = MembershipCardTier.builder()
                .id(2L)
                .code("SILVER")
                .name("Silver")
                .price(BigDecimal.valueOf(350000.00)) // Live price raised to 350k
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build();

        MembershipCardTier goldTier = MembershipCardTier.builder()
                .id(3L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(500000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .isActive(true)
                .build();

        MemberCard activeSilverCard = MemberCard.builder()
                .id(95L)
                .cardCode("CARD-0095")
                .member(memberProfile)
                .tier(silverTier)
                .startDate(LocalDate.now(clock).minusMonths(2))
                .endDate(LocalDate.now(clock).plusMonths(10))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(300000.00)) // Member actually paid 300k (not 350k)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeSilverCard));
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(105L);
        when(codeFormatter.formatCardCode(105L)).thenReturn("CARD-0105");
        when(paymentService.createPendingPayment(eq(memberProfile), any(BigDecimal.class), eq(PaymentMethod.VNPAY)))
                .thenReturn(Payment.builder().id(301L).build());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(3L)
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        // Difference: 500,000 - 300,000 (pricePaid) = 200,000, NOT 500,000 - 350,000 = 150,000
        assertEquals(BigDecimal.valueOf(200000.00), res.getPricePaid());
    }

    @Test
    void purchaseCard_cheaperTierThanCurrentHolding_startsAfterCurrentCardEndsAtFullPrice() {
        // (4) lower price scenario still works: starts after last card ends, full price
        MembershipCardTier goldTier = MembershipCardTier.builder()
                .id(3L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(500000.00))
                .durationMonths(12)
                .isActive(true)
                .build();

        MembershipCardTier silverTier = MembershipCardTier.builder()
                .id(2L)
                .code("SILVER")
                .name("Silver")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(6)
                .isActive(true)
                .build();

        LocalDate goldEnd = LocalDate.now(clock).plusMonths(6);
        MemberCard activeGoldCard = MemberCard.builder()
                .id(96L)
                .cardCode("CARD-0096")
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now(clock).minusMonths(6))
                .endDate(goldEnd)
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(500000.00))
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(2L)).thenReturn(Optional.of(silverTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeGoldCard));
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(106L);
        when(codeFormatter.formatCardCode(106L)).thenReturn("CARD-0106");
        when(paymentService.createPendingPayment(eq(memberProfile), any(BigDecimal.class), eq(PaymentMethod.VNPAY)))
                .thenReturn(Payment.builder().id(302L).build());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(2L)
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        // Full price of Silver tier
        assertEquals(BigDecimal.valueOf(300000.00), res.getPricePaid());
        // Starts the day after Gold card ends
        assertEquals(goldEnd.plusDays(1), res.getStartDate());
    }

    @Test
    void getApplicableDiscountPercentage_benefitsFollowTier_liveDiscountReturned() {
        // (5) editing a tier discount changes discount returned for existing card holders for NEW registrations
        MembershipCardTier goldTier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(8) // Live discount updated from 5% to 8%
                .isActive(true)
                .build();

        MemberCard activeCard = MemberCard.builder()
                .id(100L)
                .cardCode("CARD-0100")
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now(clock).minusMonths(1))
                .endDate(LocalDate.now(clock).plusMonths(11))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(300000.00))
                .build();

        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeCard));

        int discount = membershipCardService.getApplicableDiscountPercentage(100L, 30);
        assertEquals(8, discount);
    }

    @Test
    void purchaseCard_silverRequestedAfterGoldPriceRaise_notAnUpgrade_queuedAfterGoldEndsAtFullPrice() {
        // N-02 rank rule: member paid 500k for GOLD, GOLD is later raised to 600k.
        // Member requests SILVER at 550k. Even though 550k > 500k, SILVER (550k) <= GOLD (600k) live rank,
        // so it is NOT an upgrade. GOLD is not replaced; new card queues after GOLD ends at full SILVER price (550k).
        MembershipCardTier liveGoldTier = MembershipCardTier.builder()
                .id(3L)
                .code("GOLD")
                .name("Gold Card")
                .price(BigDecimal.valueOf(600000.00)) // Live price raised to 600k
                .durationMonths(12)
                .isActive(true)
                .build();

        MembershipCardTier silverTier = MembershipCardTier.builder()
                .id(4L)
                .code("SILVER")
                .name("Silver Card")
                .price(BigDecimal.valueOf(550000.00)) // Live price 550k
                .durationMonths(12)
                .isActive(true)
                .build();

        LocalDate goldEnd = LocalDate.now(clock).plusMonths(6);
        MemberCard activeGoldCard = MemberCard.builder()
                .id(110L)
                .cardCode("CARD-0110")
                .member(memberProfile)
                .tier(liveGoldTier)
                .startDate(LocalDate.now(clock).minusMonths(6))
                .endDate(goldEnd)
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(500000.00)) // Member paid 500k originally
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(4L)).thenReturn(Optional.of(silverTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeGoldCard));
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(111L);
        when(codeFormatter.formatCardCode(111L)).thenReturn("CARD-0111");
        when(paymentService.createPendingPayment(eq(memberProfile), any(BigDecimal.class), eq(PaymentMethod.BANK_TRANSFER)))
                .thenReturn(Payment.builder().id(401L).build());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(4L)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        assertEquals(CardStatus.PENDING_PAYMENT, res.getStatus());
        // Must charge full SILVER price, NOT 50k difference
        assertEquals(BigDecimal.valueOf(550000.00), res.getPricePaid());
        // Queued after GOLD card ends
        assertEquals(goldEnd.plusDays(1), res.getStartDate());
        assertEquals(goldEnd.plusDays(1).plusMonths(12).minusDays(1), res.getEndDate());
        // GOLD card must not be replaced
        assertEquals(CardStatus.ACTIVE, activeGoldCard.getStatus());
        ArgumentCaptor<MemberCard> cardCaptor = ArgumentCaptor.forClass(MemberCard.class);
        verify(memberCardRepository).save(cardCaptor.capture());
        assertFalse(cardCaptor.getValue().isUpgradeRequest());
        assertNull(cardCaptor.getValue().getReplacesCard());
    }

    @Test
    void purchaseCard_higherRankTierCheapenedBelowPricePaid_throws400WithDocumentedMessage() {
        // N-02 rank rule: member paid 500k for SILVER (when it was 500k).
        // Later SILVER is cheapened to 300k. Member requests GOLD which is priced at 450k (live).
        // Live prices: GOLD (450k) > SILVER (300k) -> RANK says upgrade!
        // But fee = 450k - 500k (pricePaid) = -50k <= 0 -> rejected with HTTP 400.
        MembershipCardTier cheapenedSilverTier = MembershipCardTier.builder()
                .id(2L)
                .code("SILVER")
                .name("Silver Card")
                .price(BigDecimal.valueOf(300000.00)) // Live price lowered to 300k
                .durationMonths(12)
                .isActive(true)
                .build();

        MembershipCardTier goldTier = MembershipCardTier.builder()
                .id(3L)
                .code("GOLD")
                .name("Gold Card")
                .price(BigDecimal.valueOf(450000.00)) // Live price 450k
                .durationMonths(12)
                .isActive(true)
                .build();

        MemberCard activeSilverCard = MemberCard.builder()
                .id(112L)
                .cardCode("CARD-0112")
                .member(memberProfile)
                .tier(cheapenedSilverTier)
                .startDate(LocalDate.now(clock).minusMonths(2))
                .endDate(LocalDate.now(clock).plusMonths(10))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(500000.00)) // Member actually paid 500k
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeSilverCard));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(3L)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.purchaseCard(req, memberUser));
        assertEquals("No payment is needed for this upgrade. Contact the front desk.", ex.getMessage());
    }

    @Test
    void purchaseCard_upgradeChain_feeUsesSumOfPricePaidAcrossChain() {
        // Tier A (Bronze, 100k), Tier B (Silver, 300k), Tier C (Gold, 600k)
        // Member originally bought Bronze for 100k.
        // Then upgraded to Silver paying 200k difference (total paid so far = 300k).
        // Now member upgrades to Gold (600k).
        // Fee for Gold must be: 600k - (100k + 200k) = 300k.
        MembershipCardTier bronzeTier = MembershipCardTier.builder().id(10L).code("BRONZE").price(BigDecimal.valueOf(100000.00)).durationMonths(12).build();
        MembershipCardTier silverTier = MembershipCardTier.builder().id(20L).code("SILVER").price(BigDecimal.valueOf(300000.00)).durationMonths(12).build();
        MembershipCardTier goldTier = MembershipCardTier.builder().id(30L).code("GOLD").price(BigDecimal.valueOf(600000.00)).durationMonths(12).build();

        MemberCard cardA = MemberCard.builder()
                .id(1L)
                .tier(bronzeTier)
                .pricePaid(BigDecimal.valueOf(100000.00))
                .status(CardStatus.REPLACED)
                .build();

        MemberCard cardB = MemberCard.builder()
                .id(2L)
                .tier(silverTier)
                .pricePaid(BigDecimal.valueOf(200000.00)) // Difference paid
                .upgradeRequest(true)
                .replacesCard(cardA)
                .startDate(LocalDate.now(clock).minusMonths(1))
                .endDate(LocalDate.now(clock).plusMonths(11))
                .status(CardStatus.ACTIVE)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(30L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(cardB));
        when(memberCardRepository.getNextCardCodeSequence()).thenReturn(120L);
        when(codeFormatter.formatCardCode(120L)).thenReturn("CARD-0120");
        when(paymentService.createPendingPayment(eq(memberProfile), any(BigDecimal.class), eq(PaymentMethod.VNPAY)))
                .thenReturn(Payment.builder().id(402L).build());
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(30L)
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        MemberCardResponse res = membershipCardService.purchaseCard(req, memberUser);

        assertNotNull(res);
        // Fee = 600,000 - (200,000 + 100,000) = 300,000
        assertEquals(BigDecimal.valueOf(300000.00), res.getPricePaid());
        ArgumentCaptor<MemberCard> cardCaptor = ArgumentCaptor.forClass(MemberCard.class);
        verify(memberCardRepository).save(cardCaptor.capture());
        assertTrue(cardCaptor.getValue().isUpgradeRequest());
        assertEquals(cardB.getId(), cardCaptor.getValue().getReplacesCard().getId());
    }

    @Test
    void purchaseCard_legacyUpgradeCardWithNullReplacesCard_usesTierPriceFallback() {
        // N-03: Member holds an ACTIVE card that was an upgrade (upgradeRequest = true),
        // but replacesCard is NULL (legacy row unresolvable during migration backfill).
        // pricePaid was only 150k (the incremental difference paid back then).
        // Catalog price of this held SILVER tier is 350k.
        // Target tier is GOLD (500k).
        // If fallback works: base price is 350k (tier catalog price fallback), so fee = 500k - 350k = 150k.
        // If fallback failed: base price would be 150k, overcharging the member 350k!
        MembershipCardTier silverTier = MembershipCardTier.builder()
                .id(2L)
                .code("SILVER")
                .name("Silver Card")
                .price(BigDecimal.valueOf(350000.00))
                .durationMonths(12)
                .isActive(true)
                .build();

        MembershipCardTier goldTier = MembershipCardTier.builder()
                .id(3L)
                .code("GOLD")
                .name("Gold Card")
                .price(BigDecimal.valueOf(500000.00))
                .durationMonths(12)
                .isActive(true)
                .build();

        MemberCard legacyUpgradeCard = MemberCard.builder()
                .id(130L)
                .cardCode("CARD-0130")
                .member(memberProfile)
                .tier(silverTier)
                .startDate(LocalDate.now(clock).minusMonths(3))
                .endDate(LocalDate.now(clock).plusMonths(9))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(150000.00)) // Only the difference
                .upgradeRequest(true)
                .replacesCard(null) // Unresolvable legacy row
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(tierRepository.findById(3L)).thenReturn(Optional.of(goldTier));
        when(memberCardRepository.findByMemberIdAndStatus(100L, CardStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(legacyUpgradeCard));

        MemberCardPurchaseRequest req = MemberCardPurchaseRequest.builder()
                .tierId(3L)
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.purchaseCard(req, memberUser));
        assertTrue(ex.getMessage().contains("Cannot determine upgrade baseline"));
    }

    @Test
    void activateCard_upgradeReplacedCardMismatch_throws400AndCardStaysPending() {
        // N-04: Card requested to replace card A (id 90L), but member now has a different active card B (id 91L).
        MemberCard cardA = MemberCard.builder()
                .id(90L)
                .cardCode("CARD-0090")
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now(clock).minusMonths(1))
                .endDate(LocalDate.now(clock).plusMonths(11))
                .status(CardStatus.ACTIVE)
                .build();

        MemberCard cardB = MemberCard.builder()
                .id(91L)
                .cardCode("CARD-0091")
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now(clock).minusMonths(1))
                .endDate(LocalDate.now(clock).plusMonths(11))
                .status(CardStatus.ACTIVE)
                .build();

        MemberCard pendingUpgradeCard = MemberCard.builder()
                .id(140L)
                .cardCode("CARD-0140")
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.now(clock))
                .endDate(cardA.getEndDate())
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(400000.00))
                .upgradeRequest(true)
                .replacesCard(cardA) // Recorded to replace card A
                .build();

        when(memberCardRepository.findById(140L)).thenReturn(Optional.of(pendingUpgradeCard));
        // Plan currently resolves card B instead of card A
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(cardB));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.activateCard(140L, receptionistUser));
        assertEquals("The member's card has changed since this request. Cancel it and request the card again.", ex.getMessage());
        // Card stays PENDING_PAYMENT
        assertEquals(CardStatus.PENDING_PAYMENT, pendingUpgradeCard.getStatus());
        verify(memberCardRepository, never()).save(any(MemberCard.class));
    }

    @Test
    void activateCard_legacyUpgradeWithNullReplacesCard_activatesSuccessfully() {
        // N-04: Legacy upgrade row with upgradeRequest = true and replacesCard = null.
        // It must activate without throwing ID mismatch exception.
        MemberCard activeGold = paidCard(goldTier, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));

        MemberCard legacyPendingUpgrade = MemberCard.builder()
                .id(141L)
                .cardCode("CARD-0141")
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.now(clock))
                .endDate(activeGold.getEndDate())
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(400000.00))
                .upgradeRequest(true)
                .replacesCard(null) // Legacy row with NULL replaces_card_id
                .payment(Payment.builder().id(404L).build())
                .build();

        when(memberCardRepository.findById(141L)).thenReturn(Optional.of(legacyPendingUpgrade));
        when(memberCardRepository.findActiveCardsForMember(eq(100L), eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(activeGold));
        when(memberCardRepository.save(any(MemberCard.class))).thenAnswer(i -> i.getArgument(0));

        MemberCardResponse res = membershipCardService.activateCard(141L, receptionistUser);

        assertNotNull(res);
        assertEquals(CardStatus.ACTIVE, res.getStatus());
        assertEquals(CardStatus.REPLACED, activeGold.getStatus());
        verify(paymentService).markPaymentSuccess(eq(404L), eq(receptionistUser), isNull());
    }
}

