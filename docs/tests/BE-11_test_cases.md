# BE-11 Member Schedule, Class Search, Session Detail and Coach Profile

Branch: `feat/f2-member-schedule`. Base: updated main `efd53469227a50acacfa7954fb44590d943f9d7b`.
Dependency BE-10 is present as PR #51 (`89ee7dd`). New Java, tests and documentation are ASCII English. No migration or booking/scheduling write behavior is changed.

## API contract

All three reads require authentication and role MEMBER, RECEPTIONIST or MANAGER. Anonymous requests return 401; COACH/other roles return 403. Controller method security and explicit service actor checks both apply. A missing member profile on member detail returns 403. The existing staff-only `GET /api/v1/coaches` dropdown is unchanged.

### GET /api/v1/sessions

Optional filters use AND semantics:

| Parameter | Values / validation |
|---|---|
| sportId | Positive long; unknown id gives an empty page |
| from, to | Inclusive ISO local dates, SQL Server DATE years 1..9999; from <= to when both supplied |
| format | COACH_LED or SELF_TRAINING, from ClassSession.trainingType |
| level | BEGINNER, INTERMEDIATE, ADVANCED, from optional SportClass.level |
| coachId | Positive long, assigned session coach; excludes coachless sessions when supplied |
| page | Zero-based, default 0, nonnegative |
| size | Default 20, 1..100 using PagingDefaults |

Page offset `page * size` must fit 2147483647 (JPA offset API). Malformed values, unsafe dates, invalid IDs, reversed ranges or invalid pagination return 400 through the existing timestamp/status/message envelope. There is no date-range arithmetic or unbounded recurrence expansion. Unknown valid filter IDs return empty results.

The result uses `PageResponse`: content/page/size/totalElements/totalPages, ordered sessionDate ASC, startTime ASC, id ASC. Pagination occurs in SQL, with to-one fetch graphs and a separate grouped count of CONFIRMED bookings for at most 100 returned session IDs. No collection fetch join or fetch-all/client paging is used.

