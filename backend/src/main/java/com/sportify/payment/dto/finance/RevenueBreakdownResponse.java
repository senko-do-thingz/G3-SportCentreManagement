package com.sportify.payment.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** F3-10 Revenue Reports: SUCCESS payments with payment_time between from and to (inclusive dates), grouped. */
public record RevenueBreakdownResponse(
        LocalDate from,
        LocalDate to,
        RevenueGroupBy groupBy,
        BigDecimal totalRevenue,
        long totalTransactions,
        List<RevenueBreakdownRow> rows
) {
}
