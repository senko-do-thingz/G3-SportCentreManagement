package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class ManagerAttendanceReadServiceTest {
    @Test void ratesOnlyUseCompletedConfirmedRostersAndKeepZeroDenominatorUndefined() {
        assertThat(ManagerAttendanceReadService.rate("COMPLETED",14,11,1)).isEqualByComparingTo("85.7");
        assertThat(ManagerAttendanceReadService.rate("COMPLETED",0,0,0)).isNull();
        assertThat(ManagerAttendanceReadService.rate("PUBLISHED",14,11,1)).isNull();
    }
    @Test void queriesHaveBoundedRangesAndPositiveFilters() {
        var from=LocalDate.of(2026,11,1);
        assertThatCode(() -> ManagerAttendanceReadService.validateRange(from,from.plusDays(30),null,1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ManagerAttendanceReadService.validateRange(from,from.plusDays(31),null,null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ManagerAttendanceReadService.validateRange(from,from.minusDays(1),null,null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ManagerAttendanceReadService.validateRange(from,from,0L,null)).isInstanceOf(BusinessRuleException.class);
    }
}
