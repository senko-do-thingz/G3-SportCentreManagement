package com.sportify.demoqr;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.demoqr.config.DemoQrConfiguration;
import com.sportify.demoqr.controller.DemoQrAdvice;
import com.sportify.demoqr.controller.DemoQrController;
import com.sportify.demoqr.service.DemoPaymentReader;
import com.sportify.demoqr.service.DemoQrService;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Optional loopback-only browser fixture. Not packaged in the application or used for SQL integration claims. */
@Configuration(proxyBeanMethods = false)
@Profile("qr04-browser-fixture")
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@Import({DemoQrConfiguration.class, DemoQrController.class, DemoQrAdvice.class, DemoQrService.class,
        DemoPaymentReader.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, DemoQrBrowserFixture.FixtureEndpoints.class})
public class DemoQrBrowserFixture {
    private static final String PASSWORD = "Qr04IsolatedTest2026!";
    public static void main(String[] args) {
        var application = new SpringApplication(DemoQrBrowserFixture.class);
        application.setAdditionalProfiles("qr-demo", "qr04-browser-fixture");
        application.setDefaultProperties(Map.ofEntries(
                Map.entry("server.address", "127.0.0.1"), Map.entry("server.port", "8094"),
                Map.entry("spring.flyway.enabled", "false"), Map.entry("app.demo.qr.enabled", "true"),
                Map.entry("app.demo.qr.public-base-url", "https://demo.example.test/"),
                Map.entry("app.demo.qr.allowed-origins", "https://demo.example.test"),
                Map.entry("app.demo.qr.allow-lan-http", "false"), Map.entry("app.demo.qr.lifetime-seconds", "300"),
                Map.entry("app.cors.allowed-origins", "https://demo.example.test"),
                Map.entry("application.security.jwt.secret-key", "cXJvNC1icm93c2VyLWZpeHR1cmUtb25seS1ub3QtYS1yZWFsLXNlY3JldA=="),
                Map.entry("application.security.jwt.expiration", "900000"),
                Map.entry("application.security.jwt.refresh-token.expiration", "900000")));
        application.run(args);
    }

    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean BCryptPasswordEncoder fixtureEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService fixtureUsers(BCryptPasswordEncoder encoder) {
        var role = Role.builder().code("MEMBER").name("MEMBER").build();
        var owner = UserAccount.builder().id(2L).email("owner@qr04.example.test").fullName("QR04 Demo Owner")
                .status("ACTIVE").role(role).passwordHash(encoder.encode(PASSWORD)).build();
        var other = UserAccount.builder().id(3L).email("other@qr04.example.test").fullName("QR04 Other Owner")
                .status("ACTIVE").role(role).passwordHash(encoder.encode(PASSWORD)).build();
        return email -> {
            if (email.equals(owner.getEmail())) return new CustomUserDetails(owner);
            if (email.equals(other.getEmail())) return new CustomUserDetails(other);
            throw new UsernameNotFoundException("Fixture account unavailable");
        };
    }

    @Bean
    AuthenticationProvider fixtureAuthentication(UserDetailsService users, BCryptPasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    PaymentRepository fixturePayments() {
        var repository = mock(PaymentRepository.class);
        for (long id : new long[]{1, 2}) {
            var payment = Payment.builder().id(id).member(MemberProfile.builder().id(2L).build())
                    .amount(new BigDecimal(id == 1 ? "810000.00" : "510000.00")).paymentStatus(PaymentStatus.PENDING).build();
            when(repository.findById(id)).thenReturn(Optional.of(payment));
        }
        return repository;
    }

    @Bean MemberCardRepository fixtureCards() { return mock(MemberCardRepository.class); }
    @Bean SportPackageRegistrationRepository fixtureRegistrations() { return mock(SportPackageRegistrationRepository.class); }

    @RestController
    @Profile("qr04-browser-fixture")
    static class FixtureEndpoints {
        private final JwtService jwt;
        private final AuthenticationProvider authentication;
        private final DemoPaymentReader reader;
        FixtureEndpoints(JwtService jwt, AuthenticationProvider authentication, DemoPaymentReader reader) {
            this.jwt = jwt; this.authentication = authentication; this.reader = reader;
        }
        record Credentials(String email, String password) {
            @Override public String toString() { return "Credentials[REDACTED]"; }
        }
        @PostMapping("/api/v1/auth/login")
        Map<String, String> login(@RequestBody Credentials credentials) {
            var result = authentication.authenticate(new UsernamePasswordAuthenticationToken(credentials.email(), credentials.password()));
            var principal = (CustomUserDetails) result.getPrincipal();
            return Map.of("accessToken", jwt.generateToken(principal), "refreshToken", jwt.generateRefreshToken(principal));
        }
        @GetMapping("/api/v1/users/me")
        Map<String, Object> me(@AuthenticationPrincipal CustomUserDetails principal) {
            var account = principal.getUserAccount();
            return Map.of("id", account.getId(), "email", account.getEmail(), "fullName", account.getFullName(), "role", "MEMBER", "status", "ACTIVE");
        }
        @GetMapping("/api/v1/payments/{id}")
        PaymentResponse payment(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails principal) {
            if (id != 1 && id != 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            var snapshot = reader.read(id, principal.getUserAccount());
            return PaymentResponse.builder().id(id).amount(snapshot.amount()).code("QR04-TEST-" + id)
                    .status(PaymentStatus.PENDING).memberId(2L).itemDescription("Synthetic browser fixture").build();
        }
    }
}
