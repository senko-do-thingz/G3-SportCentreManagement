# BE-12 - Booking and Cancellation Rules

## Scope and verification status

Feature: `feat/f2-booking-rules`, based on main `6b130507abab6f9931c118b6569e9aa51708e269`.

The existing implementation was extended in place. BE-10 scheduling/cancellation, BE-11 read visibility and reason precedence, and BE-21 refund completion are preserved. No dependency, schema migration or frontend file changes are required.

Local final verification on 2026-10-16: 1,227 backend unit/controller/query tests in 89 suites passed, zero failures/errors/skips. SQL Server tests were compiled, not executed locally. Docker was not invoked. The 31 new SQL Server cases and six existing BookingV20 cases must execute successfully in GitHub CI before merge. Source test counts are prepared invocations, not database execution evidence.

## Endpoints and authorization

| Method and URL | MEMBER | RECEPTIONIST / MANAGER | Other roles / anonymous |
|---|---|---|---|
| POST /api/v1/bookings | Own member id from principal | Existing target-member booking permission retained | 403 / 401 |
| DELETE /api/v1/bookings/{id} | Own booking, member cutoff | Existing staff cancellation permission retained | 403 / 401 |
| GET /api/v1/bookings/my | Own bookings only | 403 | 403 / 401 |

BE-13 extends `/bookings/member/{memberId}` with staff-only filtering and pagination. Existing `/bookings/today/{memberId}` and `/bookings/self-training/attendance` retain their original staff-only behavior. Attendance acknowledgement still documents its pre-existing Flow 4 persistence limitation. Inactive or unauthorized actors are rejected by create/cancel/own-list service guards as well as normal HTTP security.

Create request:

```json
{"sessionId": 401, "packageRegistrationId": 901}
```

`sessionId` is required and positive. A supplied `packageRegistrationId` must be positive. It remains optional for established member callers; omission uses deterministic eligible package selection. BE-13 requires staff to select both memberId and packageRegistrationId. A MEMBER may omit memberId or supply their own id; BE-13 rejects a different id with 403 before target lookup. A foreign selected registration or cancellation returns 403 for a member, without other member details. Staff selecting a registration for the wrong target member receives 400 NO_PACKAGE. Missing session, booking, registration or target profile retains the 404 policy. See BE-13_test_cases.md for staff history filtering and selected-member eligibility.

POST succeeds with HTTP 201, DELETE with HTTP 200, both returning BookingResponse. GET succeeds with HTTP 200. Existing response fields remain. Added fields: sportId, classId/className, coachId/coachName, facilityId/facilityName, packageId/packageName, cancelledAt, cancelReason and cancelledById. Existing cancelledByName/cancelledByRole remain. No account email, phone, password or private coach metadata is serialized. The historical generated `Cancelled by user: <email>` reason is normalized to `Cancelled by user` in responses.

Coach and class are nullable for SELF_TRAINING. Package and cancellation fields may be null on legacy rows or before cancellation. The API never invents a coach.

## Eligibility and error codes

One injected Clock snapshot is used after acquiring the session lock. Session must be PUBLISHED, strictly before its actual start, and visible under the BE-11 assignment predicate: active linked class where present, active assigned facility, valid active qualified COACH for coach-led training; coachless self-training is allowed. BE-11 read eligibility reserves neither capacity nor package credits.

Confirmed duplicate is checked before capacity. Actual CONFIRMED booking count determines FULL. A full cached counter when actual room remains returns CAPACITY_UNAVAILABLE, preserving the BE-11 distinction without repairing historical drift.

An explicit registration must be owned, ACTIVE, have inclusive purchased date coverage, match sport and training format, and have positive remaining sessions. It is never substituted even if another package makes BE-11 eligible=true. Existing explicit validation order is retained: ownership, status, dates, sport, format, credits. Omitted selection shares BE-11 matching and set-based precedence: usable rows sorted endDate/startDate/id; otherwise covering ACTIVE zero-credit package -> NO_SESSIONS_LEFT; no applicable ACTIVE/EXPIRED package -> NO_PACKAGE; expired status or past end -> PACKAGE_EXPIRED_BY_THEN; otherwise PACKAGE_NOT_STARTED_BY_THEN.

