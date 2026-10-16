# BE-15 - Demo Classes and Sessions

## Scope, dependencies and isolation

Migration: `backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql`.
Base main: `1be8bb18cc74dc52c7b6e6f794952d594427754a`.
BE-07 V28 and BE-08 V20 mappings are present on this base, with BE-10 scheduling,
BE-11 discovery and BE-12 transactional booking. V29 is the next unused version
across all Flyway locations. V1-V28 are unchanged. No production Java, security
settings, schema, dependency or profile configuration is changed.

Activate only `dev,demo` on a fresh isolated SQL Server demonstration database.
The existing demo location/profile guard is reused. Ordinary dev/prod locations
exclude V28/V29; prod+demo fails before Flyway initialization. Validation and
out-of-order=false remain enabled. Never repurpose a demo database for production.

## Actual seed matrix

Each class has four COACH_LED sessions and four SELF_TRAINING sessions. The class
coach remains in charge of the class; self-training session coach_id is null.
All classes are active, use ADULTS_18_PLUS and retain the listed max_members.

| Code | Name | Sport | Level | Coach | Facility | Day offset | Coached times | Self times | Capacity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DEMO-CL-BAD-BEG | Badminton Foundations - Small Group | BADMINTON | BEGINNER | quan.coach@example.com | DEMO-BADMINTON | 2 | 09:00-10:30 | 11:00-12:00 | 3 |
| DEMO-CL-BAD-INT | Badminton Rally and Footwork | BADMINTON | INTERMEDIATE | quan.coach@example.com | DEMO-BADMINTON | 4 | 18:00-19:30 | 20:00-21:00 | 3 |
| DEMO-CL-SWIM-BEG | Swimming Water Confidence | SWIMMING | BEGINNER | nam.coach@example.com | INDOOR_POOL | 3 | 07:00-08:30 | 10:00-11:00 | 8 |
| DEMO-CL-SWIM-ADV | Swimming Endurance and Technique | SWIMMING | ADVANCED | nam.coach@example.com | INDOOR_POOL | 6 | 09:00-10:30 | 11:00-12:00 | 8 |
| DEMO-CL-BB-INT | Basketball Passing and Team Play | BASKETBALL | INTERMEDIATE | ha.coach@example.com | BB_COURT_A | 1 | 17:00-18:30 | 19:00-20:00 | 10 |
| DEMO-CL-BB-ADV | Basketball Advanced Game Skills | BASKETBALL | ADVANCED | ha.coach@example.com | BB_COURT_B | 7 | 09:00-10:30 | 11:00-12:00 | 10 |

The BE-07 coaches have matching primary sport and coach_sport links; no new coach
assignment is invented. Three seeded facilities are reused. The one new reserved
facility is DEMO-BADMINTON, name Demo Badminton Court, type COURT, sport BADMINTON,
capacity 12. Existing matching rows are preserved; conflicting/inactive facilities
fail. Missing shared facilities are dependencies, not invented replacements.

## Dates, visibility and scheduling

Initial anchor = CAST(GETDATE() AS DATE). Session dates are anchor + day_offset +
7 * week_no, with week_no 0,1,2,3. The 48 rows span days 1-28 inclusive, 24 per
format and 12 per seven-day window. All begin after initial startup, with both
weekdays/weekends and multiple times/facilities. Coach-led sessions last 90 minutes;
self-training lasts 60 minutes. Sessions do not cross midnight. BE-10 has no
additional operating-hours or coach-availability table/rule; slots stay 07:00-21:00.

All intended sessions are PUBLISHED. Their active classes, facilities, active
qualified coaches (or null coaches for SELF_TRAINING) satisfy the existing BE-11
visibility query. No API semantics are changed. GET /api/v1/sessions remains
authenticated for MEMBER/RECEPTIONIST/MANAGER; COACH is not an authorized reader.
Default ordering, filters and zero-based PageResponse pagination remain intact.

Strict BE-10 overlap is same date, shared non-null coach or facility, and
first.start < second.end AND second.start < first.end. Touching boundaries are
allowed. DRAFT/PUBLISHED occupy resources; CANCELLED/COMPLETED do not. The migration
checks both planned/planned and planned/existing pairs, excluding only the exact
existing session matched by its full business identity. It never randomly shifts
conflicting dates. Coachless self-training still reserves its facility.

