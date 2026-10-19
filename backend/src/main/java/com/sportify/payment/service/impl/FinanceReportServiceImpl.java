package com.sportify.payment.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.payment.dto.finance.FinanceOverviewResponse;
import com.sportify.payment.dto.finance.FinanceTransactionResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownRow;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.dto.finance.RevenueTrendPoint;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.FinanceReportRepository;
import com.sportify.payment.repository.FinanceReportRepository.ExportRow;
import com.sportify.payment.repository.FinanceReportRepository.GroupTotal;
import com.sportify.payment.repository.FinanceReportRepository.LedgerFilter;
import com.sportify.payment.repository.FinanceReportRepository.MonthTotal;
import com.sportify.payment.repository.FinanceReportRepository.Totals;
import com.sportify.payment.service.FinanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceReportServiceImpl implements FinanceReportService {

    static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    static final String CSV_HEADER = "Payment code,Paid at,Invoice number,Member code,Member name,Item type,Item code,Item,Sport,Method,Amount";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal CENT = new BigDecimal("0.01");
    /** Typical CSV line length in bytes, used only to size the export buffer. */
    private static final long CSV_ROW_ESTIMATE = 200;
    /** Upper bound of the initial export buffer (the buffer still grows if the rows are longer). */
    private static final long EXPORT_BUFFER_CAP = 16L * 1024 * 1024;

    private final FinanceReportRepository repository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FinanceOverviewResponse overview(String month) {
        YearMonth current = parseMonth(month);
        if (current.atDay(1).isBefore(MIN_REPORT_DATE) || current.atEndOfMonth().isAfter(MAX_REPORT_DATE)) {
            throw new BusinessRuleException("Month must be between " + YearMonth.from(MIN_REPORT_DATE).format(MONTH)
                    + " and " + YearMonth.from(MAX_REPORT_DATE).minusMonths(1).format(MONTH));
        }
        YearMonth previous = current.minusMonths(1);
        LocalDateTime start = current.atDay(1).atStartOfDay();
        LocalDateTime end = current.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime previousStart = previous.atDay(1).atStartOfDay();

        Totals paid = repository.paidTotals(start, end);
        Totals pending = repository.pendingTotals(start, end);
        Totals refunded = repository.refundedTotals(start, end);
        Totals previousPaid = repository.paidTotals(previousStart, start);

        BigDecimal revenue = money(paid.amount());
        BigDecimal previousRevenue = money(previousPaid.amount());
        BigDecimal change = revenue.subtract(previousRevenue);
        BigDecimal changePercent = previousRevenue.signum() == 0 ? null
                : change.multiply(HUNDRED).divide(previousRevenue, 2, RoundingMode.HALF_UP);

        return new FinanceOverviewResponse(
                current.format(MONTH),
                current.atDay(1),
                current.atEndOfMonth(),
                revenue,
                paid.count(),
                average(revenue, paid.count()),
                money(pending.amount()),
                pending.count(),
                money(refunded.amount()),
                refunded.count(),
                previous.format(MONTH),
                previousRevenue,
                previousPaid.count(),
                change,
                changePercent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenueTrendPoint> trend(Integer months) {
        int count = months != null ? months : DEFAULT_TREND_MONTHS;
        if (count < 1 || count > MAX_TREND_MONTHS) {
            throw new BusinessRuleException("Months must be between 1 and " + MAX_TREND_MONTHS);
        }
        YearMonth last = YearMonth.now(clock);
        YearMonth first = last.minusMonths(count - 1L);

        Map<YearMonth, MonthTotal> byMonth = new HashMap<>();
        for (MonthTotal row : repository.monthlyRevenue(first.atDay(1).atStartOfDay(),
                last.plusMonths(1).atDay(1).atStartOfDay())) {
            byMonth.put(YearMonth.of(row.year(), row.month()), row);
        }
        List<RevenueTrendPoint> points = new ArrayList<>(count);
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            MonthTotal row = byMonth.get(m);
            points.add(new RevenueTrendPoint(m.format(MONTH),
                    row != null ? money(row.amount()) : money(BigDecimal.ZERO),
                    row != null ? row.count() : 0L));
        }
        return points;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FinanceTransactionResponse> transactions(PaymentStatus status, PaymentMethod method,
                                                                 InvoiceItemType type, LocalDate from, LocalDate to,
                                                                 String search, int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        requireSupportedDate("from", from);
        requireSupportedDate("to", to);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The from date must not be after the to date");
        }
        String text = search != null && !search.isBlank() ? search.trim() : null;
        LedgerFilter filter = new LedgerFilter(
                status != null ? status.name() : null,
                method != null ? method.name() : null,
                type != null ? type.name() : null,
                from != null ? from.atStartOfDay() : null,
                to != null ? to.plusDays(1).atStartOfDay() : null,
                text);

        long total = repository.countLedger(filter);
        List<FinanceTransactionResponse> content = total == 0 ? List.of() : repository.ledgerPage(filter, page, size);
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueBreakdownResponse revenue(LocalDate from, LocalDate to, RevenueGroupBy groupBy) {
        if (groupBy == null) {
            throw new BusinessRuleException("groupBy is required: ITEM_TYPE, METHOD or SPORT");
        }
        LocalDate[] range = range(from, to);
        List<GroupTotal> groups = repository.revenueBy(groupBy, range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay());

        BigDecimal total = money(groups.stream().map(GroupTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        long transactions = groups.stream().mapToLong(GroupTotal::count).sum();
        List<BigDecimal> shares = sharePercents(groups.stream().map(GroupTotal::amount).toList(), total);

        List<RevenueBreakdownRow> rows = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            GroupTotal g = groups.get(i);
            rows.add(new RevenueBreakdownRow(g.key(), label(groupBy, g), money(g.amount()), g.count(), shares.get(i)));
        }
        return new RevenueBreakdownResponse(range[0], range[1], groupBy, total, transactions, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public CsvExport exportRevenue(LocalDate from, LocalDate to) {
        LocalDate[] range = range(from, to);
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].plusDays(1).atStartOfDay();

        // Bounded export: count first, so a range with too many payments is refused before any row is read.
        long rowCount = repository.paidTotals(start, end).count();
        if (rowCount > MAX_EXPORT_ROWS) {
            throw new BusinessRuleException("The export would hold " + rowCount + " payments, more than the limit of "
                    + MAX_EXPORT_ROWS + "; choose a shorter range");
        }

        // Rows are written one at a time straight into the byte buffer: no row list and no full CSV string.
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                (int) Math.min(EXPORT_BUFFER_CAP, CSV_ROW_ESTIMATE * (rowCount + 1)));
        out.writeBytes(UTF8_BOM);
        out.writeBytes((CSV_HEADER + "\r\n").getBytes(StandardCharsets.UTF_8));
        int[] written = {0};
        // The count and stream can observe different committed rows. Bound the query and the actual output.
        repository.forEachPaidRow(start, end, MAX_EXPORT_ROWS + 1, row -> {
            if (++written[0] > MAX_EXPORT_ROWS) {
                throw new BusinessRuleException("The export exceeds the limit of " + MAX_EXPORT_ROWS + "; choose a shorter range");
            }
            out.writeBytes(csvLine(row).getBytes(StandardCharsets.UTF_8));
        });
        String fileName = "revenue_" + range[0] + "_" + range[1] + ".csv";
        return new CsvExport(fileName, out.toByteArray());
    }

    /** One CSV line with its CRLF ending. */
    static String csvLine(ExportRow row) {
        return String.join(",",
                text(row.paymentCode()),
                row.paymentTime() != null ? row.paymentTime().format(CSV_TIME) : "",
                text(row.invoiceNumber()),
                text(row.memberCode()),
                text(row.memberName()),
                text(row.itemType()),
                text(row.itemCode()),
                text(row.itemName()),
                text(row.sportName()),
                text(row.method()),
                row.amount() != null ? plainAmount(row.amount()) : "") + "\r\n";
    }

    /**
     * Shares in percent with 2 decimals that add up to exactly 100.00 (largest remainder method): every share is
     * rounded down to a cent of a percent, then the missing cents go to the rows with the largest remainders,
     * the earlier row first on a tie. Empty when the total is zero.
     */
    static List<BigDecimal> sharePercents(List<BigDecimal> amounts, BigDecimal total) {
        List<BigDecimal> result = new ArrayList<>(amounts.size());
        if (total == null || total.signum() == 0) {
            amounts.forEach(a -> result.add(BigDecimal.ZERO.setScale(2)));
            return result;
        }
        List<BigDecimal> remainders = new ArrayList<>(amounts.size());
        BigDecimal floorSum = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            BigDecimal exact = amount.multiply(HUNDRED).divide(total, 10, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(2, RoundingMode.DOWN);
            result.add(floor);
            remainders.add(exact.subtract(floor));
            floorSum = floorSum.add(floor);
        }
        int missingCents = HUNDRED.subtract(floorSum).divide(CENT, 0, RoundingMode.HALF_UP).intValue();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> remainders.get(i)).reversed().thenComparing(i -> i));
        for (int k = 0; k < missingCents && k < order.size(); k++) {
            int i = order.get(k);
            result.set(i, result.get(i).add(CENT));
        }
        return result;
    }

    /** Text cell: neutralises spreadsheet formulas and quotes the value when it holds a comma, quote or line break. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value;
        int start = 0;
        while (start < safe.length() && (Character.isWhitespace(safe.charAt(start)) || safe.charAt(start) == '\ufeff')) start++;
        char first = safe.charAt(0);
        char significant = start < safe.length() ? safe.charAt(start) : 0;
        if (significant == '=' || significant == '+' || significant == '-' || significant == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            safe = "'" + safe;
        }
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /** Amount without grouping and without trailing zeros (380000.00 is written 380000), so any Excel locale reads a number. */
    static String plainAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private String label(RevenueGroupBy groupBy, GroupTotal group) {
        String key = group.key();
        return switch (groupBy) {
            case ITEM_TYPE -> key == null ? "Other" : switch (key) {
                case "SPORT_PACKAGE" -> "Sport package";
                case "MEMBERSHIP_CARD" -> "Membership card";
                case "CLASS_DROP_IN" -> "Class drop-in";
                case "PENALTY_FEE" -> "Penalty fee";
                default -> key;
            };
            case METHOD -> key == null ? "Other" : switch (key) {
                case "CASH" -> "Cash";
                case "CREDIT_CARD" -> "Credit card";
                case "BANK_TRANSFER" -> "Bank transfer";
                case "MOMO" -> "MoMo";
                case "VNPAY" -> "VNPay";
                case "ZALOPAY" -> "ZaloPay";
                default -> key;
            };
            case SPORT -> key == null ? "No sport (membership card or other)" : group.label();
        };
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(month.trim(), MONTH);
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("Month must use the format yyyy-MM, for example 2026-10");
        }
    }

    /** Inclusive date range with defaults (first day of the current month to today) and the length limit. */
    private LocalDate[] range(LocalDate from, LocalDate to) {
        requireSupportedDate("from", from);
        requireSupportedDate("to", to);
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("The from date must not be after the to date");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessRuleException("The range can cover at most " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDate[]{start, end};
    }

    /**
     * 400 for a date outside MIN_REPORT_DATE to MAX_REPORT_DATE. Checked before any plusDays(1) or SQL bound is built:
     * LocalDate.MAX.plusDays(1) throws DateTimeException, and a bound after 9999-12-31 is rejected by SQL Server
     * DATETIME2; both would otherwise end as HTTP 500.
     */
    static void requireSupportedDate(String name, LocalDate date) {
        if (date != null && (date.isBefore(MIN_REPORT_DATE) || date.isAfter(MAX_REPORT_DATE))) {
            throw new BusinessRuleException("The " + name + " date must be between " + MIN_REPORT_DATE + " and "
                    + MAX_REPORT_DATE);
        }
    }

    private static BigDecimal average(BigDecimal revenue, long count) {
        return count == 0 ? money(BigDecimal.ZERO) : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
