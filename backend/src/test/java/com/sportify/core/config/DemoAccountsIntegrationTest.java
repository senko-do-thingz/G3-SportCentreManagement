package com.sportify.core.config;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.service.AuthenticationService;
import com.sportify.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.sportify.core.config.DemoSeedSource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Flyway, authentication and logical SQL replay in an isolated database in the CI container. */
@ActiveProfiles(value = {"dev", "demo", "test"}, inheritProfiles = false)
@TestPropertySource(properties = {"spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.format_sql=false"})
@Import(DemoAccountsIntegrationTest.FixedClock.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoAccountsIntegrationTest extends AbstractIntegrationTest {
    private static final String DATABASE = "be07_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean databaseCreated;

    @DynamicPropertySource
    static synchronized void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        if (!databaseCreated) {
            try (var connection = DriverManager.getConnection(MSSQL_SERVER_CONTAINER.getJdbcUrl(),
                    MSSQL_SERVER_CONTAINER.getUsername(), MSSQL_SERVER_CONTAINER.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE [" + DATABASE + "]");
            }
            databaseCreated = true;
        }
        // Hikari selects this catalog before Flyway or Hibernate obtains a connection.
        // The shared base datasource URL is preserved; no other suite can see our seed rows.
        registry.add("spring.datasource.hikari.catalog", () -> DATABASE);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock demoClock() {
            return Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AuthenticationService authentication;
    @Autowired PasswordEncoder encoder;
    @Autowired UserDetailsService users;
    @Autowired UserService userService;
    @Autowired JwtService jwt;
    @Autowired MembershipCardService cards;

    static Stream<Account> accounts() {
        return ACCOUNTS.stream();
    }

    @Test
    void freshFlywayDatabaseContainsExactlyTheSevenAccountsAndOrderedMigrations() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo(DATABASE);
        assertShape();
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '28' AND success = 1")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC", String.class))
                .startsWith("28", "27", "26");
    }

    @ParameterizedTest(name = "login and resolved role: {0}")
    @MethodSource("accounts")
    void everyAccountAuthenticatesUsingTheDocumentedPasswordAndReturnsTheExpectedRole(Account account) {
        var response = authentication.authenticate(new AuthRequest(account.email(), PASSWORD));
        assertTrue(response.getAccessToken() != null && !response.getAccessToken().isBlank(), "Access token missing");
        assertTrue(response.getRefreshToken() != null && !response.getRefreshToken().isBlank(), "Refresh token missing");
        assertThat(jwt.extractUsername(response.getAccessToken())).isEqualTo(account.email());
        var details = (CustomUserDetails) users.loadUserByUsername(jwt.extractUsername(response.getAccessToken()));
        assertTrue(jwt.isTokenValid(response.getAccessToken(), details), "Demo token must be valid");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_" + account.role());
        assertThat(userService.getCurrentUserProfile(details).getRole()).isEqualTo(account.role());
    }

    @ParameterizedTest(name = "stored hash: {0}")
    @MethodSource("accounts")
    void storedHashesMatchTheActualPasswordEncoderWithoutPlaintext(Account account) {
        String hash = jdbc.queryForObject("SELECT password_hash FROM user_account WHERE email = ?", String.class, account.email());
        assertTrue(encoder.matches(PASSWORD, hash), "Stored demo hash must match the documented credential");
        assertTrue(!PASSWORD.equals(hash), "Passwords must not be stored in plaintext");
        assertThat(hash).startsWith("$2a$10$");
    }

    @ParameterizedTest(name = "wrong password rejected: {0}")
    @MethodSource("accounts")
    void incorrectPasswordIsRejectedForEveryAccount(Account account) {
        assertThatThrownBy(() -> authentication.authenticate(new AuthRequest(account.email(), "incorrect-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest(name = "inactive login rejected: {0}")
    @MethodSource("accounts")
    void inactiveAccountCannotAuthenticateAndIsRestoredByRollback(Account account) {
        rollback(() -> {
            jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE email = ?", account.email());
            assertThatThrownBy(() -> authentication.authenticate(new AuthRequest(account.email(), PASSWORD)))
                    .isInstanceOfAny(DisabledException.class, LockedException.class);
        });
        assertThat(jdbc.queryForObject("SELECT status FROM user_account WHERE email = ?", String.class, account.email()))
                .isEqualTo("ACTIVE");
    }

    @Test
    void allCoachesHaveSharedKeyProfilesAndDistinctValidPrimarySportLinks() {
        var rows = jdbc.queryForList("""
                SELECT u.email, s.code AS sport_code, cs.sport_id
                FROM user_account u JOIN coach_profile p ON p.user_id = u.id
                JOIN sport s ON s.id = p.primary_sport_id
                JOIN coach_sport cs ON cs.coach_id = p.user_id AND cs.sport_id = s.id
                WHERE u.email IN ('quan.coach@example.com', 'nam.coach@example.com', 'ha.coach@example.com')
                """);
        assertThat(rows).hasSize(3);
        for (Account account : ACCOUNTS.stream().filter(a -> a.sport() != null).toList()) {
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.get("email")).isEqualTo(account.email());
                assertThat(row.get("sport_code")).isEqualTo(account.sport());
            });
        }
        assertThat(rows).extracting(row -> row.get("sport_id")).doesNotHaveDuplicates();
    }

    @Test
    void physicalGoldAndStandardCardsAndNoCardDriveTheRealMembershipReadBehavior() {
        var gold = (CustomUserDetails) users.loadUserByUsername("an.gold@example.com");
        var standard = (CustomUserDetails) users.loadUserByUsername("mai.standard@example.com");
        var noCard = (CustomUserDetails) users.loadUserByUsername("bao.nocard@example.com");
        assertThat(cards.getMyCards(gold.getUserAccount())).singleElement().satisfies(card -> {
            assertThat(card.getTierCode()).isEqualTo("GOLD");
            assertThat(card.getStartDate()).isEqualTo("2026-10-01");
            assertThat(card.getEndDate()).isEqualTo("2027-09-30");
            assertThat(card.getPricePaid()).isEqualByComparingTo("300000");
            assertThat(card.getPaymentId()).isNull();
        });
        assertThat(cards.getMyCards(standard.getUserAccount())).singleElement().satisfies(card -> {
            assertThat(card.getTierCode()).isEqualTo("STANDARD");
            assertThat(card.getEndDate()).isNull();
            assertThat(card.getPricePaid()).isZero();
            assertThat(card.getPaymentId()).isNull();
        });
        assertThat(cards.getMyCards(noCard.getUserAccount())).isEmpty();
        assertThat(userService.getCurrentUserProfile(noCard).getRole()).isEqualTo("MEMBER");
        assertThat(cards.getApplicableDiscountPercentage(gold.getUserAccount().getId(), 30)).isEqualTo(5);
        assertThat(cards.getApplicableDiscountPercentage(gold.getUserAccount().getId(), 1)).isZero();
        assertThat(cards.getApplicableDiscountPercentage(standard.getUserAccount().getId(), 30)).isZero();
        assertThat(cards.getApplicableDiscountPercentage(noCard.getUserAccount().getId(), 30)).isZero();
    }

    @Test
    void expiredAndFutureGoldTermsDoNotGrantBenefits() {
        rollback(() -> {
            long id = id("an.gold@example.com");
            jdbc.update("UPDATE member_card SET end_date = '20261014' WHERE member_id = ?", id);
            assertThat(cards.getApplicableDiscountPercentage(id, 30)).isZero();
            jdbc.update("UPDATE member_card SET start_date = '20261016', end_date = '20271015' WHERE member_id = ?", id);
            assertThat(cards.getApplicableDiscountPercentage(id, 30)).isZero();
        });
    }

    @Test
    void logicalSqlReplayIsIdempotentAndPreservesHashesProfilesCertificationsAndUnrelatedMoney() {
        rollback(() -> {
            jdbc.update("UPDATE user_account SET password_hash = ?, phone = '0900000007' WHERE email = 'quan.coach@example.com'",
                    encoder.encode("ChangedDemo2026!"));
            jdbc.update("UPDATE coach_profile SET bio = 'Existing biography' WHERE user_id = ?", id("quan.coach@example.com"));
            jdbc.update("INSERT INTO coach_certification (coach_id, name) VALUES (?, 'Existing qualification')", id("quan.coach@example.com"));
            jdbc.update("UPDATE member_profile SET staff_note = 'Preserve me' WHERE user_id = ?", id("an.gold@example.com"));
            unrelatedFixtures();
            String before = snapshot();
            replay();
            replay();
            assertThat(snapshot()).isEqualTo(before);
            assertShape();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"reception-user", "coach-user", "coach-profile", "coach-link", "member-profile", "gold-card", "standard-card", "all-demo-data"})
    void partialSeedStatesAreRepairedWithoutDuplicatingRecords(String missing) {
        rollback(() -> {
            switch (missing) {
                case "reception-user" -> jdbc.update("DELETE FROM user_account WHERE email = 'lan.reception@example.com'");
                case "coach-user", "coach-profile" -> {
                    long id = id("quan.coach@example.com");
                    jdbc.update("DELETE FROM coach_sport WHERE coach_id = ?", id);
                    jdbc.update("DELETE FROM coach_profile WHERE user_id = ?", id);
                    if (missing.equals("coach-user")) {
                        jdbc.update("DELETE FROM user_account WHERE id = ?", id);
                    }
                }
                case "coach-link" -> jdbc.update("DELETE FROM coach_sport WHERE coach_id = ?", id("quan.coach@example.com"));
                case "member-profile" -> jdbc.update("DELETE FROM member_profile WHERE user_id = ?", id("bao.nocard@example.com"));
                case "gold-card" -> jdbc.update("DELETE FROM member_card WHERE card_code = 'DEMO-CARD-GOLD'");
                case "standard-card" -> jdbc.update("DELETE FROM member_card WHERE card_code = 'DEMO-CARD-STANDARD'");
                case "all-demo-data" -> removeOnlyDemoFixtureRows();
                default -> throw new AssertionError(missing);
            }
            replay();
            assertShape();
            String repaired = snapshot();
            replay();
            assertThat(snapshot()).isEqualTo(repaired);
        });
        assertShape();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "role", "inactive", "primary-sport", "card-tier", "no-card-has-card", "tier-terms", "card-code-owner"})
    void conflictingExistingDataFailsWithoutOverwritingAnything(String conflict) {
        String original = snapshot();
        assertThatThrownBy(() -> rollback(() -> {
            switch (conflict) {
                case "name" -> jdbc.update("UPDATE user_account SET full_name = 'Unrelated owner' WHERE email = 'lan.reception@example.com'");
                case "role" -> jdbc.update("UPDATE user_account SET role_id = (SELECT id FROM role WHERE code = 'MANAGER') WHERE email = 'lan.reception@example.com'");
                case "inactive" -> jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE email = 'lan.reception@example.com'");
                case "primary-sport" -> jdbc.update("UPDATE coach_profile SET primary_sport_id = NULL WHERE user_id = ?", id("quan.coach@example.com"));
                case "card-tier" -> jdbc.update("UPDATE member_card SET tier_id = (SELECT id FROM membership_card_tier WHERE code = 'VIP') WHERE card_code = 'DEMO-CARD-GOLD'");
                case "no-card-has-card" -> jdbc.update("""
                        INSERT INTO member_card (card_code, member_id, tier_id, start_date)
                        SELECT 'BE07-CONFLICT', ?, id, '20261001' FROM membership_card_tier WHERE code = 'STANDARD'
                        """, id("bao.nocard@example.com"));
                case "tier-terms" -> jdbc.update("UPDATE membership_card_tier SET price = 1 WHERE code = 'STANDARD'");
                case "card-code-owner" -> jdbc.update("UPDATE member_card SET member_id = ? WHERE card_code = 'DEMO-CARD-GOLD'", id("mai.standard@example.com"));
                default -> throw new AssertionError(conflict);
            }
            replay();
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("BE-07");
        assertThat(snapshot()).isEqualTo(original);
    }

    @Test
    void missingSportDependencyFailsWithoutCreatingArbitrarySports() {
        String original = snapshot();
        assertThatThrownBy(() -> rollback(() -> {
            jdbc.update("UPDATE sport SET is_active = 0 WHERE code = 'BADMINTON'");
            replay();
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("BE-07 requires active");
        assertThat(snapshot()).isEqualTo(original);
    }

    @Test
    void futureRegistrationUsesTheSameSequenceWithoutCollidingWithDemoCodes() {
        rollback(() -> {
            Long next = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_member_code", Long.class);
            String code = new CodeFormatter().formatMemberCode(next);
            assertThat(count("SELECT COUNT(*) FROM member_profile WHERE member_code = ?", code)).isZero();
            assertThat(jdbc.queryForList("SELECT member_code FROM member_profile WHERE user_id IN (SELECT id FROM user_account WHERE email IN ('an.gold@example.com', 'mai.standard@example.com', 'bao.nocard@example.com'))", String.class))
                    .hasSize(3).doesNotHaveDuplicates().allMatch(value -> value.matches("MEM-[0-9]{4,16}"));
        });
    }

    @Test
    void inconsistentMemberSequenceFailsSafelyInsteadOfReusingAnotherMembersCode() {
        String original = snapshot();
        assertThatThrownBy(() -> rollback(() -> {
            unrelatedFixtures();
            Long allocated = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_member_code", Long.class);
            jdbc.update("UPDATE member_profile SET member_code = ? WHERE user_id = ?",
                    new CodeFormatter().formatMemberCode(allocated + 1), id("unrelated.be07@example.org"));
            jdbc.update("DELETE FROM member_profile WHERE user_id = ?", id("bao.nocard@example.com"));
            replay();
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("BE-07 member sequence conflicts");
        assertThat(snapshot()).isEqualTo(original);
    }

    @Test
    void concurrentLogicalReplaysSerializeAndLeaveAllRowsUnchanged() throws Exception {
        String before = snapshot();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> replay()));
            var second = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> replay()));
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void standaloneReplayOpensItsOwnTransactionAndRemainsIdempotent() {
        String before = snapshot();
        replay();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "member-code", "coach-link", "card-code", "foreign-key"})
    void actualSqlServerConstraintsRejectInvalidRelatedRecords(String constraint) {
        assertThatThrownBy(() -> rollback(() -> {
            switch (constraint) {
                case "email" -> jdbc.update("INSERT INTO user_account (email, password_hash, full_name, role_id) SELECT email, password_hash, full_name, role_id FROM user_account WHERE email = 'lan.reception@example.com'");
                case "member-code" -> jdbc.update("UPDATE member_profile SET member_code = (SELECT member_code FROM member_profile WHERE user_id = ?) WHERE user_id = ?", id("an.gold@example.com"), id("bao.nocard@example.com"));
                case "coach-link" -> jdbc.update("INSERT INTO coach_sport (coach_id, sport_id) SELECT coach_id, sport_id FROM coach_sport WHERE coach_id = ?", id("quan.coach@example.com"));
                case "card-code" -> jdbc.update("UPDATE member_card SET card_code = 'DEMO-CARD-GOLD' WHERE card_code = 'DEMO-CARD-STANDARD'");
                case "foreign-key" -> jdbc.update("INSERT INTO coach_sport (coach_id, sport_id) VALUES (?, -999999)", id("quan.coach@example.com"));
                default -> throw new AssertionError(constraint);
            }
        })).isInstanceOf(DataAccessException.class);
        assertShape();
    }

    private void assertShape() {
        assertThat(ACCOUNTS).hasSize(7);
        for (Account account : ACCOUNTS) {
            assertThat(count("SELECT COUNT(*) FROM user_account u JOIN role r ON r.id = u.role_id WHERE u.email = ? AND r.code = ? AND u.status = 'ACTIVE'", account.email(), account.role())).isEqualTo(1);
        }
        assertThat(count("SELECT COUNT(*) FROM coach_profile WHERE user_id IN (SELECT id FROM user_account WHERE email IN ('quan.coach@example.com', 'nam.coach@example.com', 'ha.coach@example.com'))")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM coach_sport WHERE coach_id IN (SELECT id FROM user_account WHERE email IN ('quan.coach@example.com', 'nam.coach@example.com', 'ha.coach@example.com'))")).isEqualTo(3);
        assertThat(count("SELECT COUNT(DISTINCT member_code) FROM member_profile WHERE user_id IN (SELECT id FROM user_account WHERE email IN ('an.gold@example.com', 'mai.standard@example.com', 'bao.nocard@example.com'))")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM member_card WHERE card_code IN ('DEMO-CARD-GOLD', 'DEMO-CARD-STANDARD')")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM member_card WHERE member_id = ?", id("bao.nocard@example.com"))).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_account u JOIN role r ON r.id = u.role_id WHERE u.email = 'manager@sportify.com' AND r.code = 'MANAGER' AND u.full_name = 'System Manager' AND u.status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id IN (OBJECT_ID('user_account'), OBJECT_ID('member_profile'), OBJECT_ID('coach_profile'), OBJECT_ID('coach_sport'), OBJECT_ID('member_card')) AND (is_disabled = 1 OR is_not_trusted = 1)")).isZero();
    }

    private void removeOnlyDemoFixtureRows() {
        // Only in this suite's isolated catalog and a rollback transaction; never shared seed cleanup.
        for (Account account : ACCOUNTS) {
            long id = id(account.email());
            jdbc.update("DELETE FROM member_card WHERE member_id = ?", id);
            jdbc.update("DELETE FROM coach_sport WHERE coach_id = ?", id);
            jdbc.update("DELETE FROM coach_profile WHERE user_id = ?", id);
            jdbc.update("DELETE FROM member_profile WHERE user_id = ?", id);
            jdbc.update("DELETE FROM user_account WHERE id = ?", id);
        }
    }

    private void unrelatedFixtures() {
        jdbc.update("INSERT INTO user_account (email, password_hash, full_name, role_id) SELECT 'unrelated.be07@example.org', ?, 'Unrelated fixture', id FROM role WHERE code = 'MEMBER'", encoder.encode("UnrelatedFixture2026!"));
        long id = id("unrelated.be07@example.org");
        jdbc.update("INSERT INTO member_profile (user_id, member_code) VALUES (?, 'BE07-UNRELATED')", id);
        jdbc.update("INSERT INTO member_card (card_code, member_id, tier_id, start_date) SELECT 'BE07-UNRELATED', ?, id, '20261001' FROM membership_card_tier WHERE code = 'STANDARD'", id);
        jdbc.update("INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, payment_time) VALUES ('BE07-UNRELATED', ?, 50, 'CASH', 'SUCCESS', SYSDATETIME())", id);
    }

    private long id(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void rollback(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            action.run();
        });
    }

    private void replay() {
        jdbc.execute(SQL);
    }

    private String snapshot() {
        // Compare full rows without printing hashes or other account values on assertion failures.
        StringBuilder data = new StringBuilder();
        for (String table : List.of("role", "sport", "membership_card_tier", "user_account", "member_profile", "coach_profile",
                "coach_sport", "coach_certification", "member_card", "payment", "invoice", "invoice_line")) {
            String order = switch (table) {
                case "member_profile", "coach_profile" -> "user_id";
                case "coach_sport" -> "coach_id, sport_id";
                default -> "id";
            };
            data.append(table).append(jdbc.queryForList("SELECT * FROM [" + table + "] ORDER BY " + order));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