## Capacity, packages and truthful payment state

The first coached DEMO-CL-BAD-BEG occurrence has capacity 3 and three CONFIRMED
bookings (An, Mai, Bao): booked_count=3, freeSeats=0, full=true. The first coached
DEMO-CL-BAD-INT occurrence has capacity 3 and two CONFIRMED bookings (An, Mai):
booked_count=2, freeSeats=1, full=false. The other 46 sessions initially have zero
bookings. All five bookings are package-linked and belong to their registration
owner; the filtered unique booking index is respected.

Two explicit complimentary demo packages are created for BADMINTON only:

| Package | Format | Duration | Credits | Session minutes | Price |
| --- | --- | --- | --- | --- | --- |
| DEMO-PKG-BAD-COACH | COACH_LED | 30 days | 8 | 90 | 0 |
| DEMO-PKG-BAD-SELF | SELF_TRAINING | 30 days | 8 | 60 | 0 |

Four ACTIVE RECEPTION grants use the real BE-07 receptionist as created_by_user_id,
zero original_price/paid_amount/discount, null payment_id and non-null activated_at.
This is supported by the current free staff-registration path; member online
registration's positive-payment rule remains unchanged. These are complimentary
entitlements, not fabricated sales or paid packages. No payment, invoice, receipt
or historical success audit is synthesized. Existing paid packages remain intact.

| Registration | Owner | Package | Initial remaining credits |
| --- | --- | --- | --- |
| DEMO-REG-BAD-AN | an.gold@example.com | DEMO-PKG-BAD-COACH | 6 |
| DEMO-REG-BAD-MAI | mai.standard@example.com | DEMO-PKG-BAD-COACH | 6 |
| DEMO-REG-BAD-BAO | bao.nocard@example.com | DEMO-PKG-BAD-COACH | 7 |
| DEMO-REG-SELF-BAO | bao.nocard@example.com | DEMO-PKG-BAD-SELF | 8 |

Dates cover anchor through anchor+29 inclusive, including every seeded session.
The five booking codes are DEMO-BK-FULL-AN/MAI/BAO and DEMO-BK-NEAR-AN/MAI. Real
CONFIRMED counts determine both cached counters and BE-11 free/full fields. Only
five credits are consumed initially. All four balances remain between zero and
total_sessions. No package credit is consumed again on a complete replay.

## Replay, partial recovery and transaction

Flyway applies V29 once. Restart does not roll dates forward, restore cancelled
bookings or refill badges after normal teacher interactions. A fresh isolated
database is the supported way to regenerate a future four-week demonstration.
Keep the demo location enabled for a database whose history includes demo versions.

Direct SQL replay joins a caller transaction or creates/commits/rolls back its own,
with XACT_ABORT and a transaction-owned BE-15 application lock. It reuses BE-07's
approved pattern. Existing owned sessions are locked first, then coaches and
facilities in ascending ID order; registration locks come after sessions/resources.
This shares BE-10 resource locks without a global table lock. SQL Server deadlock
abort remains possible with live writers; startup before serving traffic is the
intended path. No claim of freedom from database deadlocks is made.

Class code is unique. Session identity is class code + date + format + start time.
Registrations/packages/bookings/facility use reserved unique business codes.
Existing matching rows remain unchanged. A common anchor is recovered from class
created_at dates and reserved registration start dates. Conflicting anchors,
metadata, duplicate natural identities or unknown ownership fail atomically.

Safe partial recovery includes a missing class with its sessions, an empty future
session, null class coach on an otherwise exact reserved class, the reserved court
after dependent fixture removal, an unused package/registration, and a missing
known booking. A missing booking may have already consumed its credit/counter or
have had them restored. The migration accepts only the bounded delta attributable
to those specific missing bookings, inserts the real row and adjusts the remaining
delta once. It never increases credits or repairs unexplained discrepancies.
Missing past/today sessions/bookings and inactive entitlements needed for new
bookings are rejected. Missing BE-07 coach_sport or shared facilities are explicit
dependency failures. Existing CANCELLED fixture bookings and consistent additional
API-created bookings/sessions are preserved. Conflicting session status changes
are rejected by direct replay; normal Flyway startup does not replay applied SQL.

