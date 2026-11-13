package com.sportify.identity.service;

import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.PasswordResetToken;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.PasswordResetTokenRepository;
import com.sportify.identity.repository.UserRefreshTokenRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Forgot Password rules: 5 wrong attempts, 5-minute expiry, 60-second resend cooldown, single-use authorization. */
class PasswordRecoveryRulesTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserRefreshTokenRepository sessions = mock(UserRefreshTokenRepository.class);
    private final GmailOtpSender mail = mock(GmailOtpSender.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AtomicReference<PasswordResetToken> saved = new AtomicReference<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-13T10:00:00Z"));
    private UserAccount user;
    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        user = UserAccount.builder().id(7L).email("coach@example.com").passwordHash(encoder.encode("OldPassword1")).build();
        when(users.lockByEmail(anyString())).thenReturn(Optional.of(user));
        when(tokens.lockByUserId(7L)).thenAnswer(call -> saved.get() == null ? List.of() : List.of(saved.get()));
        when(tokens.save(any())).thenAnswer(call -> {
            PasswordResetToken token = call.getArgument(0);
            if (token.getCreatedAt() == null) ReflectionTestUtils.setField(token, "createdAt", LocalDateTime.now(clock));
            saved.set(token);
            return token;
        });
        when(tokens.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service = new PasswordRecoveryService(users, tokens, sessions, encoder, mail, mock(AuditService.class), clock);
        ReflectionTestUtils.setField(service, "secret", "test-only-secret-with-adequate-entropy-123456");
    }

    private String requestOtp() {
        service.request(user.getEmail());
        var otp = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(user.getEmail()), otp.capture());
        return otp.getValue();
    }

    @Test
    void fiveWrongAttemptsAreStoredAndThenEvenTheRightCodeIsRefused() {
        String otp = requestOtp();
        String wrong = otp.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.verify(user.getEmail(), wrong)).isInstanceOf(ConflictException.class);

        assertThat(saved.get().getFailedAttempts()).isEqualTo(5);
        assertThatThrownBy(() -> service.verify(user.getEmail(), otp)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aCodeExpiresAfterFiveMinutes() {
        String otp = requestOtp();
        clock.advanceSeconds(5 * 60 + 1);

        assertThatThrownBy(() -> service.verify(user.getEmail(), otp)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aSecondRequestWithinSixtySecondsSendsNoNewCode() {
        requestOtp();
        clock.advanceSeconds(30);
        service.request(user.getEmail());

        verify(mail, times(1)).send(anyString(), anyString());
    }

    @Test
    void theResetAuthorizationWorksOnceAndRevokesSessions() {
        String authorization = service.verify(user.getEmail(), requestOtp());
        when(tokens.lockByResetTokenHash(JwtService.hashToken(authorization)))
                .thenAnswer(call -> saved.get().getResetTokenHash() == null ? Optional.empty() : Optional.of(saved.get()));

        service.reset(authorization, "NewPassword2", "NewPassword2");

        verify(sessions).revokeAllForUser(eq(7L), any());
        assertThatThrownBy(() -> service.reset(authorization, "NewPassword3", "NewPassword3")).isInstanceOf(ConflictException.class);
        assertThat(encoder.matches("NewPassword2", user.getPasswordHash())).isTrue();
    }

    @Test
    void anUnknownEmailGetsNoCodeAndTheSameAnswerShape() {
        when(users.lockByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.request("nobody@example.com");

        verifyNoInteractions(mail);
        assertThatThrownBy(() -> service.verify("nobody@example.com", "123456")).isInstanceOf(ConflictException.class)
                .hasMessage("Invalid or expired verification code");
    }

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
