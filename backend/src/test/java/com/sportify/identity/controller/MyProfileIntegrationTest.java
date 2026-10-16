package com.sportify.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.impl.AuditServiceImpl;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** SQL Server HTTP, login, transaction and contention checks. Requires Docker-capable CI. */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MyProfileIntegrationTest extends AbstractIntegrationTest {
    private static final String OLD = "Original123";
    private static final String NEXT = "Replacement456";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MemberProfileRepository members;
    @Autowired CoachProfileRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired SportRepository sports;
    @Autowired MembershipCardTierRepository tiers;
    @Autowired MemberCardRepository cards;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired EntityManagerFactory emf;
    @MockitoSpyBean PasswordEncoder encoder;
    @MockitoSpyBean AuditServiceImpl audit;

    final String marker = "be02" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    int serial;
    final List<ExecutorService> workerPools = new ArrayList<>();

    @AfterEach
    void cleanup() {
        assertThat(workerPools).allMatch(ExecutorService::isTerminated, "writers stopped before cleanup");
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ")"
                + " OR (action = 'LOGIN_FAILED' AND JSON_VALUE(details, '$.email') LIKE ?)", marker + "%", marker + "%");
        jdbc.update("DELETE FROM member_card WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM coach_sport WHERE coach_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM coach_profile WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
        jdbc.update("DELETE FROM sport WHERE code LIKE ?", marker + "%");
    }

    TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    UserAccount account(String role) {
        return tx().execute(status -> {
            UserAccount account = users.saveAndFlush(UserAccount.builder().email(marker + (++serial) + "@example.test")
                    .fullName("BE02 Original").passwordHash(encoder.encode(OLD)).role(roles.findByCode(role).orElseThrow())
                    .dateOfBirth(LocalDate.of(2000, 1, 1)).address("Original address").build());
            if (role.equals("MEMBER")) {
                members.saveAndFlush(MemberProfile.builder().userAccount(account).memberCode(marker + serial)
                        .mainGoal("BUILD_SKILLS").currentLevel("INTERMEDIATE").staffNote("Private staff note").build());
            } else if (role.equals("COACH")) {
                coaches.saveAndFlush(CoachProfile.builder().userAccount(account).bio("Private biography").isPublic(false).build());
            }
            return account;
        });
    }

    long card(UserAccount owner, String tier, CardStatus status, LocalDate start, LocalDate end) {
        return tx().execute(st -> cards.saveAndFlush(MemberCard.builder().cardCode(marker + "-" + (++serial))
                .member(members.findById(owner.getId()).orElseThrow())
                .tier(tiers.findByCode(tier).orElseThrow()).status(status).startDate(start).endDate(end).build()).getId());
    }

    Sport sport() {
        return sports.saveAndFlush(Sport.builder().code(marker + (++serial)).name(marker + serial)
                .venueType(SportVenueType.INDOOR).build());
    }

    JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        // Avoid dumping request bodies or token-bearing responses on a failure.
        assertThat(response.getStatus()).isEqualTo(expected);
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }

    MockHttpServletRequestBuilder own(MockHttpServletRequestBuilder request, UserAccount owner) {
        return request.with(user(new CustomUserDetails(owner)));
    }

    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
    }

    JsonNode getProfile(UserAccount owner) throws Exception { return call(own(get("/api/v1/users/me"), owner), 200); }

    Map<String, String> password(String current, String next, String confirm) {
        return Map.of("currentPassword", current, "newPassword", next, "confirmPassword", confirm);
    }

    String storedHash(UserAccount owner) {
        return jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id = ?", String.class, owner.getId());
    }

    int auditCount(UserAccount owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE actor_user_id = ? AND action = ?",
                Integer.class, owner.getId(), AuditAction.PASSWORD_CHANGED);
    }

    JsonNode login(UserAccount owner, String password, int status) throws Exception {
        return call(body(post("/api/v1/auth/login"), Map.of("email", owner.getEmail(), "password", password)), status);
    }

    @Test
    void memberCodeAndGoldSummaryAreAccurateAndPrivateDataIsAbsent() throws Exception {
        var owner = account("MEMBER");
        LocalDate today = LocalDate.now(clock);
        long gold = card(owner, "GOLD", CardStatus.ACTIVE, today.minusDays(1), today.plusDays(30));
        var result = getProfile(owner);
        assertThat(result.path("memberCode").asText()).isEqualTo(marker + "1");
        assertThat(result.path("membershipCard").path("id").asLong()).isEqualTo(gold);
        assertThat(result.path("membershipCard").path("tierCode").asText()).isEqualTo("GOLD");
        assertThat(result.path("membershipCard").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(result.path("membershipCard").path("endDate").asText()).isEqualTo(today.plusDays(30).toString());
        assertThat(result.has("passwordHash")).isFalse();
        assertThat(result.has("staffNote")).isFalse();
        assertThat(result.has("version")).isFalse();
        assertThat(result.toString()).doesNotContain(storedHash(owner));
    }

    @Test
    void noCardMeansDefaultStandardWithoutFabricationOrOtherMembersCard() throws Exception {
        var owner = account("MEMBER");
        var other = account("MEMBER");
        card(other, "VIP", CardStatus.ACTIVE, LocalDate.now(clock), null);
        var result = getProfile(owner);
        assertThat(result.path("id").asLong()).isEqualTo(owner.getId());
        assertThat(result.path("memberCode").asText()).isEqualTo(marker + "1");
        assertThat(result.path("membershipCard").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_card WHERE member_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test
    void physicalStandardCardIsReturnedWhenAlreadyPresent() throws Exception {
        var owner = account("MEMBER");
        long standard = card(owner, "STANDARD", CardStatus.ACTIVE, LocalDate.now(clock), null);
        var result = getProfile(owner).path("membershipCard");
        assertThat(result.path("id").asLong()).isEqualTo(standard);
        assertThat(result.path("tierCode").asText()).isEqualTo("STANDARD");
        assertThat(result.path("endDate").isNull()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = CardStatus.class, names = {"EXPIRED", "PENDING_PAYMENT", "CANCELLED", "REPLACED"})
    void nonCurrentCardStatesNeverMasqueradeAsActive(CardStatus status) throws Exception {
        var owner = account("MEMBER");
        card(owner, "GOLD", status, LocalDate.now(clock).minusDays(10), LocalDate.now(clock).plusDays(10));
        assertThat(getProfile(owner).path("membershipCard").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, 10})
    void activeCardOutsideItsDateWindowIsNotCurrent(int offset) throws Exception {
        var owner = account("MEMBER");
        var today = LocalDate.now(clock);
        card(owner, "GOLD", CardStatus.ACTIVE, today.plusDays(offset), today.plusDays(offset + 1));
        assertThat(getProfile(owner).path("membershipCard").isNull()).isTrue();
    }

    @Test
    void currentCardUsesRankThenIdAndIncludesEndDateTodayInsteadOfLatestInsert() throws Exception {
        var owner = account("MEMBER");
        var today = LocalDate.now(clock);
        card(owner, "VIP", CardStatus.ACTIVE, today.minusDays(10), today);
        long preferred = card(owner, "VIP", CardStatus.ACTIVE, today.minusDays(5), today);
        card(owner, "GOLD", CardStatus.ACTIVE, today.minusDays(1), today.plusDays(10));
        card(owner, "VIP", CardStatus.PENDING_PAYMENT, today, today.plusDays(50));
        assertThat(getProfile(owner).path("membershipCard").path("id").asLong()).isEqualTo(preferred);
    }

    @Test
    void coachSportsAreSortedUniqueScopedAndLoadedWithBoundedQueries() throws Exception {
        var owner = account("COACH");
        var other = account("COACH");
        var first = sport();
        var second = sport();
        var privateSport = sport();
        coachSports.saveAllAndFlush(List.of(CoachSport.of(owner.getId(), second.getId()),
                CoachSport.of(owner.getId(), first.getId()), CoachSport.of(other.getId(), privateSport.getId())));
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = getProfile(owner);
        assertThat(result.path("sports").size()).isEqualTo(2);
        assertThat(result.path("sports").get(0).path("id").asLong()).isEqualTo(first.getId());
        assertThat(result.path("sports").get(1).path("id").asLong()).isEqualTo(second.getId());
        assertThat(result.path("sports").get(0).path("name").asText()).isEqualTo(first.getName());
        assertThat(result.has("bio")).isFalse();
        assertThat(result.path("memberCode").isNull()).isTrue();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void coachWithoutSportsReturnsEmptyArray() throws Exception {
        var result = getProfile(account("COACH")).path("sports");
        assertThat(result.isArray()).isTrue();
        assertThat(result.size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH", "RECEPTIONIST", "MANAGER"})
    void allRolesUseRealBearerAuthenticationForReadsAndBothWrites(String role) throws Exception {
        var owner = account(role);
        String token = login(owner, OLD, 200).path("accessToken").asText();
        var result = call(get("/api/v1/users/me").header("Authorization", "Bearer " + token), 200);
        assertThat(result.path("role").asText()).isEqualTo(role);
        call(body(put("/api/v1/users/me").header("Authorization", "Bearer " + token), Map.of("fullName", "Changed Name")), 200);
        call(body(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token), password(OLD, NEXT, NEXT)), 204);
        assertThat(encoder.matches(NEXT, storedHash(owner))).isTrue();
    }

    @Test
    void profilePersistsAcrossRequestsAndPreservesOtherUserAndProtectedColumns() throws Exception {
        var owner = account("MEMBER");
        var other = account("MEMBER");
        String beforeHash = storedHash(owner);
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("fullName", "  Changed Name  ", "phone", "+84912345678",
                "address", " New address ", "dateOfBirth", "1999-03-01", "mainGoal", "COMPETITION", "currentLevel", "ADVANCED"));
        payload.putAll(Map.of("id", other.getId(), "userId", other.getId(), "email", other.getEmail(), "role", "MANAGER",
                "status", "INACTIVE", "passwordHash", "injected", "memberCode", "HACK", "sports", List.of(1)));
        var saved = call(body(own(put("/api/v1/users/me"), owner), payload), 200);
        var reloaded = getProfile(owner);
        assertThat(reloaded).isEqualTo(saved);
        assertThat(reloaded.path("fullName").asText()).isEqualTo("Changed Name");
        assertThat(reloaded.path("phone").asText()).isEqualTo("+84912345678");
        assertThat(reloaded.path("address").asText()).isEqualTo("New address");
        assertThat(reloaded.path("dateOfBirth").asText()).isEqualTo("1999-03-01");
        assertThat(reloaded.path("mainGoal").asText()).isEqualTo("COMPETITION");
        assertThat(reloaded.path("currentLevel").asText()).isEqualTo("ADVANCED");
        assertThat(reloaded.path("email").asText()).isEqualTo(owner.getEmail());
        assertThat(reloaded.path("role").asText()).isEqualTo("MEMBER");
        assertThat(reloaded.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(reloaded.path("memberCode").asText()).isEqualTo(marker + "1");
        assertThat(storedHash(owner)).isEqualTo(beforeHash);
        assertThat(getProfile(other).path("fullName").asText()).isEqualTo("BE02 Original");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_profile WHERE user_id = ?", Integer.class, owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT staff_note FROM member_profile WHERE user_id = ?", String.class, owner.getId())).isEqualTo("Private staff note");
    }

    @Test
    void omittedFieldsStayAndExplicitNullOptionalsClearWithoutNullLevel() throws Exception {
        var owner = account("MEMBER");
        call(body(own(put("/api/v1/users/me"), owner), Map.of("fullName", "Only Name")), 200);
        var first = getProfile(owner);
        assertThat(first.path("address").asText()).isEqualTo("Original address");
        assertThat(first.path("currentLevel").asText()).isEqualTo("INTERMEDIATE");
        Map<String, Object> clear = new HashMap<>();
        for (String field : List.of("phone", "address", "dateOfBirth", "mainGoal", "currentLevel")) clear.put(field, null);
        call(body(own(put("/api/v1/users/me"), owner), clear), 200);
        var result = getProfile(owner);
        for (String field : List.of("phone", "address", "dateOfBirth", "mainGoal")) assertThat(result.path(field).isNull()).isTrue();
        assertThat(result.path("currentLevel").asText()).isEqualTo("BEGINNER");
        assertThat(result.path("fullName").asText()).isEqualTo("Only Name");
    }

    @ParameterizedTest
    @CsvSource({"fullName,'   '", "phone,123", "dateOfBirth,2999-01-01", "dateOfBirth,1899-12-31", "currentLevel,EXPERT", "mainGoal,UNKNOWN"})
    void invalidProfileReturns400AndDoesNotMutate(String field, String value) throws Exception {
        var owner = account("MEMBER");
        var before = getProfile(owner);
        var failure = call(body(own(put("/api/v1/users/me"), owner), Map.of(field, value)), 400);
        assertThat(failure.path("details").has(field)).isTrue();
        assertThat(getProfile(owner)).isEqualTo(before);
    }

    @Test
    void duplicatePhonePersistenceFailureRollsBackMemberAndAccountEdits() throws Exception {
        var owner = account("MEMBER");
        var other = account("MEMBER");
        call(body(own(put("/api/v1/users/me"), other), Map.of("phone", "0901234567")), 200);
        var before = getProfile(owner);
        call(body(own(put("/api/v1/users/me"), owner), Map.of("fullName", "Must Roll Back", "phone", "0901234567",
                "mainGoal", "COMPETITION", "currentLevel", "ADVANCED")), 409);
        assertThat(getProfile(owner)).isEqualTo(before);
        assertThat(getProfile(other).path("phone").asText()).isEqualTo("0901234567");
    }

    @Test
    void memberOnlyChangeAlsoAdvancesAccountVersion() throws Exception {
        var owner = account("MEMBER");
        int before = jdbc.queryForObject("SELECT version FROM user_account WHERE id = ?", Integer.class, owner.getId());
        call(body(own(put("/api/v1/users/me"), owner), Map.of("mainGoal", "COMPETITION")), 200);
        int after = jdbc.queryForObject("SELECT version FROM user_account WHERE id = ?", Integer.class, owner.getId());
        assertThat(after).isGreaterThan(before);
        assertThat(getProfile(owner).path("mainGoal").asText()).isEqualTo("COMPETITION");
        assertThat(getProfile(owner).path("fullName").asText()).isEqualTo("BE02 Original");
    }

    @Test
    void passwordChangeStoresBcryptChangesRealLoginAndWritesExactlyOneSafeAudit() throws Exception {
        var owner = account("MEMBER");
        var other = account("MEMBER");
        String otherHash = storedHash(other);
        var before = getProfile(owner);
        var tokenPair = login(owner, OLD, 200);
        Map<String, Object> payload = new HashMap<>(password(OLD, NEXT, NEXT));
        payload.put("userId", other.getId());
        call(body(own(put("/api/v1/users/me/password"), owner), payload), 204);
        String hash = storedHash(owner);
        assertThat(hash).startsWith("$2").isNotEqualTo(NEXT);
        assertThat(encoder.matches(OLD, hash)).isFalse();
        assertThat(encoder.matches(NEXT, hash)).isTrue();
        login(owner, OLD, 401);
        login(owner, NEXT, 200);
        assertThat(getProfile(owner)).isEqualTo(before);
        assertThat(storedHash(other)).isEqualTo(otherHash);
        assertThat(auditCount(owner)).isEqualTo(1);
        var log = jdbc.queryForMap("SELECT entity_type, entity_id, summary, details FROM activity_log WHERE actor_user_id = ? AND action = ?",
                owner.getId(), AuditAction.PASSWORD_CHANGED);
        assertThat(log.get("entity_type")).isEqualTo("USER_ACCOUNT");
        assertThat(((Number) log.get("entity_id")).longValue()).isEqualTo(owner.getId());
        assertThat(log.get("summary")).isEqualTo("Password changed");
        assertThat(log.get("details")).isNull();
        // Existing stateless tokens deliberately retain their established lifetime.
        call(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenPair.path("accessToken").asText()), 200);
        call(body(post("/api/v1/auth/refresh"), Map.of("refreshToken", tokenPair.path("refreshToken").asText())), 200);
    }

    @ParameterizedTest
    @CsvSource({"Wrong123,Replacement456,Replacement456,currentPassword", "Original123,Original123,Original123,newPassword",
            "Original123,Replacement456,Mismatch789,confirmPassword", "Original123,weak,weak,newPassword"})
    void rejectedPasswordChangeIs400WithoutHashOrAuditMutation(String current, String next, String confirmation, String field) throws Exception {
        var owner = account("MEMBER");
        String before = storedHash(owner);
        var result = call(body(own(put("/api/v1/users/me/password"), owner), password(current, next, confirmation)), 400);
        assertThat(result.path("details").has(field)).isTrue();
        assertThat(storedHash(owner)).isEqualTo(before);
        assertThat(auditCount(owner)).isZero();
    }

    @Test
    void actualAuditDatabaseFailureRollsBackAlreadyFlushedPassword() throws Exception {
        var owner = account("MEMBER");
        String before = storedHash(owner);
        doAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            if (AuditAction.PASSWORD_CHANGED.equals(event.getAction()) && owner.getId().equals(event.getEntityId())) {
                // A real NOT NULL violation during audit persistence, after the password UPDATE flush.
                jdbc.update("INSERT INTO activity_log(action, entity_type) VALUES (?, NULL)", AuditAction.PASSWORD_CHANGED);
            }
            return invocation.callRealMethod();
        }).when(audit).record(any());
        call(body(own(put("/api/v1/users/me/password"), owner), password(OLD, NEXT, NEXT)), 409);
        assertThat(storedHash(owner)).isEqualTo(before);
        assertThat(auditCount(owner)).isZero();
        login(owner, OLD, 200);
        login(owner, NEXT, 401);
    }

    @Test
    void lateAuditFailureRollsBackBothPasswordAndInsertedActivity() throws Exception {
        var owner = account("MEMBER");
        String before = storedHash(owner);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            AuditEvent event = invocation.getArgument(0);
            if (AuditAction.PASSWORD_CHANGED.equals(event.getAction()) && owner.getId().equals(event.getEntityId())) {
                throw new IllegalStateException("Forced failure after audit insert");
            }
            return result;
        }).when(audit).record(any());
        call(body(own(put("/api/v1/users/me/password"), owner), password(OLD, NEXT, NEXT)), 500);
        assertThat(storedHash(owner)).isEqualTo(before);
        assertThat(auditCount(owner)).isZero();
    }

    @Test
    void anonymousRefreshTokenAndDeactivatedBearerCannotUseProfileEndpoints() throws Exception {
        for (var request : List.of(get("/api/v1/users/me"), put("/api/v1/users/me"), put("/api/v1/users/me/password"))) {
            call(request, 401);
        }
        var owner = account("MEMBER");
        var pair = login(owner, OLD, 200);
        for (String path : List.of("/api/v1/users/me", "/api/v1/users/me/password")) {
            call(body(put(path).header("Authorization", "Bearer " + pair.path("refreshToken").asText()), Map.of()), 401);
        }
        jdbc.update("UPDATE user_account SET status = 'INACTIVE', version = version + 1 WHERE id = ?", owner.getId());
        call(get("/api/v1/users/me").header("Authorization", "Bearer " + pair.path("accessToken").asText()), 401);
        call(body(put("/api/v1/users/me").header("Authorization", "Bearer " + pair.path("accessToken").asText()), Map.of("fullName", "No change")), 401);
        call(body(put("/api/v1/users/me/password").header("Authorization", "Bearer " + pair.path("accessToken").asText()), password(OLD, NEXT, NEXT)), 401);
    }

    @Test
    void pathAndQueryIdsCannotReadOrWriteAnotherUsersProfile() throws Exception {
        var owner = account("MEMBER");
        var other = account("MEMBER");
        var result = call(own(get("/api/v1/users/me").param("userId", other.getId().toString()), owner), 200);
        assertThat(result.path("id").asLong()).isEqualTo(owner.getId());
        call(own(get("/api/v1/users/" + other.getId()), owner), 404);
        call(body(own(put("/api/v1/users/" + other.getId()), owner), Map.of("fullName", "Attack")), 404);
        assertThat(getProfile(other).path("fullName").asText()).isEqualTo("BE02 Original");
    }

    @Test
    void simultaneousChangesCannotBothCommitTheSameStaleCurrentPassword() throws Exception {
        var owner = account("MEMBER");
        var bothChecked = new CountDownLatch(2);
        doAnswer(invocation -> {
            boolean matches = (boolean) invocation.callRealMethod();
            assertThat(matches).isTrue();
            bothChecked.countDown();
            assertThat(bothChecked.await(15, TimeUnit.SECONDS)).isTrue();
            return matches;
        }).when(encoder).matches(eq(OLD), anyString());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        workerPools.add(pool);
        try {
            Future<Integer> one = pool.submit(() -> mvc.perform(body(own(put("/api/v1/users/me/password"), owner), password(OLD, NEXT, NEXT)))
                    .andReturn().getResponse().getStatus());
            Future<Integer> two = pool.submit(() -> mvc.perform(body(own(put("/api/v1/users/me/password"), owner), password(OLD, "Different789", "Different789")))
                    .andReturn().getResponse().getStatus());
            assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 409);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(auditCount(owner)).isEqualTo(1);
        String stored = storedHash(owner);
        assertThat(encoder.matches(NEXT, stored) ^ encoder.matches("Different789", stored)).isTrue();
    }

    @Test
    void concurrentAccountDeactivationIsNotOverwrittenByPasswordChange() throws Exception {
        var owner = account("MEMBER");
        String before = storedHash(owner);
        var checked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            boolean matches = (boolean) invocation.callRealMethod();
            checked.countDown();
            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
            return matches;
        }).when(encoder).matches(eq(OLD), anyString());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        workerPools.add(pool);
        try {
            Future<Integer> writer = pool.submit(() -> mvc.perform(body(own(put("/api/v1/users/me/password"), owner), password(OLD, NEXT, NEXT)))
                    .andReturn().getResponse().getStatus());
            assertThat(checked.await(15, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE user_account SET status = 'INACTIVE', version = version + 1 WHERE id = ?", owner.getId());
            release.countDown();
            assertThat(writer.get(30, TimeUnit.SECONDS)).isEqualTo(409);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(storedHash(owner)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM user_account WHERE id = ?", String.class, owner.getId())).isEqualTo("INACTIVE");
        assertThat(auditCount(owner)).isZero();
    }
}
