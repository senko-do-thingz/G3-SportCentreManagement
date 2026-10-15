package com.sportify.identity.service.impl;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.service.MemberSearchService;
import com.sportify.testsupport.FrontDeskFixtures;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SQL Server coverage; skipped locally when Docker is unavailable. */
@Transactional
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.show-sql=false"})
class MemberSearchIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MemberSearchService search;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    private FrontDeskFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new FrontDeskFixtures(jdbc, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "phone", "email", "name"})
    void searchMembers_whenAnySupportedFieldMatches_returnsMember(String field) {
        String query = switch (field) {
            case "code" -> fixtures.marker.toUpperCase(Locale.ROOT);
            case "phone" -> fixtures.phone;
            case "email" -> (fixtures.marker + "@example.test").toUpperCase(Locale.ROOT);
            default -> fixtures.marker + " ALICE";
        };
        var result = search.searchMembers(" " + query + " ", PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
        assertEquals(fixtures.memberId, result.getContent().getFirst().getUserId());
        assertNull(result.getContent().getFirst().getCardTier());
        assertEquals("ACTIVE", result.getContent().getFirst().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "[", "\\"})
    void searchMembers_whenInputHasSqlServerWildcards_matchesOnlyLiteralCharacters(String wildcard) {
        jdbc.update("UPDATE user_account SET full_name = ? WHERE id = ?", fixtures.marker + wildcard + " literal", fixtures.memberId);
        long control = fixtures.account(fixtures.marker + "control@example.test", fixtures.randomPhone(), fixtures.marker + "X literal");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                control, fixtures.marker + "other", fixtures.today);
        var result = search.searchMembers(fixtures.marker + wildcard, PageRequest.of(0, 20));
        assertEquals(List.of(fixtures.memberId), result.getContent().stream().map(MemberSearchResponse::getUserId).toList());
    }

    @Test
    void searchMembers_whenMultipleCardsExist_returnsOneRowWithHighestCurrentTierAndCorrectCount() {
        LocalDateTime now = fixtures.today.atStartOfDay();
        fixtures.card("STANDARD", "ACTIVE", fixtures.today.minusDays(5), null, now);
        fixtures.card("GOLD", "ACTIVE", fixtures.today.minusDays(3), fixtures.today.plusDays(30), now);
        fixtures.card("GOLD", "ACTIVE", fixtures.today.minusDays(2), fixtures.today.plusDays(30), now);
        fixtures.card("VIP", "ACTIVE", fixtures.today.plusDays(1), fixtures.today.plusDays(30), now);
        fixtures.card("VIP", "ACTIVE", fixtures.today.minusDays(30), fixtures.today.minusDays(1), now);
        fixtures.card("VIP", "PENDING_PAYMENT", fixtures.today, null, now);
        var result = search.searchMembers(fixtures.marker, PageRequest.of(0, 1, Sort.by("fullName")));
        assertEquals(1, result.getTotalElements());
        assertEquals("GOLD", result.getContent().getFirst().getCardTier());
        assertTrue(search.searchMembers(fixtures.marker, PageRequest.of(1, 1)).isEmpty());
    }

    @Test
    void searchMembers_whenManyRowsAreReturned_usesAtMostTwoQueriesIncludingCount() {
        for (int i = 0; i < 6; i++) {
            long id = fixtures.account(fixtures.marker + i + "@example.test", fixtures.randomPhone(), fixtures.marker + " Person " + i);
            jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                    id, fixtures.marker + i, fixtures.today);
        }
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = search.searchMembers(fixtures.marker, PageRequest.of(0, 3));
        assertEquals(7, result.getTotalElements());
        assertEquals(3, result.getNumberOfElements());
        // Guard against per-member lazy queries as the search page grows.
        assertEquals(2, statistics.getPrepareStatementCount());
    }

    @Test
    void searchMembers_whenIdAliasesAreSortedTogether_returns200OnSqlServer() throws Exception {
        mvc.perform(get("/api/v1/members").with(user("staff").roles("RECEPTIONIST"))
                        .param("query", fixtures.marker).param("sort", "id,asc", "userId,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(fixtures.memberId));
    }

}
