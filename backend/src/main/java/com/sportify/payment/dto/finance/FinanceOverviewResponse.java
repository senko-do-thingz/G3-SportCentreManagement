package com.sportify.payment.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3-08 Financial Overview for one month.
 * paidRevenue is gross collected revenue: money received in the month by payment_time, including payments refunded
 * later (a closed month never changes). Pending counts PENDING payments created in the month.
 * Refunds are a separate deduction: COMPLETED requests by REFUND_COMPLETED log time, with legacy timestamp fallback.
 * netRevenue = paidRevenue - refundedAmount for the same month (can be negative in a month with large refunds).
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
        long simulatedTransactions,
        BigDecimal netRevenue
) {
    @com.fasterxml.jackson.annotation.JsonCreator
    public FinanceOverviewResponse {
        if (netRevenue == null && paidRevenue != null && refundedAmount != null) {
            netRevenue = paidRevenue.subtract(refundedAmount);
        }
    }
    /** Net revenue derived as paidRevenue - refundedAmount. */
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
            BigDecimal revenueChangePercent,
            BigDecimal simulatedRevenue,
            long simulatedTransactions
    ) {
        this(month, from, to, paidRevenue, paidTransactions, averagePayment,
                pendingAmount, pendingTransactions, refundedAmount, refundedTransactions,
                previousMonth, previousPaidRevenue, previousPaidTransactions,
                revenueChange, revenueChangePercent, simulatedRevenue, simulatedTransactions, null);
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
