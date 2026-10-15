# BE 05 verification cases

Feature branch: feat/f1-front-desk-queues.
API examples and decisions: [Front desk API contract](../api/BE-05-front-desk.md).

## Local unit and MVC coverage

| Suite | Verified behavior |
| --- | --- |
| MemberSearchPagingTest | First sort wins per mapped column; id/userId aliases; repeated fields; unpaged defaults and sort; unknown fields; size cap |
| FrontDeskQueuePagingTest | Forced oldest-first order; ignored client sort; size cap and unpaged defaults |
| MemberSearchServiceImplTest | Trim/blank normalization; literal wildcard escaping; bounded paging; sort allowlist; MapStruct tier and status projection |
| FrontDeskMemberServiceImplTest | Duplicate pre-checks; sequence codes; encoded passwords; unique account/profile conflict handling; unchanged propagation of other integrity errors; safe staff accountability log |
| UniqueViolationClassifierTest | SQL Server unique error numbers; nested Hibernate/Spring causes; chained SQL exceptions; other integrity failures; absent vendor evidence; cyclic exception chains |
| FrontDeskMemberOverviewServiceImplTest | Overview assembly with/without pending items and two check-ins; unknown member; MapStruct response composition |
| TemporaryPasswordGeneratorTest | Twenty-character passwords with all character classes and distinct outputs across repeated calls |
| FrontDeskQueueServiceImplTest | PENDING_PAYMENT only; immutable queue order; page cap; member/item/amount mapping |
| MemberControllerTest | Search/create 401; MEMBER/COACH 403; staff success; field errors; duplicate 409; raw integrity errors still mapped to 409 by the real global handler; Location and no-store on creation |
| FrontDeskMemberOverviewControllerTest | Overview 401; MEMBER/COACH 403; staff 200 with unchanged JSON; unknown-member 404 |
| FrontDeskQueueControllerTest | Both pending endpoints: 401, role-specific 403 and staff 200 |

Paging helpers belong to identity.service and catalog.service. Creation remains in identity; overview
aggregation, its DTO and endpoint controller belong to catalog. Tests mirror each owning package.
The BE-05 identity implementation does not import catalog classes.
Both paging helpers share the module-independent core.common.PagingDefaults constants (20 and 100).
Account fixture phone values use SecureRandom, including the existing session integration fixtures.

Run from backend in PowerShell:

```powershell
mvn clean verify
```

Real sort validation is checked in paging/service unit tests. Controller tests do not pretend that
mocking a validation exception exercises the validation implementation.

## SQL Server integration coverage

Each suite extends AbstractIntegrationTest directly, inheriting its integration tag and Testcontainers
disabledWithoutDocker behavior. All four fully qualified names are required by the CI report guard.

| Suite | Cases |
| --- | --- |
| com.sportify.identity.service.impl.MemberSearchIntegrationTest | Code/phone/email/name search; case-insensitive matches; literal percent/underscore/bracket/backslash; current tier with legacy multiple cards; correct count/pages; duplicate id/userId sorts via HTTP; two-statement search budget |
| com.sportify.catalog.service.impl.FrontDeskQueueIntegrationTest | Only PENDING_PAYMENT across every request status; stable oldest-first ties; page boundaries; member, item, amount and requested-at values |
| com.sportify.catalog.service.impl.FrontDeskMemberOverviewIntegrationTest | Cards, eligible/pending packages and seven arrival records; five latest check-ins; exactly five statements; null/empty overview |
| com.sportify.identity.service.impl.MemberCreationIntegrationTest | Normalized duplicate pre-check HTTP 409; direct DB email/phone unique constraints; persisted password hash and member code; concurrent sequence allocation |

The statement-count assertions explicitly guard against N+1 queries. Overview package lists remain
unbounded; tests do not imply a response-size limit for package registrations.

FrontDeskFixtures in com.sportify.testsupport creates isolated records with a random marker and numeric
phones. Transactional cases roll back automatically. The concurrent creation case runs outside the
test transaction and cleans its generated users/profiles in a finally block.

Local machines without Docker skip the SQL Server suites. Compilation and unit/MVC success do not
establish integration success. Check GitHub Actions on the current commit before claiming these
SQL Server cases passed.

## Repository checks

Run the repository PowerShell conventions check, the migration guard with Git Bash, and the unit
report guard from the repository root:

```powershell
powershell -NoProfile -File scripts/ci/check-conventions.ps1 origin/main
& 'C:/Program Files/Git/bin/bash.exe' scripts/ci/check-migrations.sh origin/main
python scripts/ci/check-test-reports.py unit backend/target/surefire-reports
git diff --check
```

V26 is unmerged and may be edited in place. It contains only unfiltered queue indexes on
(status, created_at, id), without INCLUDE columns. The existing V5 recent check-in index is reused.
