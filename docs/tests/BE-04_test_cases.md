# BE-04 Manager Activity Log

## Endpoint and authorization

`GET /api/v1/manager/activity-logs` requires an ACTIVE authenticated MANAGER.
The controller and service both enforce `hasRole('MANAGER')`. MEMBER, COACH,
RECEPTIONIST and unsupported roles receive 403; anonymous, inactive accounts
and refresh tokens receive 401 under the existing JWT filter. The read service
uses a read-only transaction, does not invoke AuditService and never edits rows.

## Query parameters

| Parameter | Contract |
|---|---|
| actorId | Optional positive signed 64-bit account ID; exact stored FK match. Unknown positive IDs return an empty page. |
| action | Optional case-sensitive AuditAction constant, including legacy LOGIN and REFRESH_TOKEN. Trimmed; blank means absent. Unknown values return 400. |
| from | Optional inclusive lower local timestamp or beginning of calendar day. |
| to | Optional inclusive timestamp or entire final calendar day. |
| search | Optional trimmed case-insensitive literal text, maximum 200 UTF-16 code units. Blank means absent. |
| page | Zero-based integer, default 0; nonnegative. page * size must not exceed Integer.MAX_VALUE. |
| size | Integer 1-100 inclusive, default 20; invalid values return 400 rather than silently changing the requested size. |

All applicable filters combine with AND in the database before pagination. Search
matches actor current display name, action or entity type using contains/OR, plus
an exact signed 64-bit numeric entityId match when the entire search parses as a
Long. Numeric IDs are not cast to strings or matched as substrings. Actor ID is a
separate filter. Missing actors remain searchable through action/type/target ID.
Percent, underscore, opening bracket and backslash are escaped with the existing
SQL Server SearchUtils/LIKE ESCAPE convention. Apostrophes are bound data. Java
normalization uses Locale.ROOT and SQL LOWER; accent equivalence follows the
existing SQL Server collation. Email, phone, entityCode, IP address, free-form
summary and raw JSON details are neither searched nor returned. Search cannot
infer a secret from hidden details through result counts.

## Dates and ordering

Accept exactly `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm:ss`, years 0001-9999.
Offsets, fractions, blank values and invalid calendar dates return 400. Whole
seconds match the actual `DATETIME2(0)` storage precision. A date-only from is
midnight inclusive; a date-only to becomes the next midnight exclusive, so all
seconds of the final day are included. At 9999-12-31, to is instead <=
9999-12-31T23:59:59 without overflow. Timestamp boundaries are inclusive. Reversed
ranges, including an empty reversed mixed date/timestamp range, return 400.

createdAt and boundaries are offsetless center wall-clock values in
Asia/Ho_Chi_Minh (UTC+07:00). AuditConfig explicitly connects JPA auditing to the
existing Clock bean in that zone. There is no implicit JVM-default timezone
conversion or UTC suffix. The SQL default SYSDATETIME applies to direct SQL
inserts that omit created_at; those external writers must align their database
clock with center time. This endpoint does not infer or rewrite historical
offsets. Callers supply center wall time.

Order is always `createdAt DESC, id DESC`; arbitrary client sorting is not
supported. The data query projects eight scalar fields with LEFT actor/role
joins, SQL offset/limit and a separate matching count query. No account entities,
metadata LOBs or collections are fetched. PageResponse returns the requested page
and size even for an empty or out-of-range page. totalElements and totalPages
refer to the complete filtered dataset, not the visible page. Live inserts can
shift offset pages between requests; this is not a cursor or snapshot API.

## Response and historical semantics

