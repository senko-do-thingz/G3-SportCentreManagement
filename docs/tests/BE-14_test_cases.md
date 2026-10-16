# BE-14 Coach Teaching Schedule and Class Roster

## Official scope and dependencies

F2-13 lists a coach's assigned teaching sessions; F2-14 lists only CONFIRMED
bookings for an authorized session. Foreign coach sessions return 403; MEMBER
and RECEPTIONIST are forbidden. BE-10 scheduling and BE-12 booking/cancellation
are present. Their transactions, locks, counters, credit rules and public APIs
are unchanged. No migration or new dependency is needed. Existing BE-11 member
visibility and coach-profile APIs remain separate.

## Authentication and ownership

| Reader | Schedule | Roster |
|---|---|---|
| COACH | Own assigned sessions only; optional own coachId accepted; other coachId is 403 | Own session only; another coach or null coach is 403 |
| MANAGER | Supplied coachId filters an existing profile; omitted coachId returns all coach-assigned sessions | Any existing session, including coachless self-training |
| MEMBER / RECEPTIONIST / unsupported roles | 403 | 403 |
| Anonymous | Existing 401 envelope | Existing 401 envelope |

Both controller and service require hasAnyRole('COACH','MANAGER'). The service
resolves CustomUserDetails from the authenticated security context, reloads the
actual UserAccount by authenticated ID, and verifies its current ACTIVE status
and current COACH/MANAGER role. There is no request-supplied actor argument.
The normal JWT filter rejects inactive accounts and refresh tokens with 401;
a stale direct/internal context rejected by the service receives Access Denied.

CoachProfile @MapsId uses user_id = UserAccount.id. class_session.coach_id
references that coach profile, not the sport class's default coach. A coach
must have a real profile at that shared key, otherwise 403; it never becomes an
unrestricted query. Public-profile visibility and coach_sport qualifications do
not hide an owner's historical assignments. A manager needs no CoachProfile.
An explicitly selected nonexistent coach profile returns 404. Managers can
review assignments for inactive coaches as well as active coaches.

For rosters, the service reads only session ID/coach ID first, returns 404 for a
missing session, and checks ownership before any member query. A positive ID for
another coach or an unassigned session returns 403 with no contacts or counts.
The roster SQL repeats the owner restriction; reassignment after the preliminary
check cannot disclose the roster to the old coach. If reassignment occurs in
that interval, an empty result can be returned. No write-path lock is added.
A query/header coach ID cannot override this ownership check.

## GET /api/v1/coach/sessions

| Parameter | Rule |
|---|---|
| from | Required strict YYYY-MM-DD, inclusive lower calendar date, years 0001-9999 |
| to | Required strict YYYY-MM-DD, inclusive upper calendar date, years 0001-9999 |
| coachId | Optional positive Long; COACH may supply only their own profile ID; MANAGER may select another |

Missing either or both bounds, blank/malformed dates, offsets/timestamps, invalid
calendar dates, reversed ranges, more than 62 days inclusive, or nonpositive IDs
return 400. These are the existing BE-10 bounded-calendar conventions; there is
no implicit date window. Maximum date 9999-12-31 is safe: filtering uses BETWEEN
without adding a day. Session DATE and TIME(0) values are center local time,
Asia/Ho_Chi_Minh (UTC+07:00), without timezone conversion. Existing ClockConfig
uses that zone. Clients must send calendar dates rather than UTC instants.

Response is a JSON array, following the manager calendar. It is not paginated
and is never silently truncated. No page/size parameters are introduced. Sort:
sessionDate ASC, startTime ASC, id ASC. All valid statuses DRAFT, PUBLISHED,
CANCELLED and COMPLETED are included, as are past sessions within the range.
Inactive classes/facilities/sports and private coach profiles are not silently
hidden. Manager omitted-coach lists exclude all null-coach sessions; a manager
can still read such a session's roster by its explicit ID.

bookedCount is the actual number of CONFIRMED bookings, grouped in the database,
matching BE-10/BE-11 read semantics. CANCELLED bookings do not count. The cached
class_session.booked_count is not overwritten or repaired. If it drifts, the read
shows the real count while BE-12's existing guarded cache/capacity checks remain
unchanged. Counts exceeding capacity are returned as stored aggregate facts,
not clamped or presented as permission to book.

```json
[{
  "id": 91,
  "classId": 10,
  "className": "Beginner badminton",
  "sportId": 20,
  "sportName": "Badminton",
  "coachId": 17,
  "coachName": "Alex Coach",
  "facilityId": 30,
  "facilityName": "Court A",
  "sessionDate": "2026-10-16",
  "startTime": "10:00:00",
  "endTime": "11:00:00",
  "capacity": 20,
  "bookedCount": 3,
  "status": "PUBLISHED"
}]
```

classId/className and facilityId/facilityName are nullable for supported legacy
rows. No placeholder class, facility or coach is invented. The list contains no
roster/member contacts, cancellation notes, private biographies or credentials.
No matches returns 200 with [].

```text
/api/v1/coach/sessions?from=2026-10-01&to=2026-10-31
/api/v1/coach/sessions?from=2026-10-16&to=2026-10-16&coachId=17
```

## GET /api/v1/coach/sessions/{id}/roster

