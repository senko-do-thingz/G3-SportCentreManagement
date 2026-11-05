# Manager attendance reads

Release 1.1 adds enabled-MANAGER-only reads. Existing attendance creation, correction and administrative backfill policies are unchanged. The service also rechecks the actor's current database role and ACTIVE status, including when an HTTP principal is stale.

- GET `/api/v1/manager/training/attendance/overview?from=YYYY-MM-DD&to=YYYY-MM-DD&classId=&coachId=` requires inclusive dates no more than 31 days apart (30-day difference). Optional IDs must be positive.
- GET `/api/v1/manager/training/attendance/sessions/{sessionCode}` returns the current session and booking history. Existing historical Session IDs resolve to the current session using session_code_history.
- The overview returns the newest 200 matching PUBLISHED or COMPLETED sessions and `truncated=true` if more exist. Narrow filters when truncated. These rows are not a center-wide aggregate.
- DRAFT and CANCELLED sessions are excluded. Confirmed counts include only currently CONFIRMED bookings. Cancelled bookings remain visible in detail as history and never enter summary counts.
- PRESENT, LATE, ABSENT and EXCUSED are actual persisted statuses. A confirmed booking without an attendance record is UNMARKED. It is never silently treated as ABSENT.
- Following F4-10, the rate for a COMPLETED session is `(PRESENT + LATE) * 100 / CONFIRMED`, rounded to one decimal. EXCUSED and UNMARKED remain in this denominator. A zero denominator or a session not marked COMPLETED returns null, shown as a dash.
- Details show member name/code, booking status, attendance status, operator, marked time and notes. No billing or contact fields are returned. More than 2000 bookings yields an explicit error rather than a silently incomplete detail.

The UI at `/manager/training/attendance` uses class search and Coach selection. `/manager/training/attendance/backfill` remains a separate write workflow. All report queries are read-only, use bound SQL parameters, and require no schema migration.

Verification: ManagerAttendanceReadServiceTest, ManagerAttendanceReadControllerTest, the R11 Manager attendance methods in TrainingWorkflowIntegrationTest, and frontend managerAttendanceOverview.test.tsx. Full suite results and desktop SQL/browser evidence are recorded in the Release 1.1 task reports.
