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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PaymentPendingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                .paymentTime(null)
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

        Sport sport = sportRepository.save(com.sportify.catalog.test.SportTestData.aValidSport().build());

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
                .paymentTime(null)
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
                .paymentTime(null)
                .build();
        payment = paymentRepository.save(payment);

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setNotes("Payment cancelled due to card request cancellation");
        Payment updated = paymentRepository.save(payment);

        Payment reloaded = paymentRepository.findById(updated.getId()).orElseThrow();
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reloaded.getNotes()).isEqualTo("Payment cancelled due to card request cancellation");
    }

    @Test
    @DisplayName("Invalid payment status is rejected by ck_payment_status check constraint")
    void invalidPaymentStatus_RejectedByCheckConstraint() {
        MemberProfile member = getOrCreateMember();
        Long memberId = member.getId();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-BAD-STATUS', ?, 100000.00, 'BANK_TRANSFER', 'INVALID_STATUS')
                """, memberId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_payment_status");
    }

    @Test
    @DisplayName("Save payment, invoice with 2 lines, and read them back")
    void savePaymentAndInvoiceWithTwoLines_ReadsThemBack() {
        MemberProfile member = getOrCreateMember();

        Long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = paymentRepository.save(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(BigDecimal.valueOf(500000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.SUCCESS)
                .paymentTime(LocalDateTime.now())
                .build());

        Long invSeq = invoiceRepository.getNextInvoiceNumberSequence();
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .invoiceNumber(codeFormatter.formatInvoiceNumber(invSeq))
                .payment(payment)
                .member(member)
                .subtotalAmount(BigDecimal.valueOf(500000.00))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(500000.00))
                .status(InvoiceStatus.PAID)
                .issueDate(LocalDateTime.now())
                .build());

        InvoiceLine line1 = invoiceLineRepository.save(InvoiceLine.builder()
                .invoice(invoice)
                .itemType(InvoiceItemType.SPORT_PACKAGE)
                .itemReferenceId(101L)
                .description("Line 1 item")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(300000.00))
                .lineTotal(BigDecimal.valueOf(300000.00))
                .build());

        InvoiceLine line2 = invoiceLineRepository.save(InvoiceLine.builder()
                .invoice(invoice)
                .itemType(InvoiceItemType.MEMBERSHIP_CARD)
                .itemReferenceId(202L)
                .description("Line 2 item")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(200000.00))
                .lineTotal(BigDecimal.valueOf(200000.00))
                .build());

        Optional<Invoice> foundInvoice = invoiceRepository.findById(invoice.getId());
        assertThat(foundInvoice).isPresent();
        assertThat(foundInvoice.get().getPayment().getId()).isEqualTo(payment.getId());

        List<InvoiceLine> foundLines = invoiceLineRepository.findByInvoiceId(invoice.getId());
        assertThat(foundLines).hasSize(2);
        assertThat(foundLines).extracting(InvoiceLine::getId).containsExactlyInAnyOrder(line1.getId(), line2.getId());
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
