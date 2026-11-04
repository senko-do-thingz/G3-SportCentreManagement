package com.sportify.demoqr.service;

import com.sportify.demoqr.config.UnifiedDemoSafetyGuard;
import com.sportify.demoqr.config.UnifiedDemoSafetyGuardTest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedDemoPaymentServiceTest {
    @ParameterizedTest @CsvSource({"10,ordinary@example.com", "99,fixture@unified-demo.invalid", "10,fixture@unified-demo.invalid"})
    void explicitMemberIdAndSyntheticNamespaceAreBothRequired(long id, String email) {
        var env = UnifiedDemoSafetyGuardTest.canonical();
        var payments = mock(PaymentRepository.class);
        var store = mock(SqlQrStore.class);
        var service = new LocalDemoPaymentService(payments, mock(DemoPaymentReader.class), store, mock(InvoiceRepository.class), env);
        var actor = UserAccount.builder().id(id).email(email).status("ACTIVE").role(Role.builder().code("MEMBER").build()).build();
        assertThat(service.isEnabled()).isTrue();
        assertThatThrownBy(() -> service.confirmDemoPayment(1, actor)).isInstanceOf(DemoQrException.class);
        if (id == 10 && email.endsWith("@unified-demo.invalid")) verify(store).spend(id, true);
        else verifyNoInteractions(store, payments);
        env.setProperty(UnifiedDemoSafetyGuard.ENABLED, "false");
        assertThat(service.isEnabled()).isFalse();
    }
}
