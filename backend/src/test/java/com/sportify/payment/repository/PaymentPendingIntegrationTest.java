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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
    private PlatformTransactionManager transactionManager;

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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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

        Sport sport = sportRepository.findAll().stream().findFirst()
                .orElseGet(() -> sportRepository.save(com.sportify.catalog.test.SportTestData.aValidSport().build()));

        String pkgCode = "SWIM-30D-" + System.currentTimeMillis();
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code(pkgCode)
                .name("Swimming 30 Days " + System.currentTimeMillis())
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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

    @Test
    @DisplayName("Unique filtered index on member_card(payment_id) rejects duplicates and allows multiple nulls")
    void uniqueFilteredIndex_MemberCardPaymentId_RejectsDuplicateAndAllowsMultipleNulls() {
        MemberProfile member = getOrCreateMember();

        MembershipCardTier tier = membershipCardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-UQ-" + System.currentTimeMillis())
                .name("Unique Index Tier " + System.currentTimeMillis())
                .price(BigDecimal.valueOf(100000.00))
                .durationMonths(6)
                .discountPercentage(0)
                .isActive(true)
                .build());

        // 1. Multiple cards with NULL payment_id must succeed
        Long seq1 = memberCardRepository.getNextCardCodeSequence();
        MemberCard nullPaymentCard1 = memberCardRepository.saveAndFlush(MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(seq1))
                .member(member)
                .tier(tier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(100000.00))
                .payment(null)
                .build());

        Long seq2 = memberCardRepository.getNextCardCodeSequence();
        MemberCard nullPaymentCard2 = memberCardRepository.saveAndFlush(MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(seq2))
                .member(member)
                .tier(tier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(100000.00))
                .payment(null)
                .build());

        assertThat(nullPaymentCard1.getId()).isNotNull();
        assertThat(nullPaymentCard2.getId()).isNotNull();

        // 2. First card with a non-null payment_id succeeds
        Long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = paymentRepository.save(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(BigDecimal.valueOf(100000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Long seq3 = memberCardRepository.getNextCardCodeSequence();
        memberCardRepository.saveAndFlush(MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(seq3))
                .member(member)
                .tier(tier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(100000.00))
                .payment(payment)
                .build());

        // 3. Second card with the same payment_id must fail unique constraint
        Long seq4 = memberCardRepository.getNextCardCodeSequence();
        MemberCard duplicateCard = MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(seq4))
                .member(member)
                .tier(tier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(100000.00))
                .payment(payment)
                .build();

        assertThatThrownBy(() -> memberCardRepository.saveAndFlush(duplicateCard))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Unique filtered index on sport_package_registration(payment_id) rejects duplicates and allows multiple nulls")
    void uniqueFilteredIndex_SportPackageRegistrationPaymentId_RejectsDuplicateAndAllowsMultipleNulls() {
        MemberProfile member = getOrCreateMember();
        Sport sport = sportRepository.findAll().stream().findFirst()
                .orElseGet(() -> sportRepository.save(com.sportify.catalog.test.SportTestData.aValidSport().build()));

        String pkgCode = "PKG-UQ-" + System.currentTimeMillis();
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code(pkgCode)
                .name("Unique Index Pkg " + System.currentTimeMillis())
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(200000.00))
                .isActive(true)
                .build());

        // 1. Multiple registrations with NULL payment_id must succeed
        Long regSeq1 = sportPackageRegistrationRepository.getNextRegistrationCodeSequence();
        SportPackageRegistration nullReg1 = sportPackageRegistrationRepository.saveAndFlush(SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(regSeq1))
                .member(member)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(BigDecimal.valueOf(200000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(200000.00))
                .totalSessions(10)
                .remainingSessions(10)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(29))
                .status(PackageRegistrationStatus.ACTIVE)
                .createdByUser(member.getUserAccount())
                .payment(null)
                .build());

        Long regSeq2 = sportPackageRegistrationRepository.getNextRegistrationCodeSequence();
        SportPackageRegistration nullReg2 = sportPackageRegistrationRepository.saveAndFlush(SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(regSeq2))
                .member(member)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(BigDecimal.valueOf(200000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(200000.00))
                .totalSessions(10)
                .remainingSessions(10)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(29))
                .status(PackageRegistrationStatus.ACTIVE)
                .createdByUser(member.getUserAccount())
                .payment(null)
                .build());

        assertThat(nullReg1.getId()).isNotNull();
        assertThat(nullReg2.getId()).isNotNull();

        // 2. First registration with non-null payment_id succeeds
        Long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = paymentRepository.save(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(BigDecimal.valueOf(200000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Long regSeq3 = sportPackageRegistrationRepository.getNextRegistrationCodeSequence();
        sportPackageRegistrationRepository.saveAndFlush(SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(regSeq3))
                .member(member)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(BigDecimal.valueOf(200000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(200000.00))
                .totalSessions(10)
                .remainingSessions(10)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(29))
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .createdByUser(member.getUserAccount())
                .payment(payment)
                .build());

        // 3. Second registration with duplicate payment_id fails
        Long regSeq4 = sportPackageRegistrationRepository.getNextRegistrationCodeSequence();
        SportPackageRegistration duplicateReg = SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(regSeq4))
                .member(member)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(BigDecimal.valueOf(200000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(200000.00))
                .totalSessions(10)
                .remainingSessions(10)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(29))
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .createdByUser(member.getUserAccount())
                .payment(payment)
                .build();

        assertThatThrownBy(() -> sportPackageRegistrationRepository.saveAndFlush(duplicateReg))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Database check constraint ck_payment_amount rejects zero and negative amounts")
    void paymentAmount_CheckConstraint_RejectsZeroAndNegativeAmounts() {
        MemberProfile member = getOrCreateMember();

        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, SYSDATETIME(), SYSDATETIME())",
                    "PAY-ZERO", member.getId(), BigDecimal.ZERO, "CASH", "PENDING"
            );
        }).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, SYSDATETIME(), SYSDATETIME())",
                    "PAY-NEG", member.getId(), BigDecimal.valueOf(-50000.00), "CASH", "PENDING"
            );
        }).isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Optimistic lock conflict throws ObjectOptimisticLockingFailureException when concurrent transaction updates payment")
    void optimisticLockConflict_ThrowsObjectOptimisticLockingFailureException() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        Long memberId = txTemplate.execute(status -> getOrCreateMember().getId());

        Long paymentId = txTemplate.execute(status -> {
            MemberProfile member = memberProfileRepository.findById(memberId).orElseThrow();
            Long paySeq = paymentRepository.getNextPaymentCodeSequence();
            Payment payment = Payment.builder()
                    .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                    .member(member)
                    .amount(BigDecimal.valueOf(100000.00))
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentStatus(PaymentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return paymentRepository.save(payment).getId();
        });

        try {
            Payment p1 = txTemplate.execute(status -> paymentRepository.findById(paymentId).orElseThrow());

            txTemplate.execute(status -> {
                Payment p2 = paymentRepository.findById(paymentId).orElseThrow();
                p2.setPaymentMethod(PaymentMethod.CREDIT_CARD);
                paymentRepository.save(p2);
                return null;
            });

            assertThatThrownBy(() -> txTemplate.execute(status -> {
                p1.setPaymentMethod(PaymentMethod.CASH);
                paymentRepository.save(p1);
                return null;
            })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            txTemplate.execute(status -> {
                paymentRepository.deleteById(paymentId);
                return null;
            });
        }
    }

    @Test
    @DisplayName("Check constraint ck_payment_success_time rejects SUCCESS payment with NULL payment_time")
    void successPayment_WithNullPaymentTime_RejectedByCheckConstraint() {
        MemberProfile member = getOrCreateMember();
        memberProfileRepository.flush();
        String code = "PAY-SN-" + (System.currentTimeMillis() % 1000000000L);
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, payment_time, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, NULL, SYSDATETIME(), SYSDATETIME())",
                    code, member.getId(), BigDecimal.valueOf(100000.00), "CASH", "SUCCESS"
            );
        }).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_payment_success_time");
    }

    @Test
    @DisplayName("Native SQL insert of PENDING payment without payment_time leaves payment_time NULL because default constraint was dropped")
    void pendingPayment_NativeSqlInsertWithoutPaymentTime_LeavesPaymentTimeNull() {
        MemberProfile member = getOrCreateMember();
        memberProfileRepository.flush();
        String paymentCode = "PAY-NT-" + (System.currentTimeMillis() % 1000000000L);
        jdbcTemplate.update(
                "INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, SYSDATETIME(), SYSDATETIME())",
                paymentCode, member.getId(), BigDecimal.valueOf(100000.00), "CASH", "PENDING"
        );

        List<Timestamp> times = jdbcTemplate.query(
                "SELECT payment_time FROM payment WHERE payment_code = ?",
                (rs, rowNum) -> rs.getTimestamp("payment_time"),
                paymentCode
        );
        assertThat(times).hasSize(1);
        assertThat(times.get(0)).isNull();
    }

    @Test
    @DisplayName("findByMemberIdOrderByCreatedAtDesc returns newest PENDING payment first")
    void findByMemberIdOrderByCreatedAtDesc_ReturnsNewestPendingPaymentFirst() {
        MemberProfile member = getOrCreateMember();

        paymentRepository.save(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paymentRepository.getNextPaymentCodeSequence()))
                .member(member)
                .amount(BigDecimal.valueOf(100000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now().minusHours(2))
                .updatedAt(LocalDateTime.now().minusHours(2))
                .build());

        Payment newerPending = paymentRepository.save(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paymentRepository.getNextPaymentCodeSequence()))
                .member(member)
                .amount(BigDecimal.valueOf(200000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        List<Payment> list = paymentRepository.findByMemberIdOrderByCreatedAtDesc(member.getId());
        assertThat(list).isNotEmpty();
        assertThat(list.get(0).getId()).isEqualTo(newerPending.getId());
    }

    private MemberProfile getOrCreateMember() {
        Optional<MemberProfile> existing = memberProfileRepository.findAll().stream().findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseGet(() -> roleRepository.saveAndFlush(Role.builder()
                        .code("MEMBER")
                        .name("Member")
                        .isSystem(true)
                        .build()));

        UserAccount user = userRepository.saveAndFlush(UserAccount.builder()
                .email("it-member-" + System.nanoTime() + "@sportify.com")
                .passwordHash("DummyHashForTestPurposesOnly")
                .fullName("IT Test Member")
                .role(memberRole)
                .status("ACTIVE")
                .build());

        Long memberSeq = memberProfileRepository.getNextMemberCode();
        return memberProfileRepository.saveAndFlush(MemberProfile.builder()
                .userAccount(user)
                .memberCode(codeFormatter.formatMemberCode(memberSeq))
                .currentLevel("BEGINNER")
                .joinedOn(LocalDate.now())
                .build());
    }
}
