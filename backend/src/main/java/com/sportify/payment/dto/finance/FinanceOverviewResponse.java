package com.sportify.payment.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3-08 Financial Overview for one month.
 * Revenue counts SUCCESS payments by payment_time. Pending counts PENDING payments created in the month.
 * Refunds are shown separately: COMPLETED requests by REFUND_COMPLETED log time, with legacy timestamp fallback.
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
        BigDecimal revenueChangePercent,
        BigDecimal simulatedRevenue,
        long simulatedTransactions
) {
    @com.fasterxml.jackson.annotation.JsonCreator
    public FinanceOverviewResponse {
    }
    public FinanceOverviewResponse(
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
        this(month, from, to, paidRevenue, paidTransactions, averagePayment,
                pendingAmount, pendingTransactions, refundedAmount, refundedTransactions,
                previousMonth, previousPaidRevenue, previousPaidTransactions,
                revenueChange, revenueChangePercent, BigDecimal.ZERO, 0L);
    }
}
