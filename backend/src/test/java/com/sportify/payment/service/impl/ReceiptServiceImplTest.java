package com.sportify.payment.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.InvoiceLineResponse;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.dto.MemberPaymentResponse;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.InvoiceLine;
import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private MemberCardRepository memberCardRepository;
    @Mock
    private SportPackageRegistrationRepository registrationRepository;

    private ReceiptServiceImpl service;
    private UserAccount memberUser;
    private UserAccount otherMemberUser;
    private UserAccount receptionist;
    private MemberProfile member;
    private Payment confirmedPayment;
    private Payment pendingPayment;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        PaymentItemResolver resolver = new PaymentItemResolver(memberCardRepository, registrationRepository, invoiceRepository);
        service = new ReceiptServiceImpl(paymentRepository, invoiceRepository, resolver);

        memberUser = account(100L, "MEMBER", "Minh Member");
        otherMemberUser = account(200L, "MEMBER", "Other Member");
        receptionist = account(7L, "RECEPTIONIST", "Rita Reception");
        member = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-0100").build();

        confirmedPayment = Payment.builder()
                .id(1L).paymentCode("PAY-1001").member(member)
                .amount(new BigDecimal("380000.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER).paymentStatus(PaymentStatus.SUCCESS)
                .referenceCode("FT2610140001")
                .paymentTime(LocalDateTime.of(2026, 10, 14, 10, 0))
                .createdAt(LocalDateTime.of(2026, 10, 13, 9, 0))
                .build();
        pendingPayment = Payment.builder()
                .id(2L).paymentCode("PAY-1002").member(member)
                .amount(new BigDecimal("1200000.00"))
                .paymentMethod(PaymentMethod.MOMO).paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 10, 14, 11, 0))
                .build();

        invoice = Invoice.builder()
                .id(55L).invoiceNumber("INV-1001").payment(confirmedPayment).member(member)
                .subtotalAmount(new BigDecimal("400000.00")).discountAmount(new BigDecimal("20000.00"))
                .taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("380000.00"))
                .status(InvoiceStatus.PAID).issueDate(LocalDateTime.of(2026, 10, 14, 10, 0))
                .build();
        invoice.addLine(InvoiceLine.builder().id(1L).itemType(InvoiceItemType.SPORT_PACKAGE).itemReferenceId(20L)
                .description("Swimming 30 Days (REG-PKG-1020)").quantity(1)
                .unitPrice(new BigDecimal("400000.00")).lineTotal(new BigDecimal("400000.00")).build());
    }

    private static UserAccount account(Long id, String role, String name) {
        return UserAccount.builder().id(id).fullName(name)
                .role(Role.builder().code(role).name(role).build()).build();
    }

    private SportPackageRegistration registrationOf(Payment payment) {
        SportPackage pkg = SportPackage.builder().id(5L).name("Swimming 30 Days").durationDays(30).build();
        return SportPackageRegistration.builder()
                .id(20L).registrationCode("REG-PKG-1020").member(member).sportPackage(pkg)
                .originalPrice(new BigDecimal("400000.00")).discountPercentage(5)
                .paidAmount(new BigDecimal("380000.00"))
                .status(PackageRegistrationStatus.ACTIVE).payment(payment)
                .build();
    }

    @Test
    @DisplayName("My payments: own payments, newest first, item, invoice number for the confirmed one")
    void listMyPayments_ReturnsOwnPaymentsWithInvoice() {
        when(paymentRepository.findByMemberId(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingPayment, confirmedPayment)));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(registrationOf(confirmedPayment)));
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(invoiceRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(invoice));

        PageResponse<MemberPaymentResponse> page = service.listMyPayments(null, 0, 20, memberUser);

        assertThat(page.content()).extracting(MemberPaymentResponse::getCode).containsExactly("PAY-1002", "PAY-1001");
        MemberPaymentResponse pending = page.content().get(0);
        assertThat(pending.getInvoiceId()).isNull();
        assertThat(pending.getInvoiceNumber()).isNull();
        assertThat(pending.getTime()).isEqualTo(pendingPayment.getCreatedAt());

        MemberPaymentResponse paid = page.content().get(1);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paid.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(paid.getAmount()).isEqualByComparingTo("380000");
        assertThat(paid.getItemType()).isEqualTo(InvoiceItemType.SPORT_PACKAGE);
        assertThat(paid.getItemDescription()).isEqualTo("Swimming 30 Days");
        assertThat(paid.getOriginalPrice()).isEqualByComparingTo("400000");
        assertThat(paid.getDiscount()).isEqualByComparingTo("20000");
        assertThat(paid.getInvoiceId()).isEqualTo(55L);
        assertThat(paid.getInvoiceNumber()).isEqualTo("INV-1001");
        assertThat(paid.getTime()).isEqualTo(confirmedPayment.getPaymentTime());
        assertThat(paid.getMemberCode()).isEqualTo("MEM-0100");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByMemberId(eq(100L), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        verify(paymentRepository, never()).findByMemberIdAndPaymentStatus(anyLong(), any(), any());
    }

    @Test
    @DisplayName("My payments with a status filter uses the status query for the signed-in member only")
    void listMyPayments_StatusFilter() {
        when(paymentRepository.findByMemberIdAndPaymentStatus(eq(100L), eq(PaymentStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingPayment)));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(invoiceRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());

        PageResponse<MemberPaymentResponse> page = service.listMyPayments(PaymentStatus.PENDING, 0, 10, memberUser);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getItemDescription()).isEqualTo("Payment PAY-1002");
        verify(paymentRepository, never()).findByMemberId(anyLong(), any());
    }

    @Test
    @DisplayName("My payments: an empty history answers an empty page, not an error")
    void listMyPayments_Empty() {
        when(paymentRepository.findByMemberId(eq(100L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PageResponse<MemberPaymentResponse> page = service.listMyPayments(null, 0, 20, memberUser);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -1})
    @DisplayName("My payments: size outside 1 to 100 is a bad request")
    void listMyPayments_InvalidSize(int size) {
        assertThatThrownBy(() -> service.listMyPayments(null, 0, size, memberUser))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("My payments: a negative page is a bad request")
    void listMyPayments_NegativePage() {
        assertThatThrownBy(() -> service.listMyPayments(null, -1, 20, memberUser))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("My payments: staff have no payment history, 403")
    void listMyPayments_Staff_Forbidden() {
        assertThatThrownBy(() -> service.listMyPayments(null, 0, 20, receptionist))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Invoice by id: the owner gets header and lines; total = lines - discount + tax")
    void getInvoice_Owner() {
        when(invoiceRepository.findById(55L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.getInvoice(55L, memberUser);

        assertThat(response.getInvoiceNumber()).isEqualTo("INV-1001");
        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(response.getIssueDate()).isEqualTo(LocalDateTime.of(2026, 10, 14, 10, 0));
        assertThat(response.getMemberFullName()).isEqualTo("Minh Member");
        assertThat(response.getMemberCode()).isEqualTo("MEM-0100");
        assertThat(response.getPaymentId()).isEqualTo(1L);
        assertThat(response.getPaymentCode()).isEqualTo("PAY-1001");
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.getPaymentReference()).isEqualTo("FT2610140001");
        assertThat(response.getLines()).hasSize(1);
        BigDecimal linesTotal = response.getLines().stream()
                .map(InvoiceLineResponse::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(response.getSubtotalAmount()).isEqualByComparingTo(linesTotal);
        assertThat(linesTotal.subtract(response.getDiscountAmount()).add(response.getTaxAmount()))
                .isEqualByComparingTo(response.getTotalAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    @DisplayName("Invoice by id: staff read the invoice of any member")
    void getInvoice_Staff(String role) {
        when(invoiceRepository.findById(55L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.getInvoice(55L, account(8L, role, "Staff"));

        assertThat(response.getId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("Invoice by id: another member gets 403")
    void getInvoice_OtherMember_Forbidden() {
        when(invoiceRepository.findById(55L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.getInvoice(55L, otherMemberUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Invoice by id: a user with no known role gets 403")
    void getInvoice_UnknownRole_Forbidden() {
        when(invoiceRepository.findById(55L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.getInvoice(55L, account(100L, "COACH", "Same id, other role")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Invoice by id: unknown id gets 404")
    void getInvoice_NotFound() {
        when(invoiceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInvoice(999L, memberUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Invoice by id: lines come back in insert order")
    void getInvoice_LinesSortedById() {
        Invoice multi = Invoice.builder()
                .id(56L).invoiceNumber("INV-1002").payment(confirmedPayment).member(member)
                .subtotalAmount(new BigDecimal("500000.00")).discountAmount(new BigDecimal("50000.00"))
                .taxAmount(new BigDecimal("10000.00")).totalAmount(new BigDecimal("460000.00"))
                .status(InvoiceStatus.PAID).build();
        multi.addLine(InvoiceLine.builder().id(9L).itemType(InvoiceItemType.PENALTY_FEE).itemReferenceId(3L)
                .description("Late fee").quantity(1)
                .unitPrice(new BigDecimal("100000.00")).lineTotal(new BigDecimal("100000.00")).build());
        multi.addLine(InvoiceLine.builder().id(8L).itemType(InvoiceItemType.SPORT_PACKAGE).itemReferenceId(20L)
                .description("Package").quantity(2)
                .unitPrice(new BigDecimal("200000.00")).lineTotal(new BigDecimal("400000.00")).build());
        when(invoiceRepository.findById(56L)).thenReturn(Optional.of(multi));

        InvoiceResponse response = service.getInvoice(56L, receptionist);

        assertThat(response.getLines()).extracting(InvoiceLineResponse::getId).containsExactly(8L, 9L);
        BigDecimal linesTotal = response.getLines().stream()
                .map(InvoiceLineResponse::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(linesTotal.subtract(response.getDiscountAmount()).add(response.getTaxAmount()))
                .isEqualByComparingTo(response.getTotalAmount());
    }

    @Test
    @DisplayName("Invoice by id: a stored invoice whose totals do not add up is returned as recorded and logged as a warning")
    void getInvoice_TotalsMismatch_ReturnedAsStoredAndLogged() {
        Invoice broken = Invoice.builder()
                .id(57L).invoiceNumber("INV-1003").payment(confirmedPayment).member(member)
                .subtotalAmount(new BigDecimal("400000.00")).discountAmount(new BigDecimal("20000.00"))
                .taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("390000.00"))
                .status(InvoiceStatus.PAID).build();
        broken.addLine(InvoiceLine.builder().id(1L).itemType(InvoiceItemType.SPORT_PACKAGE).itemReferenceId(20L)
                .description("Swimming 30 Days (REG-PKG-1020)").quantity(1)
                .unitPrice(new BigDecimal("400000.00")).lineTotal(new BigDecimal("400000.00")).build());
        when(invoiceRepository.findById(57L)).thenReturn(Optional.of(broken));

        Logger logger = (Logger) LoggerFactory.getLogger(ReceiptServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            InvoiceResponse response = service.getInvoice(57L, memberUser);

            assertThat(response.getTotalAmount()).isEqualByComparingTo("390000");
            assertThat(response.getSubtotalAmount()).isEqualByComparingTo("400000");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("20000");
            assertThat(response.getLines()).hasSize(1);
            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("INV-1003").contains("do not add up"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Invoice by id: totals that add up produce no warning")
    void getInvoice_TotalsMatch_NoWarning() {
        when(invoiceRepository.findById(55L)).thenReturn(Optional.of(invoice));

        Logger logger = (Logger) LoggerFactory.getLogger(ReceiptServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.getInvoice(55L, memberUser);

            assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Invoice by payment: owner and staff read it")
    void getInvoiceByPayment_OwnerAndStaff() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(confirmedPayment));
        when(invoiceRepository.findByPaymentId(1L)).thenReturn(List.of(invoice));

        assertThat(service.getInvoiceByPayment(1L, memberUser).getInvoiceNumber()).isEqualTo("INV-1001");
        assertThat(service.getInvoiceByPayment(1L, receptionist).getLines()).hasSize(1);
    }

    @Test
    @DisplayName("Invoice by payment: another member gets 403 and the invoice table is not read")
    void getInvoiceByPayment_OtherMember_Forbidden() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(confirmedPayment));

        assertThatThrownBy(() -> service.getInvoiceByPayment(1L, otherMemberUser))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invoiceRepository);
    }

    @Test
    @DisplayName("Invoice by payment: a PENDING payment has no invoice yet, 404")
    void getInvoiceByPayment_NoInvoice_NotFound() {
        when(paymentRepository.findById(2L)).thenReturn(Optional.of(pendingPayment));
        when(invoiceRepository.findByPaymentId(2L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getInvoiceByPayment(2L, memberUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No invoice");
    }

    @Test
    @DisplayName("Invoice by payment: unknown payment gets 404")
    void getInvoiceByPayment_PaymentNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInvoiceByPayment(999L, memberUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("No signed-in user: 403 on every call")
    void nullActor_Forbidden() {
        assertThatThrownBy(() -> service.listMyPayments(null, 0, 20, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getInvoice(55L, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getInvoiceByPayment(1L, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(paymentRepository, invoiceRepository);
    }
}
