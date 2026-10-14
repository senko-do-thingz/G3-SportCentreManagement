package com.sportify.payment.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.payment.dto.finance.FinanceOverviewResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownRow;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.dto.finance.RevenueTrendPoint;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.FinanceReportService;
import com.sportify.payment.service.FinanceReportService.CsvExport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerFinanceController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagerFinanceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FinanceReportService financeReportService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private CustomUserDetails userWithRole(String roleCode) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        return new CustomUserDetails(UserAccount.builder().id(8L).email(roleCode.toLowerCase() + "@sportify.com")
                .fullName("Test " + roleCode).role(role).build());
    }

    @ParameterizedTest(name = "{0} {1} -> 403")
    @CsvSource({
            "MEMBER, /api/v1/manager/finance/overview",
            "MEMBER, /api/v1/manager/finance/trend",
            "MEMBER, /api/v1/manager/finance/transactions",
            "MEMBER, /api/v1/manager/finance/revenue",
            "MEMBER, /api/v1/manager/finance/revenue/export",
            "RECEPTIONIST, /api/v1/manager/finance/overview",
            "RECEPTIONIST, /api/v1/manager/finance/transactions",
            "RECEPTIONIST, /api/v1/manager/finance/revenue/export",
            "COACH, /api/v1/manager/finance/overview",
            "COACH, /api/v1/manager/finance/revenue"
    })
    @DisplayName("Non-manager roles get 403 and the service is never called")
    void nonManager_Forbidden(String role, String url) throws Exception {
        mockMvc.perform(get(url).with(user(userWithRole(role))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(financeReportService);
    }

    @Test
    @DisplayName("No token is 401")
    void anonymous_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/manager/finance/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /overview?month=2026-10 as MANAGER returns the figures")
    void overview_Manager() throws Exception {
        when(financeReportService.overview("2026-10")).thenReturn(new FinanceOverviewResponse("2026-10",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("2000000.00"), 3,
                new BigDecimal("666666.67"), new BigDecimal("700000.00"), 1, new BigDecimal("200000.00"), 1,
                "2026-09", new BigDecimal("1600000.00"), 2, new BigDecimal("400000.00"), new BigDecimal("25.00")));

        mockMvc.perform(get("/api/v1/manager/finance/overview?month=2026-10").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidRevenue").value(2000000.00))
                .andExpect(jsonPath("$.paidTransactions").value(3))
                .andExpect(jsonPath("$.averagePayment").value(666666.67))
                .andExpect(jsonPath("$.revenueChangePercent").value(25.00))
                .andExpect(jsonPath("$.from").value("2026-10-01"));
    }

    @Test
    @DisplayName("GET /overview with a bad month is 400")
    void overview_BadMonth() throws Exception {
        when(financeReportService.overview("2026-13")).thenThrow(new BusinessRuleException("Month must use the format yyyy-MM"));

        mockMvc.perform(get("/api/v1/manager/finance/overview?month=2026-13").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /trend?months=6 returns the points")
    void trend_Manager() throws Exception {
        when(financeReportService.trend(6)).thenReturn(List.of(
                new RevenueTrendPoint("2026-09", new BigDecimal("1600000.00"), 2),
                new RevenueTrendPoint("2026-10", new BigDecimal("2000000.00"), 3)));

        mockMvc.perform(get("/api/v1/manager/finance/trend?months=6").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].month").value("2026-10"));
    }

    @Test
    @DisplayName("GET /transactions passes every filter to the service")
    void transactions_Filters() throws Exception {
        when(financeReportService.transactions(PaymentStatus.FAILED, PaymentMethod.CASH, InvoiceItemType.SPORT_PACKAGE,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), "Anh", 1, 10))
                .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get("/api/v1/manager/finance/transactions?status=FAILED&method=CASH&type=SPORT_PACKAGE"
                        + "&from=2026-10-01&to=2026-10-15&search=Anh&page=1&size=10").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
        verify(financeReportService).transactions(PaymentStatus.FAILED, PaymentMethod.CASH, InvoiceItemType.SPORT_PACKAGE,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), "Anh", 1, 10);
    }

    @Test
    @DisplayName("GET /transactions with an unknown status or type is 400")
    void transactions_BadEnum() throws Exception {
        mockMvc.perform(get("/api/v1/manager/finance/transactions?status=PAID").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/manager/finance/transactions?type=TOWEL").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(financeReportService);
    }

    @Test
    @DisplayName("GET /revenue defaults groupBy to ITEM_TYPE and returns rows with shares")
    void revenue_DefaultGroupBy() throws Exception {
        when(financeReportService.revenue(isNull(), isNull(), eq(RevenueGroupBy.ITEM_TYPE))).thenReturn(
                new RevenueBreakdownResponse(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), RevenueGroupBy.ITEM_TYPE,
                        new BigDecimal("2000000.00"), 3, List.of(
                        new RevenueBreakdownRow("MEMBERSHIP_CARD", "Membership card", new BigDecimal("1200000.00"), 1, new BigDecimal("60.00")),
                        new RevenueBreakdownRow("SPORT_PACKAGE", "Sport package", new BigDecimal("800000.00"), 2, new BigDecimal("40.00")))));

        mockMvc.perform(get("/api/v1/manager/finance/revenue").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("ITEM_TYPE"))
                .andExpect(jsonPath("$.rows[0].sharePercent").value(60.00))
                .andExpect(jsonPath("$.rows[1].label").value("Sport package"));
    }

    @Test
    @DisplayName("GET /revenue?groupBy=COLOR is 400")
    void revenue_BadGroupBy() throws Exception {
        mockMvc.perform(get("/api/v1/manager/finance/revenue?groupBy=COLOR").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /revenue/export returns text/csv UTF-8 as an attachment, bytes unchanged")
    void export_Manager() throws Exception {
        byte[] body = ("\ufeffPayment code,Member name\r\nPAY-0001,Nguy\u1ec5n V\u0103n \u00c1nh\r\n").getBytes(StandardCharsets.UTF_8);
        when(financeReportService.exportRevenue(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)))
                .thenReturn(new CsvExport("revenue_2026-10-01_2026-10-15.csv", body));

        mockMvc.perform(get("/api/v1/manager/finance/revenue/export?from=2026-10-01&to=2026-10-15")
                        .with(user(userWithRole("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"revenue_2026-10-01_2026-10-15.csv\""))
                .andExpect(content().bytes(body));
    }

    @Test
    @DisplayName("GET /revenue/export with a bad date is 400")
    void export_BadDate() throws Exception {
        mockMvc.perform(get("/api/v1/manager/finance/revenue/export?from=2026-15-01").with(user(userWithRole("MANAGER"))))
                .andExpect(status().isBadRequest());
        verify(financeReportService, org.mockito.Mockito.never()).exportRevenue(any(), any());
    }
}
