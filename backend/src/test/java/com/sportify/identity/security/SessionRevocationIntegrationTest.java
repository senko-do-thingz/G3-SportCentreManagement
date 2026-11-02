package com.sportify.identity.security;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RefreshTokenRequest;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserSession;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.repository.UserSessionRepository;
import com.sportify.identity.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionRevocationIntegrationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private com.sportify.core.audit.AuditService auditService;

    @Mock
    private com.sportify.identity.repository.MemberProfileRepository memberProfileRepository;

    @Mock
    private com.sportify.core.common.CodeFormatter codeFormatter;

    private JwtService jwtService;
    private Clock fixedClock;
    private com.sportify.identity.service.impl.AuthenticationServiceImpl authService;

    private UserAccount testUser;
    private Role memberRole;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("UTC"));

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L); // 15 mins
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L); // 7 days
        jwtService.validateSecretKey();

        authService = new com.sportify.identity.service.impl.AuthenticationServiceImpl(
                userRepository,
                roleRepository,
                auditService,
                memberProfileRepository,
                userSessionRepository,
                passwordEncoder,
                jwtService,
                authenticationManager,
                codeFormatter,
                fixedClock
        );
        ReflectionTestUtils.setField(authService, "refreshExpiration", 604_800_000L);

        memberRole = new Role();
        memberRole.setId(1L);
        memberRole.setCode("MEMBER");

        testUser = UserAccount.builder()
                .id(42L)
                .email("member@sportify.com")
                .passwordHash("encoded_password")
                .fullName("Sportify Member")
                .phone("0912345678")
                .role(memberRole)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("TC-D04-01: Login creates an active server session and binds access/refresh tokens to session ID")
    void login_createsServerSessionAndBindsSessionId() {
        when(userRepository.findByEmailIgnoreCase("member@sportify.com")).thenReturn(Optional.of(testUser));

        AuthResponse response = authService.authenticate(new AuthRequest("member@sportify.com", "Password123"));

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());

        String accessSid = jwtService.extractSessionId(response.getAccessToken());
        String refreshSid = jwtService.extractSessionId(response.getRefreshToken());
        assertNotNull(accessSid);
        assertNotNull(refreshSid);
        assertEquals(accessSid, refreshSid, "Access and refresh tokens must share the same session ID");

        verify(userSessionRepository).save(argThat(session ->
                session.getSessionId().equals(accessSid) &&
                session.getUserAccount().equals(testUser) &&
                session.getRevokedAt() == null &&
                session.getRefreshTokenHash().equals(JwtService.hashToken(response.getRefreshToken()))
        ));
    }

    @Test
    @DisplayName("TC-D04-02: Logout idempotently revokes the server session")
    void logout_revokesServerSession() {
        authService.logout("sid-to-revoke-123");

        verify(userSessionRepository).revokeSession(
                eq("sid-to-revoke-123"),
                eq(LocalDateTime.now(fixedClock)),
                eq("USER_LOGOUT")
        );

        // Duplicate logout call is safe and idempotent
        assertDoesNotThrow(() -> authService.logout("sid-to-revoke-123"));
        assertDoesNotThrow(() -> authService.logout(null));
        assertDoesNotThrow(() -> authService.logout("   "));
    }

    @Test
    @DisplayName("TC-D04-03: Refresh rotation updates refresh token hash in session atomically")
    void refreshToken_rotatesTokenAndUpdatesSessionHash() {
        String sessionId = "active-sid-001";
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        String initialRefreshToken = jwtService.generateRefreshToken(userDetails, sessionId);

        when(userRepository.findByEmailIgnoreCase("member@sportify.com")).thenReturn(Optional.of(testUser));

        UserSession activeSession = UserSession.builder()
                .sessionId(sessionId)
                .userAccount(testUser)
                .refreshTokenHash(JwtService.hashToken(initialRefreshToken))
                .createdAt(LocalDateTime.now(fixedClock))
                .lastAccessedAt(LocalDateTime.now(fixedClock))
                .expiresAt(LocalDateTime.now(fixedClock).plusDays(7))
                .build();
        when(userSessionRepository.findForUpdateBySessionId(sessionId)).thenReturn(Optional.of(activeSession));

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(initialRefreshToken);

        AuthResponse refreshed = authService.refreshToken(req);

        assertNotNull(refreshed);
        assertNotEquals(initialRefreshToken, refreshed.getRefreshToken(), "Refresh token must be rotated");
        assertEquals(sessionId, jwtService.extractSessionId(refreshed.getAccessToken()));
        assertEquals(sessionId, jwtService.extractSessionId(refreshed.getRefreshToken()));

        // Session refresh_token_hash must now match the new rotated token
        assertEquals(JwtService.hashToken(refreshed.getRefreshToken()), activeSession.getRefreshTokenHash());
        verify(userSessionRepository).save(activeSession);
    }

    @Test
    @DisplayName("TC-D04-04: Replay of an old/already-rotated refresh token triggers reuse detection and revokes the session family")
    void refreshToken_reuseDetectionRevokesSessionFamily() {
        String sessionId = "reuse-sid-002";
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        String replayedOldToken = jwtService.generateRefreshToken(userDetails, sessionId);
        String currentRotatedToken = jwtService.generateRefreshToken(userDetails, sessionId);

        when(userRepository.findByEmailIgnoreCase("member@sportify.com")).thenReturn(Optional.of(testUser));

        // The session already progressed to currentRotatedToken
        UserSession progressedSession = UserSession.builder()
                .sessionId(sessionId)
                .userAccount(testUser)
                .refreshTokenHash(JwtService.hashToken(currentRotatedToken))
                .createdAt(LocalDateTime.now(fixedClock))
                .lastAccessedAt(LocalDateTime.now(fixedClock))
                .expiresAt(LocalDateTime.now(fixedClock).plusDays(7))
                .build();
        when(userSessionRepository.findForUpdateBySessionId(sessionId)).thenReturn(Optional.of(progressedSession));

        // Attacker replays replayedOldToken
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(replayedOldToken);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("reuse detected"), "Exception must state reuse detected");

        // The entire session must be immediately revoked
        assertNotNull(progressedSession.getRevokedAt());
        assertEquals("REFRESH_TOKEN_REUSE_DETECTED", progressedSession.getRevokedReason());
        verify(userSessionRepository).save(progressedSession);
    }

    @Test
    @DisplayName("TC-D04-05: Refresh fails when session is revoked or expired")
    void refreshToken_failsWhenSessionRevoked() {
        String sessionId = "revoked-sid-003";
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        String refreshToken = jwtService.generateRefreshToken(userDetails, sessionId);

        when(userRepository.findByEmailIgnoreCase("member@sportify.com")).thenReturn(Optional.of(testUser));

        UserSession revokedSession = UserSession.builder()
                .sessionId(sessionId)
                .userAccount(testUser)
                .refreshTokenHash(JwtService.hashToken(refreshToken))
                .createdAt(LocalDateTime.now(fixedClock).minusHours(1))
                .lastAccessedAt(LocalDateTime.now(fixedClock).minusHours(1))
                .expiresAt(LocalDateTime.now(fixedClock).plusDays(6))
                .revokedAt(LocalDateTime.now(fixedClock).minusMinutes(30))
                .revokedReason("USER_LOGOUT")
                .build();
        when(userSessionRepository.findForUpdateBySessionId(sessionId)).thenReturn(Optional.of(revokedSession));

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshToken);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("revoked or expired"));
    }

    @Test
    @DisplayName("TC-D04-06: Legacy refresh token without session ID is rejected (fail closed cutover)")
    void refreshToken_rejectsLegacyTokenWithoutSessionId() {
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        // Generate legacy token without sid claim
        String legacyToken = jwtService.generateRefreshToken(userDetails, null);
        assertNull(jwtService.extractSessionId(legacyToken));

        when(userRepository.findByEmailIgnoreCase("member@sportify.com")).thenReturn(Optional.of(testUser));

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(legacyToken);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("session"));
    }

    @Test
    @DisplayName("TC-D04-07: Deactivated/disabled user account cannot refresh even with valid active session")
    void refreshToken_rejectsDisabledUserAccount() {
        testUser.setStatus("INACTIVE");
        String sessionId = "disabled-sid-004";
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        String token = jwtService.generateRefreshToken(userDetails, sessionId);

        when(userRepository.findByEmailIgnoreCase("member@sportify.com")).thenReturn(Optional.of(testUser));

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(token);

        assertThrows(DisabledException.class, () -> authService.refreshToken(req));
        verify(userSessionRepository, never()).findForUpdateBySessionId(any());
    }
}
