package com.sportify.core.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClockConfigTest {

    @Test
    void clock_UsesAsiaHoChiMinhZone() {
        ClockConfig clockConfig = new ClockConfig();
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), clockConfig.clock().getZone());
    }
}
