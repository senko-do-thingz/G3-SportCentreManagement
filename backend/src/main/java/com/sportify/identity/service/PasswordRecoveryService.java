package com.sportify.identity.service;

import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.PasswordResetToken;
import com.sportify.identity.repository.PasswordResetTokenRepository;
import com.sportify.identity.repository.UserRefreshTokenRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.core.audit.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final UserRefreshTokenRepository sessions;
    private final PasswordEncoder encoder;
    private final GmailOtpSender mail;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final java.util.concurrent.ConcurrentHashMap<String, LocalDateTime> recentRequests = new java.util.concurrent.ConcurrentHashMap<>();
    @Value("${PASSWORD_RESET_OTP_SECRET:}") private String secret;

    public PasswordRecoveryService(UserRepository users, PasswordResetTokenRepository tokens,
            UserRefreshTokenRepository sessions, PasswordEncoder encoder, GmailOtpSender mail,
            AuditService audit, Clock clock) {
        this.users = users; this.tokens = tokens; this.sessions = sessions;
        this.encoder = encoder; this.mail = mail; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        requireSecret();
        if (email == null || email.isBlank()) throw new ConflictException("A valid email is required");
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        LocalDateTime now = LocalDateTime.now(clock);
        if (recentRequests.size() > 10000) recentRequests.clear();
        var lastRequest = recentRequests.putIfAbsent(normalized, now);
        if (lastRequest != null) {
            if (lastRequest.isAfter(now.minusSeconds(60))) return;
            recentRequests.replace(normalized, lastRequest, now);
        }
        var account = users.lockByEmail(email.trim());
        if (account.isEmpty()) return;
        var user = account.get();
        var prior = tokens.lockByUserId(user.getId());
        if (!prior.isEmpty() && prior.get(0).getCreatedAt() != null
                && prior.get(0).getCreatedAt().isAfter(now.minusSeconds(60))) return;
        for (var token : prior) { token.setUsedAt(now); token.setResetTokenHash(null); }
        String otp = String.format("%06d", random.nextInt(1_000_000));
        tokens.save(PasswordResetToken.builder().userAccount(user)
                .tokenHash(hmac(user.getEmail().toLowerCase() + ":" + otp))
                .expiresAt(now.plusMinutes(5)).failedAttempts(0).build());
        mail.send(user.getEmail(), otp);
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public String verify(String email, String otp) {
        requireSecret();
        if (otp == null || !otp.matches("[0-9]{6}")) throw new ConflictException("Invalid or expired verification code");
        var account = users.lockByEmail(email == null ? "" : email.trim());
        if (account.isEmpty()) throw new ConflictException("Invalid or expired verification code");
        var rows = tokens.lockByUserId(account.get().getId());
        if (rows.isEmpty()) throw new ConflictException("Invalid or expired verification code");
        var row = rows.get(0);
        LocalDateTime now = LocalDateTime.now(clock);
        if (row.getUsedAt() != null || !row.getExpiresAt().isAfter(now) || row.getFailedAttempts() >= 5)
            throw new ConflictException("Invalid or expired verification code");
        if (!MessageDigest.isEqual(row.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hmac(account.get().getEmail().toLowerCase() + ":" + otp).getBytes(StandardCharsets.US_ASCII))) {
            row.setFailedAttempts(row.getFailedAttempts() + 1);
            tokens.save(row);
            throw new ConflictException("Invalid or expired verification code");
        }
        String authorization = UUID.randomUUID() + "-" + UUID.randomUUID();
        row.setUsedAt(now);
        row.setResetTokenHash(sha256(authorization));
        tokens.save(row);
        return authorization;
    }

    @Transactional
    public void reset(String authorization, String password, String confirmation) {
        if (authorization == null || authorization.isBlank()) throw new ConflictException("Reset authorization is invalid");
        if (password == null || !password.equals(confirmation) || password.length() < 8 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s).*[\\p{L}].*") || !password.matches("(?s).*\\d.*"))
            throw new ConflictException("New password must meet password rules and match confirmation");
        var row = tokens.lockByResetTokenHash(sha256(authorization))
                .orElseThrow(() -> new ConflictException("Reset authorization is invalid"));
        if (row.getExpiresAt().isBefore(LocalDateTime.now(clock))) throw new ConflictException("Reset authorization has expired");
        var user = row.getUserAccount();
        if (encoder.matches(password, user.getPasswordHash())) throw new ConflictException("New password must differ from current password");
        user.setPasswordHash(encoder.encode(password));
        users.saveAndFlush(user);
        row.setResetTokenHash(null);
        tokens.saveAndFlush(row);
        sessions.revokeAllForUser(user.getId(), LocalDateTime.now(clock));
        audit.record(AuditEvent.builder().actor(user).action(AuditAction.PASSWORD_CHANGED)
                .entityType("USER_ACCOUNT").entityId(user.getId()).summary("Password reset by email verification").build());
    }

    private void requireSecret() { if (secret == null || secret.isBlank()) throw new IllegalStateException("Password recovery is not configured"); }
    private String hmac(String value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Password recovery unavailable"); }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Password recovery unavailable"); }
    }
}
