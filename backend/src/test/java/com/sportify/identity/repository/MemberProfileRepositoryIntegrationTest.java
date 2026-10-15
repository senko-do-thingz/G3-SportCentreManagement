package com.sportify.identity.repository;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.service.FrontDeskQueueService;
import com.sportify.catalog.service.FrontDeskMemberOverviewService;
import com.sportify.identity.dto.*;
import com.sportify.identity.service.FrontDeskMemberService;
import com.sportify.identity.service.MemberSearchService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SQL Server query, mapping and transaction coverage; skipped locally when Docker is unavailable. */
@Transactional
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.show-sql=false"})
class MemberProfileRepositoryIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MemberSearchService search;
    @Autowired private FrontDeskMemberService members;
    @Autowired private FrontDeskMemberOverviewService overviews;
    @Autowired private FrontDeskQueueService queues;
    @Autowired private CheckInRepository checkIns;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PasswordEncoder encoder;
    @Autowired private Clock clock;
    @Autowired private MockMvc mvc;
    private final String marker = "be05" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private long memberId;
    private LocalDate today;
    private int serial;
    private String phone;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        phone = randomPhone();
        memberId = account(marker + "@example.test", phone, marker + " Alice Smith");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                memberId, marker, today);
    }

    private String randomPhone() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
    }

    private long account(String email, String accountPhone, String name) {
        return jdbc.queryForObject("""
                INSERT INTO user_account(email, phone, full_name, password_hash, role_id, status)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, 'integration-test-hash', (SELECT id FROM role WHERE code = 'MEMBER'), 'ACTIVE')
                """, Long.class, email, accountPhone, name);
    }

    private long card(String tier, String status, LocalDate start, LocalDate end, LocalDateTime requestedAt) {
        return jdbc.queryForObject("""
                INSERT INTO member_card(card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at)
                OUTPUT INSERTED.id VALUES (?, ?, (SELECT id FROM membership_card_tier WHERE code = ?), ?, ?, ?, 250000, ?)
                """, Long.class, marker + ++serial, memberId, tier, start, end, status, requestedAt);
    }

    private long registration(String status, LocalDateTime requestedAt) {
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel,
                    original_price, discount_percentage, paid_amount, total_sessions, remaining_sessions,
                    start_date, end_date, status, created_by_user_id, created_at)
                OUTPUT INSERTED.id VALUES (?, ?, (SELECT TOP (1) id FROM sport_package ORDER BY id),
                    'RECEPTION', 100000, 0, 100000, 8, 8, ?, ?, ?, ?, ?)
                """, Long.class, marker + ++serial, memberId, today.minusDays(1), today.plusDays(30),
                status, memberId, requestedAt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "phone", "email", "name"})
    void searchMembers_whenAnySupportedFieldMatches_returnsMember(String field) {
        String query = switch (field) {
            case "code" -> marker.toUpperCase(Locale.ROOT);
            case "phone" -> phone;
            case "email" -> (marker + "@example.test").toUpperCase(Locale.ROOT);
            default -> marker + " ALICE";
        };
        var result = search.searchMembers(" " + query + " ", PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
        assertEquals(memberId, result.getContent().getFirst().getUserId());
        assertNull(result.getContent().getFirst().getCardTier());
        assertEquals("ACTIVE", result.getContent().getFirst().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "[", "\\"})
    void searchMembers_whenInputHasSqlServerWildcards_matchesOnlyLiteralCharacters(String wildcard) {
        jdbc.update("UPDATE user_account SET full_name = ? WHERE id = ?", marker + wildcard + " literal", memberId);
        long control = account(marker + "control@example.test", randomPhone(), marker + "X literal");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                control, marker + "other", today);
        var result = search.searchMembers(marker + wildcard, PageRequest.of(0, 20));
        assertEquals(List.of(memberId), result.getContent().stream().map(MemberSearchResponse::getUserId).toList());
    }

    @Test
    void searchMembers_whenMultipleCardsExist_returnsOneRowWithHighestCurrentTierAndCorrectCount() {
        LocalDateTime now = today.atStartOfDay();
        card("STANDARD", "ACTIVE", today.minusDays(5), null, now);
        card("GOLD", "ACTIVE", today.minusDays(3), today.plusDays(30), now);
        card("GOLD", "ACTIVE", today.minusDays(2), today.plusDays(30), now);
        card("VIP", "ACTIVE", today.plusDays(1), today.plusDays(30), now);
        card("VIP", "ACTIVE", today.minusDays(30), today.minusDays(1), now);
        card("VIP", "PENDING_PAYMENT", today, null, now);
        var result = search.searchMembers(marker, PageRequest.of(0, 1, Sort.by("fullName")));
        assertEquals(1, result.getTotalElements());
        assertEquals("GOLD", result.getContent().getFirst().getCardTier());
        assertTrue(search.searchMembers(marker, PageRequest.of(1, 1)).isEmpty());
    }

    @Test
    void searchMembers_whenManyRowsAreReturned_usesAtMostTwoQueriesIncludingCount() {
        for (int i = 0; i < 6; i++) {
            long id = account(marker + i + "@example.test", randomPhone(), marker + " Person " + i);
            jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                    id, marker + i, today);
        }
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = search.searchMembers(marker, PageRequest.of(0, 3));
        assertEquals(7, result.getTotalElements());
        assertEquals(3, result.getNumberOfElements());
        assertEquals(2, statistics.getPrepareStatementCount());
    }

    @Test
    void pendingQueues_whenOtherStatusesExist_returnOnlyPendingOldestFirstWithStableTies() {
        LocalDateTime oldest = LocalDateTime.of(2001, 1, 1, 0, 0);
        long firstCard = card("GOLD", "PENDING_PAYMENT", today, null, oldest);
        long secondCard = card("VIP", "PENDING_PAYMENT", today, null, oldest);
        for (String status : List.of("ACTIVE", "EXPIRED", "CANCELLED", "REPLACED")) {
            card("STANDARD", status, today, null, oldest.minusDays(1));
        }
        long firstPackage = registration("PENDING_PAYMENT", oldest);
        long secondPackage = registration("PENDING_PAYMENT", oldest);
        for (String status : List.of("ACTIVE", "EXPIRED", "CANCELLED", "REFUNDED")) {
            registration(status, oldest.minusDays(1));
        }
        var cardPage = queues.pendingCards(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertEquals(firstCard, cardPage.getContent().getFirst().id());
        assertEquals(secondCard, queues.pendingCards(PageRequest.of(1, 1)).getContent().getFirst().id());
        assertTrue(cardPage.getTotalElements() >= 2);
        assertEquals("PENDING_PAYMENT", cardPage.getContent().getFirst().status().name());
        assertEquals(marker, cardPage.getContent().getFirst().memberCode());
        assertEquals(marker + " Alice Smith", cardPage.getContent().getFirst().memberName());
        assertEquals(0, new BigDecimal("250000").compareTo(cardPage.getContent().getFirst().amount()));
        assertEquals(oldest, cardPage.getContent().getFirst().requestedAt());
        var packagePage = queues.pendingPackages(PageRequest.of(0, 1));
        assertEquals(firstPackage, packagePage.getContent().getFirst().id());
        assertEquals(secondPackage, queues.pendingPackages(PageRequest.of(1, 1)).getContent().getFirst().id());
        assertEquals("PENDING_PAYMENT", packagePage.getContent().getFirst().status().name());
        assertNotNull(packagePage.getContent().getFirst().packageName());
        assertEquals(0, new BigDecimal("100000").compareTo(packagePage.getContent().getFirst().amount()));
    }

    @Test
    void overview_whenCardsPackagesAndSevenCheckInsExist_returnsFiveMostRecentUsingFiveQueries() {
        LocalDateTime now = today.atTime(10, 0);
        card("STANDARD", "ACTIVE", today.minusDays(1), null, now);
        long current = card("GOLD", "ACTIVE", today, today.plusDays(30), now);
        long pending = card("VIP", "PENDING_PAYMENT", today, null, now);
        card("GOLD", "PENDING_PAYMENT", today, null, now.plusHours(1));
        long active = registration("ACTIVE", now);
        long unpaid = registration("PENDING_PAYMENT", now);
        registration("CANCELLED", now);
        long expired = registration("ACTIVE", now);
        jdbc.update("UPDATE sport_package_registration SET start_date = ?, end_date = ? WHERE id = ?",
                today.minusDays(5), today.minusDays(1), expired);
        List<Long> checkInIds = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            checkInIds.add(jdbc.queryForObject("""
                    INSERT INTO check_in(member_id, checked_in_at, result, recorded_by)
                    OUTPUT INSERTED.id VALUES (?, ?, 'ALLOWED', ?)
                    """, Long.class, memberId, now.plusMinutes(i / 2), memberId));
        }
        Collections.reverse(checkInIds);
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var overview = overviews.overview(memberId);
        assertEquals(current, overview.currentCard().id());
        assertEquals("GOLD", overview.profile().getCardTier());
        assertEquals(pending, overview.pendingCardRequest().id());
        assertEquals(List.of(active), overview.activePackageRegistrations().stream().map(FrontDeskPackageResponse::id).toList());
        assertEquals(List.of(unpaid), overview.pendingPackageRegistrations().stream().map(FrontDeskPackageResponse::id).toList());
        assertEquals(checkInIds.subList(0, 5), overview.recentCheckIns().stream().map(FrontDeskCheckInResponse::id).toList());
        assertEquals(5, statistics.getPrepareStatementCount());
        assertEquals(5, checkIns.findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(memberId).size());
    }

    @Test
    void overview_whenNoRelatedRows_returnsNullCardsAndEmptyLists() {
        var result = overviews.overview(memberId);
        assertNull(result.currentCard());
        assertNull(result.pendingCardRequest());
        assertTrue(result.activePackageRegistrations().isEmpty());
        assertTrue(result.pendingPackageRegistrations().isEmpty());
        assertTrue(result.recentCheckIns().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "phone"})
    void create_whenExistingIdentityIsUsed_returns409(String field) throws Exception {
        String email = field.equals("email") ? marker.toUpperCase(Locale.ROOT) + "@EXAMPLE.TEST" : marker + "new@example.test";
        String requestPhone = field.equals("phone") ? phone : randomPhone();
        mvc.perform(post("/api/v1/members").with(user("desk").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"New Member","phone":" %s ","email":" %s "}
                                """.formatted(requestPhone, email)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "phone"})
    void uniqueConstraints_whenPreCheckIsBypassed_rejectDuplicateIdentity(String field) {
        String email = field.equals("email") ? marker + "@example.test" : marker + "race@example.test";
        String duplicatePhone = field.equals("phone") ? phone : randomPhone();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> account(email, duplicatePhone, "Concurrent identity"));
    }

    @Test
    void create_whenSuccessful_persistsOnlyHashAndGeneratedMemberCode() {
        String newPhone = randomPhone();
        var response = members.create(new MemberCreateRequest(" New Member ", newPhone, marker + "new@example.test"));
        assertTrue(response.memberCode().matches("MEM-\\d{4,}"));
        String stored = jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id = ?", String.class, response.userId());
        assertNotEquals(response.temporaryPassword(), stored);
        assertTrue(encoder.matches(response.temporaryPassword(), stored));
        assertEquals("New Member", overviews.overview(response.userId()).profile().getFullName());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_whenConcurrentRequestsUseDifferentIdentities_generatesDistinctSequenceCodes() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> members.create(new MemberCreateRequest("Concurrent First",
                    randomPhone(), marker + "first@example.test")));
            var second = executor.submit(() -> members.create(new MemberCreateRequest("Concurrent Second",
                    randomPhone(), marker + "second@example.test")));
            assertNotEquals(first.get(30, TimeUnit.SECONDS).memberCode(), second.get(30, TimeUnit.SECONDS).memberCode());
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(40, TimeUnit.SECONDS));
            jdbc.update("DELETE FROM member_profile WHERE user_id IN (SELECT id FROM user_account WHERE email LIKE ?)", marker + "%");
            jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
        }
    }
}
