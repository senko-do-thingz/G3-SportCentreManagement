package com.sportify.identity.service.impl;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.dto.RefreshTokenRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditAction;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.core.common.CodeFormatter;
import java.time.Clock;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import com.sportify.identity.entity.MemberProfile;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final MemberProfileRepository memberProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

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
        String jwtToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public AuthResponse authenticate(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );
        } catch (org.springframework.security.authentication.BadCredentialsException | 
                 org.springframework.security.authentication.DisabledException | 
                 org.springframework.security.authentication.LockedException ex) {
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
                AuthenticationServiceImpl.log.warn("Failed to write LOGIN_FAILED audit log");
            }
            throw ex;
        }

        UserAccount user = repository.findByEmailIgnoreCase(email)
                .orElseThrow();

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String jwtToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        String userEmail = jwtService.extractUsername(token);
        if (userEmail != null) {
            UserAccount user = repository.findByEmailIgnoreCase(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            CustomUserDetails userDetails = new CustomUserDetails(user);
            if ("refresh".equals(jwtService.extractTokenType(token)) && jwtService.isTokenValid(token, userDetails)) {
                if (!"ACTIVE".equals(user.getStatus())) {
                    throw new org.springframework.security.authentication.DisabledException("Account is disabled");
                }
                String accessToken = jwtService.generateToken(userDetails);
                String newRefreshToken = jwtService.generateRefreshToken(userDetails);
                
                return AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(newRefreshToken)
                        .build();
            }
        }
        throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
    }
}
