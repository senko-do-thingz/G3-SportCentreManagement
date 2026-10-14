package com.sportify.payment.dto.finance;

import java.math.BigDecimal;

/**
 * One group of the F3-10 revenue report. key is the item type, method or sport id (as text); null groups payments
 * that have no value for the grouping (for example a membership card has no sport). sharePercent has 2 decimals and
 * the shares of all rows add up to exactly 100.00 when there is revenue.
 */
public record RevenueBreakdownRow(String key, String label, BigDecimal revenue, long transactions, BigDecimal sharePercent) {
}
