package com.sportify.identity.security;

import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1000L * 60 * 15); // 15 mins
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 1000L * 60 * 60 * 24 * 7); // 7 days

        UserAccount user = new UserAccount();
        user.setEmail("test@sportify.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("MEMBER");
        user.setRole(role);
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void generateToken_shouldBeValid() {
        String token = jwtService.generateToken(userDetails);
        assertTrue(jwtService.isTokenValid(token, userDetails));
        assertEquals("access", jwtService.extractTokenType(token));
        assertEquals("test@sportify.com", jwtService.extractUsername(token));
    }

    @Test
    void generateRefreshToken_shouldBeValidAndHaveRefreshType() {
        String token = jwtService.generateRefreshToken(userDetails);
        assertTrue(jwtService.isTokenValid(token, userDetails));
        assertEquals("refresh", jwtService.extractTokenType(token));
    }

    @Test
    void isTokenValid_shouldThrowExceptionIfInvalidSignature() {
        String token = jwtService.generateToken(userDetails) + "invalid";
        assertThrows(SignatureException.class, () -> jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_shouldBeFalseIfExpired() throws InterruptedException {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1L); // 1 ms
        String token = jwtService.generateToken(userDetails);
        Thread.sleep(10);
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwtService.isTokenValid(token, userDetails));
    }
}
