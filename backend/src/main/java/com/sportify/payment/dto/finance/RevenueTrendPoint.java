package com.sportify.payment.dto.finance;

import java.math.BigDecimal;

/** Revenue of one month (yyyy-MM) in the F3-08 trend chart. Months without payments are included with zero. */
public record RevenueTrendPoint(String month, BigDecimal revenue, long transactions) {
}
