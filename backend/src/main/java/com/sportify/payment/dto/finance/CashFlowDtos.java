package com.sportify.payment.dto.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CashFlowDtos {
    private CashFlowDtos() { }

    public record Totals(BigDecimal grossCashReceived, BigDecimal completedRefundCashOut,
                         long collectionCount, long completedRefundCount) {
        public BigDecimal netCashFlow() { return grossCashReceived.subtract(completedRefundCashOut); }
    }
    public record Coverage(String status, Instant coverageStart, String timeBasis, String timezone,
                           String evidenceBasis, Instant asOf) { }
    public record Overview(String month, LocalDate from, LocalDate to, BigDecimal grossCashReceived,
                           BigDecimal completedRefundCashOut, BigDecimal netCashFlow, long collectionCount,
                           long completedRefundCount, Coverage coverage) { }
    public record Group(String key, String label, BigDecimal grossCashReceived, BigDecimal completedRefundCashOut,
                        BigDecimal netCashFlow, long collectionCount, long completedRefundCount) { }
    public record Breakdown(LocalDate from, LocalDate to, RevenueGroupBy groupBy, BigDecimal grossCashReceived,
                            BigDecimal completedRefundCashOut, BigDecimal netCashFlow, long collectionCount,
                            long completedRefundCount, Coverage coverage, List<Group> rows) { }
    public record Event(long id, String type, Instant time, Long paymentId, Long refundRequestId,
                        BigDecimal amount, String method, String itemType, String sportKey, String sportName) {
        public BigDecimal cashImpact() { return "COLLECTION".equals(type) ? amount : amount.negate(); }
    }
}