```json
{
  "content": [{
    "id": 100, "sportId": 10, "sportName": "Football",
    "classId": 40, "className": "Fundamentals", "trainingFormat": "COACH_LED", "level": "BEGINNER",
    "coachId": 20, "coachName": "Alex Coach", "facilityId": 30, "facilityName": "Court 1",
    "sessionDate": "2026-10-16", "startTime": "11:00:00", "endTime": "12:00:00",
    "capacity": 10, "bookedCount": 7, "freeSeats": 3, "full": false, "status": "PUBLISHED"
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Examples use illustrative IDs. Self-training may have null coachId/name. Legacy sessions may have null classId/name/level; they remain visible when other conditions hold. A level filter excludes classless sessions. Facility must be present and active.

### Visibility and time

One application Clock snapshot per schedule operation, using its configured Asia/Ho_Chi_Minh zone. Future means date > today OR date = today AND startTime > now. The exact start instant is already started. The SQL query casts the LocalTime parameter to TIME, following BE-10's SQL Server convention.

Only PUBLISHED sessions with endTime > startTime are returned. Linked classes must be active. Facility must be active. An assigned coach must have an active COACH account and a coach_sport qualification for the session sport. COACH_LED without a coach is unavailable; SELF_TRAINING with no coach is valid. No fake/default class coach is substituted for the assigned session coach. Draft, cancelled, completed, started, past, inactive/unavailable assignments and invalid overnight rows are hidden.

List and detail share exactly the same visibility predicate. GET `/sessions/{id}` returns 404 for hidden or nonexistent sessions for every allowed role. Historical manager access remains on the existing BE-10 calendar.

Capacity uses real CONFIRMED rows, matching BE-10 read DTOs. Cancelled bookings do not count. `freeSeats = max(0, capacity - bookedCount)`; `full = freeSeats == 0`. Over-capacity data is represented with zero free seats and a true full flag, without repairing counters in a read. The existing cached booked_count is deliberately not authoritative for these views.

### GET /api/v1/sessions/{id}

Positive session id required. No memberId parameter is accepted as member context. For a MEMBER, ownership comes from the authenticated account's shared MemberProfile primary key. Suitable packages never belong to another member.

```json
{
  "session": {
    "id": 100, "sportId": 10, "sportName": "Football", "classId": 40, "className": "Fundamentals",
    "trainingFormat": "COACH_LED", "level": "BEGINNER", "coachId": 20, "coachName": "Alex Coach",
    "facilityId": 30, "facilityName": "Court 1", "sessionDate": "2026-10-16", "startTime": "11:00:00",
    "endTime": "12:00:00", "capacity": 10, "bookedCount": 7, "freeSeats": 3, "full": false, "status": "PUBLISHED"
  },
  "memberContext": true, "bookingStatus": null, "alreadyBooked": false, "eligible": true, "reason": null,
  "suitableRegistrations": [{
    "registrationId": 200, "registrationCode": "REG-0200", "packageId": 80, "packageName": "30-day Football",
    "sportId": 10, "trainingFormat": "COACH_LED", "startDate": "2026-10-15", "endDate": "2026-11-13", "remainingSessions": 5
  }]
}
```

Staff get the same common session object with `memberContext=false`, null bookingStatus/alreadyBooked/eligible/reason, and an empty suitableRegistrations array. They do not receive any member package or booking lookup. This explicit representation resolves the specification's unspecified staff member context without adding a member selector.

Eligibility and precedence:

1. Current member CONFIRMED booking -> ALREADY_BOOKED, bookingStatus CONFIRMED, alreadyBooked true, eligible false. This precedes FULL, matching BookingService's duplicate check before capacity.
2. No free seat -> FULL. No package list is exposed as usable.
3. Real free seats exist, but cached booked_count >= capacity -> CAPACITY_UNAVAILABLE. The existing booking write would reject this known stale-counter condition. Keep full=false and real freeSeats, but eligible=false; do not falsely label it FULL or repair counters from a read. Contact staff for reconciliation through a separately approved process.
4. Owned, matching-sport, matching-format ACTIVE registrations with inclusive purchased start/end dates covering the session and positive remaining credits -> eligible true. Return all usable registrations sorted endDate/startDate/id ASC.
5. If any matching ACTIVE registration covers the date but all such balances are zero -> NO_SESSIONS_LEFT.
6. No matching ACTIVE or EXPIRED registration -> NO_PACKAGE. Wrong sport/format/owner and PENDING_PAYMENT/CANCELLED/REFUNDED are not candidates.
7. No date-covering usable registration and an EXPIRED status or end date before the session exists -> PACKAGE_EXPIRED_BY_THEN.
8. Otherwise -> PACKAGE_NOT_STARTED_BY_THEN. This minimal new code distinguishes a purchased package starting after the session from an already expired one; falsely labeling it eligible or expired would be incorrect.

The precedence is based on sets and sorted usable rows, not database return order. A valid package wins over unrelated exhausted/expired packages. An ACTIVE row can still be expired by the future session date. Expired status never becomes eligible even if its stored dates look valid. Cancelled bookings do not trigger ALREADY_BOOKED.

BE-10 prohibits overnight sessions, and booking validates inclusive LocalDate bounds, so session start and end occur on the same covered date. No end-of-day duration or current catalog-duration rule is invented. Age and member-overlap checks remain planned existing business rules. Eligibility is a read-time snapshot; it reserves neither a seat nor a credit. Clients should send a returned registrationId explicitly when booking, since legacy automatic package selection does not enforce training format. Actual booking retains all existing locks and conditional debit checks and may reject a snapshot that has become stale.

### GET /api/v1/coaches/{id}

Positive coach id, authentication and the same three roles required. Only an active COACH account with `is_public=true` has a public profile. Unknown/private/inactive/non-coach profiles return 404. This new public-facing policy does not change the staff dropdown. Fields:

```json
{
  "id": 20, "name": "Alex Coach", "headline": "Training coach", "bio": "Public coaching biography",
  "yearsExperience": 5, "specialties": null,
  "sports": [{"id": 10, "name": "Football"}],
  "certifications": [{"id": 1, "sportId": 10, "name": "Coaching certificate", "issuer": "Sport Board",
    "issuedOn": "2024-10-15", "expiresOn": "2027-10-15", "verified": true}],
  "upcomingSessions": {"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
}
```

Bio exists in the current schema and is returned as stored, including null. Sports come from coach_sport, sorted by sport id. Certification metadata includes explicit verified state and dates (nullable); certificates are not used to invent additional booking qualifications. Document URLs, account email/phone/password/authentication data and audit fields are never serialized. Upcoming uses the same visible session query, first 20, stable ordering and full pagination metadata. For subsequent pages use GET `/sessions?coachId={id}&page=1&size=20`. No profile collection fetch is used with session pagination.

## Automated coverage

| Group | Location | Cases / purpose |
|---|---|---|
| Service | `catalog/service/impl/MemberScheduleServiceImplTest` | 58 unit cases: every filter's typed contract, pagination/ordering/empty, unsafe parameters, fixed Clock forwarding, four capacity bounds, stale-cache compatibility guard, null-coach self-training, all eligibility codes, statuses, ownership, multiple candidates/precedence, staff context, invalid principal/profile |
| Controller | `catalog/controller/MemberScheduleControllerTest` | 24 MVC cases: nine allowed role/endpoint combinations, anonymous/COACH denials for three endpoints, typed/malformed filters, original error envelope, member injection, unchanged staff dropdown |
| Coach service | `identity/service/impl/CoachPublicProfileServiceImplTest` | 21 unit cases: public fields, multiple sports/certificates, null bio/empty collections, bounded upcoming metadata, invisible/missing coach, service role guard, unsafe ids, JSON private-field exclusion |
| Real query metadata | `catalog/repository/MemberScheduleQueryTest` | 3 unit cases: actual search/count/detail/owned-registration HQL parse and typed parameter binding using entity metadata and SQL Server dialect, with no database access |
| SQL Server | `catalog/controller/MemberScheduleIntegrationTest` | 56 parameter-expanded CI-only cases, described below; extends existing AbstractIntegrationTest and is required in the CI manifest |

Database filter execution is tested on real SQL Server in CI, not simulated by unit mocks. The unit query parser is not evidence of successful JDBC execution or SQL Server execution plans.

### Required SQL Server scenarios (56 cases, pending CI)

| Method | Cases | Checks |
|---|---:|---|
| search_filtersInSqlAndCombinesWithPublishedFutureVisibility | 7 | Default, sport, from, to, format, level, coach; every decoy otherwise visible |
| allFiltersUseAndNotOrAcrossIndependentDecoys | 1 | All filters together and six independent mismatches |
| paginationCountsJoinedRowsOnceAndHasBoundedQueryCountAndStableTieBreaks | 1 | 25 sessions in three pages, distinct IDs, stable tie sorting, confirmed-only count despite stale cache, three prepared statements for full page |
| hiddenSessionsAreAbsentAndDirectDetailIs404 | 12 | Past date/time, exact start, DRAFT/CANCELLED/COMPLETED, inactive class/facility/coach, unqualified/missing coach, overnight invalidity |
| sameDayFutureSelfTrainingWithNullCoachRemainsVisibleAndEligible | 1 | One second after now, null class/coach, actual self-training registration, facility, coach filter exclusion |
| eligibilityUsesOwnedMatchingPurchasedRegistrationsAgainstRealSql | 12 | Eligible, missing, zero, date/status expired, future start, wrong sport/format, pending/cancelled/refunded, other member |
| actualBookingCancellationRestoresFreeSeatAndCreditsAndCancelledBookingIsNotCurrent | 1 | Existing booking/cancellation services, credits and seats restored, no cancelled current booking |
| multipleRegistrationsHaveDeterministicUsableOrderAndInclusiveSessionEndDate | 1 | Mixed usable/exhausted/expired packages, inclusive endpoints, stable end/start/id order |
| extremeSqlDateBoundsAreSafeAndInvalidPaginationIs400AtHttp | 1 | Years 1..9999 without overflow; invalid page/size/date HTTP validation |
| managerCancellationHidesSessionWithoutChangingReadPolicyOrBookingRefundRules | 1 | Existing manager cancellation makes list empty and detail 404 |
| staleCachedCapacityDoesNotPromiseEligibilityWhenExistingWriteWouldReject | 1 | Real free seats stay visible, but cached booking guard returns CAPACITY_UNAVAILABLE, matching actual write rejection with no credit deduction |
| alreadyBookedPrecedesFullAndUnownedPackagesNeverLeakAcrossMembers | 1 | Full plus already booked precedence, foreign member gets no packages |
| coachProfileJoinsDoNotDuplicateSessionsAndBoundUpcomingAtTwentyWithNoSecrets | 1 | Two sports/certificates, >20 sessions, published/future filtering, no duplication/private data, private profile 404 |
| httpRoleMatrixAndMemberInjectionAreEnforced | 15 | Three endpoints x MEMBER/RECEPTIONIST/MANAGER/COACH/anonymous; staff has no member context |

Fixtures use UUID-scoped rows and generated identities; no assumed seed IDs. Cleanup removes only marked data in FK order. Fixed Clock is scoped to this integration test configuration. No existing test/migration/booking rule is disabled or modified.

## Verification commands and limitations

Final local verification (2026-10-15): 106 targeted BE-11 tests passed; the clean full backend unit suite passed 1120 tests with zero failures, errors or skips. The shared report guard confirms integration-tagged suites did not leak into the unit run. The 56 SQL Server cases are compiled and registered, not executed locally. Build-only Maven verification intentionally skips tests after the separate full unit run; see the exported results for exact exits and scope.

From backend:

```powershell
mvn -B -ntp test '-Dtest=MemberScheduleServiceImplTest,CoachPublicProfileServiceImplTest,MemberScheduleControllerTest,MemberScheduleQueryTest'
mvn -B -ntp clean test
mvn -B -ntp -DskipUnitTests=true -DskipITs=true verify
```

From repository root:

```powershell
python scripts/ci/check-test-reports.py unit backend/target/surefire-reports
python -m unittest discover -s scripts/ci -p 'test_*.py'
git diff --check
```

SQL Server CI must run `mvn -B -ntp verify -DskipUnitTests=true` without skipITs, then `python scripts/ci/check-test-reports.py integration backend/target/failsafe-reports`. The required manifest names MemberScheduleIntegrationTest. The guard must report nonzero test counts, zero failures/errors/skips, and all required suites executed. Docker is unavailable locally: these 56 cases are compiled and discoverable, NOT RUN, and not counted as passed. Current exact local outcomes and command exits are in the exported BE-11 Test Results report.

Known limits: read-time concurrency snapshots, no seat reservation, no cache repair, no new booking/locking/age/overlap rules, no profile schema migration, first 20 coach upcoming sessions, live SQL execution/performance awaiting CI. Existing unavailable assignments are hidden; manager historical reads remain available separately.