| HTTP | Code / behavior | Condition |
|---|---|---|
| 409 | ALREADY_BOOKED | Existing CONFIRMED booking or named ux_booking_active violation |
| 409 | FULL | Actual full session or conditional reserve command affects zero rows |
| 400 | CAPACITY_UNAVAILABLE | Actual room exists but cached counter is full |
| 400 | SESSION_UNAVAILABLE | Unpublished/terminal session or unavailable BE-11 assignment |
| 400 | SESSION_STARTED | Booking at/after start or past date; member cancellation at/after start |
| 400 | NO_PACKAGE | No matching package, wrong sport/format, or disallowed status |
| 400 | NO_SESSIONS_LEFT | No usable credits or lost conditional debit race |
| 400 | PACKAGE_EXPIRED_BY_THEN | EXPIRED status or session after purchased end |
| 400 | PACKAGE_NOT_STARTED_BY_THEN | Session before purchased start |
| 400 | CANCELLATION_CUTOFF | Member cancellation after start minus 120 minutes, before start |
| 400 | ALREADY_CANCELLED | Repeated cancellation or lost confirmed-state transition |
| 400 | ALREADY_CHECKED_IN | Existing ALLOWED check-in |
| 400 | SESSION_ENDED | Staff cancellation at/after actual session end |
| 409 | BOOKING_CHANGED | Guarded seat release did not affect exactly one session |
| 400 | INVALID_REQUEST | Service-level null/nonpositive required ids |
| 400 | INVALID_PAGINATION | page < 0, size outside 1..100, or page*size > 2147483647 |
| 400 | Existing validation envelope, no guaranteed code | Malformed JSON, nonpositive body ids, invalid enum/numeric query |
| 401 / 403 / 404 | Existing envelopes | Authentication / authorization / missing resource |

Reason codes are optional top-level `code` fields in the established error object (`timestamp`, `status`, `message`). Existing unrelated BusinessRuleException calls retain their old envelopes and causes. Only a database failure naming ux_booking_active is translated to ALREADY_BOOKED. Unrelated database errors keep the established generic handler behavior; they are not concealed as duplicate bookings.

## Atomic transaction and locking

Creation: session pessimistic write lock -> refresh -> duplicate/visibility/true-capacity/package validation -> conditional seat increment -> refresh session -> existing conditional credit decrement -> save and flush booking -> REQUIRED success audit -> single commit.

Reservation SQL requires PUBLISHED and `0 <= booked_count < capacity`, increments by exactly one and must affect exactly one row. Credit consumption requires ACTIVE, inclusive date validity and positive credits at the database. Exactly one row is required. Failure throws inside the transaction, so the earlier seat reservation rolls back. Booking persistence and audit failures roll back seats, credits, booking and success audit together.

The existing filtered unique index ux_booking_active prevents duplicate CONFIRMED bookings. The existing registration CHECK bounds credits at 0..total_sessions. Registration remainingSessions is excluded from JPA entity updates; refreshed session entities cannot overwrite the native counter with a stale value.

Cancellation: scalar session-id lookup -> same session lock -> refresh session and booking -> ownership/check-in/time/state validation -> conditional CONFIRMED and not-ALLOWED transition -> refresh -> guarded atomic seat release -> refresh session -> ACTIVE-only capped credit restoration -> save/flush metadata -> REQUIRED audit -> commit. A repeated/concurrent cancellation fails before restoring again. REFUNDED/PENDING_PAYMENT/EXPIRED/CANCELLED balances remain unchanged. Null legacy registration links release a seat without inventing a credit.

Check-in now takes this session lock and refreshes booking/package before ALLOWED insertion. It retains the same selection, denial, authorization and no-extra-debit behavior. If check-in commits first, cancellation is rejected; if cancellation commits first, check-in cannot become ALLOWED for that booking.

BE-10 manager cancellation retains session -> booking range -> registrations. BE-21 completion retains payment/refund -> sessions in sorted order -> bookings -> registration; booking never takes a payment lock or a new session lock after registration mutation. This avoids adding an inverted order to those paths. It does not prove global absence of SQL Server deadlocks or lock escalation. No timeout/retry policy is introduced.

There is no global session-capacity CHECK constraint in the current schema. Locking and conditional statements preserve bounds from valid starting rows for these supported writers. Direct SQL/historical drift is not repaired. Shared package catalog/assignment reads remain validation snapshots rather than newly introduced long-lived locks.

## Cancellation cutoff