```json
{
  "content": [{
    "id": 91,
    "createdAt": "2026-10-16T09:00:00",
    "actorId": 17,
    "actorName": "Audit Manager",
    "actorRole": "MANAGER",
    "action": "PASSWORD_CHANGED",
    "entityType": "USER_ACCOUNT",
    "entityId": 17,
    "shortDetail": "Password changed"
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Actor name/role describe the current account, including inactive accounts; they
are not historical snapshots. Account renames and role changes affect old rows'
display. Null/unresolved/blank actor names use `System / unavailable actor` and
missing roles stay null, without inventing historical roles. Actor IDs and target
IDs can be null. entityType is passed through; the current schema requires it,
but the mapper also tolerates null legacy values. A referenced actor cannot be
physically deleted under the current NO ACTION foreign key. LEFT joins retain
all supported null/system entries. No FK is weakened to manufacture broken rows.

shortDetail is a deterministic safe action label: known constant names become
sentence case with underscores replaced by spaces, at most 60 characters;
unknown historical actions use `Activity recorded` while their original action
is preserved. The field intentionally does not copy or truncate stored summary
or details, which can contain personal information or arbitrary notes. Thus null,
long or malformed legacy metadata cannot affect display or leak credentials.
Unicode actor display names are returned unchanged, without substring truncation.
The existing JSON constraint prevents malformed details in ordinary SQL fixtures.
Stored records, metadata and existing audit-writing behavior remain untouched.

## Examples and errors

```text
/api/v1/manager/activity-logs
/api/v1/manager/activity-logs?actorId=17&action=PASSWORD_CHANGED
/api/v1/manager/activity-logs?from=2026-10-01&to=2026-10-16&search=USER_ACCOUNT&page=0&size=20
/api/v1/manager/activity-logs?actorId=17&action=PASSWORD_CHANGED&from=2026-10-01&to=2026-10-16&search=Audit&page=0&size=5
/api/v1/manager/activity-logs?from=2026-10-16T12:00:00&to=2026-10-16T12:00:00
```

Use standard URL encoding for special characters. Example field-validation error:

```json
{"timestamp":"2026-10-16T09:00:00","status":400,"message":"Validation Error","details":{"size":"Must be between 1 and 100"}}
```

Malformed numeric arguments use the established 400 Invalid value for parameter
message. Authentication/authorization use the existing 401 Unauthorized and
403 Access Denied JSON envelopes with timestamp, status and message. Responses
never echo arbitrary invalid date/action/search values. No new API error schema
or authentication behavior is introduced.

## Test cases and verification

| Coverage | Automated source | Expected evidence |
|---|---|---|
| Defaults, blank/normalized search, all filters, action registry | ActivityLogQueryTest | Local JUnit pass |
| Valid/reversed/malformed/same-day/extreme/timezone-independent dates | ActivityLogQueryTest | Local JUnit pass |
| Page/size/actor/search limits and overflow | ActivityLogQueryTest | Local JUnit pass |
| Safe action labels, nullable targets, Unicode, actor fallback | ActivityLogResponseTest | Local JUnit pass |
| PageResponse metadata and empty unknown actor | ActivityLogServiceImplTest | Local JUnit pass |
| Real HTTP path, defaults, combined arguments, 400 envelopes | ActivityLogControllerTest | Local MockMvc pass |
| Manager, all non-manager roles, anonymous, inactive, refresh, direct service | ActivityLogControllerTest | Local real security-chain pass |
| Single/combined filters and database counts | databaseFiltersAndCounts, standaloneFiltersMatchIndependentSqlWithoutSearch | SQL Server CI, eleven isolated combinations plus seven comparisons with independent SQL |
| First/middle/last/empty pages, equal timestamps, filtered totals | pagesStableAndCorrect, filteredCountsApplyBeforePagination | SQL Server CI |
| Exact timestamps, complete final day, year 9999, reversed range | timestampBoundariesAndFinalDay, maximumSupportedDayDoesNotOverflow, reversedDateRejectedOnSqlServerStack | SQL Server CI |
| Null actors/targets, current inactive/renamed actor and changed role | nullableActorsAndTargetsSurviveLeftJoins, currentInactiveRenamedActorAndRoleAreReported | SQL Server CI |
| Safe deletion history, unchanged NO ACTION FK | deletedUnreferencedAccountDoesNotRemoveSystemHistory | SQL Server CI |
| Name/action/type/exact numeric target, literal wildcards and injection text | searchNamesActionsTypesAndExactTargetId, literalSearchAndInjectionSafety | SQL Server CI |
| Null/unknown historical details and long sensitive JSON | nullAndLegacyMetadataAndHistoricalActionRemainReadable, rawMetadataNeverSelectedSearchedOrReturned | SQL Server CI |
| Large dataset, bounded two-query scalar projection, no N+1 | largeDatasetUsesTwoBoundedScalarQueries | SQL Server CI |
| Read does not add/change rows, existing AuditService still writes | readsDoNotWriteAndAuditServiceStillWorks | SQL Server CI |
| Actual signed access JWT and deactivation | actualAccessTokenAndInactiveAccountRejection | SQL Server CI |

Run focused non-Docker tests from the repository root:

```powershell
mvn -B -ntp -f backend/pom.xml test '-Dtest=ActivityLogQueryTest,ActivityLogResponseTest,ActivityLogServiceImplTest,ActivityLogControllerTest'
mvn -B -ntp -f backend/pom.xml test '-Dtest=!*IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -B -ntp -f backend/pom.xml test-compile -DskipTests
```

Do not run Docker locally. CI must run `mvn -B -ntp verify -DskipUnitTests=true`
from backend, with SQL Server 2022 Testcontainers and the entire existing required
suite manifest, now including `com.sportify.identity.controller.ActivityLogIntegrationTest`.
The suite inherits the integration tag and uses UUID-scoped rollback fixtures,
real Flyway tables and foreign keys. No global seeds or BE-07 demo accounts are
modified or deleted; IDs are generated, never assumed. It is compiled locally,
not claimed executed. Existing regression unit suites are run unchanged.

Known limits: leading-wildcard LOWER searches and exact counts can scan large
tables; action has no dedicated index. Existing timestamp, actor/timestamp and
target indexes are preserved. Future indexes need approval and measured plans.
Deep offset pages have normal offset cost. No measured performance claim is made.
No frontend activity-log screen implementation or detail drill-down is included.
