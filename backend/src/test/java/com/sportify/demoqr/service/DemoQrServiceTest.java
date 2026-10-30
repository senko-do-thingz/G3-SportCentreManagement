package com.sportify.demoqr.service;

import com.sportify.demoqr.config.DemoQrSettings;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pure security/contract checks; persistence, restart and races are tested against SQL Server. */
class DemoQrServiceTest {
    private final DemoPaymentReader reader = mock(DemoPaymentReader.class);
    private final SqlQrStore store = mock(SqlQrStore.class);
    private DemoQrService service;
    private UserAccount owner;

    @BeforeEach
    void setup() {
        service = new DemoQrService(reader, new DemoQrSettings("https://demo.example.test/", "https://demo.example.test", false, 300), store);
        owner = UserAccount.builder().id(2L).status("ACTIVE").role(Role.builder().code("MEMBER").build()).build();
        when(store.spend(2, true)).thenReturn(true);
        when(store.spend(2, false)).thenReturn(true);
        when(store.transaction(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"x","../secret","token=52"})
    void malformedTokensSpendPersistentBudgetWithoutLookup(String token) {
        assertThatThrownBy(() -> service.preview(token, owner)).isInstanceOf(DemoQrException.class);
        verify(store).spend(2, false);
        verify(store, never()).findVerifier(anyString(), anyLong());
    }

    @Test void inactiveAndWrongRoleCannotReachSql() {
        owner.setStatus("INACTIVE");
        assertThatThrownBy(() -> service.preview("a".repeat(43), owner)).isInstanceOf(DemoQrException.class);
        owner.setStatus("ACTIVE"); owner.getRole().setCode("MANAGER");
        assertThatThrownBy(() -> service.create(52, DemoQrService.Bank.BIDV_DEMO, UUID.randomUUID(), owner)).isInstanceOf(DemoQrException.class);
        verifyNoInteractions(reader, store);
    }

    @Test void exhaustedAttemptsCannotReachVerifierOrFinancialReader() {
        when(store.spend(2, false)).thenReturn(false);
        assertThatThrownBy(() -> service.confirm("a".repeat(43), owner)).isInstanceOf(DemoQrException.class).hasMessageContaining("rate limit");
        verifyNoInteractions(reader);
        verify(store, never()).findVerifier(anyString(), anyLong());
    }

    @Test void tokenVerifierUsesSha256AndDebugStringsRedactRawToken() {
        String token = "a".repeat(43);
        assertThat(DemoQrService.digest(token)).hasSize(64).doesNotContain(token);
        assertThat(new DemoQrService.Created(null, "https://example.test/#token=" + token).toString()).doesNotContain(token);
        assertThat(new com.sportify.demoqr.controller.DemoQrController.TokenRequest(token).toString()).doesNotContain(token);
    }

    @Test void duplicateCreateCannotRecoverTokenOrRevokeExistingSession() {
        UUID request = UUID.randomUUID();
        when(reader.read(52,owner)).thenReturn(new DemoPaymentReader.Snapshot(52, BigDecimal.TEN, "VND"));
        when(store.requestExists(2,request)).thenReturn(true);
        assertThatThrownBy(() -> service.create(52,DemoQrService.Bank.BIDV_DEMO,request,owner))
                .isInstanceOf(DemoQrException.class).hasMessageContaining("already processed");
        verify(store, never()).replace(anyLong(),anyLong());
    }

    @Test void sqlDisconnectCannotReturnFakeSuccess() {
        when(store.spend(2,false)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("disconnected"));
        assertThatThrownBy(() -> service.confirm("a".repeat(43),owner)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verifyNoInteractions(reader);
    }

    @Test void confirmSettlesPendingSessionAtomicallyAndReturnsProvenance() {
        String token = "b".repeat(43);
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-30T06:00:00Z");
        var pending = new SqlQrStore.Session(sessionId, 52L, 2L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.BIDV_DEMO, now.minusSeconds(30), now.plusSeconds(270),
                DemoQrService.State.PENDING, 0L);
        var settled = new SqlQrStore.Session(sessionId, 52L, 2L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.BIDV_DEMO, now.minusSeconds(30), now.plusSeconds(270),
                DemoQrService.State.SETTLED, 1L);
        var tx = new SqlQrStore.SimulatedTx(901L, "SIM-QR-52-ABCDEF123456", now);
        var info = new SqlQrStore.SettlementInfo("SIMULATED", "SPORTIFY_SIMULATOR", 901L,
                "SIM-QR-52-ABCDEF123456", now, 77L, "INV-1077");

        when(store.findVerifier(DemoQrService.digest(token), 2L)).thenReturn(pending);
        when(store.lock(sessionId, 2L)).thenReturn(pending);
        when(store.expire(pending)).thenReturn(pending);
        when(reader.read(52L, owner)).thenReturn(new DemoPaymentReader.Snapshot(52L, new BigDecimal("810000.00"), "VND"));
        when(store.isBeforeDeadline(sessionId)).thenReturn(true);
        when(store.insertSettlement(pending)).thenReturn(tx);
        when(reader.settle(52L, owner, "BIDV_DEMO", 901L, "SIM-QR-52-ABCDEF123456"))
                .thenReturn(new DemoPaymentReader.SettlementOutcome(77L, "INV-1077"));
        when(store.settle(pending)).thenReturn(settled);
        when(store.findSettlement(sessionId, 52L)).thenReturn(java.util.Optional.of(info));
        when(store.now()).thenReturn(now);

        var view = service.confirm(token, owner);

        assertThat(view.state()).isEqualTo(DemoQrService.State.SETTLED);
        assertThat(view.settlementType()).isEqualTo("SIMULATED");
        assertThat(view.settlementProvider()).isEqualTo("SPORTIFY_SIMULATOR");
        assertThat(view.simulatedTransactionId()).isEqualTo(901L);
        assertThat(view.transactionCode()).isEqualTo("SIM-QR-52-ABCDEF123456");
        assertThat(view.invoiceId()).isEqualTo(77L);
        assertThat(view.invoiceNumber()).isEqualTo("INV-1077");
        verify(store).insertSettlement(pending);
        verify(reader).settle(52L, owner, "BIDV_DEMO", 901L, "SIM-QR-52-ABCDEF123456");
        verify(store).settle(pending);
    }

    @Test void confirmOnAlreadySettledSessionIsIdempotentWithoutDuplicateWrites() {
        String token = "c".repeat(43);
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-30T06:00:00Z");
        var settled = new SqlQrStore.Session(sessionId, 52L, 2L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.BIDV_DEMO, now.minusSeconds(60), now.plusSeconds(240),
                DemoQrService.State.SETTLED, 1L);
        var info = new SqlQrStore.SettlementInfo("SIMULATED", "SPORTIFY_SIMULATOR", 901L,
                "SIM-QR-52-ABCDEF123456", now, 77L, "INV-1077");

        when(store.findVerifier(DemoQrService.digest(token), 2L)).thenReturn(settled);
        when(store.lock(sessionId, 2L)).thenReturn(settled);
        when(store.expire(settled)).thenReturn(settled);
        when(store.findSettlement(sessionId, 52L)).thenReturn(java.util.Optional.of(info));
        when(store.now()).thenReturn(now);

        var view = service.confirm(token, owner);

        assertThat(view.state()).isEqualTo(DemoQrService.State.SETTLED);
        assertThat(view.simulatedTransactionId()).isEqualTo(901L);
        verify(store, never()).insertSettlement(any());
        verify(reader, never()).settle(anyLong(), any(), anyString(), anyLong(), anyString());
        verify(store, never()).settle(any());
    }

    @Test void cancelOnSettledSessionIsRejectedWithConflict() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-30T06:00:00Z");
        var settled = new SqlQrStore.Session(sessionId, 52L, 2L, new BigDecimal("810000.00"), "VND",
                DemoQrService.Bank.BIDV_DEMO, now.minusSeconds(60), now.plusSeconds(240),
                DemoQrService.State.SETTLED, 1L);

        when(store.findId(sessionId, 2L)).thenReturn(settled);
        when(store.lock(sessionId, 2L)).thenReturn(settled);
        when(store.expire(settled)).thenReturn(settled);

        assertThatThrownBy(() -> service.cancel(sessionId.toString(), owner))
                .isInstanceOf(DemoQrException.class)
                .satisfies(ex -> {
                    DemoQrException dqe = (DemoQrException) ex;
                    assertThat(dqe.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(dqe.code()).isEqualTo("SIMULATOR_SETTLED");
                });
        verify(store, never()).cancel(any());
    }
}
