# Test Cases: BE-22 Financial Overview, Transactions and Revenue Reports

## Overview
- **Task ID**: BE-22
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Screens**: F3-08 Financial Overview, F3-09 Transactions, F3-10 Revenue Reports, S3-Export
- **Branch**: `feat/f3-revenue-reports` (based on `feat/f3-confirm-payment`, BE-18)
- **Priority**: P2
- **Depends on**: BE-18
- **Objective**: Manager-only reports. Revenue counts only SUCCESS payments by payment_time; PENDING, FAILED and REFUNDED payments appear in the ledger only; refunds are reported separately. CSV export opens in Excel with Vietnamese names.
- **Data source**: views `vw_payment_ledger` and `vw_paid_payment` created by migration V25, read with native SQL (`FinanceReportRepository`).
- **Environment Note**: Maven Central and the SQL Server image could not be downloaded in the environment that wrote this change, so nothing below was executed there. Unit and MockMvc tests run with `mvn -B test` on a developer machine; the Testcontainers tests need Docker and run in GitHub Actions CI. Update the Execution Status column after the first run.

---

## 1. API Contract (MANAGER only; MEMBER, RECEPTIONIST, COACH -> 403; no token -> 401)

| Endpoint | Parameters | Answer |
|---|---|---|
| GET /api/v1/manager/finance/overview | month=yyyy-MM (default current month; bad format 400) | month, from, to, paidRevenue, paidTransactions, averagePayment, pendingAmount, pendingTransactions, refundedAmount, refundedTransactions, previousMonth, previousPaidRevenue, previousPaidTransactions, revenueChange, revenueChangePercent (null if previous month is 0) |
| GET /api/v1/manager/finance/trend | months 1 to 24 (default 6) | [{month, revenue, transactions}], oldest first, ends with the current month, zero months included |
| GET /api/v1/manager/finance/transactions | status, method, type (item type), from, to (inclusive dates on payment time, else request time), search, page, size (1 to 100) | Page of {id, code, time, paymentTime, createdAt, status, method, amount, referenceCode, memberId, memberCode, memberName, itemType, itemCode, itemName, sportName, invoiceId, invoiceNumber}, newest first |
| GET /api/v1/manager/finance/revenue | from, to (default this month to today, at most 366 days), groupBy ITEM_TYPE (default), METHOD, SPORT | {from, to, groupBy, totalRevenue, totalTransactions, rows [{key, label, revenue, transactions, sharePercent}]}, shares add up to 100.00 |
| GET /api/v1/manager/finance/revenue/export | from, to (same defaults and limit) | text/csv; charset=UTF-8, attachment revenue_<from>_<to>.csv, BOM, header, CRLF |

---

## 2. Integration Tests - `FinanceReportIntegrationTest` (SQL Server, known data in March 2001)

| Key | Status | Amount | Method | Item | Time |
|---|---|---|---|---|---|
| P1 | SUCCESS | 300000 | BANK_TRANSFER | package, sport A, invoice | paid 2001-03-05 10:00 |
| P2 | SUCCESS | 500000 | CASH | package, sport B, member name with comma and quotes | paid 2001-03-10 09:30 |
| P3 | SUCCESS | 1200000 | MOMO | membership card | paid 2001-03-20 18:00 |
| P4 | PENDING | 700000 | BANK_TRANSFER | package, sport A | requested 2001-03-15 |
| P5 | FAILED | 250000 | CASH | none | requested 2001-03-16 |
| P7 | REFUNDED | 400000 | VNPAY | package; refund 200000 COMPLETED 2001-03-25 | paid 2001-03-12 |
| P6 | SUCCESS | 100000 | CASH | none | paid 2001-02-14 |
| P8 | SUCCESS | 999000 | CASH | none | paid 2001-04-01 00:00:00 (boundary) |

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE22-I01 | Overview 2001-03 | revenue 2000000, count 3, average 666666.67, pending 700000 (1), refunded 200000 (1), previous 100000 (1), change 1900000, 1900.00 percent; PENDING, FAILED, REFUNDED and P8 not counted. Overview 2001-04: 999000 | Not run |
| BE22-I02 | Revenue 2001-03-01 to 2001-03-31 by ITEM_TYPE, METHOD, SPORT | Card 1200000 (60.00), package 800000 (2 tx, 40.00); MOMO 60.00, CASH 25.00, BANK_TRANSFER 15.00; no sport 60.00, sport B 25.00, sport A 15.00 with sport names as labels; each report: rows sum to totalRevenue 2000000 and shares sum to 100.00 | Not run |
| BE22-I03 | Ledger 2001-02-01 to 2001-04-30, search by member code prefix | 8 rows in order P8, P3, P5, P4, P7, P2, P1, P6; status PENDING 1, FAILED 1, REFUNDED 1; type MEMBERSHIP_CARD 1 (P3), SPORT_PACKAGE 4; method CASH 4; search by invoice number finds P1 with the Vietnamese member name, item type and sport; page 1 size 3 starts with P4, 3 pages | Not run |
| BE22-I04 | CSV export 2001-03 | text/csv, attachment name, BOM EF BB BF, exact header, 3 rows (P1, P2, P3) with time, invoice number, Vietnamese names, quoted name with comma and doubled quotes, amounts 300000, 500000, 1200000; P4, P5, P7, P8 absent | Not run |
| BE22-I05 | Trend months=6 | 6 points, last is the current month | Not run |
| BE22-I06 | RECEPTIONIST on overview and export | 403 | Not run |

