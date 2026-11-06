package com.sportify.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    /**
     * E2E-BUG-05 (PROVISIONAL, E2E-D7a pending): credential fingerprint claim. It is an HMAC of the account's stored
     * password hash, keyed with the signing secret, so it reveals nothing about the hash. Changing the password changes
     * the hash and therefore revokes every access and refresh token issued before, with no schema change.
     */
    static final String CREDENTIAL_CLAIM = "cred";

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @PostConstruct
    public void validateSecretKey() {
        try {
            if (secretKey == null || secretKey.trim().isEmpty()) {
                throw new IllegalArgumentException("JWT_SECRET is null or blank");
            }
            getSignInKey();
        } catch (Exception ex) {
            throw new IllegalStateException("JWT_SECRET must be a valid Base64 string that decodes to at least 32 bytes", ex);
        }
    }

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", "access");
        return generateToken(extraClaims, userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", "refresh");
        return buildToken(extraClaims, userDetails, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(CREDENTIAL_CLAIM, credentialFingerprint(userDetails));
        return Jwts
                .builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token) && hasCurrentCredential(token, userDetails);
    }

    /**
     * userDetails is loaded from the database for each request (filter) and each refresh, so a password change is seen
     * at once. A token without the claim (issued before this change) is rejected: the member logs in again once.
     */
    private boolean hasCurrentCredential(String token, UserDetails userDetails) {
        String claim = extractClaim(token, claims -> claims.get(CREDENTIAL_CLAIM, String.class));
        return claim != null && MessageDigest.isEqual(claim.getBytes(StandardCharsets.US_ASCII),
                credentialFingerprint(userDetails).getBytes(StandardCharsets.US_ASCII));
    }

    private String credentialFingerprint(UserDetails userDetails) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Decoders.BASE64.decode(secretKey), "HmacSHA256"));
            String password = userDetails.getPassword() == null ? "" : userDetails.getPassword();
            byte[] digest = mac.doFinal(("credential:" + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 16));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot compute the token credential fingerprint", ex);
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
