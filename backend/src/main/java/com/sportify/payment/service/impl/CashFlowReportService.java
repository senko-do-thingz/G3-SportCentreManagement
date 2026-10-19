package com.sportify.payment.service.impl;

import com.sportify.payment.dto.finance.CashFlowDtos.*;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.repository.CashFlowRepository;
import com.sportify.payment.service.FinanceReportService.CsvExport;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static com.sportify.payment.service.FinanceReportService.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
public class CashFlowReportService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CashFlowRepository repository;
    private final Clock clock;
    public CashFlowReportService(CashFlowRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    public Overview overview(String month) {
        YearMonth period = month(month);
        LocalDate from = period.atDay(1), to = period.atEndOfMonth();
        validDate(from); validDate(to);
        Coverage coverage = coverage(from, to);
        return overview(period, from, to, repository.totals(start(from), start(to.plusDays(1))), coverage);
    }

    public List<Overview> trend(Integer months) {
        int count = months == null ? DEFAULT_TREND_MONTHS : months;
        if (count < 1 || count > MAX_TREND_MONTHS) throw new BusinessRuleException("Months must be between 1 and " + MAX_TREND_MONTHS);
        YearMonth last = YearMonth.now(clock.withZone(ZONE)), first = last.minusMonths(count - 1L);
        validDate(first.atDay(1)); validDate(last.atEndOfMonth());
        Coverage coverage = coverage(first.atDay(1), last.atEndOfMonth());
        List<Overview> result = new ArrayList<>();
        for (YearMonth period = first; !period.isAfter(last); period = period.plusMonths(1)) {
            LocalDate from = period.atDay(1), to = period.atEndOfMonth();
            result.add(overview(period, from, to, repository.totals(start(from), start(to.plusDays(1))), coverage));
        }
        return List.copyOf(result);
    }

    public Breakdown breakdown(LocalDate from, LocalDate to, RevenueGroupBy groupBy) {
        LocalDate[] range = range(from, to);
        if (groupBy == null) throw new BusinessRuleException("groupBy is required");
        Coverage coverage = coverage(range[0], range[1]);
        Instant start = start(range[0]), end = start(range[1].plusDays(1));
        Totals total = repository.totals(start, end);
        List<Group> rows = repository.groups(start, end, groupBy);
        BigDecimal gross = BigDecimal.ZERO, refunds = BigDecimal.ZERO;
        long collections = 0, payouts = 0;
        for (Group row : rows) {
            gross = gross.add(row.grossCashReceived()); refunds = refunds.add(row.completedRefundCashOut());
            collections += row.collectionCount(); payouts += row.completedRefundCount();
        }
        if (gross.compareTo(total.grossCashReceived()) != 0 || refunds.compareTo(total.completedRefundCashOut()) != 0
                || collections != total.collectionCount() || payouts != total.completedRefundCount()) {
            throw new IllegalStateException("Cash grouping failed reconciliation");
        }
        return new Breakdown(range[0], range[1], groupBy, total.grossCashReceived(), total.completedRefundCashOut(),
                total.netCashFlow(), total.collectionCount(), total.completedRefundCount(), coverage, List.copyOf(rows));
    }

    public CsvExport export(LocalDate from, LocalDate to) {
        LocalDate[] range = range(from, to);
        coverage(range[0], range[1]);
        Instant start = start(range[0]), end = start(range[1].plusDays(1));
        Totals expected = repository.totals(start, end);
        if (expected.collectionCount() + expected.completedRefundCount() > MAX_EXPORT_ROWS) {
            throw new BusinessRuleException("The export exceeds the limit of " + MAX_EXPORT_ROWS + "; choose a shorter range");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
        out.writeBytes(("event_id,event_time_utc,event_type,payment_id,refund_request_id,amount,cash_impact,provenance,"
                + "original_method,original_item_type,original_sport_key,original_sport_name\r\n").getBytes(StandardCharsets.UTF_8));
        int[] written = {0};
        BigDecimal[] sums = {BigDecimal.ZERO, BigDecimal.ZERO};
        long[] counts = {0, 0};
        repository.events(start, end, MAX_EXPORT_ROWS + 1, row -> {
            if (++written[0] > MAX_EXPORT_ROWS) throw new BusinessRuleException("The export exceeds the limit of " + MAX_EXPORT_ROWS);
            int kind = "COLLECTION".equals(row.type()) ? 0 : 1;
            sums[kind] = sums[kind].add(row.amount()); counts[kind]++;
            String line = row.id() + "," + row.time() + "," + row.type() + "," + id(row.paymentId()) + ","
                    + id(row.refundRequestId()) + "," + row.amount().toPlainString() + "," + row.cashImpact().toPlainString()
                    + ",LIVE_SERVICE," + text(row.method()) + "," + text(row.itemType()) + "," + text(row.sportKey()) + "," + text(row.sportName()) + "\r\n";
            out.writeBytes(line.getBytes(StandardCharsets.UTF_8));
        });
        if (counts[0] != expected.collectionCount() || counts[1] != expected.completedRefundCount()
                || sums[0].compareTo(expected.grossCashReceived()) != 0 || sums[1].compareTo(expected.completedRefundCashOut()) != 0) {
            throw new IllegalStateException("Cash export failed reconciliation");
        }
        return new CsvExport("cash_flow_" + range[0] + "_" + range[1] + ".csv", out.toByteArray());
    }

    private Coverage coverage(LocalDate from, LocalDate to) {
        Instant cutover = repository.cutover();
        if (cutover == null || start(from).isBefore(cutover) || !repository.guardsActive()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("coverageStatus", "UNAVAILABLE"); details.put("timeBasis", "EVENT_PERIOD");
            details.put("timezone", ZONE.toString()); details.put("from", from.toString()); details.put("to", to.toString());
            if (cutover != null) details.put("coverageStart", cutover.toString());
            throw new ConflictException("Cash history is not verified for the entire requested period", "CASH_HISTORY_UNVERIFIED", details);
        }
        return new Coverage("VERIFIED_COMPLETE", cutover, "EVENT_PERIOD", ZONE.toString(), "SYSTEM_RECORDED", clock.instant());
    }
    private static Overview overview(YearMonth month, LocalDate from, LocalDate to, Totals totals, Coverage coverage) {
        return new Overview(month.toString(), from, to, totals.grossCashReceived(), totals.completedRefundCashOut(), totals.netCashFlow(),
                totals.collectionCount(), totals.completedRefundCount(), coverage);
    }
    private YearMonth month(String value) {
        if (value == null || value.isBlank()) return YearMonth.now(clock.withZone(ZONE));
        try {
            if (!value.trim().matches("[0-9]{4}-[0-9]{2}")) throw new DateTimeParseException("Invalid month", value, 0);
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) { throw new BusinessRuleException("Month must use the format yyyy-MM"); }
    }
    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate start = from == null ? today.withDayOfMonth(1) : from, end = to == null ? today : to;
        validDate(start); validDate(end);
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessRuleException("The range must be ordered and cover at most " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDate[]{start, end};
    }
    private static void validDate(LocalDate date) { FinanceReportServiceImpl.requireSupportedDate("report", date); }
    private static Instant start(LocalDate date) { return date.atStartOfDay(ZONE).toInstant(); }
    private static String text(String value) { return FinanceReportServiceImpl.text(value == null ? "UNALLOCATED" : value); }
    private static String id(Long value) { return value == null ? "" : value.toString(); }
}
