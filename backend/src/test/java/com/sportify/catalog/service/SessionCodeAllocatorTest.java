package com.sportify.catalog.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SessionCodeAllocatorTest {
    private final LocalDate date = LocalDate.of(2026,11,3);

    @ParameterizedTest @CsvSource({"FOOTBALL,FB","SWIMMING,SW","BADMINTON,BM","VOLLEYBALL,VB","BASKETBALL,BK","TENNIS,TE"})
    void canonicalSports(String sport,String code) {
        assertThat(SessionCodeAllocator.format(date,sport,1)).isEqualTo("031126"+code+"01");
        assertThat(SessionCodeAllocator.format(date.plusDays(1),sport,99)).isEqualTo("041126"+code+"99");
    }
    @Test void rejectsUnsupportedDatesSportsAndOverflow() {
        for (int year : new int[]{1999,2100})
            assertThatThrownBy(()->SessionCodeAllocator.format(date.withYear(year),"FOOTBALL",1)).hasMessageContaining("2000-2099");
        for (int n : new int[]{0,100})
            assertThatThrownBy(()->SessionCodeAllocator.format(date,"FOOTBALL",n)).hasMessageContaining("99");
        assertThatThrownBy(()->SessionCodeAllocator.format(date,"Football",1)).hasMessageContaining("canonical sports");
        assertThat(SessionCodeAllocator.format(LocalDate.of(2000,2,29),"TENNIS",1)).isEqualTo("290200TE01");
    }
    @Test void applicationLockFailureStopsBeforeAnyCounterOrSessionWrite() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("sportify.session-identity"))).thenReturn(-1);
        assertThatThrownBy(()->new SessionCodeAllocator(jdbc).reserve(date,"FOOTBALL"))
            .isInstanceOf(com.sportify.core.exception.ConflictException.class).hasMessageContaining("retry the complete request");
        verify(jdbc).queryForObject(anyString(),eq(Integer.class),eq("sportify.session-identity"));
        verifyNoMoreInteractions(jdbc);
    }
}
