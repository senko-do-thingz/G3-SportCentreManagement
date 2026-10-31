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
        String invoiceNumber,
        String settlementType,
        String settlementProvider,
        Long simulatedTransactionId
) {
    @com.fasterxml.jackson.annotation.JsonCreator
    public FinanceTransactionResponse {
    }
    public FinanceTransactionResponse(
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
        this(id, code, time, paymentTime, createdAt, status, method, amount, referenceCode,
                memberId, memberCode, memberName, itemType, itemCode, itemName, sportName,
                invoiceId, invoiceNumber, null, null, null);
    }
}
