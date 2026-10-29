package com.sportify.demoqr.service;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.demoqr.config.DemoQrSettings;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoQrServiceTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final MemberCardRepository cards = mock(MemberCardRepository.class);
    private final SportPackageRegistrationRepository registrations = mock(SportPackageRegistrationRepository.class);
    private final MutableClock clock = new MutableClock();
    private final DemoQrSettings settings = new DemoQrSettings("https://demo.example.test/sportify/", "https://demo.example.test", false, 300);
    private DemoPaymentReader reader;
    private DemoQrService service;
    private Payment payment;
    private UserAccount owner;

    public static UserAccount actor(long id, String role) {
        return UserAccount.builder().id(id).email("owner" + id + "@example.test").status("ACTIVE")
                .role(Role.builder().code(role).name(role).build()).build();
    }

    @BeforeEach
    void setup() {
        owner = actor(2, "MEMBER");
        payment = Payment.builder().id(52L).member(MemberProfile.builder().id(2L).build())
                .amount(new BigDecimal("810000.00")).paymentStatus(PaymentStatus.PENDING).version(7).build();
        when(payments.findById(52L)).thenReturn(Optional.of(payment));
        reader = new DemoPaymentReader(payments, cards, registrations);
        service = new DemoQrService(reader, settings, clock);
    }

    private DemoQrService.Created create() { return service.create(52, DemoQrService.Bank.VIETCOMBANK_DEMO, owner); }
    private String token(DemoQrService.Created result) { return result.confirmationUrl().split("#token=")[1]; }

    @ParameterizedTest
    @EnumSource(DemoQrService.Bank.class)
    void bindsEveryAllowedBankToImmutableAuthorizedSnapshot(DemoQrService.Bank bank) {
        var result = service.create(52, bank, owner);
        assertThat(result.session().bankCode()).isEqualTo(bank);
        assertThat(result.session().amount()).isEqualByComparingTo("810000");
        assertThat(result.session().reference()).isEqualTo("DEMO-52");
        assertThat(result.session().mode()).isEqualTo("NON_PAYABLE");
        assertThat(result.confirmationUrl()).startsWith("https://demo.example.test/sportify/demo/qr/confirm#token=");
        assertThat(token(result)).matches("[A-Za-z0-9_-]{43}");
        assertThat(result.session().expiresAt()).isEqualTo(clock.instant().plusSeconds(300));
    }

    @Test
    void previewDoesNotConfirmAndSuccessNeverMutatesFinancialObjectsOrRepositories() {
        var created = create();
        assertThat(service.preview(token(created), owner).state()).isEqualTo(DemoQrService.State.PENDING);
        assertThat(service.confirm(token(created), owner).state()).isEqualTo(DemoQrService.State.CONFIRMED);
        assertThat(service.confirm(token(created), owner).state()).isEqualTo(DemoQrService.State.CONFIRMED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("810000");
        assertThat(payment.getVersion()).isEqualTo(7);
        assertThat(payment.getPaymentTime()).isNull();
        verify(payments, times(2)).findById(52L);
        verify(cards, times(2)).findByPaymentId(52L);
        verify(registrations, times(2)).findByPaymentId(52L);
        verifyNoMoreInteractions(payments, cards, registrations);
    }

    @Test
    void replacementCancelsPreviousLinkWithoutChangingItsBank() {
        var first = create();
        var next = service.create(52, DemoQrService.Bank.BIDV_DEMO, owner);
        assertThat(service.preview(token(first), owner).state()).isEqualTo(DemoQrService.State.CANCELED);
        assertThat(service.preview(token(first), owner).bankCode()).isEqualTo(DemoQrService.Bank.VIETCOMBANK_DEMO);
        assertThat(service.preview(token(next), owner).state()).isEqualTo(DemoQrService.State.PENDING);
        assertThatThrownBy(() -> service.confirm(token(first), owner)).isInstanceOf(DemoQrException.class).hasMessageContaining("CANCELED");
    }

    @Test
    void exactExpiryBoundaryRejectsConfirmAndCannotBeRevivedByCancelOrReplay() {
        var first = create();
        clock.now.set(first.session().expiresAt());
        assertThatThrownBy(() -> service.confirm(token(first), owner)).isInstanceOf(DemoQrException.class).hasMessageContaining("EXPIRED");
        assertThat(service.get(first.session().id().toString(), owner).state()).isEqualTo(DemoQrService.State.EXPIRED);
        assertThat(service.cancel(first.session().id().toString(), owner).state()).isEqualTo(DemoQrService.State.EXPIRED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void oneInstantBeforeExpiryCanConfirmAndConfirmedIsTerminal() {
        var first = create();
        clock.now.set(first.session().expiresAt().minusNanos(1));
        assertThat(service.confirm(token(first), owner).state()).isEqualTo(DemoQrService.State.CONFIRMED);
        clock.now.set(first.session().expiresAt().plusSeconds(1));
        assertThat(service.cancel(first.session().id().toString(), owner).state()).isEqualTo(DemoQrService.State.CONFIRMED);
    }

    @Test
    void canceledIsTerminalAndCancellationIdempotent() {
        var first = create();
        for (int i = 0; i < 2; i++) assertThat(service.cancel(first.session().id().toString(), owner).state()).isEqualTo(DemoQrService.State.CANCELED);
        assertThatThrownBy(() -> service.confirm(token(first), owner)).isInstanceOf(DemoQrException.class).hasMessageContaining("CANCELED");
    }

    @Test
    void concurrentConfirmRequestsProduceOneFinanciallyIsolatedTerminalTransition() throws Exception {
        var first = create();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<DemoQrService.View>>();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> { start.await(); return service.confirm(token(first), owner); }));
            start.countDown();
            for (var result : futures) assertThat(result.get(5, TimeUnit.SECONDS).state()).isEqualTo(DemoQrService.State.CONFIRMED);
        }
        verify(payments, times(2)).findById(52L);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void expiryRacingWithAuthorizedReadWinsAtTheTransition() throws Exception {
        var controlledReader = mock(DemoPaymentReader.class);
        var snapshot = new DemoPaymentReader.Snapshot(52, new BigDecimal("810000"));
        when(controlledReader.read(52, owner)).thenReturn(snapshot);
        var controlled = new DemoQrService(controlledReader, settings, clock);
        var first = controlled.create(52, DemoQrService.Bank.BIDV_DEMO, owner);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(controlledReader.read(52, owner)).thenAnswer(ignored -> { reading.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return snapshot; });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> controlled.confirm(token(first), owner));
            assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
            clock.now.set(first.session().expiresAt());
            release.countDown();
            assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(DemoQrException.class);
        } finally { release.countDown(); }
        assertThat(controlled.preview(token(first), owner).state()).isEqualTo(DemoQrService.State.EXPIRED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"x", "token=52", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "../secret"})
    void malformedAndUnknownTokensReturnGenericDenial(String token) {
        assertThatThrownBy(() -> service.preview(token, owner)).isInstanceOf(DemoQrException.class).hasMessage(DemoQrException.unavailable().getMessage());
    }

    @Test
    void anotherOwnerCannotReadConfirmCancelOrCreateAgainstThisPayment() {
        var first = create();
        var other = actor(3, "MEMBER");
        assertThatThrownBy(() -> service.get(first.session().id().toString(), other)).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.confirm(token(first), other)).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.cancel(first.session().id().toString(), other)).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.create(52, DemoQrService.Bank.BIDV_DEMO, other)).isInstanceOf(DemoQrException.class);
        assertThat(service.preview(token(first), owner).state()).isEqualTo(DemoQrService.State.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED", "REFUNDED"})
    void rejectsIneligibleFinancialStatusAtCreateAndRevalidatesAtConfirm(PaymentStatus status) {
        var first = create();
        payment.setPaymentStatus(status);
        assertThatThrownBy(this::create).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.confirm(token(first), owner)).isInstanceOf(DemoQrException.class);
        assertThat(service.preview(token(first), owner).state()).isEqualTo(DemoQrService.State.CANCELED);
        assertThat(payment.getPaymentStatus()).isEqualTo(status);
    }

    @Test
    void amountChangeInvalidatesPendingSnapshot() {
        var first = create();
        payment.setAmount(BigDecimal.ONE);
        assertThatThrownBy(() -> service.confirm(token(first), owner)).isInstanceOf(DemoQrException.class);
        assertThat(service.preview(token(first), owner).amount()).isEqualByComparingTo("810000");
        assertThat(service.preview(token(first), owner).state()).isEqualTo(DemoQrService.State.CANCELED);
    }

    @Test
    void rejectsInvalidIdsAmountsActorsAndLinkedItems() {
        assertThatThrownBy(() -> service.create(0, DemoQrService.Bank.BIDV_DEMO, owner)).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.create(999, DemoQrService.Bank.BIDV_DEMO, owner)).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.create(52, null, owner)).isInstanceOf(DemoQrException.class);
        assertThatThrownBy(() -> service.create(52, DemoQrService.Bank.BIDV_DEMO, actor(2, "MANAGER"))).isInstanceOf(DemoQrException.class);
        payment.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(this::create).isInstanceOf(DemoQrException.class);
        payment.setAmount(BigDecimal.TEN);
        when(cards.findByPaymentId(52L)).thenReturn(Optional.of(MemberCard.builder().status(CardStatus.ACTIVE).build()));
        clock.now.set(clock.instant().plusSeconds(61));
        assertThatThrownBy(this::create).isInstanceOf(DemoQrException.class);
        when(cards.findByPaymentId(52L)).thenReturn(Optional.empty());
        when(registrations.findByPaymentId(52L)).thenReturn(Optional.of(SportPackageRegistration.builder().status(PackageRegistrationStatus.CANCELLED).build()));
        assertThatThrownBy(this::create).isInstanceOf(DemoQrException.class);
    }

    @Test
    void rawTokensAreAbsentFromStoreAndDebugStringsAndRestartRevokesThem() throws Exception {
        var tokens = new HashSet<String>();
        for (int i = 0; i < 5; i++) tokens.add(token(create()));
        assertThat(tokens).hasSize(5);
        var last = createAfterMinute();
        var sessionsField = DemoQrService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        var store = (java.util.Map<?, ?>) sessionsField.get(service);
        for (var entry : store.values()) for (var field : entry.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            assertThat(String.valueOf(field.get(entry))).doesNotContain(token(last));
        }
        assertThat(last.toString()).doesNotContain(token(last));
        var restarted = new DemoQrService(reader, settings, clock);
        assertThatThrownBy(() -> restarted.preview(token(last), owner)).isInstanceOf(DemoQrException.class);
    }

    private DemoQrService.Created createAfterMinute() { clock.now.set(clock.instant().plusSeconds(61)); return create(); }

    @Test
    void creationAndTokenAccessHaveBoundedRateLimitsAndRecoverAfterClockWindow() {
        var first = create();
        for (int i = 0; i < 4; i++) create();
        assertThatThrownBy(this::create).isInstanceOf(DemoQrException.class).hasMessageContaining("rate limit");
        for (int i = 0; i < 60; i++) service.preview(token(first), owner);
        assertThatThrownBy(() -> service.preview(token(first), owner)).isInstanceOf(DemoQrException.class).hasMessageContaining("rate limit");
        assertThat(createAfterMinute().session().state()).isEqualTo(DemoQrService.State.PENDING);
    }

    @Test
    void boundedStoreRejectsExcessSessionsAndCleansOldTerminalEntries() {
        var fixtureReader = mock(DemoPaymentReader.class);
        when(fixtureReader.read(eq(52L), any(UserAccount.class))).thenReturn(new DemoPaymentReader.Snapshot(52, BigDecimal.TEN));
        var bounded = new DemoQrService(fixtureReader, settings, clock);
        for (long id = 1; id <= 200; id++) for (int attempt = 0; attempt < 5; attempt++) {
            bounded.create(52, DemoQrService.Bank.BIDV_DEMO, actor(id, "MEMBER"));
        }
        var next = actor(201, "MEMBER");
        assertThatThrownBy(() -> bounded.create(52, DemoQrService.Bank.BIDV_DEMO, next))
                .isInstanceOf(DemoQrException.class).hasMessageContaining("capacity");
        clock.now.set(clock.instant().plusSeconds(901));
        assertThat(bounded.create(52, DemoQrService.Bank.BIDV_DEMO, next).session().state()).isEqualTo(DemoQrService.State.PENDING);
    }

    static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-29T08:00:00Z"));
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
