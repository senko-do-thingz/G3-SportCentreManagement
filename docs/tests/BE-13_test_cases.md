# BE-13 - Receptionist Books for a Member

Branch: `feat/f2-reception-booking`. Main base: `91c1f29346de91c3d5832e2e1b8735d92f06de30`.

This implements F2-11 Member Booking Search, F2-12 Book for Member and S2-ReceptionConfirmed by extending BE-11/BE-12. No database migration, dependency, frontend change or separate staff booking engine is introduced. Docker must not be used locally; SQL Server execution is a GitHub CI requirement, not a local pass claim.

## Authorization and identity

| Operation | MEMBER | RECEPTIONIST / MANAGER | COACH / other roles | Anonymous |
|---|---|---|---|---|
| GET /bookings/member/{memberId} | 403, including own id | Selected member | 403 | 401 |
| GET /sessions/{id}/eligibility?memberId= | 403, including own id | Selected member | 403 | 401 |
| POST /bookings | Own profile only | Explicit selected member | 403 | 401 |
| GET /bookings/my | Existing own-only behavior | Existing 403 | 403 | 401 |
| GET /sessions/{id} | Existing own eligibility | Existing generic detail | Existing 403 | 401 |

Paths are prefixed with `/api/v1`. Method security uses the authenticated CustomUserDetails; new service methods also check actor id, ACTIVE status and staff role before target queries. Client role/actor/staff fields have no authority and are not part of BookingCreateRequest. JWT authentication still reloads the account and rejects disabled accounts.

The member profile shares `user_account.id` through `member_profile.user_id`. A selected profile must exist, reference the same account id and have MEMBER role. A missing/non-member profile returns 404. Valid inactive members remain visible in staff history; eligibility and creation require an ACTIVE beneficiary and reject an inactive beneficiary with 403. No staff actor is converted into the booking owner.

## Staff history

`GET /api/v1/bookings/member/42?when=UPCOMING&status=CONFIRMED&page=0&size=20`

| Parameter | Contract |
|---|---|
| memberId | Required positive path id, validated after authorization |
| when | Optional exact enum UPCOMING or PAST |
| status | Optional exact BookingStatus CONFIRMED or CANCELLED |
| page | Zero-based, default 0 if only size supplied |
| size | Default 20 if only page supplied; 1..100 |

All supplied predicates combine with AND in SQL. Malformed enums/integers, negative page, invalid size and page*size beyond 2147483647 return 400. An unknown member is 404, while no matching bookings is an empty result.

The exact BE-12 period predicate is reused: UPCOMING means CONFIRMED, session DRAFT/PUBLISHED, and session start strictly after the Clock snapshot. PAST includes a CANCELLED booking, CANCELLED/COMPLETED session, or session start at/before now. A future cancelled booking therefore belongs to PAST. A conflicting pair such as UPCOMING+CANCELLED is empty. Time uses the injected application Clock in Asia/Ho_Chi_Minh.

Without page and size, the result remains a JSON array, including when/status-only requests. With either paging parameter it uses PageResponse:

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Items reuse BookingResponse and the same mapper as `/bookings/my`:

```json
{
  "id":300,"bookingCode":"BK-0300","sessionId":100,"sessionDate":"2026-10-17",
  "startTime":"12:00:00","endTime":"13:00:00","sportName":"Badminton","sportId":10,
  "classId":40,"className":"Fundamentals","coachId":20,"coachName":"Alex Coach",
  "facilityId":30,"facilityName":"Court 1","trainingType":"COACH_LED",
  "memberId":42,"memberCode":"MEM-0042","memberFullName":"Taylor Member",
  "packageRegistrationId":200,"packageId":80,"packageName":"30-day Badminton",
  "status":"CONFIRMED","bookedAt":"2026-10-16T10:00:00","cancelledAt":null,
  "cancelReason":null,"cancelledById":null,"cancelledByName":null,"cancelledByRole":null
}
```

No period: bookedAt/id DESC. UPCOMING: sessionDate/startTime/id ASC. PAST: those fields DESC. Pagination and counting apply identical predicates; fetch graphs use only to-one relationships, retain nullable coach/class/package/cancellation fields and avoid per-booking metadata queries. No unbounded in-memory filtering or paging is used. The legacy array is intentionally unbounded for compatibility; clients should request pagination.

## Staff eligibility

`GET /api/v1/sessions/100/eligibility?memberId=42`

This returns the actual BE-11 SessionDetailResponse with `memberContext=true` for the selected member. The unchanged generic staff detail endpoint still returns `memberContext=false`.

```json
{
  "session":{
    "id":100,"sportId":10,"sportName":"Badminton","classId":40,"className":"Fundamentals",
    "trainingFormat":"COACH_LED","level":"BEGINNER","coachId":20,"coachName":"Alex Coach",
    "facilityId":30,"facilityName":"Court 1","sessionDate":"2026-10-17",
    "startTime":"12:00:00","endTime":"13:00:00","capacity":10,"bookedCount":0,
    "freeSeats":10,"full":false,"status":"PUBLISHED"
  },
  "memberContext":true,"bookingStatus":null,"alreadyBooked":false,"eligible":true,"reason":null,
  "suitableRegistrations":[{
    "registrationId":200,"registrationCode":"REG-0200","packageId":80,"packageName":"30-day Badminton",
    "sportId":10,"trainingFormat":"COACH_LED","startDate":"2026-10-15","endDate":"2026-11-13","remainingSessions":5
  }]
}
```

