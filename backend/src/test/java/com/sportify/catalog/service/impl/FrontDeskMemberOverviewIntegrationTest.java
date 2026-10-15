package com.sportify.catalog.service.impl;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.FrontDeskCheckInResponse;
import com.sportify.catalog.dto.FrontDeskPackageResponse;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.service.FrontDeskMemberOverviewService;
import com.sportify.testsupport.FrontDeskFixtures;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQL Server coverage; skipped locally when Docker is unavailable. */
@Transactional
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.show-sql=false"})
class FrontDeskMemberOverviewIntegrationTest extends AbstractIntegrationTest {
    @Autowired private FrontDeskMemberOverviewService overviews;
    @Autowired private CheckInRepository checkIns;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    private FrontDeskFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new FrontDeskFixtures(jdbc, clock);
    }

    @Test
    void overview_whenCardsPackagesAndSevenCheckInsExist_returnsFiveMostRecentUsingFiveQueries() {
        LocalDateTime now = fixtures.today.atTime(10, 0);
        fixtures.card("STANDARD", "ACTIVE", fixtures.today.minusDays(1), null, now);
        long current = fixtures.card("GOLD", "ACTIVE", fixtures.today, fixtures.today.plusDays(30), now);
        long pending = fixtures.card("VIP", "PENDING_PAYMENT", fixtures.today, null, now);
        fixtures.card("GOLD", "PENDING_PAYMENT", fixtures.today, null, now.plusHours(1));
        long active = fixtures.registration("ACTIVE", now);
        long unpaid = fixtures.registration("PENDING_PAYMENT", now);
        fixtures.registration("CANCELLED", now);
        long expired = fixtures.registration("ACTIVE", now);
        jdbc.update("UPDATE sport_package_registration SET start_date = ?, end_date = ? WHERE id = ?",
                fixtures.today.minusDays(5), fixtures.today.minusDays(1), expired);
        List<Long> checkInIds = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            checkInIds.add(jdbc.queryForObject("""
                    INSERT INTO check_in(member_id, checked_in_at, result, recorded_by)
                    OUTPUT INSERTED.id VALUES (?, ?, 'ALLOWED', ?)
                    """, Long.class, fixtures.memberId, now.plusMinutes(i / 2), fixtures.memberId));
        }
        Collections.reverse(checkInIds);
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var overview = overviews.overview(fixtures.memberId);
        assertEquals(current, overview.currentCard().id());
        assertEquals("GOLD", overview.profile().getCardTier());
        assertEquals(pending, overview.pendingCardRequest().id());
        assertEquals(List.of(active), overview.activePackageRegistrations().stream().map(FrontDeskPackageResponse::id).toList());
        assertEquals(List.of(unpaid), overview.pendingPackageRegistrations().stream().map(FrontDeskPackageResponse::id).toList());
        assertEquals(checkInIds.subList(0, 5), overview.recentCheckIns().stream().map(FrontDeskCheckInResponse::id).toList());
        // Guard the fixed overview budget against per-package or per-check-in lazy queries.
        assertEquals(5, statistics.getPrepareStatementCount());
        assertEquals(5, checkIns.findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(fixtures.memberId).size());
    }

    @Test
    void overview_whenNoRelatedRows_returnsNullCardsAndEmptyLists() {
        var result = overviews.overview(fixtures.memberId);
        assertNull(result.currentCard());
        assertNull(result.pendingCardRequest());
        assertTrue(result.activePackageRegistrations().isEmpty());
        assertTrue(result.pendingPackageRegistrations().isEmpty());
        assertTrue(result.recentCheckIns().isEmpty());
    }

}
