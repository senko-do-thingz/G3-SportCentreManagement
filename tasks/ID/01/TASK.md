# ID-01 - Persistent canonical Session ID

## Goal
Make 031126FB01 the actual public-facing Session ID, not just a decoration. Keep the numeric class_session.id internally so existing SQL foreign keys and booking/attendance/result records remain valid. All new client-facing references call the code "Session ID". Prefer sessionId (string) for new public contracts; retain a clearly deprecated numeric id/legacy route only when needed for backward compatibility.

## Required format
DDMMYY + SPORT + NN, exactly 10 uppercase ASCII characters.
Sport mapping (canonical IDs/names, never free text):
FOOTBALL FB; SWIMMING SW; BADMINTON BM; VOLLEYBALL VB; BASKETBALL BK; TENNIS TE.
Example: 031126FB01 = 03/11/2026, Football, allocation 01.
Counter resets independently for each local scheduled date and sport. Two sessions on different courts or with different coaches still have distinct counters. Allocation order means creation/allocation order, NOT necessarily start-time order. Cancellation never reuses a code. Maximum 99 allocations per date/sport; fail clearly and atomically if 100th is attempted. Validate representable year window: two-digit year is not unique across centuries, so explicitly reject unsupported scheduled years or document a safe representation policy rather than silently colliding.

## Implementation
- Audit schema, migration version lineage, seed data, Manager session create/weekly batch/update/cancel, existing historical data, DTOs, Spring JPA entity and DB constraints before edits.
- Add a stable NOT NULL UNIQUE session identifier to class_session via a NEW forward-only Flyway migration. Backfill all existing rows deterministically by date, sport, start time, numeric PK, including CANCELLED rows. No silent reordering after historical code assignment. Detect invalid sport/date, collisions and >99 per date/sport. If existing data conflicts with the format, fail with actionable diagnostics before modifying any rows.
- Implement transactional, concurrency-safe allocation on SQL Server. Use a durable reservation/counter or appropriate application lock with unique constraint and tested retry semantics; a naked SELECT MAX + 1 without locking is forbidden. Multi-session weekly batches must be all-or-nothing.
- Prevent code reuse when sessions are cancelled or data is re-created. Avoid deleting allocation history.
- Rules for date change: the original code must never become inconsistent with the scheduled date. Do not silently mutate a public ID attached to bookings. For a booked or completed session, reject date mutation and direct user to a controlled cancel/replacement workflow. For an unbooked session, if reallocation is supported, implement it transactionally and audit the old/new identifiers and reserve the retired identifier; otherwise reject date change and explain the needed replacement. Never silently transfer existing bookings.
- Coach, facility, start/end time and allowed non-identity edits must preserve public Session ID.
- No migration edits to applied V37, no demo-photo migration imports, no QR DB use. Verify newer catalog migration versions and Flyway config before assigning the new version.

## Required tests
All six sports, reset per day, same sport across classes/facilities, concurrent inserts across transactions, batch atomicity, cancellation non-reuse, 99 limit, historical backfill deterministic order, schema uniqueness, invalid date, century ambiguity, date-change rules, DB rollback, booking/attendance FK preservation.

## Evidence
Write 01\reports\round-01\IMPLEMENTATION_REPORT.md, CHANGED_FILES.md, TEST_CASES.md, AI_AUDIT_LOG.docx, DDL inspection and SQL integration results. Never overwrite existing round folders.