The existing calculation is shared, not copied. Precedence remains ALREADY_BOOKED, FULL, CAPACITY_UNAVAILABLE, usable packages, NO_SESSIONS_LEFT, NO_PACKAGE, PACKAGE_EXPIRED_BY_THEN, PACKAGE_NOT_STARTED_BY_THEN. Only the selected member's matching sport/format registrations participate. Usable registrations must be ACTIVE, cover the session date inclusively and have positive credits; all usable registrations are ordered endDate/startDate/id. REFUNDED/CANCELLED/PENDING_PAYMENT do not become eligible. Expired rows only explain rejection.

Nonexistent, draft, cancelled, completed, started/past or otherwise invisible sessions retain BE-11 404. Self-training may have no coach or class; visibility still requires an active facility and valid assignments. No new reason codes or relaxed assignment rules are introduced.

Eligibility is read-only and advisory. It writes no booking, seat, credit, profile, payment, invoice or routine activity_log. It does not reserve resources. POST always rechecks current state; a previously eligible result is no guarantee after a refund, expiration, cancellation or another booking.

## Staff creation

`POST /api/v1/bookings`

```json
{"memberId":42,"sessionId":100,"packageRegistrationId":200}
```

Staff must explicitly supply a positive memberId and packageRegistrationId. The beneficiary must own that selected registration; another eligible package is never substituted. Missing selection is 400 INVALID_REQUEST. Members retain omission-based own booking and automatic package selection; an explicit own memberId is accepted. A foreign memberId now returns 403 before target lookup, as required by BE-13, instead of the previous BE-12 ignored-field behavior. The existing regression test now verifies that stronger rejection and separately retains valid self-booking/cancellation assertions.

Success remains 201 BookingResponse. FULL and ALREADY_BOOKED remain 409. Other business ineligibility remains 400 with existing codes; foreign package ownership is 400 NO_PACKAGE for staff and 403 for members. Missing resources remain 404. Error shape retains timestamp/status/message, optional details and code:

```json
{"timestamp":"2026-10-16T10:00:00","status":409,"message":"Session is fully booked","code":"FULL"}
```

Actor identity comes only from the principal. The existing schema has no booked_by_user_id; the existing BOOKING_CREATED activity_log writes actor_user_id, BOOKING target and safe memberId/sessionId/packageRegistrationId/status details. It joins the successful booking transaction, with requiresNew=false. Audit persistence failure propagates and rolls back the seat, credit, booking and audit. No actor request field, new audit action or migration is added.

BE-12 locks the session first, checks current CONFIRMED bookings, selects/validates the beneficiary's package, conditionally reserves one seat, conditionally consumes one credit, flushes the booking and writes the audit. The conditional predicates and filtered unique ux_booking_active index are unchanged. Capacity stays within bounds and credits cannot become negative. Staff/member creation share one transaction path and lock order; cancellation, check-in, payment, refunds and BE-06 jobs are unchanged.

## Test coverage and execution

| Suite | Purpose |
|---|---|
| ReceptionBookingServiceTest | 40 actor/target, history filters/paging, ownership, package rules, ordered mutations and audit propagation cases |
| ReceptionEligibilityServiceTest | 25 exact member/staff parity, reasons, ownership, multiple-package/self-training and service-authorization cases |
| ReceptionBookingControllerTest | 31 real-security-chain mappings, roles, 401/403 privacy, array/page, validation, actor-spoofing and error-contract cases |
| MemberScheduleQueryTest | Existing coverage plus typed combined staff content/count HQL binding with actual SQL Server dialect metadata |
| ReceptionBookingIntegrationTest | 59 prepared real SQL Server executions, committed fixture isolation, role/shape/filter/count parity, actor persistence, audit rollback and races |
| Existing BE-11/BE-12 suites | Retained rules/assignment/cancellation/refund/unique-index regressions; only spoofed-beneficiary expectations strengthened to required 403 |

SQL cases use independent transactions for last-seat staff races, member/staff and two-staff duplicates, last-credit races, and held session locks preceding committed refund/cancellation status changes. Seat/duplicate/credit races assert distinct SQL connection ids and observe the exact second connection waiting on the first in sys.dm_exec_requests before releasing its transaction. The last-credit case first loads the second worker's registration snapshot, so the failed conditional debit must roll back its reserved seat. Refund/cancellation status races isolate the booking interaction with the real status/lock boundary; full refund/cancellation workflows remain covered by their existing integration suites. Audit failure is injected by a real private-database trigger after insertion. No repository spy or Mockito-only concurrency simulation is used.

SQL fixtures live in a generated private database on the existing disposable container, migrated by normal Flyway configuration. DB_NAME is verified before fixture operations. Test-owned ids and production-created booking/audit children are tracked, workers are interrupted/joined, and every foreign-key-ordered cleanup is attempted even after prior failure. Counts must return to the canonical six-sport/36-package baseline after every invocation. No global demo account is modified; partial setup and assertion failures still run teardown. A worker that fails to terminate fails teardown, and a private database prevents contamination of other suites.

Local commands and actual counts are recorded in the exported BE-13_Test_Results.md. SQL sources compile locally; they must actually execute in GitHub Actions. Require the dedicated suite in scripts/ci/required-integration-tests.txt and zero failures/errors/skips in Failsafe XML. Compilation and prepared counts are not SQL pass evidence.

Known limits: legacy unpaged arrays remain unbounded; eligibility is a non-reserving snapshot; no throughput measurements or new deadlock retry policy are claimed. Existing offset-free timestamps and production lock/escalation constraints remain unchanged. The CI container needs database-creation rights for isolation. Fresh CI is required before merge review is complete.
