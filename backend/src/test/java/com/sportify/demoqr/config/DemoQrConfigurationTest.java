package com.sportify.demoqr.config;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.demoqr.controller.DemoQrController;
import com.sportify.demoqr.service.DemoPaymentReader;
import com.sportify.demoqr.service.DemoQrService;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentVerificationService;
import com.sportify.demoqr.service.SqlQrStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DemoQrConfigurationTest {
    @ParameterizedTest
    @CsvSource({"qr-simulator,true,true", "qr-demo,true,false", "dev,true,false", "dev,false,false", "prod,true,false", "production,true,false", "default,true,false", "'dev,prod',true,false", "'qr-demo,production',true,false"})
    void independentProfileAndExplicitOptInAreBothRequired(String profiles, String enabled, boolean present) {
        new ApplicationContextRunner().withUserConfiguration(DemoQrConfiguration.class, DemoQrService.class, DemoQrController.class, DemoPaymentReader.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(SqlQrStore.class, () -> mock(SqlQrStore.class))
                .withBean(PaymentRepository.class, () -> mock(PaymentRepository.class))
                .withBean(MemberCardRepository.class, () -> mock(MemberCardRepository.class))
                .withBean(SportPackageRegistrationRepository.class, () -> mock(SportPackageRegistrationRepository.class))
                .withBean(PaymentVerificationService.class, () -> mock(PaymentVerificationService.class))
                .withPropertyValues("spring.profiles.active=" + profiles, "app.payment.simulator.enabled=" + enabled,
                        "app.payment.simulator.public-base-url=https://demo.example.test/", "app.payment.simulator.allowed-origins=https://demo.example.test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (Class<?> type : new Class<?>[]{DemoQrController.class, DemoQrService.class, DemoPaymentReader.class, DemoQrSettings.class}) {
                        assertThat(context.getBeansOfType(type).size()).isEqualTo(present ? 1 : 0);
                    }
                });
    }

    @Test
    void demoPaymentReaderRequiresPaymentVerificationServiceFailClosed() {
        assertThatThrownBy(() -> new DemoPaymentReader(
                mock(PaymentRepository.class),
                mock(MemberCardRepository.class),
                mock(SportPackageRegistrationRepository.class),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("PaymentVerificationService is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173/", "http://127.0.0.1:5173/", "http://evil.example/", "https://evil.example/", "https://demo.example.test/?next=evil", "https://demo.example.test/#token=evil", "https://user@demo.example.test/", "https://demo.example.test/a/../", "http://010.0.0.1:5173/"})
    void rejectsUntrustedOriginsRedirectMaterialAndUnreachableLocalhost(String base) {
        assertThatThrownBy(() -> new DemoQrSettings(base, "https://demo.example.test,http://localhost:5173,http://127.0.0.1:5173,http://010.0.0.1:5173", true, 300))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Configure an allowlisted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://192.168.1.50:5173/", "http://10.0.0.20:5173/base/", "http://172.16.0.4:5173/"})
    void privateLanHttpIsRejectedEvenWithTheRetiredOptIn(String base) {
        String origin = java.net.URI.create(base).getScheme() + "://" + java.net.URI.create(base).getAuthority();
        assertThatThrownBy(() -> new DemoQrSettings(base, origin, true, 300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoQrSettings(base, origin, false, 300)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 29, 601, 100000})
    void lifetimeIsBounded(long seconds) {
        assertThatThrownBy(() -> new DemoQrSettings("https://demo.example.test", "https://demo.example.test", false, seconds)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost/", "https://demo.localhost/", "https://127.0.0.2/", "https://0.0.0.0/", "https://[::1]/"})
    void evenAnExplicitAllowlistCannotEnableUnreachableLoopbackOrigins(String base) {
        assertThatThrownBy(() -> new DemoQrSettings(base, base.substring(0, base.length() - 1), false, 300))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
