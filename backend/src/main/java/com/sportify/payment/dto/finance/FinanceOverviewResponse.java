package com.sportify.payment.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3-08 Financial Overview for one month.
 * Revenue counts SUCCESS payments by payment_time. Pending counts PENDING payments created in the month.
 * Refunds are shown separately: COMPLETED refund requests whose last update falls in the month.
 * revenueChangePercent is null when the previous month had no revenue.
 */
public record FinanceOverviewResponse(
        String month,
        LocalDate from,
        LocalDate to,
        BigDecimal paidRevenue,
        long paidTransactions,
        BigDecimal averagePayment,
        BigDecimal pendingAmount,
        long pendingTransactions,
        BigDecimal refundedAmount,
        long refundedTransactions,
        String previousMonth,
        BigDecimal previousPaidRevenue,
        long previousPaidTransactions,
        BigDecimal revenueChange,
        BigDecimal revenueChangePercent
) {
}