No runtime cutoff configuration exists. The named default is `MEMBER_CANCELLATION_CUTOFF_MINUTES = 120`.

For a session starting at 12:00, member cancellation at 09:59 and 10:00 is allowed; 10:01 is rejected. At 12:00 it is already started. All decisions use the application Clock in Asia/Ho_Chi_Minh and actual date/time, including tomorrow's sessions. RECEPTIONIST and MANAGER bypass only the member cutoff; they can cancel an in-progress session before end when not checked in, but cannot cancel ended, past-date, cancelled/completed or checked-in bookings. Metadata stores the actual actor, one timestamp and `Cancelled by user` without an email.

## My Bookings filtering, pagination and compatibility

- Neither `page` nor `size` supplied: JSON array, including `?when=UPCOMING`. This preserves the merged dashboard's asList contract. No when returns all own bookings as before.
- Either supplied: PageResponse with `content`, `page`, `size`, `totalElements`, `totalPages`. Defaults page 0 / size 20; size 1..100; bounded integer JPA offset.
- `UPCOMING`: CONFIRMED booking, session DRAFT or PUBLISHED, actual session start strictly after the single request-time snapshot. DRAFT reservations remain visible to their owner even though new DRAFT booking creation is prohibited.
- `PAST`: cancelled booking, CANCELLED/COMPLETED session, or actual session start at/before now. Future cancelled bookings remain reviewable. In-progress sessions are PAST once their start is reached; PAST does not mean only after end.
- Filtered ordering: sessionDate/startTime/booking id ascending for UPCOMING and descending for PAST. Unfiltered ordering: bookedAt/id descending. ID breaks ties across page boundaries.
- Filters, owner predicate, count and pagination execute in SQL. To-one entity graphs include nullable coach/class/package/cancellation actor without collection joins or duplicate booking rows. A full page has a content query and count query; an incomplete page can omit the count through Spring Data's optimization.
- Empty legacy result is `[]`; empty page content is `[]` with totalElements/totalPages 0 for a member with no matching rows. Out-of-range pages retain totals for the actual result set.
- Enum values are case-sensitive; invalid when returns 400. Client memberId query parameters do not affect ownership.

Examples:

```text
GET /api/v1/bookings/my?when=UPCOMING
GET /api/v1/bookings/my?when=PAST&page=0&size=20
GET /api/v1/bookings/my?page=1&size=10
```

## Local test coverage

| Suite | Executions | Coverage |
|---|---:|---|
| BookingServiceImplTest | 42 | Retained original creation, ownership, cancellation, atomic debit and constraint cases |
| BookingRulesServiceTest | 49 | Selected-package reasons, deterministic auto selection, BE-11 visibility, true/cache capacity, row-count guards, cutoff, role and page rules, metadata, failure propagation |
| BookingControllerTest | 6 | Retained established routes and security |
| BookingRulesControllerTest | 24 | Actual SecurityConfig role matrix, 401/403, body/query validation, 400/409 codes, array/page route selection, cancellation and private field contract |
| CheckInServiceTest | 13 | Existing behavior plus stale booking and duplicate rechecks after lock |
| MemberScheduleQueryTest | 4 | Existing BE-11 and new booking HQL/count/parameter metadata using the SQL Server dialect without JDBC |
| GlobalExceptionHandlerTest | 5 | Original envelopes plus optional business code and legacy cause preservation |
| ManagerSessionServiceImplTest / ControllerTest | 74 / 55 | BE-10 unit regression |
| MemberScheduleServiceImplTest / ControllerTest | 58 / 24 | BE-11 unit regression |
| RefundPaymentLinkTest | 50 | BE-21 unit regression |

All above and other existing unit suites are included in the full 1,227 run. Mockito failure propagation tests do not establish database rollback. Original booking counter assertions now check the SQL command/row-count boundary rather than pretending a mocked native update mutates its entity; actual physical counters are asserted in SQL Server tests.

## SQL Server integration coverage, required in CI

`com.sportify.catalog.controller.BookingRulesIntegrationTest`: 16 methods, 31 parameter-expanded cases. Uses inherited integration tag, real SQL Server 2022 migrations, isolated UUID-scoped generated-id fixtures and a fixed primary Clock. No @Disabled or skip-on-missing-Docker override is added.

