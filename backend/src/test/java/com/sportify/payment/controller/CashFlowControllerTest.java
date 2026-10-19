package com.sportify.payment.controller;

import com.sportify.core.exception.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.*;
import com.sportify.payment.dto.finance.CashFlowDtos.*;
import com.sportify.payment.service.FinanceReportService.CsvExport;
import com.sportify.payment.service.impl.CashFlowReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashFlowController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CashFlowControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CashFlowReportService service;
    @MockitoBean JwtService jwt;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService users;
    static Stream<String> routes() { return Stream.of("overview", "trend", "breakdown", "export").map(p -> "/api/v1/manager/finance/cash-flow/" + p); }
    static Stream<Arguments> forbidden() { return Stream.of("MEMBER", "COACH", "RECEPTIONIST").flatMap(r -> routes().map(p -> Arguments.of(r, p))); }
    CustomUserDetails actor(String role) {
        return new CustomUserDetails(UserAccount.builder().id(8L).email("synthetic@example.invalid").fullName("Synthetic")
                .role(Role.builder().code(role).name(role).build()).build());
    }
    @ParameterizedTest @MethodSource("routes") void anonymousGets401(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @ParameterizedTest @MethodSource("forbidden") void nonManagerGets403(String role, String path) throws Exception {
        mvc.perform(get(path).with(user(actor(role)))).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @ParameterizedTest @MethodSource("routes") void malformedTokenGets401(String path) throws Exception {
        when(jwt.extractUsername(anyString())).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed"));
        mvc.perform(get(path).header("Authorization", "Bearer invalid-token")).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void managerReceivesSignedNetAndCoverage() throws Exception {
        var coverage = new Coverage("VERIFIED_COMPLETE", Instant.parse("2026-01-01T00:00:00Z"), "EVENT_PERIOD",
                "Asia/Ho_Chi_Minh", "SYSTEM_RECORDED", Instant.parse("2026-05-01T00:00:00Z"));
        when(service.overview("2026-04")).thenReturn(new Overview("2026-04", LocalDate.of(2026,4,1), LocalDate.of(2026,4,30),
                BigDecimal.ZERO, new BigDecimal("100000.00"), new BigDecimal("-100000.00"), 0, 1, coverage));
        mvc.perform(get("/api/v1/manager/finance/cash-flow/overview?month=2026-04").with(user(actor("MANAGER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.netCashFlow").value(-100000))
                .andExpect(jsonPath("$.coverage.evidenceBasis").value("SYSTEM_RECORDED"));
    }
    @Test void unavailableExportIs409JsonWithoutAttachment() throws Exception {
        when(service.export(any(), any())).thenThrow(new ConflictException("Unavailable", "CASH_HISTORY_UNVERIFIED", Map.of("coverageStatus", "UNAVAILABLE")));
        mvc.perform(get("/api/v1/manager/finance/cash-flow/export").with(user(actor("MANAGER"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CASH_HISTORY_UNVERIFIED"))
                .andExpect(jsonPath("$.details.coverageStatus").value("UNAVAILABLE"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }
    @Test void managerExportUsesAuthenticatedSafeAttachment() throws Exception {
        when(service.export(any(), any())).thenReturn(new CsvExport("cash_flow_2026-04-01_2026-04-30.csv", new byte[]{1,2}));
        mvc.perform(get("/api/v1/manager/finance/cash-flow/export").with(user(actor("MANAGER"))))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("cash_flow_2026-04-01_2026-04-30.csv")));
    }
    @ParameterizedTest @ValueSource(strings={"breakdown?from=bad", "breakdown?groupBy=INVALID", "trend?months=bad"})
    void malformedParametersAre400(String path) throws Exception {
        mvc.perform(get("/api/v1/manager/finance/cash-flow/" + path).with(user(actor("MANAGER"))))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
}