id must be a positive Long. This read accepts no alternate owner or member
context. The query restricts the target session and BookingStatus.CONFIRMED in
SQL; CANCELLED and any future non-confirmed status are excluded. It does not
infer confirmation from a payment or member relationship. Session status/date
does not impose BE-11's future-PUBLISHED filter on an authorized teaching roster.
A valid empty roster returns 200 with []. Sort: bookedAt ASC, bookingId ASC.

```json
[{
  "bookingId": 81,
  "memberCode": "MEM-0123",
  "memberName": "Taylor Member",
  "memberPhone": "0900000000",
  "packageRegistrationId": 71,
  "packageId": 61,
  "packageName": "Badminton 10 sessions",
  "bookedAt": "2026-10-14T09:00:00"
}]
```

memberCode is member_profile.member_code; name/phone come from the member's
current UserAccount. Phone can be null. Required member/profile relationships
are protected by existing foreign keys; no test disables them to manufacture
missing members. LEFT joins also tolerate unresolved optional/legacy data.
Only the eight documented fields are exposed; no email, address, private staff
notes, goals, password hashes, tokens, payment credentials or other registrations.
Names and phone numbers are returned unchanged, including Unicode and nulls.
No roster response or member phone is printed by the new code/tests.

Package fields come only from the actual booking.package_registration_id and
its sport_package. An expired/refunded historical registration remains selected;
another active registration is never substituted. Package labels reflect current
catalog names because no historical package-name snapshot exists on Booking.
Legacy null registration returns null for all three package fields. An inconsistent
cross-member registration link is suppressed by the member-ownership join, keeping
the booking visible with null package fields and without altering stored data.

bookedAt is the authoritative booking.booked_at timestamp, not created_at,
updated_at, payment time, session start or the current clock. It is returned as
stored LocalDateTime/DATETIME2(0), without timezone conversion. New BE-12 writes
use the center Clock; legacy timestamps are not rewritten by this read.

## Errors, read-only behavior and query design

Existing JSON errors use timestamp/status/message, with details for field errors:

```json
{"timestamp":"2026-10-16T09:00:00","status":400,"message":"Validation Error","details":{"to":"Must not precede from"}}
```

400: malformed/missing parameters, reversed/oversized date range, invalid IDs.
401: missing/invalid access authentication under the existing filter.
403: unsupported roles, missing coach mapping, inactive/stale service actor,
foreign coach selection, foreign/unassigned coach roster.
404: unknown requested coach profile or session. Errors contain no roster data.

The service is @Transactional(readOnly=true). Calendar metadata uses scalar
to-one projections, followed by the existing BE-10 grouped confirmed-count query
without an unbounded IN list. Rosters use an ownership scalar query followed by
one scalar booking/member/selected-package query. No collections or account
entities are serialized; no N+1 mapping or duplicate coach-sport/registration
joins are introduced. Actor revalidation has a constant lookup cost. Reads never
change sessions, bookings, cancellations, package credits, profiles, payments
or audit rows. Existing scheduling/booking locks and writers are unchanged.

## Automated coverage and local commands

| Test class | Coverage |
|---|---|
| CoachScheduleQueryTest | Exact date grammar, both/one/no bounds, leap day, same day, 62-day limit, reversed range, SQL date extrema, coach ID |
| CoachScheduleReadQueryTest | Actual Hibernate HQL parsing/DTO constructors/typed parameters with SQL Server dialect and all entity mappings, without JDBC |
| CoachScheduleServiceImplTest | Context identity, actual DB role/status, own scopes/counts/all statuses, manager default/filter, missing profile/session, pre-PII denial and direct calls |
| CoachScheduleControllerTest | Real security filter chain, HTTP paths/parameters/JSON, roles, JWT type/status, ownership, safe errors, null data and service proxy |
| CoachScheduleIntegrationTest | Real SQL Server relationships, bilateral isolation, ranges/statuses/ties/count drift, package/timestamp/contact mapping, read-only snapshots, query counts, reassignment guard and BE-10/11/12 interoperability |

From the repository root, without Docker:

```powershell
mvn -B -ntp -f backend/pom.xml test '-Dtest=CoachScheduleQueryTest,CoachScheduleReadQueryTest,CoachScheduleServiceImplTest,CoachScheduleControllerTest'
mvn -B -ntp -f backend/pom.xml test '-Dtest=!*IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -B -ntp -f backend/pom.xml test-compile -DskipTests
mvn -B -ntp -f backend/pom.xml package -DskipUnitTests=true -DskipITs=true
```

CI must execute the required com.sportify.catalog.controller.CoachScheduleIntegrationTest
with SQL Server 2022 Testcontainers, plus every existing required suite in
scripts/ci/required-integration-tests.txt. Run mvn verify -DskipUnitTests=true
from backend and both report/no-skips guards. Do not classify compile-only
results or disabledWithoutDocker skips as passing integration verification.
Fixtures use generated IDs, UUID-scoped accounts/catalog data and rollback;
no global demo accounts or seeded rows are changed or deleted.

Known limits: 62 days bounds time, not the number of rows; dense calendars/rosters
are complete arrays and can be large. Existing coach/date and booking/session/status
indexes are reused. Sorting/counting and key lookups may cost more at scale; no
benchmark or plan was measured, and any new index needs approval. Separate read
queries use existing isolation, so concurrent bookings/assignment edits can change
the view; there is no cross-request snapshot guarantee. The roster's own query
still enforces assignment at retrieval. Future pagination requires contract review.
