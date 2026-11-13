package com.sportify.identity.service;

import com.sportify.core.audit.AuditService;
import com.sportify.identity.entity.PasswordResetToken;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    @Test
    void otpIsOneTimeAndPasswordChangeRevokesSessions() {
        var users = mock(UserRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var sessions = mock(UserRefreshTokenRepository.class);
        var mail = mock(GmailOtpSender.class);
        var audit = mock(AuditService.class);
        var encoder = new BCryptPasswordEncoder();
        var user = UserAccount.builder().id(7L).email("member@example.com")
                .passwordHash(encoder.encode("OldPassword1")).build();
        var saved = new AtomicReference<PasswordResetToken>();
        when(users.lockByEmail(anyString())).thenReturn(Optional.of(user));
        when(tokens.lockByUserId(7L)).thenAnswer(call -> saved.get() == null ? List.of() : List.of(saved.get()));
        when(tokens.save(any())).thenAnswer(call -> { saved.set(call.getArgument(0)); return saved.get(); });
        var service = new PasswordRecoveryService(users, tokens, sessions, encoder, mail, audit,
                Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "secret", "test-only-secret-with-adequate-entropy-123456");
        service.request(user.getEmail());
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(user.getEmail()), otp.capture());
        assertThat(otp.getValue()).matches("[0-9]{6}");
        assertThat(saved.get().getTokenHash()).doesNotContain(otp.getValue());
        assertThatThrownBy(() -> service.verify(user.getEmail(), "abcdef")).isInstanceOf(RuntimeException.class);
        String authorization = service.verify(user.getEmail(), otp.getValue());
        assertThatThrownBy(() -> service.verify(user.getEmail(), otp.getValue())).isInstanceOf(RuntimeException.class);
        when(tokens.lockByResetTokenHash(JwtService.hashToken(authorization))).thenReturn(Optional.of(saved.get()));
        service.reset(authorization, "NewPassword2", "NewPassword2");
        assertThat(encoder.matches("NewPassword2", user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("OldPassword1", user.getPasswordHash())).isFalse();
        verify(sessions).revokeAllForUser(eq(7L), any());
        assertThat(saved.get().getResetTokenHash()).isNull();
    }
}
