package com.sportify.payment.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the F3-09 transaction ledger: every payment status (PENDING, SUCCESS, FAILED, REFUNDED).
 * time is paymentTime once paid, otherwise createdAt; the ledger is ordered and filtered by it.
 */
public record FinanceTransactionResponse(
        Long id,
        String code,
        LocalDateTime time,
        LocalDateTime paymentTime,
        LocalDateTime createdAt,
        String status,
        String method,
        BigDecimal amount,
        String referenceCode,
        Long memberId,
        String memberCode,
        String memberName,
        String itemType,
        String itemCode,
        String itemName,
        String sportName,
        Long invoiceId,
        String invoiceNumber
) {
}