## 3. Unit Tests - `FinanceReportServiceImplTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE22-U01 | Overview figures | average 666666.67 (half up), change 400000, 25.00 percent, month range 2026-10-01 to 2026-10-31 | Not run |
| BE22-U02 | Overview default month, no data | current month, average 0, change percent null | Not run |
| BE22-U03 | Revenue fell to zero | -100.00 percent | Not run |
| BE22-U04 | Bad month 2026-13, 10-2026, 2026/10, abc | 400, repository not called | Not run |
| BE22-U05 | Trend default | 6 months 2026-05 to 2026-10, missing months 0 | Not run |
| BE22-U06 | Trend 12 months | starts 2025-11, crosses the year | Not run |
| BE22-U07 | Trend months 0, 25, -1 | 400 | Not run |
| BE22-U08 | Ledger filters | enum names, to date exclusive (+1 day), search trimmed, total pages 3 for 45 rows of 20 | Not run |
| BE22-U09 | Ledger empty | no page query | Not run |
| BE22-U10 | Ledger bad page, size, range | 400, repository not called | Not run |
| BE22-U11 | Revenue by method, default range | labels MoMo, Cash, Bank transfer; shares 60.00, 25.00, 15.00 | Not run |
| BE22-U12 | Revenue by sport with a null group | label "No sport (membership card or other)" | Not run |
| BE22-U13 | Revenue with no payments | empty rows, total 0 | Not run |
| BE22-U14 | Revenue from after to, 367 days, no groupBy | 400 | Not run |
| BE22-U15 | Shares of three equal parts | 33.34, 33.33, 33.33, sum 100.00 | Not run |
| BE22-U16 | Shares 1,1,1,1,1,1,3 of 9 | sum 100.00, extra cent to the largest remainder (33.34), others 11.11 | Not run |
| BE22-U17 | CSV format | exact bytes: BOM, header, CRLF, Vietnamese text, quoted comma and quotes, formula prefixed with ', empty cells for null, 1200000.5 | Not run |
| BE22-U18 | CSV with no rows | BOM and header only | Not run |
| BE22-U19 | Plain amounts | 380000.00 -> 380000, 1000000 -> 1000000, 12.50 -> 12.5, 0.00 -> 0 | Not run |

## 4. Web Layer Tests - `ManagerFinanceControllerTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE22-C01 | MEMBER, RECEPTIONIST, COACH on the endpoints (10 cases) | 403, service not called | Not run |
| BE22-C02 | No token | 401 | Not run |
| BE22-C03 | Overview as MANAGER | 200 with figures and dates as yyyy-MM-dd | Not run |
| BE22-C04 | Overview bad month | 400 | Not run |
| BE22-C05 | Trend | 200 with points | Not run |
| BE22-C06 | Transactions with every filter | values passed to the service | Not run |
| BE22-C07 | Transactions status=PAID or type=TOWEL | 400, service not called | Not run |
| BE22-C08 | Revenue without groupBy | ITEM_TYPE used, rows with shares | Not run |
| BE22-C09 | Revenue groupBy=COLOR | 400 | Not run |
| BE22-C10 | Export | text/csv;charset=UTF-8, attachment file name, body bytes unchanged | Not run |
| BE22-C11 | Export with a bad date | 400 | Not run |

## 5. Manual Check in Excel (S3-Export)

1. Sign in as MANAGER and open GET /api/v1/manager/finance/revenue/export?from=2026-10-01&to=2026-10-31 in the browser, save the file.
2. Double-click the .csv file to open it in Excel.
3. Expected: columns split correctly, Vietnamese names (for example Nguyễn Văn Ánh) shown with their accents, amounts recognised as numbers (right aligned, SUM works).
4. If every row lands in column A, Windows uses ";" as the list separator (Vietnamese regional format). Open it with Data, From Text/CSV, File origin 65001 UTF-8, Delimiter Comma.
