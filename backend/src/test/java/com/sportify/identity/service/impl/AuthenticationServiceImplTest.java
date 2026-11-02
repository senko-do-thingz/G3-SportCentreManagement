package com.sportify.identity.service.impl;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.dto.RefreshTokenRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserSession;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditAction;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.repository.UserSessionRepository;
import com.sportify.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private com.sportify.identity.repository.MemberProfileRepository memberProfileRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private com.sportify.core.common.CodeFormatter codeFormatter;
    @org.mockito.Spy
    private java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-05T00:00:00Z"), java.time.ZoneId.of("UTC"));

    @InjectMocks
    private AuthenticationServiceImpl authService;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private UserAccount userAccount;
    private Role role;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiration", 604800000L);
        registerRequest = new RegisterRequest("Test User", "test@sportify.com", "Password123", "0123456789");
        authRequest = new AuthRequest("test@sportify.com", "password");

        role = new Role();
        role.setId(1L);
        role.setCode("MEMBER");

        userAccount = UserAccount.builder()
                .id(1L)
                .email("test@sportify.com")
                .passwordHash("hashed")
                .fullName("Test User")
                .phone("0123456789")
                .role(role)
                .status("ACTIVE")
                .build();
    }

    @ParameterizedTest
    @ValueSource(classes = {BadCredentialsException.class, DisabledException.class, LockedException.class})
    void authenticate_shouldRecordAuditLogOnAuthException(Class<? extends RuntimeException> exClass) {
        when(authenticationManager.authenticate(any()))
                .thenThrow(exClass);

        assertThrows(exClass, () -> authService.authenticate(authRequest));

        org.mockito.ArgumentCaptor<AuditEvent> captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent event = captor.getValue();
        assertEquals(AuditAction.LOGIN_FAILED, event.getAction());
        assertEquals("USER_ACCOUNT", event.getEntityType());
        assertEquals("Login failed", event.getSummary());
        assertEquals("test@sportify.com", event.getDetails().get("email"));
        assertTrue(event.isRequiresNew());
    }

    @Test
    void authenticate_shouldNotFailIfAuditServiceThrowsException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        doThrow(new RuntimeException("DB error")).when(auditService).record(any());

        assertThrows(BadCredentialsException.class, () -> authService.authenticate(authRequest));
    }

    @Test
    void register_shouldThrowConflictExceptionIfEmailExists() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(true);

        assertThrows(com.sportify.core.exception.ConflictException.class, () -> authService.register(registerRequest));
        verify(auditService, never()).record(any());
    }

    @Test
    void register_shouldThrowConflictExceptionOnDataIntegrityViolation() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findByCode("MEMBER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate"));

        assertThrows(com.sportify.core.exception.ConflictException.class, () -> authService.register(registerRequest));
        verify(auditService, never()).record(any());
    }

    @Test
    void register_shouldSucceed() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findByCode("MEMBER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(memberProfileRepository.getNextMemberCode()).thenReturn(1L);
        when(codeFormatter.formatMemberCode(1L)).thenReturn("MEM-0001");

        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            assertEquals("test@sportify.com", saved.getEmail());
            saved.setId(1L);
            return saved;
        });

        when(jwtService.generateToken(any(org.springframework.security.core.userdetails.UserDetails.class), anyString())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(org.springframework.security.core.userdetails.UserDetails.class), anyString())).thenReturn("refresh");

        authService.register(registerRequest);
        verify(userRepository).existsByEmailIgnoreCase("test@sportify.com");
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    void authenticate_shouldNormalizeEmail() {
        authRequest.setEmail("  Test@Sportify.COM ");
        when(userRepository.findByEmailIgnoreCase("test@sportify.com")).thenReturn(Optional.of(userAccount));
        when(jwtService.generateToken(any(org.springframework.security.core.userdetails.UserDetails.class), anyString())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(org.springframework.security.core.userdetails.UserDetails.class), anyString())).thenReturn("refresh");

        authService.authenticate(authRequest);

        org.mockito.ArgumentCaptor<org.springframework.security.authentication.UsernamePasswordAuthenticationToken> captor = org.mockito.ArgumentCaptor.forClass(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertEquals("test@sportify.com", captor.getValue().getPrincipal());
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    void refreshToken_shouldThrowResourceNotFoundIfUserMissing() {
        com.sportify.identity.dto.RefreshTokenRequest req = new com.sportify.identity.dto.RefreshTokenRequest();
        req.setRefreshToken("token");
        when(jwtService.extractUsername("token")).thenReturn("missing@sportify.com");
        when(userRepository.findByEmailIgnoreCase("missing@sportify.com")).thenReturn(Optional.empty());

        assertThrows(com.sportify.core.exception.ResourceNotFoundException.class, () -> authService.refreshToken(req));
        verify(auditService, never()).record(any());
    }

    @Test
    void refreshToken_shouldSucceed() {
        com.sportify.identity.dto.RefreshTokenRequest req = new com.sportify.identity.dto.RefreshTokenRequest();
        req.setRefreshToken("valid_token");
        when(jwtService.extractUsername("valid_token")).thenReturn("test@sportify.com");
        when(userRepository.findByEmailIgnoreCase("test@sportify.com")).thenReturn(Optional.of(userAccount));
        when(jwtService.extractTokenType("valid_token")).thenReturn("refresh");
        when(jwtService.isTokenValid(eq("valid_token"), any())).thenReturn(true);
        when(jwtService.extractSessionId("valid_token")).thenReturn("sid-123");

        UserSession activeSession = UserSession.builder()
                .sessionId("sid-123")
                .userAccount(userAccount)
                .refreshTokenHash(JwtService.hashToken("valid_token"))
                .createdAt(LocalDateTime.now(clock))
                .lastAccessedAt(LocalDateTime.now(clock))
                .expiresAt(LocalDateTime.now(clock).plusDays(7))
                .build();
        when(userSessionRepository.findForUpdateBySessionId("sid-123")).thenReturn(Optional.of(activeSession));
        when(jwtService.generateToken(any(org.springframework.security.core.userdetails.UserDetails.class), eq("sid-123"))).thenReturn("new_access");
        when(jwtService.generateRefreshToken(any(org.springframework.security.core.userdetails.UserDetails.class), eq("sid-123"))).thenReturn("new_refresh");

        AuthResponse res = authService.refreshToken(req);

        assertNotNull(res);
        assertEquals("new_access", res.getAccessToken());
        assertEquals("new_refresh", res.getRefreshToken());
        verify(userSessionRepository).save(activeSession);
        verify(auditService, never()).record(any());
    }

    @Test
    void refreshToken_shouldThrowBadCredentialsIfLegacyTokenWithoutSessionId() {
        com.sportify.identity.dto.RefreshTokenRequest req = new com.sportify.identity.dto.RefreshTokenRequest();
        req.setRefreshToken("legacy_token");
        when(jwtService.extractUsername("legacy_token")).thenReturn("test@sportify.com");
        when(userRepository.findByEmailIgnoreCase("test@sportify.com")).thenReturn(Optional.of(userAccount));
        when(jwtService.extractTokenType("legacy_token")).thenReturn("refresh");
        when(jwtService.isTokenValid(eq("legacy_token"), any())).thenReturn(true);
        when(jwtService.extractSessionId("legacy_token")).thenReturn(null);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("session"));
    }

    @Test
    void refreshToken_shouldRevokeSessionAndThrowOnReuseDetection() {
        com.sportify.identity.dto.RefreshTokenRequest req = new com.sportify.identity.dto.RefreshTokenRequest();
        req.setRefreshToken("replayed_token");
        when(jwtService.extractUsername("replayed_token")).thenReturn("test@sportify.com");
        when(userRepository.findByEmailIgnoreCase("test@sportify.com")).thenReturn(Optional.of(userAccount));
        when(jwtService.extractTokenType("replayed_token")).thenReturn("refresh");
        when(jwtService.isTokenValid(eq("replayed_token"), any())).thenReturn(true);
        when(jwtService.extractSessionId("replayed_token")).thenReturn("sid-replay");

        UserSession session = UserSession.builder()
                .sessionId("sid-replay")
                .userAccount(userAccount)
                .refreshTokenHash(JwtService.hashToken("different_newer_token"))
                .createdAt(LocalDateTime.now(clock))
                .lastAccessedAt(LocalDateTime.now(clock))
                .expiresAt(LocalDateTime.now(clock).plusDays(7))
                .build();
        when(userSessionRepository.findForUpdateBySessionId("sid-replay")).thenReturn(Optional.of(session));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("reuse detected"));
        assertNotNull(session.getRevokedAt());
        assertEquals("REFRESH_TOKEN_REUSE_DETECTED", session.getRevokedReason());
        verify(userSessionRepository).save(session);
    }

    @Test
    void logout_shouldInvokeRepositoryRevokeSession() {
        authService.logout("sid-logout-123");
        verify(userSessionRepository).revokeSession(eq("sid-logout-123"), any(LocalDateTime.class), eq("USER_LOGOUT"));
    }

    @Test
    void register_shouldThrowIllegalStateExceptionIfRoleMissing() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findByCode("MEMBER")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> authService.register(registerRequest));
    }
}
