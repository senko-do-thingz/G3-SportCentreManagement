# Ordinary attendance session date policy

Ordinary Coach and Reception first attendance writes require the session calendar
date in the server Clock's Asia/Ho_Chi_Minh zone. A future or earlier session date
returns HTTP 400 without an attendance row or successful-write audit event. The
existing confirmed-booking, current-Coach assignment, cancellation, locking and
financial checks remain in force. Self-training retains its same-day behavior.

GET /api/v1/coach/training/sessions and GET /api/v1/reception/training/sessions now
include the additive boolean `attendanceDateOpen` in each session summary. It
reports only whether the date is eligible for an ordinary first mark at the time
the server reads the session. It does not grant role/booking authorization or
permit an incompatible correction. Existing clients may ignore this field. The
staff UI uses it to display closed-date sessions and retain saved records without
offering a first-mark form for an unmarked member. The server revalidates every
write; the UI never determines eligibility from the browser's clock.

The UI accepts an omitted field for compatibility with older responses; this
cannot bypass the authoritative write guard. Reload session data to refresh date
state after a center-calendar midnight transition.

Existing corrections remain separate, require meaningful reasons and permanent
correction/audit records, and retain the saved-result consistency guard. A closed
first-mark date does not hide historical participation or erase corrections.
No new correction deadline is imposed. A separate Manager-only administrative
first-mark endpoint is implemented at
`POST /api/v1/manager/training/attendance/backfill`. P-02 Option A approves its
Manager-only scope; ordinary attendance permissions and existing corrections
are unchanged. It defaults disabled with no configured window and fails closed.
Production cutoff duration and feature activation remain PENDING_APPROVAL.
See [Manager administrative attendance backfill](attendance-administrative-backfill.md)
for authorization, session-state checks, audit, configuration and roster limits.

The Coach session-start opening, Reception 15-minute early allowance, 48-hour
administrative/correction limit and escalation contract remain
PENDING_CODEOWNER_CONFIRMATION. Same-day pre-start behavior remains compatible
until those cutoffs are signed. These values are Sportify proposals, not verified
universal FPT University rules. No academic absence penalty or attendance QR is
introduced. P-01/P-03/P-04/P-07 remain pending formal policy approval.
