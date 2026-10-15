package com.sportify.catalog.service.impl;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.service.FrontDeskQueueService;
import com.sportify.testsupport.FrontDeskFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQL Server coverage; skipped locally when Docker is unavailable. */
@Transactional
class FrontDeskQueueIntegrationTest extends AbstractIntegrationTest {
    @Autowired private FrontDeskQueueService queues;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    private FrontDeskFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new FrontDeskFixtures(jdbc, clock);
    }

    @Test
    void pendingQueues_whenOtherStatusesExist_returnOnlyPendingOldestFirstWithStableTies() {
        LocalDateTime oldest = LocalDateTime.of(2001, 1, 1, 0, 0);
        long firstCard = fixtures.card("GOLD", "PENDING_PAYMENT", fixtures.today, null, oldest);
        long secondCard = fixtures.card("VIP", "PENDING_PAYMENT", fixtures.today, null, oldest);
        for (String status : List.of("ACTIVE", "EXPIRED", "CANCELLED", "REPLACED")) {
            fixtures.card("STANDARD", status, fixtures.today, null, oldest.minusDays(1));
        }
        long firstPackage = fixtures.registration("PENDING_PAYMENT", oldest);
        long secondPackage = fixtures.registration("PENDING_PAYMENT", oldest);
        for (String status : List.of("ACTIVE", "EXPIRED", "CANCELLED", "REFUNDED")) {
            fixtures.registration(status, oldest.minusDays(1));
        }
        var cardPage = queues.pendingCards(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertEquals(firstCard, cardPage.getContent().getFirst().id());
        assertEquals(secondCard, queues.pendingCards(PageRequest.of(1, 1)).getContent().getFirst().id());
        assertTrue(cardPage.getTotalElements() >= 2);
        assertEquals("PENDING_PAYMENT", cardPage.getContent().getFirst().status().name());
        assertEquals(fixtures.marker, cardPage.getContent().getFirst().memberCode());
        assertEquals(fixtures.marker + " Alice Smith", cardPage.getContent().getFirst().memberName());
        assertEquals(0, new BigDecimal("250000").compareTo(cardPage.getContent().getFirst().amount()));
        assertEquals(oldest, cardPage.getContent().getFirst().requestedAt());
        var packagePage = queues.pendingPackages(PageRequest.of(0, 1));
        assertEquals(firstPackage, packagePage.getContent().getFirst().id());
        assertEquals(secondPackage, queues.pendingPackages(PageRequest.of(1, 1)).getContent().getFirst().id());
        assertEquals("PENDING_PAYMENT", packagePage.getContent().getFirst().status().name());
        assertNotNull(packagePage.getContent().getFirst().packageName());
        assertEquals(0, new BigDecimal("100000").compareTo(packagePage.getContent().getFirst().amount()));
    }

}
