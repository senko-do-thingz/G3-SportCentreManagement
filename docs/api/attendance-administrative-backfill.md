# Manager administrative attendance backfill

P-02 Option A is approved in scope. P02_TIME_WINDOW_POLICY remains PENDING_APPROVAL.
P-01/P-03/P-04/P-07 remain pending separate approval. No merge is authorized.

Production and all other profiles default to disabled. Both settings must be
explicitly configured before any administrative write can succeed:

| Property | Environment variable | Type | Default |
| --- | --- | --- | --- |
| app.training.attendance-backfill.enabled | ATTENDANCE_BACKFILL_ENABLED | Boolean | false |
| app.training.attendance-backfill.window | ATTENDANCE_BACKFILL_WINDOW | Positive ISO-8601 Duration | Empty; fail closed |

Missing, malformed, zero, negative or overflowing durations never mean unlimited.
PT48H is used only in explicitly enabled isolated development/test fixtures. It
is not a signed production cutoff or a universal FPT University rule. CODEOWNER
must authorize the operational duration and activation separately.

GET /api/v1/manager/training/attendance/backfill returns enabled, window, message
and bookings. Disabled or invalid configuration returns enabled=false with an
empty roster. Enabled configuration returns up to 200 recent confirmed coached
bookings with no attendance/result, filtered by the server's current window.
The Manager desktop page is /manager/training/attendance/backfill. The server
revalidates every save; an earlier roster response grants no write permission.

## Candidate discovery limit

The query selects at most 200 matching rows before applying the end/window filter
in Java. SQL order is session_date descending, end_time descending, then booking
ID descending. It considers confirmed real Member bookings for PUBLISHED or
COMPLETED past-date coached sessions without attendance or a result. This is a
bounded discovery list, not a complete search or a total eligible count. There
is no offset, continuation token or pagination contract.

With 201 otherwise eligible bookings, the oldest row by that order can be
omitted. Rows later excluded by the end/window filter still consume the initial
200 slots; for example, not-yet-ended overnight sessions can hide older eligible
rows. An empty returned list therefore does not prove that no eligible booking
exists outside the selected subset. Sorting uses persisted dates/end times, not
actual end instants adjusted for overnight sessions.

The cap does not change POST authorization: a known booking ID is fully
revalidated under locks even when absent from the discovery list. It does not
authorize direct SQL, date edits, unlimited backfill or another privileged route.
The current UI offers only this returned subset and has no further-page action.
No production volume bound below 200 has been established. Before activating the
feature, the operational owner must assess missed-booking volume and accept this
limitation or request a separately scoped lookup/pagination change. No broad
pagination or API/DTO refactoring is introduced in this follow-up.

POST to the same API route accepts bookingId, status (PRESENT, LATE, ABSENT or
EXCUSED), reason and optional notes. bookingId is authoritative; member/coach
IDs are never used from the request. Reason must be nonblank, at least three
trimmed characters and no more than 500 raw characters; notes allow 500.
Success is 201 with {"id": attendanceRecordId}. Existing attendance always gives
409, even for identical status. Use the existing correction workflow instead.

Both controller and service enforce the enabled ROLE_MANAGER principal. The
service also binds actorId to that principal and rechecks ACTIVE/MANAGER in the
database. Anonymous access is 401; authenticated unauthorized actors are 403.
Disabled JWTs are rejected by the existing authentication filter; an explicitly
disabled authenticated principal is rejected with 403. Reception is not delegated.

The transaction locks the session first, then the booking, rechecking identity,
CONFIRMED status, real Member, PUBLISHED or COMPLETED COACH_LED session and missing
attendance. DRAFT and CANCELLED are rejected, including anomalous confirmed
bookings attached to a historical draft. Normal booking creation requires a
PUBLISHED session, but there is no database cross-table constraint guaranteeing
that every persisted confirmed booking is attached to one. A past date alone
does not make a draft an actual class. Elapsed PUBLISHED sessions remain eligible
without inventing a mandatory transition to COMPLETED. The session calendar date
must be strictly before today in
Asia/Ho_Chi_Minh. The persisted session end date/time is the only authoritative
end available in the current schema; no actual-end tracking field is invented.
An end time at/before start is interpreted as the following day for overnight
sessions. The session must already have ended. The configured expiry is end
instant plus duration; equality is allowed, any later instant is denied. This
inclusive implementation boundary is testable configuration behavior pending
formal operational cutoff approval. Browser time never decides eligibility.

Session locks serialize this path with ordinary attendance, correction, result
creation and cancellation. The existing unique (session_id, member_id) constraint
remains a final safeguard. A stale moved booking is rejected, and an orphaned
saved result also prevents backfill. No existing attendance or result is updated.

The same transaction inserts attendance_record and an append-only activity_log
event TRAINING_ATTENDANCE_BACKFILLED targeting ATTENDANCE_RECORD. Required JSON
details contain actorId, bookingId, sessionId, memberId, status, trimmed reason,
actionTime and zone. Both rows use the actual server action time to second
precision, matching the schema, never the historical session timestamp. The
new operation uses a strict JDBC audit insert into the existing audit store so
required details cannot be silently dropped by best-effort serialization. Any
serialization/audit/database failure rolls back the attendance insert. No
fictitious attendance_correction row is created. Corrections remain separate.

No schema migration, booking/enrollment creation, seat count, package credit,
invoice, payment or refund change is introduced. Owners read the saved status
through existing Member class detail and attendance history endpoints; other
Members remain isolated. Audit payloads are private operational records, not
public evidence. PR #193 remains excluded; HOT-07/HOT-08/HOT-09 remain DEFERRED.