| Case group | Invocations | Required physical/HTTP evidence |
|---|---:|---|
| Last seat (A) | 1 | Distinct SPIDs/transactions, actual blocking observed, one 201 and one 409 FULL, one booking/audit/debit, counter 1 |
| Shared last credit (B) | 1 | Both preload balance 1, distinct sessions/connections, second debit blocks, one 201 and one 400 NO_SESSIONS_LEFT, credit 0, losing seat rolled back |
| Double cancel (C) | 1 | Both preload booking, wait on same session, 200 then 400 ALREADY_CANCELLED, one seat/credit restoration, persisted actor/time and one cancellation audit |
| Cutoff and staff | 5 | 121/120/119 minutes; member failure, receptionist/manager success |
| ACTIVE-only capped restore and refund compatibility | 6 | ACTIVE, at-cap, pending, expired, cancelled, refunded balances and repeated cancellation |
| Audit failure rollback | 2 | Throw after real audit insert on create/cancel, assert all committed state and success audit counts |
| Real persistence failure | 1 | Duplicate booking_code, assert rollback without misclassifying as member/session duplicate |
| Self-training | 1 | Null class/coach, package metadata, cancellation and PAST history |
| Explicit selection and duplicate | 1 | Another usable package cannot authorize selected exhausted registration; actual HTTP duplicate 409 |
| Ownership and spoofed memberId | 1 | Foreign registration/cancel 403, principal ownership, no cross-member page rows |
| BE-11 assignment visibility | 5 | Inactive facility/class/coach, missing qualification/coach, no booking/debit/audit |
| History classification | 1 | Start equality, cancelled future, completed, draft, foreign member, legacy array |
| SQL pagination | 1 | 25 rows, stable ties/no duplicates, count metadata, two statements for full page, no N+1 |
| Managed native counter | 1 | Refreshed entity and later metadata save cannot overwrite reserved seat; capacity gate returns zero |
| BE-10 manager cancellation | 1 | No second restoration by individual cancel |
| Check-in versus cancel | 2 | Both serialization orders; never ALLOWED check-in on CANCELLED booking |

The three races and two check-in orders use bounded latches/futures and sys.dm_exec_requests to prove blocking on the exact competing connection, not sleeps or Mockito race simulations. CI Testcontainers uses SA for the DMV inspection. Writers terminate before UUID-scoped cleanup; cleanup fails closed if one remains active. Timeouts, deadlocks and unexpected HTTP responses fail the suite.

`com.sportify.catalog.service.BookingV20IntegrationTest`: all six original invocations retained, now explicitly required in the CI manifest. Positive fixtures receive an active facility to satisfy the real BE-11 visibility contract. Existing assertions are retained.

Also required: ManagerSessionIntegrationTest (BE-10), MemberScheduleIntegrationTest (BE-11), RefundPaymentLinkIntegrationTest (BE-21) and every previously required manifest entry. The manifest has 18 classes. Never replace the complete CI run with only BE-12 classes.

## Commands and merge gates

Local, from backend:

```powershell
mvn -B -ntp clean test
mvn -B -ntp -DskipTests test-compile
```

Surefire excludes the inherited integration tag in the unchanged pom. Failsafe includes that tag, independent of naming. Compiled integration classes are NOT RUN locally, rather than reported as passing or locally skipped.

Docker-capable GitHub CI only, from backend:

```text
mvn -B -ntp verify -DskipUnitTests=true
```

From root, verify CI artifacts:

```text
python scripts/ci/check-test-reports.py integration backend/target/failsafe-reports
bash scripts/ci/check-no-skipped-tests.sh backend/target/failsafe-reports
```

Require all 31 new cases and six BookingV20 invocations with zero failure/error/skip, all 18 required classes present, and the existing BE-10/11/21 regressions successful. Review Failsafe XML method names and counts, not only Maven exit or a green empty suite. Never use skipITs, failIfNoTests=false or exclusions as remediation. Full workflow, packaging, frontend and security gates remain required by repository policy.

## Known limitations

Legacy unpaged arrays remain unbounded for compatibility; callers can opt into bounded pages. No counter repair, global capacity CHECK, waitlist promotion, notifications, age/overlap validation, new cutoff setting, timeout policy or absolute deadlock guarantee is introduced. A read eligibility result is a snapshot. Actual SQL Server runtime and rollback evidence remains pending CI. These limits are explicit and do not replace the required database merge gate.
