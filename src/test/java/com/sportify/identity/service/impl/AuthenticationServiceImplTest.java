package com.sportify.identity.service.impl;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private com.sportify.identity.repository.MemberProfileRepository memberProfileRepository;
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
        registerRequest = new RegisterRequest("Test User", "test@sportify.com", "password", "123");
        authRequest = new AuthRequest("test@sportify.com", "password");
        
        role = new Role();
        role.setCode("MEMBER");
        
        userAccount = new UserAccount();
        userAccount.setId(1L);
        userAccount.setEmail("test@sportify.com");
        userAccount.setRole(role);
        userAccount.setStatus("ACTIVE");

    }

    @Test
    void register_shouldThrowIfEmailExists() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
    }

    @Test
    void register_shouldSucceedWithMemberRole() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findByCode("MEMBER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(userAccount);
        when(memberProfileRepository.getNextMemberCode()).thenReturn(1L);
        when(codeFormatter.formatMemberCode(1L)).thenReturn("MEM-0001");
        when(jwtService.generateToken(any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse res = authService.register(registerRequest);

        assertNotNull(res);
        assertEquals("access", res.getAccessToken());
        verify(userRepository, times(1)).save(any(UserAccount.class));
        verify(activityLogRepository, times(1)).save(any());
    }

    @Test
    void authenticate_shouldSucceed() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.of(userAccount));
        when(jwtService.generateToken(any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse res = authService.authenticate(authRequest);

        assertNotNull(res);
        assertEquals("access", res.getAccessToken());
        verify(authenticationManager, times(1)).authenticate(any());
        verify(activityLogRepository, times(1)).save(any());
    }

    @Test
    void authenticate_shouldThrowIfBadCredentials() {
        doThrow(new BadCredentialsException("Bad")).when(authenticationManager).authenticate(any());
        assertThrows(BadCredentialsException.class, () -> authService.authenticate(authRequest));
    }
}
