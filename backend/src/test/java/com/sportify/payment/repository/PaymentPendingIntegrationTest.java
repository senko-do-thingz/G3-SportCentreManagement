package com.sportify.payment.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PaymentPendingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MemberCardRepository memberCardRepository;

    @Autowired
    private MembershipCardTierRepository membershipCardTierRepository;

    @Autowired
    private SportPackageRepository sportPackageRepository;

    @Autowired
    private SportPackageRegistrationRepository sportPackageRegistrationRepository;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CodeFormatter codeFormatter;

    @Test
    @DisplayName("Save pending payment and link with member card in same transaction")
    void savePendingPayment_WithMemberCard_LinksAndFindsByPaymentId() {
        MemberProfile member = getOrCreateMember();

        MembershipCardTier tier = membershipCardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-PENDING")
                .name("Pending Tier")
                .price(BigDecimal.valueOf(250000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .isActive(true)
                .build());

        Long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(BigDecimal.valueOf(250000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentTime(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);

        Long cardSeq = memberCardRepository.getNextCardCodeSequence();
        MemberCard card = MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(cardSeq))
                .member(member)
                .tier(tier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(250000.00))
                .payment(payment)
                .build();
        card = memberCardRepository.save(card);

        Optional<MemberCard> found = memberCardRepository.findByPaymentId(payment.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(card.getId());
        assertThat(found.get().getPayment().getId()).isEqualTo(payment.getId());
        assertThat(found.get().getStatus()).isEqualTo(CardStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Save pending payment and link with sport package registration in same transaction")
    void savePendingPayment_WithSportPackageRegistration_LinksAndFindsByPaymentId() {
        MemberProfile member = getOrCreateMember();

        Sport sport = sportRepository.save(Sport.builder()
                .code("SWIM")
                .name("Swimming")
                .isActive(true)
                .build());

        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("SWIM-30D")
                .name("Swimming 30 Days")
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(400000.00))
                .isActive(true)
                .build());

        Long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(BigDecimal.valueOf(400000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentTime(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);

        Long regSeq = sportPackageRegistrationRepository.getNextRegistrationCodeSequence();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(regSeq))
                .member(member)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(BigDecimal.valueOf(400000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(400000.00))
                .totalSessions(10)
                .remainingSessions(10)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(29))
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .createdByUser(member.getUserAccount())
                .payment(payment)
                .build();
        reg = sportPackageRegistrationRepository.save(reg);

        Optional<SportPackageRegistration> found = sportPackageRegistrationRepository.findByPaymentId(payment.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(reg.getId());
        assertThat(found.get().getPayment().getId()).isEqualTo(payment.getId());
        assertThat(found.get().getStatus()).isEqualTo(PackageRegistrationStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Cancel pending payment sets payment status to FAILED with notes")
    void cancelPendingPayment_UpdatesStatusToFailedWithNotes() {
        MemberProfile member = getOrCreateMember();

        Long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(BigDecimal.valueOf(150000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentTime(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setNotes("Payment cancelled due to card request cancellation");
        Payment updated = paymentRepository.save(payment);

        Payment reloaded = paymentRepository.findById(updated.getId()).orElseThrow();
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reloaded.getNotes()).isEqualTo("Payment cancelled due to card request cancellation");
    }

    private MemberProfile getOrCreateMember() {
        Optional<MemberProfile> existing = memberProfileRepository.findAll().stream().findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("MEMBER")
                        .name("Member")
                        .isSystem(true)
                        .build()));

        UserAccount user = userRepository.save(UserAccount.builder()
                .email("it-member-" + System.nanoTime() + "@sportify.com")
                .passwordHash("DummyHashForTestPurposesOnly")
                .fullName("IT Test Member")
                .role(memberRole)
                .status("ACTIVE")
                .build());

        Long memberSeq = memberProfileRepository.getNextMemberCode();
        return memberProfileRepository.save(MemberProfile.builder()
                .userAccount(user)
                .memberCode(codeFormatter.formatMemberCode(memberSeq))
                .currentLevel("BEGINNER")
                .joinedOn(LocalDate.now())
                .build());
    }
}
