package com.sportify.catalog.repository;

import com.sportify.catalog.entity.PackageRegistrationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCancellationRepositoryTest {
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void cancelConfirmedBookings_refundStatementRequiresActiveButCancellationCoversAllConfirmedBookings() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 10, 0);
        when(jdbc.update(anyString(), eq(now), eq(100L), eq(PackageRegistrationStatus.ACTIVE.name()))).thenReturn(1);
        when(jdbc.update(anyString(), eq(40L), eq(now), eq("Weather"), eq(now), eq(100L))).thenReturn(2);
        SessionCancellationRepository repository = new SessionCancellationRepository(jdbc);

        assertEquals(2, repository.cancelConfirmedBookings(100L, 40L, "Weather", now));

        var order = inOrder(jdbc);
        ArgumentCaptor<String> lockSql = ArgumentCaptor.forClass(String.class);
        order.verify(jdbc).queryForObject(lockSql.capture(), eq(Integer.class), eq(100L));
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
}
