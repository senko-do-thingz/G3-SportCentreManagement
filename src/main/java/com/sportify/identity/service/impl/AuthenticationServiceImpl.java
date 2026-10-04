package com.sportify.identity.service.impl;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.dto.RefreshTokenRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.repository.ActivityLogRepository;
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

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final ActivityLogRepository activityLogRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (repository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        UserAccount user = UserAccount.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(memberRole)
                .status("ACTIVE")
                .build();

        UserAccount savedUser = repository.save(user);

        if ("MEMBER".equals(memberRole.getCode())) {
            Long nextSeq = memberProfileRepository.getNextMemberCode();
            String memberCode = codeFormatter.formatMemberCode(nextSeq);
            com.sportify.identity.entity.MemberProfile profile = com.sportify.identity.entity.MemberProfile.builder()
                    .userAccount(savedUser)
                    .memberCode(memberCode)
                    .currentLevel("BEGINNER")
                    .joinedOn(java.time.LocalDate.now(clock))
                    .build();
            memberProfileRepository.save(profile);
        }

        logActivity(savedUser, "REGISTER", "USER_ACCOUNT", savedUser.getId(), "User registered");

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
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserAccount user = repository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow();

        logActivity(user, "LOGIN", "USER_ACCOUNT", user.getId(), "User logged in");

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
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            CustomUserDetails userDetails = new CustomUserDetails(user);
            if ("refresh".equals(jwtService.extractTokenType(token)) && jwtService.isTokenValid(token, userDetails)) {
                if (!"ACTIVE".equals(user.getStatus())) {
                    throw new org.springframework.security.authentication.DisabledException("Account is disabled");
                }
                String accessToken = jwtService.generateToken(userDetails);
                String newRefreshToken = jwtService.generateRefreshToken(userDetails);
                
                logActivity(user, "REFRESH_TOKEN", "USER_ACCOUNT", user.getId(), "Token refreshed");
                
                return AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(newRefreshToken)
                        .build();
            }
        }
        throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
    }

    private void logActivity(UserAccount actor, String action, String entityType, Long entityId, String summary) {
        ActivityLog log = new ActivityLog();
        log.setActor(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setSummary(summary);
        activityLogRepository.save(log);
    }
}
