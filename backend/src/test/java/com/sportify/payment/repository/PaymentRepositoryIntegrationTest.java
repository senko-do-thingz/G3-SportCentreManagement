package com.sportify.payment.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PaymentRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberCardRepository memberCardRepository;

    @Autowired
    private MembershipCardTierRepository membershipCardTierRepository;

    @Autowired
    private SportPackageRepository sportPackageRepository;

    @Autowired
    private SportPackageRegistrationRepository sportPackageRegistrationRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private CodeFormatter codeFormatter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Save payment, invoice with 2 lines, and read them back successfully")
    void savesPaymentInvoiceWithTwoLinesAndReadsThemBack() {
        MemberProfile member = getOrCreateTestMember();

        // 1. Generate payment code and persist payment
        long paySeq = paymentRepository.getNextPaymentCodeSequence();
        String paymentCode = codeFormatter.formatPaymentCode(paySeq);
        assertThat(paymentCode).matches("^PAY-\\d{4,}$");

        Payment payment = Payment.builder()
                .paymentCode(paymentCode)
                .member(member)
                .amount(new BigDecimal("1100000.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.SUCCESS)
                .referenceCode("TXN-PAY-001")
                .notes("Payment for package and member card")
                .paymentTime(LocalDateTime.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        assertThat(savedPayment.getId()).isNotNull();

        // 2. Generate invoice number and persist invoice with 2 invoice lines
        long invSeq = invoiceRepository.getNextInvoiceNumberSequence();
        String invoiceNumber = codeFormatter.formatInvoiceNumber(invSeq);
        assertThat(invoiceNumber).matches("^INV-\\d{4,}$");

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .payment(savedPayment)
                .member(member)
                .subtotalAmount(new BigDecimal("1100000.00"))
                .discountAmount(new BigDecimal("60000.00"))
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1040000.00"))
                .status(InvoiceStatus.PAID)
                .issueDate(LocalDateTime.now())
                .build();

        InvoiceLine line1 = InvoiceLine.builder()
                .itemType(InvoiceItemType.SPORT_PACKAGE)
                .itemReferenceId(101L)
                .description("Football 30-Day Training Pass")
                .quantity(1)
                .unitPrice(new BigDecimal("500000.00"))
                .lineTotal(new BigDecimal("500000.00"))
                .build();

        InvoiceLine line2 = InvoiceLine.builder()
                .itemType(InvoiceItemType.MEMBERSHIP_CARD)
                .itemReferenceId(202L)
                .description("VIP 12-Month Card")
                .quantity(1)
                .unitPrice(new BigDecimal("600000.00"))
                .lineTotal(new BigDecimal("600000.00"))
                .build();

        invoice.addLine(line1);
        invoice.addLine(line2);

        Invoice savedInvoice = invoiceRepository.save(invoice);
        assertThat(savedInvoice.getId()).isNotNull();

        // Flush and clear persistence context to force database retrieval
        entityManager.flush();
        entityManager.clear();

        // 3. Read back payment
        Optional<Payment> fetchedPaymentOpt = paymentRepository.findById(savedPayment.getId());
        assertThat(fetchedPaymentOpt).isPresent();
        Payment fetchedPayment = fetchedPaymentOpt.get();
        assertThat(fetchedPayment.getPaymentCode()).isEqualTo(paymentCode);
        assertThat(fetchedPayment.getAmount()).isEqualByComparingTo("1100000.00");
        assertThat(fetchedPayment.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(fetchedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(fetchedPayment.getReferenceCode()).isEqualTo("TXN-PAY-001");
        assertThat(fetchedPayment.getVersion()).isNotNull();
        assertThat(fetchedPayment.getMember().getId()).isEqualTo(member.getId());

        // 4. Read back invoice
        Optional<Invoice> fetchedInvoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber);
        assertThat(fetchedInvoiceOpt).isPresent();
        Invoice fetchedInvoice = fetchedInvoiceOpt.get();
        assertThat(fetchedInvoice.getPayment().getId()).isEqualTo(savedPayment.getId());
        assertThat(fetchedInvoice.getMember().getId()).isEqualTo(member.getId());
        assertThat(fetchedInvoice.getSubtotalAmount()).isEqualByComparingTo("1100000.00");
        assertThat(fetchedInvoice.getDiscountAmount()).isEqualByComparingTo("60000.00");
        assertThat(fetchedInvoice.getTotalAmount()).isEqualByComparingTo("1040000.00");
        assertThat(fetchedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Verify lines
        assertThat(fetchedInvoice.getLines()).hasSize(2);
        assertThat(fetchedInvoice.getLines())
                .extracting(InvoiceLine::getItemType)
                .containsExactlyInAnyOrder(InvoiceItemType.SPORT_PACKAGE, InvoiceItemType.MEMBERSHIP_CARD);
        assertThat(fetchedInvoice.getLines())
                .extracting(InvoiceLine::getDescription)
                .containsExactlyInAnyOrder("Football 30-Day Training Pass", "VIP 12-Month Card");

        List<InvoiceLine> repoLines = invoiceLineRepository.findByInvoiceId(fetchedInvoice.getId());
        assertThat(repoLines).hasSize(2);
    }

    @Test
    @DisplayName("Database check constraints reject invalid payment status, method, and amounts")
    void checkConstraintsRejectBadStatusesAndInvalidValues() {
        MemberProfile member = getOrCreateTestMember();
        long memberId = member.getId();

        // 1. Invalid payment_status rejected by ck_payment_status
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-BAD-STATUS', ?, 100000.00, 'CASH', 'UNKNOWN_STATUS')
                """, memberId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_payment_status");

        // 2. Invalid payment_method rejected by ck_payment_method
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-BAD-METHOD', ?, 100000.00, 'BITCOIN', 'SUCCESS')
                """, memberId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_payment_method");

        // 3. Invalid payment amount (<= 0) rejected by ck_payment_amount
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-BAD-AMOUNT', ?, 0.00, 'CASH', 'SUCCESS')
                """, memberId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_payment_amount");

        // 4. Valid payment insert to test invoice checks
        jdbcTemplate.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status)
                VALUES ('PAY-FOR-INV-CK', ?, 200000.00, 'MOMO', 'SUCCESS')
                """, memberId);
        long paymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment WHERE payment_code = 'PAY-FOR-INV-CK'", Long.class);

        // 5. Invalid invoice status rejected by ck_invoice_status
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO invoice (invoice_number, payment_id, member_id, subtotal_amount, total_amount, status)
                VALUES ('INV-BAD-STATUS', ?, ?, 200000.00, 200000.00, 'INVALID_STATUS')
                """, paymentId, memberId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_invoice_status");

        // 6. Invalid invoice amounts (< 0) rejected by ck_invoice_amounts
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO invoice (invoice_number, payment_id, member_id, subtotal_amount, total_amount, status)
                VALUES ('INV-BAD-AMOUNTS', ?, ?, -100.00, -100.00, 'ISSUED')
                """, paymentId, memberId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_invoice_amounts");

        // 7. Valid invoice insert to test invoice line checks
        jdbcTemplate.update("""
                INSERT INTO invoice (invoice_number, payment_id, member_id, subtotal_amount, total_amount, status)
                VALUES ('INV-FOR-LINE-CK', ?, ?, 200000.00, 200000.00, 'ISSUED')
                """, paymentId, memberId);
        long invoiceId = jdbcTemplate.queryForObject(
                "SELECT id FROM invoice WHERE invoice_number = 'INV-FOR-LINE-CK'", Long.class);

        // 8. Invalid invoice_line item_type rejected by ck_invoice_line_item_type
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO invoice_line (invoice_id, item_type, item_reference_id, description, quantity, unit_price, line_total)
                VALUES (?, 'ILLEGAL_TYPE', 1, 'Bad line', 1, 1000.00, 1000.00)
                """, invoiceId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_invoice_line_item_type");

        // 9. Invalid invoice_line quantity (<= 0) rejected by ck_invoice_line_quantity
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO invoice_line (invoice_id, item_type, item_reference_id, description, quantity, unit_price, line_total)
                VALUES (?, 'SPORT_PACKAGE', 1, 'Bad quantity', 0, 1000.00, 1000.00)
                """, invoiceId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_invoice_line_quantity");
    }

    @Test
    @DisplayName("Updating a Payment increases its version")
    void updatingPaymentIncreasesVersion() {
        MemberProfile member = getOrCreateTestMember();
        long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(new BigDecimal("100000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        Payment saved = paymentRepository.saveAndFlush(payment);
        Integer initialVersion = saved.getVersion();
        assertThat(initialVersion).isEqualTo(0);

        saved.setPaymentStatus(PaymentStatus.SUCCESS);
        Payment updated = paymentRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
    }

    @Test
    @DisplayName("Saving a stale Payment copy throws ObjectOptimisticLockingFailureException")
    void savingStalePaymentThrowsOptimisticLockingFailureException() {
        MemberProfile member = getOrCreateTestMember();
        long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(new BigDecimal("100000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PENDING)
                .build());

        entityManager.detach(payment);

        Payment concurrentCopy = paymentRepository.findById(payment.getId()).orElseThrow();
        concurrentCopy.setPaymentStatus(PaymentStatus.SUCCESS);
        paymentRepository.saveAndFlush(concurrentCopy);

        payment.setNotes("Stale update attempt");
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Codes from sequences are formatted correctly by CodeFormatter as PAY-xxxx and INV-xxxx")
    void codesAreGeneratedInTheRightFormat() {
        long paySeq = paymentRepository.getNextPaymentCodeSequence();
        String paymentCode = codeFormatter.formatPaymentCode(paySeq);
        assertThat(paymentCode).startsWith("PAY-");
        assertThat(paymentCode).matches("^PAY-\\d{4,}$");

        long invSeq = invoiceRepository.getNextInvoiceNumberSequence();
        String invoiceNumber = codeFormatter.formatInvoiceNumber(invSeq);
        assertThat(invoiceNumber).startsWith("INV-");
        assertThat(invoiceNumber).matches("^INV-\\d{4,}$");
    }

    @Test
    @DisplayName("Map payment_id on MemberCard, SportPackageRegistration, and RefundRequest")
    void paymentIdForeignKeysOnExistingEntities() {
        MemberProfile member = getOrCreateTestMember();

        // 1. Create and save a Payment
        long paySeq = paymentRepository.getNextPaymentCodeSequence();
        Payment payment = paymentRepository.save(Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paySeq))
                .member(member)
                .amount(new BigDecimal("500000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.SUCCESS)
                .build());

        // 2. MemberCard with payment_id
        MembershipCardTier tier = membershipCardTierRepository.findAll().stream()
                .findFirst()
                .orElseThrow();

        long cardSeq = memberCardRepository.getNextCardCodeSequence();
        MemberCard memberCard = MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(cardSeq))
                .member(member)
                .tier(tier)
                .startDate(LocalDate.now())
                .status(CardStatus.ACTIVE)
                .pricePaid(new BigDecimal("300000.00"))
                .payment(payment)
                .build();
        MemberCard savedCard = memberCardRepository.save(memberCard);

        // 3. SportPackageRegistration with payment_id
        SportPackage sportPackage = sportPackageRepository.findAll().stream()
                .findFirst()
                .orElseThrow();

        long regSeq = sportPackageRegistrationRepository.getNextRegistrationCodeSequence();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .registrationCode(codeFormatter.formatPackageRegCode(regSeq))
                .member(member)
                .sportPackage(sportPackage)
                .channel(RegistrationChannel.ONLINE)
                .originalPrice(new BigDecimal("500000.00"))
                .discountPercentage(0)
                .paidAmount(new BigDecimal("500000.00"))
                .totalSessions(8)
                .remainingSessions(8)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(PackageRegistrationStatus.ACTIVE)
                .createdByUser(member.getUserAccount())
                .payment(payment)
                .build();
        SportPackageRegistration savedReg = sportPackageRegistrationRepository.save(reg);

        // 4. RefundRequest with payment_id
        long refSeq = refundRequestRepository.getNextRefundCodeSequence();
        RefundRequest refund = RefundRequest.builder()
                .refundCode(codeFormatter.formatRefundCode(refSeq))
                .packageRegistration(savedReg)
                .member(member)
                .amountRequested(new BigDecimal("500000.00"))
                .reason("Customer requested refund for unused sessions")
                .status(RefundStatus.PENDING)
                .requestedBy(member.getUserAccount())
                .payment(payment)
                .build();
        RefundRequest savedRefund = refundRequestRepository.save(refund);

        entityManager.flush();
        entityManager.clear();

        // 5. Verify MemberCard payment link
        MemberCard fetchedCard = memberCardRepository.findById(savedCard.getId()).orElseThrow();
        assertThat(fetchedCard.getPayment()).isNotNull();
        assertThat(fetchedCard.getPayment().getId()).isEqualTo(payment.getId());

        // 6. Verify SportPackageRegistration payment link
        SportPackageRegistration fetchedReg = sportPackageRegistrationRepository.findById(savedReg.getId()).orElseThrow();
        assertThat(fetchedReg.getPayment()).isNotNull();
        assertThat(fetchedReg.getPayment().getId()).isEqualTo(payment.getId());

        // 7. Verify RefundRequest payment link
        RefundRequest fetchedRefund = refundRequestRepository.findById(savedRefund.getId()).orElseThrow();
        assertThat(fetchedRefund.getPayment()).isNotNull();
        assertThat(fetchedRefund.getPayment().getId()).isEqualTo(payment.getId());
    }

    private MemberProfile getOrCreateTestMember() {
        String email = "test.payment.member@sportify.test";
        return userRepository.findByEmailIgnoreCase(email)
                .flatMap(u -> memberProfileRepository.findById(u.getId()))
                .orElseGet(() -> {
                    long roleId = jdbcTemplate.queryForObject(
                            "SELECT id FROM role WHERE code = 'MEMBER'", Long.class);

                    jdbcTemplate.update("""
                            INSERT INTO user_account (email, password_hash, full_name, role_id, status)
                            VALUES (?, 'hash123', 'Test Payment Member', ?, 'ACTIVE')
                            """, email, roleId);
                    long userId = jdbcTemplate.queryForObject(
                            "SELECT id FROM user_account WHERE email = ?", Long.class, email);

                    jdbcTemplate.update("""
                            INSERT INTO member_profile (user_id, member_code, current_level, joined_on)
                            VALUES (?, 'MEM-PAY-TEST', 'BEGINNER', '2026-10-01')
                            """, userId);

                    return memberProfileRepository.findById(userId).orElseThrow();
                });
    }
}
