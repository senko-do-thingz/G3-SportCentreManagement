package com.sportify.payment.service;

import com.sportify.payment.config.VietQrGatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class VietQrCallbackTokenService {

    private final VietQrGatewayProperties properties;

    public String issueToken(String authorizationHeader) {
        if (!matchesBasicCredentials(authorizationHeader)) return null;
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(properties.getCallbackTokenTtlSeconds());
        return Jwts.builder()
                .setSubject("vietqr-callback")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expires))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValidBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return false;
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) return false;
        try {
            var claims = Jwts.parserBuilder().setSigningKey(signingKey()).build().parseClaimsJws(token).getBody();
            return "vietqr-callback".equals(claims.getSubject());
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean matchesBasicCredentials(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) return false;
        String username = properties.getCallbackUsername();
        String password = properties.getCallbackPassword();
        String secret = properties.getCallbackJwtSecret();
        if (username == null || username.isBlank() || password == null || password.isBlank()
                || secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) return false;
        try {
            byte[] supplied = Base64.getDecoder().decode(authorizationHeader.substring("Basic ".length()).trim());
            byte[] expected = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(supplied, expected);
        } catch (IllegalArgumentException invalidBase64) {
            return false;
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getCallbackJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
