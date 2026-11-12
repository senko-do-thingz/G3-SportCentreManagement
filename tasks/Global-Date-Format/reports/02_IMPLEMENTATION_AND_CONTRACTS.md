# Implementation and Contracts

## Shared date-field behavior

`CalendarDateField` presents a locale-independent `dd/MM/yyyy` text editor and keeps the application value as ISO `yyyy-MM-dd`.

- Exact two-digit day/month and four-digit year are required.
- Gregorian validity, leap days, year zero, partial input, and malformed input are checked without browser-local `Date` conversion.
- Invalid or partial text never enters application state and therefore cannot reach a request payload.
- Empty values and valid dates commit on blur or Enter; Escape restores the last application value.
- `min`/`max`, required state, accessible label, hint, `aria-describedby`, `aria-invalid`, and alert feedback are supported.
- Syntactically valid but domain-invalid ranges propagate as canonical ISO so the owning form can show its domain-specific error and block submission.
- The calendar button is a visually hidden native picker adapter. Its selection is immediately converted back to the visible `dd/MM/yyyy` field.
- Optional hidden form submission uses the ISO value, never the display string.

## Formatting contracts

| Value kind | UI | Application/wire |
| --- | --- | --- |
| Calendar date | `dd/MM/yyyy` | `yyyy-MM-dd` |
| Instant | `dd/MM/yyyy HH:mm` in `Asia/Ho_Chi_Minh` | Existing ISO timestamp/offset contract |
| Month bucket | `MM/yyyy` or complete date-range hint | `yyyy-MM` |
| ISO week | Complete Monday-Sunday `dd/MM/yyyy` range hint | `yyyy-Www` |
| Local session time | `HH:mm` | Existing local-time contract |

## Financial submission safety

- Revenue filters still send ISO `from`/`to` query values.
- Manager financial month inputs remain semantic month controls; no arbitrary first day is stored or submitted.
- Wallet-history date filters still submit ISO dates.
- Existing domain range validation remains before requests.
- Invalid visual text cannot update state; impossible dates such as `31/02/2026` produce an inline error.
- The finance CSV export contract was deliberately preserved.
- No payment, refund, wallet, receipt, or revenue amount logic changed.

## UI scope isolation

- Styling is limited to `.calendar-date-field*` selectors in the existing component stylesheet.
- No global input selector, browser locale override, or application-wide layout rule was introduced.
- The only broader formatter changes correct explicit date surfaces: finance month labels, operations ranges, coach week ranges, member/reception support, goals, suitable sessions, and membership-card blocking dates.

## Files and ownership

The implementation changes 36 frontend source/test files. It does not modify backend behavior, CODEOWNERS, governance, or PR #308/#309/#310 branches. After #308 and #310 merged into `main`, the final rebase integrated their UI in two Member files using the previously verified resolution; this branch retains only its own date-contract changes on top of that merged base. Details are in `04_CROSS_PR_INTEGRATION.md`.
