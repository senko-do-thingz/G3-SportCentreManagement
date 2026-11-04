# ID-03 - Attendance session list and member roster UI

## Current problem
Coach and Reception attendance views currently use plain session and member dropdowns. Users cannot scan attendance quickly. Dates are ISO, codes are invisible, and note fields take too much space.

## Implement
- Replace the session dropdown with an accessible searchable card/list selector. Each item prominently shows public Session ID, class/sport, dd/MM/yyyy HH:mm range, Coach, facility, and attendance state.
- Highlight actionable sessions on the current center date; label future, closed and partially marked sessions clearly. Use actual server-authoritative state and roster counts, not guesses. Saved corrections may remain possible after ordinary marking closes subject to existing backend rules.
- Replace member dropdown with an actual roster list/table. Each row includes Member, booking ID, current badge and status selector with Present/Late/Absent/Excused controls. Use a clear Save attendance or Save correction action, not an accidental one-click unconfirmed destructive update.
- Attendance note: compact single-line input, max length aligned to backend; placeholder e.g. Short attendance note. Correction reason is distinct, mandatory, min 3 characters, max per backend, captured in audit. Existing status options with saved result restricted to PRESENT or LATE.
- Preserve ALL existing backend policies: same-date first marking Asia/Ho_Chi_Minh, active booking, assigned Coach authorization, Reception scope, transactional correction history, no hard-delete, no extra package credit, saved results conflict protection, Manager backfill disabled by default.
- Provide roster refresh after write, per-row pending/error feedback, duplicate submission guard, keyboard and screen-reader labels, empty/loading/error states, usable mobile 390px and desktop 1280/1440, light/dark.
- Do not expose unassigned Coach members or private histories. No bulk attendance write without separate reviewed API contract.
- Keep member own attendance history readable with the canonical Session ID.

## Tests
Client tests for per-row action, create vs correct, reason, result restriction, disabled future date, already marked, no eligible booking, input clipping and dates. Backend security/regression tests must remain green.

## Evidence
Write 03\reports\round-01\IMPLEMENTATION_REPORT.md, CHANGED_FILES.md, TEST_CASES.md, AI_AUDIT_LOG.docx, role-based before/after screenshots and UX accessibility report. Continue without interim CODEOWNER request.