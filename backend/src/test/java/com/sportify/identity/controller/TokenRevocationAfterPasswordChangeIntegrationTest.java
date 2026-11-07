package com.sportify.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * E2E-BUG-05 (Flow 1 security): a password change revokes every earlier access AND refresh token. Each token carries
 * an HMAC fingerprint of the stored password hash (claim "cred"); a new password means a new hash, so old tokens fail. PROVISIONAL: immediate revocation follows the working recommendation of E2E-D7a, PENDING
 * CODEOWNER approval. Single-use refresh rotation (E2E-D7b) is NOT implemented and is documented as such below.
 * Real JWTs through the real security filter chain and SQL Server.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TokenRevocationAfterPasswordChangeIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();
    static final String PASSWORD = "Original123";

    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${application.security.jwt.secret-key}") String secret;

    private final String marker = "b05" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private UserAccount member;
    private UserAccount manager;

    @BeforeEach void fixture() {
        member = account("MEMBER", PASSWORD);
        manager = account("MANAGER", PASSWORD);
    }

    @AfterEach void cleanup() {
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ") OR entity_id IN (" + own + ")", marker + "%", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
    }

    @Test
    void b05_01_02_03_oldAccessAndRefreshTokensStopWorkingAfterAPasswordChangeAndNewTokensWork() {
        Tokens old = login(member.getEmail(), PASSWORD);
        assertThat(me(old.access())).isEqualTo(200);

        assertThat(changePassword(old.access(), PASSWORD, "Changed4567")).isEqualTo(204);

        assertThat(me(old.access())).as("B05-01 old access token").isIn(401, 403);
        assertThat(refresh(old.refresh()).status()).as("B05-02 old refresh token cannot mint tokens").isEqualTo(401);
        Tokens fresh = login(member.getEmail(), "Changed4567");
        assertThat(me(fresh.access())).as("B05-03 new access token").isEqualTo(200);
        Outcome renewed = refresh(fresh.refresh());
        assertThat(renewed.status()).as("B05-03 new refresh token").isEqualTo(200);
        assertThat(me(renewed.body().path("accessToken").asText())).isEqualTo(200);
    }

    @Test @Timeout(60)
    void b05_04_concurrentPasswordChangesLeaveNoSupersededTokenUsable() throws Exception {
        Tokens old = login(member.getEmail(), PASSWORD);
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses;
        try {
            Future<Integer> a = pool.submit(() -> { start.await(20, TimeUnit.SECONDS); return changePassword(old.access(), PASSWORD, "FirstNew111"); });
            Future<Integer> b = pool.submit(() -> { start.await(20, TimeUnit.SECONDS); return changePassword(old.access(), PASSWORD, "SecondNew222"); });
            statuses = List.of(a.get(40, TimeUnit.SECONDS), b.get(40, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }

        long succeeded = statuses.stream().filter(s -> s == 204).count();
        assertThat(succeeded).as("statuses " + statuses).isBetween(1L, 2L);
        assertThat(statuses).allMatch(s -> s == 204 || (s >= 400 && s < 500));
        assertThat(me(old.access())).isIn(401, 403);
        assertThat(refresh(old.refresh()).status()).isEqualTo(401);
        String winner = loginStatus(member.getEmail(), "SecondNew222") == 200 ? "SecondNew222" : "FirstNew111";
        assertThat(me(login(member.getEmail(), winner).access())).isEqualTo(200);
    }

    @Test
    void b05_05_inactiveAccountsAndExpiredTokensStayRejected() {
        Tokens tokens = login(member.getEmail(), PASSWORD);
        String expired = jwtWithFingerprintOf(member.getEmail(), -60_000L);
        assertThat(me(expired)).as("expired token").isIn(401, 403);

        Tokens managerTokens = login(manager.getEmail(), PASSWORD);
        assertThat(status(patch("/api/v1/manager/users/" + member.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("status", "INACTIVE"))), managerTokens.access())).isEqualTo(200);

        assertThat(me(tokens.access())).isIn(401, 403);
        assertThat(refresh(tokens.refresh()).status()).isIn(401, 403);
    }

    @Test
    void tokensIssuedBeforeThisChangeWithoutACredentialClaimAreRejected() {
        // Rollout: tokens signed before this change carry no fingerprint. They are rejected rather than trusted, so an
        // old refresh token cannot outlive a password change; the member logs in again once.
        String legacyAccess = token(member.getEmail(), "access", 900_000L);
        String legacyRefresh = token(member.getEmail(), "refresh", 900_000L);

        assertThat(me(legacyAccess)).isIn(401, 403);
        assertThat(refresh(legacyRefresh).status()).isEqualTo(401);
        assertThat(me(login(member.getEmail(), PASSWORD).access())).isEqualTo(200);
    }

    @Test
    void a1_a2_singleUseRotationEnforced_firstRefreshSucceedsReplayFailsAndRotatedTokenWorks() {
        Tokens tokens = login(member.getEmail(), PASSWORD);
        Outcome firstRefresh = refresh(tokens.refresh());
        assertThat(firstRefresh.status()).as("A1: first refresh with valid token").isEqualTo(200);

        // Replay of the original consumed token must be rejected with 401 without password change
        Outcome replay = refresh(tokens.refresh());
        assertThat(replay.status()).as("A1: replay of original refresh token rejected with 401").isEqualTo(401);

        // A2: rotated refresh token works once and rotates again
        String rotatedToken = firstRefresh.body().path("refreshToken").asText();
        assertThat(rotatedToken).isNotBlank().isNotEqualTo(tokens.refresh());
        Outcome secondRefresh = refresh(rotatedToken);
        assertThat(secondRefresh.status()).as("A2: rotated token works once").isEqualTo(200);

        // Replaying the second rotated token also fails with 401
        assertThat(refresh(rotatedToken).status()).as("A2: replay of rotated token rejected with 401").isEqualTo(401);

        // Successor from second refresh works
        String thirdToken = secondRefresh.body().path("refreshToken").asText();
        assertThat(refresh(thirdToken).status()).isEqualTo(200);
    }

    @Test @Timeout(60)
    void a3_concurrentRefreshRaceWithSameTokenResultsInExactlyOneSuccessAndOne401() throws Exception {
        Tokens tokens = login(member.getEmail(), PASSWORD);
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        List<Outcome> outcomes;
        try {
            Future<Outcome> a = pool.submit(() -> { start.await(20, TimeUnit.SECONDS); return refresh(tokens.refresh()); });
            Future<Outcome> b = pool.submit(() -> { start.await(20, TimeUnit.SECONDS); return refresh(tokens.refresh()); });
            outcomes = List.of(a.get(40, TimeUnit.SECONDS), b.get(40, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }

        long succeeded = outcomes.stream().filter(o -> o.status() == 200).count();
        long rejected = outcomes.stream().filter(o -> o.status() == 401).count();
        assertThat(succeeded).as("A3: exactly one concurrent refresh succeeded").isEqualTo(1L);
        assertThat(rejected).as("A3: exactly one concurrent refresh rejected with 401").isEqualTo(1L);

        Outcome winner = outcomes.stream().filter(o -> o.status() == 200).findFirst().orElseThrow();
        String successorRefresh = winner.body().path("refreshToken").asText();
        assertThat(refresh(successorRefresh).status()).as("A3: successor token of winner is usable").isEqualTo(200);
    }

    @Test
    void a4_consumedRefreshTokenRemainsDeniedPersistedInDatabase() {
        Tokens tokens = login(member.getEmail(), PASSWORD);
        Outcome firstRefresh = refresh(tokens.refresh());
        assertThat(firstRefresh.status()).isEqualTo(200);

        // Verify token row exists in DB with consumed_at NOT NULL
        Integer consumedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_refresh_token WHERE consumed_at IS NOT NULL", Integer.class);
        assertThat(consumedCount).isNotNull().isGreaterThan(0);

        // Replay of consumed token still rejected with 401
        assertThat(refresh(tokens.refresh()).status()).as("A4: persisted consumed token still denied 401").isEqualTo(401);
    }

    // ---------------------------------------------------------------- helpers

    private record Tokens(String access, String refresh) {}
    private record Outcome(int status, JsonNode body) {}

    private UserAccount account(String role, String password) {
        return users.saveAndFlush(UserAccount.builder().email(marker + role.toLowerCase(Locale.ROOT) + "@sportify.test")
                .fullName(role + " fixture").phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash(encoder.encode(password)).role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build());
    }
    private Tokens login(String email, String password) {
        Outcome login = call(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("email", email, "password", password))), null);
        assertThat(login.status()).as(login.body().toString()).isEqualTo(200);
        return new Tokens(login.body().path("accessToken").asText(), login.body().path("refreshToken").asText());
    }
    private int loginStatus(String email, String password) {
        return call(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("email", email, "password", password))), null).status();
    }
    private Outcome refresh(String refreshToken) {
        return call(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("refreshToken", refreshToken))), null);
    }
    private int me(String accessToken) { return status(get("/api/v1/users/me"), accessToken); }
    private int changePassword(String accessToken, String current, String next) {
        return status(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "currentPassword", current, "newPassword", next, "confirmPassword", next))), accessToken);
    }
    /** An otherwise valid token whose only defect is its expiry, built by the real JwtService claims. */
    private String jwtWithFingerprintOf(String subject, long lifetimeMillis) {
        String valid = login(subject, PASSWORD).access();
        var claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).build()
                .parseClaimsJws(valid).getBody();
        long now = System.currentTimeMillis();
        return Jwts.builder().setClaims(new HashMap<>(claims)).setSubject(subject).setIssuedAt(new Date(now - 120_000L))
                .setExpiration(new Date(now + lifetimeMillis))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), SignatureAlgorithm.HS256).compact();
    }
    /** Signs a token without the credential claim, as tokens issued before this change were. */
    private String token(String subject, String type, long lifetimeMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", type);
        long now = System.currentTimeMillis();
        return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(now - 120_000L))
                .setExpiration(new Date(now + lifetimeMillis))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), SignatureAlgorithm.HS256).compact();
    }
    private int status(MockHttpServletRequestBuilder request, String accessToken) { return call(request, accessToken).status(); }
    private Outcome call(MockHttpServletRequestBuilder request, String accessToken) {
        try {
            if (accessToken != null) request.header("Authorization", "Bearer " + accessToken);
            var response = mvc.perform(request).andReturn().getResponse();
            String body = response.getContentAsString();
            return new Outcome(response.getStatus(), body.isBlank() ? json.createObjectNode() : json.readTree(body));
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception error) { throw new AssertionError(error); }
    }
}
