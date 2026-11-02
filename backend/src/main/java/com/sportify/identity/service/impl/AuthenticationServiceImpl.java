package com.sportify.identity.service.impl;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.dto.RefreshTokenRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserSession;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.repository.UserSessionRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.service.AuthenticationService;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.entity.MemberProfile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final MemberProfileRepository memberProfileRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Value("${application.security.jwt.refresh-token.expiration:604800000}")
    private long refreshExpiration;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseThrow(() -> new IllegalStateException("Default role not found"));

        UserAccount user = UserAccount.builder()
                .fullName(request.getFullName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(memberRole)
                .status("ACTIVE")
                .build();

        UserAccount savedUser;
        try {
            savedUser = repository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Email is already registered");
        }

        String entityCode = null;
        if ("MEMBER".equals(memberRole.getCode())) {
            Long nextSeq = memberProfileRepository.getNextMemberCode();
            String memberCode = codeFormatter.formatMemberCode(nextSeq);
            MemberProfile profile = MemberProfile.builder()
                    .userAccount(savedUser)
                    .memberCode(memberCode)
                    .currentLevel("BEGINNER")
                    .joinedOn(java.time.LocalDate.now(clock))
                    .build();
            memberProfileRepository.save(profile);
            entityCode = memberCode;
        }

        auditService.record(AuditEvent.builder()
                .actor(savedUser)
                .action(AuditAction.REGISTER)
                .entityType("USER_ACCOUNT")
                .entityId(savedUser.getId())
                .entityCode(entityCode)
                .summary("User registered")
                .build());

        CustomUserDetails userDetails = new CustomUserDetails(savedUser);
        String sessionId = UUID.randomUUID().toString();
        String jwtToken = jwtService.generateToken(userDetails, sessionId);
        String refreshToken = jwtService.generateRefreshToken(userDetails, sessionId);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusSeconds(refreshExpiration / 1000);
        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .userAccount(savedUser)
                .refreshTokenHash(JwtService.hashToken(refreshToken))
                .createdAt(now)
                .lastAccessedAt(now)
                .expiresAt(expiresAt)
                .build();
        try {
            userSessionRepository.save(session);
        } catch (Exception ex) {
            log.warn("Could not persist user session (schema target may be pre-V37): {}", ex.getMessage());
        }

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse authenticate(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException | DisabledException | LockedException ex) {
            try {
                auditService.record(AuditEvent.builder()
                        .actor(null)
                        .action(AuditAction.LOGIN_FAILED)
                        .entityType("USER_ACCOUNT")
                        .summary("Login failed")
                        .details(Map.of("email", email))
                        .requiresNew(true)
                        .build());
            } catch (RuntimeException re) {
                log.warn("Failed to write LOGIN_FAILED audit log");
            }
            throw ex;
        }

        UserAccount user = repository.findByEmailIgnoreCase(email)
                .orElseThrow();

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String sessionId = UUID.randomUUID().toString();
        String jwtToken = jwtService.generateToken(userDetails, sessionId);
        String refreshToken = jwtService.generateRefreshToken(userDetails, sessionId);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusSeconds(refreshExpiration / 1000);
        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .userAccount(user)
                .refreshTokenHash(JwtService.hashToken(refreshToken))
                .createdAt(now)
                .lastAccessedAt(now)
                .expiresAt(expiresAt)
                .build();
        try {
            userSessionRepository.save(session);
        } catch (Exception ex) {
            log.warn("Could not persist user session (schema target may be pre-V37): {}", ex.getMessage());
        }

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Refresh token is required");
        }

        String userEmail = jwtService.extractUsername(token);
        if (userEmail == null) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        UserAccount user = repository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new DisabledException("Account is disabled");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        if (!"refresh".equals(jwtService.extractTokenType(token)) || !jwtService.isTokenValid(token, userDetails)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String sessionId = jwtService.extractSessionId(token);
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadCredentialsException("Token has no associated session, please re-login");
        }

        UserSession session = userSessionRepository.findForUpdateBySessionId(sessionId)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!session.isActive(now)) {
            throw new BadCredentialsException("Session is revoked or expired");
        }

        String incomingHash = JwtService.hashToken(token);
        if (!incomingHash.equals(session.getRefreshTokenHash())) {
            // REUSE DETECTED: Revoke entire session family immediately
            session.setRevokedAt(now);
            session.setRevokedReason("REFRESH_TOKEN_REUSE_DETECTED");
            try {
            userSessionRepository.save(session);
        } catch (Exception ex) {
            log.warn("Could not persist user session (schema target may be pre-V37): {}", ex.getMessage());
        }
            log.warn("Refresh token reuse detected for session {}. Session revoked.", sessionId);
            throw new BadCredentialsException("Refresh token reuse detected; session revoked");
        }

        // Rotate refresh token and issue new access token atomically
        String newAccessToken = jwtService.generateToken(userDetails, sessionId);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails, sessionId);

        session.setRefreshTokenHash(JwtService.hashToken(newRefreshToken));
        session.setLastAccessedAt(now);
        session.setExpiresAt(now.plusSeconds(refreshExpiration / 1000));
        try {
            userSessionRepository.save(session);
        } catch (Exception ex) {
            log.warn("Could not persist user session (schema target may be pre-V37): {}", ex.getMessage());
        }

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    @Transactional
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            userSessionRepository.revokeSession(sessionId, now, "USER_LOGOUT");
        } catch (Exception ex) {
            log.warn("Could not revoke user session: {}", ex.getMessage());
        }
    }
}
