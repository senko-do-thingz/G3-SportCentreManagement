# Global Date Format Source Inventory

## Scope and baseline

- Audit date: 11/11/2026 (Asia/Bangkok).
- Baseline: `origin/main` at `bf5a12407117b6ed9f354f13f5c48511f9e2a11c`.
- Implementation commit: `ecf8ec046918ecab2f287424cb14d8779d752f5c`.
- Display contract: calendar dates use `dd/MM/yyyy`; instants use `dd/MM/yyyy HH:mm` in the centre time zone (`Asia/Ho_Chi_Minh`).
- Transport/storage contract: ISO date, timestamp, month, and week values are unchanged.

## Source audit matrix

| Area | Audited surfaces | Result |
| --- | --- | --- |
| Home/public | Landing page and public navigation | No user-facing date surface exists. Recorded as an explicit exclusion and included in the screenshot matrix. |
| Manager | Activity log filters and timestamps; revenue report filters; finance month controls/chart axes; transaction timestamps; operations summary range; session editor; attendance overview | Date-only editors now share `CalendarDateField`. Raw operations dates were converted. Month-only data renders as `MM/yyyy` without inventing a day. Existing canonical timestamp renderers remain. |
| Reception | Register/renew start date; support request timestamps; membership/package expiry; bookings, check-in, payments, refunds, receipts | Register/renew uses the shared field. Support reply times now use the canonical datetime formatter. Existing canonical displays were retained. |
| Coach | Teaching week range; session dates; roster booking timestamps; attendance/training views | Week range now shows two complete `dd/MM/yyyy` values. Existing session and timestamp formatters were retained. No editable date-only control exists. |
| Member | Class and recommendation filters; profile date of birth; package start; wallet history; support; goals; suitable sessions; bookings, schedule, payments, membership, receipts | All date-only editors use the shared field. Raw/inconsistent support, goal, suitable-session, and membership-block dates were converted. Existing canonical displays were retained. |
| Shared utilities | Calendar, month, week, date, time, and datetime formatting and strict parsing | Added strict locale-independent editable conversion and month-only display. Existing centre-time parsing remains the canonical instant contract. |
| Backend | Human-facing finance export, identifiers, persistence, tests, and database helpers | No unsafe global rewrite was made. See approved exceptions below. |

## Native-control inventory after remediation

- Production `type="date"`: one hidden picker adapter inside `CalendarDateField`; it never supplies visible locale-dependent text.
- Production `type="month"`: Manager Financial Overview and Transactions. Both retain semantic month values and display a complete `dd/MM/yyyy` range hint.
- Production `type="week"`: Member Schedule. It retains the semantic ISO week value and displays the full Monday-Sunday `dd/MM/yyyy` range.
- Production `type="datetime-local"`: none.
- Date-only native controls outside the shared component: none.

## Approved non-display exceptions

| Source | Value | Reason |
| --- | --- | --- |
| Finance CSV export | `yyyy-MM-dd HH:mm:ss` and UTC instant fields | Machine-readable integration contract; changing it would risk downstream finance consumers. |
| Session identifiers | `ddMMyy` prefix | Technical identifier contract, not a displayed calendar value. |
| API/query/storage values | `yyyy-MM-dd`, ISO timestamps, ISO month/week values | Wire compatibility and correct server-side parsing. |
| Test database/resource names | compact `yyyyMMddHHmmss` | Internal uniqueness token, never user-visible. |

## Search evidence

The final production search found no date-only native field outside the hidden shared picker adapter, no `datetime-local` field, and no additional human-facing backend formatter requiring conversion. All remaining compact or ISO patterns are covered by the exception table.
