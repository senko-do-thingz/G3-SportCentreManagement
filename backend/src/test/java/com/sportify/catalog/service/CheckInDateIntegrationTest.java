package com.sportify.catalog.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.testsupport.FrontDeskFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
class CheckInDateIntegrationTest extends AbstractIntegrationTest {
    @Autowired private CheckInService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    private long insert(FrontDeskFixtures fixture, LocalDateTime time) {
        return jdbc.queryForObject("""
                INSERT INTO check_in(member_id, checked_in_at, result, recorded_by)
                OUTPUT INSERTED.id VALUES (?, ?, 'DENIED', ?)
                """, Long.class, fixture.memberId, time, fixture.memberId);
    }

    @Test
    void includesMidnightExcludesNextDayAndOrdersTiesById() {
        var fixture = new FrontDeskFixtures(jdbc, clock);
        var other = new FrontDeskFixtures(jdbc, clock);
        var start = fixture.today.atStartOfDay();
        insert(fixture, start.minusSeconds(1));
        long midnight = insert(fixture, start);
        long first = insert(fixture, start.plusHours(23));
        long second = insert(fixture, start.plusHours(23));
        insert(fixture, start.plusDays(1));
        long otherId = insert(other, start.plusHours(12));
        assertEquals(List.of(second, first, midnight), service.getCheckInsOn(fixture.today, fixture.memberId)
                .stream().map(CheckInResponse::getId).toList());
        // Query a far future isolated day to verify the all-member path without seeded rows.
        var isolated = java.time.LocalDate.of(9000, 1, 1);
        jdbc.update("UPDATE check_in SET checked_in_at = ? WHERE id IN (?, ?)", isolated.atStartOfDay(), first, otherId);
        assertEquals(List.of(otherId, first), service.getCheckInsOn(isolated, null)
                .stream().map(CheckInResponse::getId).toList());
    }
}
