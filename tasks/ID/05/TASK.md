# ID-05 - Global frontend date display consistency

## Contract
All USER-VISIBLE dates must be dd/MM/yyyy (zero-padded), times HH:mm in 24-hour notation, and datetimes dd/MM/yyyy HH:mm. Center business calendar timezone: Asia/Ho_Chi_Minh.
API JSON, query parameters, database and native input values must remain appropriate ISO values (yyyy-MM-dd or ISO datetime). Do not change transport schemas to localized strings.

## Implement
- Inventory all frontend production user-facing date representations. Include Member, Coach, Reception, Manager, Public/Home, attendance, booking, payment/receipt, finance/reporting, training/plan/feedback, toasts, notifications, modals, empty/error states, mobile, light/dark and exports rendered by frontend.
- Introduce or consolidate shared utilities for date, time and datetime with strict validation and center timezone semantics; use existing utilities if correct and avoid duplicate formatters.
- Replace visible raw ISO text and locale-dependent inconsistent formatting. For native <input type="date"> and type="week", retain ISO value and browser input format but provide consistent dd/MM/yyyy nearby where needed.
- Do not call new Date(dateOnlyString) then local formatter if it can shift the date across timezone. Invalid/missing date must show safe fallback, not NaN or unrelated current date.
- Distinguish date-only from timestamps with offsets. If only a date is stored, display the same local calendar date in every timezone. Display center-based timestamps consistently even if client is in UTC+9 or UTC-8.
- Keep E2E/contract snapshots consistent; update tests deliberately; never change business cutoff logic during formatting.

## Tests
Leap days, leading zeros, cross-year, invalid dates, UTC/Japan/US browser timezones, epoch timestamps, date-only, cross-midnight center sessions, native date inputs, responsive and both themes. Do not accidentally localize API request payloads.

## Evidence
Write 05\reports\round-01\IMPLEMENTATION_REPORT.md, CHANGED_FILES.md, TEST_CASES.md, DATE_FORMAT_AUDIT.md (before/after by screen and residual exceptions), AI_AUDIT_LOG.docx. Continue to ID-06.