| SQL error | Meaning |
| --- | --- |
| 51500 | BE-15 application lock unavailable |
| 51501 | Required accounts, roles, sports, profiles, qualifications, age group, facility or resolved booking dependency missing |
| 51502 | Reserved identity/metadata/anchor conflict or duplicate session identity |
| 51503 | Coach/facility overlap with planned or blocking existing data |
| 51504 | Ambiguous capacity, credits, booking ownership or alternate confirmed booking |
| 51505 | A missing fixture cannot safely be recreated for its anchored date/state |

## Automated coverage

| Suite | Cases and purpose |
| --- | --- |
| DemoClassesMigrationTest | Next unique demo version; unchanged V1-V28 content fingerprint; real Flyway SQL Server parser batch; six-class specification; existing enum/sport/coach compatibility; four-week leap/year-boundary plans; no overlaps; realistic capacity/credit fixture arithmetic; stable IDs, atomic guards, ASCII and README consistency |
| DemoProfileSafetyConfigurationTest | Actual profile property binding/discovery excludes V28/V29 for ordinary profiles and includes both for demo; prod/demo combinations fail before Flyway creation |
| Existing DemoAccountsMigrationTest / SqlServerScriptReplayTest | BE-07 source, credentials and schema conventions; complete JDBC result/error consumption |
| DemoClassesIntegrationTest | Fresh V28+V29 Flyway; six classes/three sports/three levels; 48 published future rows; coach/facility validity and overlap; real capacity/credit/payment invariants; Flyway and SQL replay; concurrent replay; eight partial states; thirteen conflicts; four blocking overlap states; terminal/adjacent exceptions; past repair rejection; standalone late rollback; real ordinary-location migrations; all seven demo logins; every search filter/pagination; API flags/eligibility; real last-seat booking/cancellation and self-training; BE-10 conflicts and preservation of added sessions/cancelled fixture bookings |
| Existing DemoAccountsIntegrationTest | Original V28-only isolated fixture and all original assertions retained via explicit test-only Flyway target=28; V29 full-stack account compatibility is tested in the new suite |

The dedicated SQL suite has 59 prepared executions. It uses separate UUID-scoped
catalogs in the existing Testcontainers SQL Server instance and actual migration
resources. Normal mutations/recovery/conflicts are enclosed in rollback-only
transactions. Standalone rollback and ordinary-discovery checks use additional
fresh isolated catalogs, left to the container lifecycle. Shared/demo accounts are
never deleted in new cleanup code; generated IDs are resolved by business keys.
Snapshots use digests so assertion failures do not print credential data. HTTP
debug printing is disabled in the new suite. The corrected BE-07 replay helper
consumes all JDBC results, including errors after intermediate update counts.

## Local and CI verification

PowerShell from the repository root:

```powershell
mvn -B -ntp -f backend/pom.xml test "-Dtest=DemoClassesMigrationTest,DemoAccountsMigrationTest,DemoProfileSafetyConfigurationTest,SqlServerScriptReplayTest"
mvn -B -ntp -f backend/pom.xml test "-Dtest=!*IntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -B -ntp -f backend/pom.xml test-compile -DskipTests
python scripts/ci/check-test-reports.py unit backend/target/surefire-reports
git diff --check
```

Docker must not be run locally for this task. Test compilation and the Flyway
parser are not SQL Server execution or semantic SQL validation. GitHub Actions
must run the full tagged integration job, including
`com.sportify.core.config.DemoClassesIntegrationTest`, registered in the required
suite manifest. Its Failsafe report must show 59 executed cases and zero failures,
errors or skips, together with BE-07, BE-08, BE-10, BE-11 and BE-12 regressions.
Missing/zero/skipped reports are not passes. The normal migration guard already
includes db/demo; supplemental working-tree checks validate the uncommitted patch.

Read-only inspection examples for an active demonstration database:

```sql
SELECT code, name, level, max_members FROM sport_class WHERE code LIKE 'DEMO-CL-%';
SELECT c.code, s.session_date, s.training_type, s.capacity, s.booked_count,
       s.capacity - s.booked_count AS available_seats
FROM class_session s JOIN sport_class c ON c.id = s.class_id
WHERE c.code LIKE 'DEMO-CL-%'
ORDER BY s.session_date, s.start_time, s.id;
```

The exported BE-15 SQL verification report contains the full read-only overlap,
duplicate, booking, credit and foreign-key checks. The test-results report records
the actual executed counts, commands, exit codes and unexecuted CI requirements.
