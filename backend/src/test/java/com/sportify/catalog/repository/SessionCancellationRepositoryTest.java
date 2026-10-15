package com.sportify.catalog.repository;

import com.sportify.catalog.entity.PackageRegistrationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCancellationRepositoryTest {
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void cancelConfirmedBookings_refundStatementRequiresActiveButCancellationCoversAllConfirmedBookings() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 10, 0);
        ResultSet groupedRow = mock(ResultSet.class);
        when(groupedRow.getLong("package_registration_id")).thenReturn(50L);
        when(groupedRow.getInt("package_bookings")).thenReturn(1);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<SessionCancellationRepository.PackageBookings>>any(), eq(100L)))
                .thenAnswer(invocation -> {
                    RowMapper<SessionCancellationRepository.PackageBookings> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(groupedRow, 0));
                });
        when(jdbc.update(anyString(), eq(now), eq(100L), eq(PackageRegistrationStatus.ACTIVE.name()))).thenReturn(1);
        when(jdbc.update(anyString(), eq(40L), eq(now), eq("Weather"), eq(now), eq(100L))).thenReturn(2);
        SessionCancellationRepository repository = new SessionCancellationRepository(jdbc);

        var result = repository.cancelConfirmedBookings(100L, 40L, "Weather", now);
        assertEquals(2, result.cancelledCount(), "All cancellations include legacy null-package bookings");
        assertEquals(List.of(new SessionCancellationRepository.PackageBookings(50L, 1)), result.registrations());

        var order = inOrder(jdbc);
        ArgumentCaptor<String> lockSql = ArgumentCaptor.forClass(String.class);
        order.verify(jdbc).query(lockSql.capture(), any(RowMapper.class), eq(100L));
        assertTrue(lockSql.getValue().contains("WITH (UPDLOCK, HOLDLOCK)"));

        ArgumentCaptor<String> refundSql = ArgumentCaptor.forClass(String.class);
        order.verify(jdbc).update(refundSql.capture(), eq(now), eq(100L), eq(PackageRegistrationStatus.ACTIVE.name()));
        assertEquals("ACTIVE", PackageRegistrationStatus.ACTIVE.name());
        assertTrue(refundSql.getValue().contains("WHERE r.status = ?"));
        assertTrue(refundSql.getValue().contains("THEN r.total_sessions"));
        assertTrue(refundSql.getValue().contains("package_registration_id IS NOT NULL"));

        ArgumentCaptor<String> cancellationSql = ArgumentCaptor.forClass(String.class);
        order.verify(jdbc).update(cancellationSql.capture(), eq(40L), eq(now), eq("Weather"), eq(now), eq(100L));
        assertTrue(cancellationSql.getValue().contains("WHERE session_id = ? AND status = 'CONFIRMED'"));
        assertFalse(cancellationSql.getValue().contains("r.status"));
        assertFalse(cancellationSql.getValue().contains("sport_package_registration"));
        order.verifyNoMoreInteractions();
    }

    @Test
    void cancelConfirmedBookings_withoutBookingsReturnsZeroAndEmptyRegistrationTrail() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 10, 0);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<SessionCancellationRepository.PackageBookings>>any(), eq(100L)))
                .thenReturn(List.of());
        SessionCancellationRepository repository = new SessionCancellationRepository(jdbc);

        var result = repository.cancelConfirmedBookings(100L, 40L, "Weather", now);

        assertEquals(0, result.cancelledCount());
        assertEquals(List.of(), result.registrations());
        var order = inOrder(jdbc);
        order.verify(jdbc).query(anyString(), any(RowMapper.class), eq(100L));
        order.verify(jdbc).update(anyString(), eq(now), eq(100L), eq("ACTIVE"));
        order.verify(jdbc).update(anyString(), eq(40L), eq(now), eq("Weather"), eq(now), eq(100L));
        order.verifyNoMoreInteractions();
    }
}
