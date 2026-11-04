package com.sportify.demoqr.dto;

public record LocalDemoSettlementResponse(
        long paymentId,
        String status,
        String settlementType,
        String settlementProvider,
        Long simulatedTransactionId,
        String referenceCode,
        Long invoiceId,
        String invoiceNumber
) {}
