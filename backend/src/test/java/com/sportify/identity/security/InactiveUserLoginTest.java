package com.sportify.identity.security;

import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An INACTIVE account cannot log in. Uses the same DaoAuthenticationProvider setup as ApplicationConfig,
 * with an in-memory user instead of the database.
 */
class InactiveUserLoginTest {

    private static final String PASSWORD = "Temp1234";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private DaoAuthenticationProvider provider;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        account = UserAccount.builder()
                .id(9L)
                .email("linh@sportify.demo")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.builder().code("MEMBER").build())
                .status("ACTIVE")
                .build();

        UserDetailsService userDetailsService = username -> new CustomUserDetails(account);
        provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
    }

    private Authentication loginToken() {
        return new UsernamePasswordAuthenticationToken("linh@sportify.demo", PASSWORD);
    }

    @Test
    void activeAccount_shouldAuthenticate() {
        Authentication result = provider.authenticate(loginToken());

        assertTrue(result.isAuthenticated());
    }

    @Test
    void inactiveAccount_shouldNotAuthenticateEvenWithTheRightPassword() {
        account.setStatus("INACTIVE");

        assertThrows(AccountStatusException.class, () -> provider.authenticate(loginToken()));
    }

    @Test
    void reactivatedAccount_shouldAuthenticateAgain() {
        account.setStatus("INACTIVE");
        assertThrows(AccountStatusException.class, () -> provider.authenticate(loginToken()));

        account.setStatus("ACTIVE");

        assertTrue(provider.authenticate(loginToken()).isAuthenticated());
    }

    @Test
    void inactiveAccount_shouldBeReportedAsDisabledByUserDetails() {
        account.setStatus("INACTIVE");
        CustomUserDetails details = new CustomUserDetails(account);

        assertFalse(details.isEnabled());
        assertFalse(details.isAccountNonLocked());
    }

    @Test
    void inactiveLoginFailure_shouldBeMappedToHttp403() {
        account.setStatus("INACTIVE");
        AccountStatusException failure = assertThrows(AccountStatusException.class,
                () -> provider.authenticate(loginToken()));

        var response = new GlobalExceptionHandler().handleDisabledException(failure);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Account is disabled", response.getBody().get("message"));
    }
}
