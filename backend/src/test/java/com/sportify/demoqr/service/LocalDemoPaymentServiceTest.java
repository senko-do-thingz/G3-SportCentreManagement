package com.sportify.demoqr.service;

import com.sportify.demoqr.dto.LocalDemoSettlementResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalDemoPaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock DemoPaymentReader demoPaymentReader;
    @Mock SqlQrStore sqlQrStore;
    @Mock InvoiceRepository invoiceRepository;
    private MockEnvironment environment;

    private LocalDemoPaymentService service;
    private UserAccount memberAccount;
    private MemberProfile memberProfile;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        service = new LocalDemoPaymentService(
                paymentRepository,
                demoPaymentReader,
                sqlQrStore,
                invoiceRepository,
                environment
        );

        memberAccount = UserAccount.builder()
                .id(10L)
                .email("an.gold@example.com")
                .status("ACTIVE")
                .role(Role.builder().code("MEMBER").name("MEMBER").build())
                .build();

        memberProfile = MemberProfile.builder()
                .id(10L)
                .userAccount(memberAccount)
                .memberCode("MEM-00010")
                .build();
    }

    private void configureValidEnvironment() {
        environment.setActiveProfiles("dev", "local-demo");
        environment.setProperty("app.payment.local-demo.enabled", "true");
        environment.setProperty("server.address", "127.0.0.1");
        environment.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;");
    }

    @Test
    void confirmThrowsUnavailableWhenServiceDisabled() {
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, memberAccount))
                .isInstanceOf(DemoQrException.class);
    }

    @Test
    void confirmThrowsUnavailableForNonMemberOrInactiveActor() {
        configureValidEnvironment();
        UserAccount staff = UserAccount.builder().id(2L).status("ACTIVE")
                .role(Role.builder().code("RECEPTIONIST").build()).build();

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, staff))
                .isInstanceOf(DemoQrException.class);

        UserAccount inactive = UserAccount.builder().id(10L).status("INACTIVE")
                .role(Role.builder().code("MEMBER").build()).build();

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, inactive))
                .isInstanceOf(DemoQrException.class);
    }

    @Test
    void confirmThrowsUnavailableForInvalidPaymentId() {
        configureValidEnvironment();

        assertThatThrownBy(() -> service.confirmDemoPayment(0L, memberAccount))
                .isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.confirmDemoPayment(-5L, memberAccount))
                .isInstanceOf(DemoQrException.class);
    }

    @Test
    void confirmThrowsRateLimitWhenSpendExhausted() {
        configureValidEnvironment();
        when(sqlQrStore.spend(eq(10L), eq(true))).thenReturn(false);

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, memberAccount))
                .isInstanceOf(DemoQrException.class)
                .satisfies(ex -> assertThat(((DemoQrException) ex).status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void confirmThrowsUnavailableWhenPaymentNotFoundOrWrongOwner() {
        configureValidEnvironment();
        when(sqlQrStore.spend(eq(10L), eq(true))).thenReturn(true);
        when(sqlQrStore.transaction(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(paymentRepository.findByIdForUpdate(52L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, memberAccount))
                .isInstanceOf(DemoQrException.class);

        UserAccount otherAccount = UserAccount.builder().id(99L).build();
        MemberProfile otherProfile = MemberProfile.builder().id(99L).userAccount(otherAccount).build();
        Payment wrongOwner = Payment.builder().id(52L).member(otherProfile).build();

        when(paymentRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(wrongOwner));

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, memberAccount))
                .isInstanceOf(DemoQrException.class);
    }

    @Test
    void confirmThrowsConflictWhenPaymentAlreadySettledWithActual() {
        configureValidEnvironment();
        when(sqlQrStore.spend(eq(10L), eq(true))).thenReturn(true);
        when(sqlQrStore.transaction(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        Payment actualPaid = Payment.builder()
                .id(52L)
                .member(memberProfile)
                .paymentStatus(PaymentStatus.SUCCESS)
                .settlementType("ACTUAL")
                .settlementProvider("DESK_CASH")
                .build();

        when(paymentRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(actualPaid));

        assertThatThrownBy(() -> service.confirmDemoPayment(52L, memberAccount))
                .isInstanceOf(DemoQrException.class)
                .satisfies(ex -> assertThat(((DemoQrException) ex).status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void confirmIsIdempotentWhenAlreadySettledWithSimulated() {
        configureValidEnvironment();
        when(sqlQrStore.spend(eq(10L), eq(true))).thenReturn(true);
        when(sqlQrStore.transaction(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        Payment alreadySimulated = Payment.builder()
                .id(52L)
                .member(memberProfile)
                .paymentStatus(PaymentStatus.SUCCESS)
                .settlementType("SIMULATED")
                .settlementProvider("SPORTIFY_SIMULATOR")
                .simulatedTransactionId(77L)
                .referenceCode("SIM-QR-52-PREV")
                .build();

        Invoice invoice = Invoice.builder().id(123L).invoiceNumber("INV-2026-00123").build();

        when(paymentRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(alreadySimulated));
        when(invoiceRepository.findByPaymentId(52L)).thenReturn(List.of(invoice));

        LocalDemoSettlementResponse response = service.confirmDemoPayment(52L, memberAccount);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(52L);
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.settlementType()).isEqualTo("SIMULATED");
        assertThat(response.settlementProvider()).isEqualTo("SPORTIFY_SIMULATOR");
        assertThat(response.simulatedTransactionId()).isEqualTo(77L);
        assertThat(response.referenceCode()).isEqualTo("SIM-QR-52-PREV");
        assertThat(response.invoiceId()).isEqualTo(123L);
        assertThat(response.invoiceNumber()).isEqualTo("INV-2026-00123");

        verifyNoInteractions(demoPaymentReader);
    }

    @Test
    void confirmHappyPathAtomicallySettlesAndReturnsResponse() {
        configureValidEnvironment();
        when(sqlQrStore.spend(eq(10L), eq(true))).thenReturn(true);
        when(sqlQrStore.transaction(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        Payment pending = Payment.builder()
                .id(52L)
                .member(memberProfile)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(new BigDecimal("810000.00"))
                .build();

        when(paymentRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(pending));
        when(demoPaymentReader.read(52L, memberAccount))
                .thenReturn(new DemoPaymentReader.Snapshot(52L, new BigDecimal("810000.00"), "VND"));

        UUID sessionId = UUID.randomUUID();
        when(sqlQrStore.insert(eq(52L), eq(10L), eq(new BigDecimal("810000.00")), any(), any(), any(), eq(300L)))
                .thenReturn(sessionId);

        SqlQrStore.Session session = new SqlQrStore.Session(
                sessionId, 52L, 10L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.VIETCOMBANK_DEMO, Instant.now(), Instant.now().plusSeconds(300),
                DemoQrService.State.PENDING, 0L
        );
        when(sqlQrStore.findId(sessionId, 10L)).thenReturn(session);

        SqlQrStore.SimulatedTx tx = new SqlQrStore.SimulatedTx(88L, "SIM-QR-52-NEW", Instant.now());
        when(sqlQrStore.insertSettlement(session)).thenReturn(tx);

        when(demoPaymentReader.settle(eq(52L), eq(memberAccount), anyString(), eq(88L), eq("SIM-QR-52-NEW")))
                .thenReturn(new DemoPaymentReader.SettlementOutcome(555L, "INV-2026-00555"));

        LocalDemoSettlementResponse response = service.confirmDemoPayment(52L, memberAccount);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(52L);
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.settlementType()).isEqualTo("SIMULATED");
        assertThat(response.settlementProvider()).isEqualTo("SPORTIFY_SIMULATOR");
        assertThat(response.simulatedTransactionId()).isEqualTo(88L);
        assertThat(response.referenceCode()).isEqualTo("SIM-QR-52-NEW");
        assertThat(response.invoiceId()).isEqualTo(555L);
        assertThat(response.invoiceNumber()).isEqualTo("INV-2026-00555");

        verify(sqlQrStore).replace(52L, 10L);
        verify(sqlQrStore).settle(session);
    }
}
