# Twelve-minute desktop committee demo

Use one 1440x900 desktop browser and the owned disposable database named in the R11-10/R11-11 rehearsal evidence. Record candidate SHA, executable JAR checksum, database name, client/API origins and center-local date before starting. Never point a fixture script at sportify, sportify_demo, sportify_qr_demo or any existing shared database. Do not put account passwords or tokens in reports or slides.

Prepare four isolated browser contexts for MANAGER, RECEPTIONIST, MEMBER and COACH. The rehearsal runner creates a new timestamped fixture, obtains IDs through existing APIs and SQL on that fixture, and records the actual IDs in evidence. Use its run-local credentials from the private process environment. Do not change the business clock or reschedule a started session to make a demonstration succeed.

| Time | Actor and action | Expected evidence |
|---|---|---|
| 00:00-01:00 | Manager opens Users and Membership Cards | Real member/staff roles and package terms; fixed-role scope stated |
| 01:00-02:30 | Reception opens Support requests, searches the prepared member, selects a category, creates a request | Actual 201 and stable request ID; reload/list/detail readback |
| 02:30-03:30 | Reception moves OPEN -> IN_PROGRESS -> RESOLVED with meaningful notes and filters by status/member | Actual valid state persists; prepared API negative case rejects forbidden/terminal transition |
| 03:30-04:45 | Manager opens Classes and Schedule; shows class, Coach, facility and canonical Session ID | Existing scheduling works; reschedule a future fixture only if eligible; booking and credit invariants unchanged |
| 04:45-06:00 | Member opens own schedule/bookings and package validity | Real confirmed booking; own data only; cancel only a separately prepared cancellable booking |
| 06:00-07:30 | Manager opens Revenue Reports and changes dates | Operations registration/current-enabled subset and booking counters match API; upcoming/completed not added to total; revenue/CSV retain existing semantics |
| 07:30-09:00 | Coach opens Member progress from class roster/plans, chooses class/member | Actual goal/level and recorded outcomes; notes-only/empty state remains truthful; existing feedback readback; no billing/contact exposure |
| 09:00-10:30 | Manager opens Attendance overview, filters class/Coach and opens session detail | Confirmed counts, unmarked versus absent, completed-session rate, current public ID and operator detail agree with fixture |
| 10:30-11:30 | Member views own results/attendance; demonstrate prepared wrong-role and other-Coach API denial | Independent backend 401/403/404 evidence; no cross-member leakage |
| 11:30-12:00 | Presenter states tested SHA, CI/native SQL/browser evidence and known limits | Required Flows 1-3, included optional Flow 4, deferred scope and review status are accurate |

Before the live session, run the full rehearsal once, reload created support and saved result/feedback data, and restart only the backend process owned by the rehearsal to verify persistence. Compare selected report counters and attendance counts directly with SQL from the owned fixture. Record success/failure, not just screenshots.

Safe fallback: if a read is empty, show the real empty state and explain the fixture limitation. If a request fails, show its error and retry; do not claim persistence or reset a shared service. Use already verified evidence only for the exact candidate SHA. Keep a separate report of any failed step and do not announce a GO decision that CODEOWNER has not signed.

This is a desktop script. Mobile, AI Audit Log, AI features, Waitlist, dynamic roles and legacy removal are not demonstration requirements. The actual execution and unresolved blockers are recorded separately in the task rehearsal report.
