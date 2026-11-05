# Four-role desktop demo - draft 12 minutes

This is a script skeleton for R11-10. The task owner must resolve real accounts, fixtures, session identifiers, routes, screenshots and status before claiming a successful rehearsal. All data must come from an approved disposable demo database. Do not commit passwords.

| Time | Actor | Action | Expected verifiable result |
|---|---|---|---|
| 00:00-01:15 | MANAGER | Open Users and Membership Cards; show roles/tiers | Existing Flow 1 account/card management works |
| 01:15-02:30 | RECEPTIONIST | Search existing Member -> create support request (F1-16) | 201, real ticket ID, status OPEN, then list/detail |
| 02:30-03:15 | RECEPTIONIST | Change ticket to IN_PROGRESS, filter; attempt invalid transition as prepared API negative test | State persisted, invalid transition rejected, audit preserved |
| 03:15-04:45 | MANAGER | Open class/session schedule, show assigned Coach, reschedule eligible future session | Public Session ID changes by date, booking and credit unchanged |
| 04:45-06:00 | MEMBER | View session availability and own booking; demonstrate cancellation only if fixture permits | Booking reads correct, credit rules preserved |
| 06:00-07:45 | MANAGER | Open Operations Reports, change from/to | Member counts, class booking totals/status and revenue agree with API |
| 07:45-09:15 | COACH | Open assigned class and member progress | Goal/profile/history shown only for Coach's own class |
| 09:15-10:30 | MANAGER | Open Attendance Overview, filter and inspect session | Attendance summary and real session detail are consistent |
| 10:30-11:30 | RECEPTIONIST or COACH | Demonstrate previously implemented attendance/result or correction | Writes/readback and authorization work with real DB |
| 11:30-12:00 | PRESENTER | State scope, optional exclusions and quality evidence | Required Flow 1-3 covered; Flow 4 MVP optional; no false AI/mobile claims |

## Pre-demo steps

1. Record executable backend JAR SHA, exact main commit, database name, client origin and time zone. Use Asia/Ho_Chi_Minh session calendar.
2. Verify four account roles and anonymized member bookings with status/credit checks.
3. Prepare one support ticket request and expected status changes. Do not reuse the same idempotency UUID across distinct request payloads.
4. Use a controlled date range where operations counts are non-zero and deterministic.
5. Prepare one Coach/class/Member with progress data and one Manager attendance session in the selected interval.
6. Confirm no required page is a placeholder or showing stale backend data.
7. Preflight GETs before live demo, then perform a complete dry run and record actual success/failure.

## Safe fallback

- If optional F4 data is empty, demonstrate read-only empty state and show validated API/SQL test evidence; do not invent member history.
- If live demo fails, report the failure and use already-verified evidence, not fabricated screenshots.
- Do not restart or reset production/shared databases to fix a fixture.
- If task R11-04 or R11-05 is officially deferred, remove it from the spoken demo and state the known gap rather than claiming full F4 completion.
