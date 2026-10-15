# BE 05 verification cases

Base branch: origin/main at 89ee7dd. Feature branch: feat/f1-front-desk-queues.
API examples and decisions: [Front desk API contract](../api/BE-05-front-desk.md).

## Local unit and MVC coverage

| Suite | Verified behavior |
| --- | --- |
| MemberSearchServiceImplTest | Trim/blank normalization; literal wildcard escaping; max page size; sort allowlist and stable ties; MapStruct projection of tier and status |
| FrontDeskMemberServiceImplTest | Normalized email/phone duplicate pre-checks; sequence codes; encoded passwords; transactional flush conflicts; mapped overview with/without pending items and two check-ins; unknown member |
| TemporaryPasswordGeneratorTest | Twenty-character passwords with all character classes and distinct outputs across repeated calls |
| FrontDeskQueueServiceImplTest | Only PENDING_PAYMENT requested; immutable oldest-first queue order; page cap; member/item/amount mapping |
| MemberControllerTest | 401 without authentication; 403 for MEMBER/COACH; success for MANAGER/RECEPTIONIST; field errors; duplicate and database 409; unknown-member 404; no password hash; no-store on creation |
| FrontDeskQueueControllerTest | Both pending endpoints: 401, role-specific 403 and staff 200 |

The previous MemberSearchServiceTest was replaced by MemberSearchServiceImplTest in the implementation
package, mirroring the class under test. All new tests mirror the owning production package.

Run from backend in PowerShell:

```powershell
mvn clean verify
```

## SQL Server integration coverage

MemberProfileRepositoryIntegrationTest inherits AbstractIntegrationTest, its integration tag and
Testcontainers disabledWithoutDocker behavior. It is registered in scripts/ci/required-integration-tests.txt.

| Case | Expected result |
| --- | --- |
| Search by member code, phone, email, partial full name | The matching member; case-insensitive text search |
| Search for percent, underscore, opening bracket and backslash | Literal matches only; no wildcard broadening |
| Search with several active/future/expired/pending cards | One row per member; highest current tier; correct count and page boundary |
| Search returning a full page of members | Exactly two statements, including count |
| Pending queues with every request status present | Only PENDING_PAYMENT, createdAt ASC and id ASC, including ties and page boundaries |
| Overview with cards, packages and seven check-ins | Current and pending cards, eligible and pending packages, five latest arrivals; exactly five statements |
| Overview without related rows | Null cards and empty arrays |
| Create duplicate email or phone through HTTP | Standard 409 body after normalization |
| Bypass service pre-check and insert duplicate email/phone | SQL Server unique constraint violation |
| Create a valid member | Existing code format, stored hash verifies the returned temporary password |
| Concurrent member creation | Distinct sequence-generated member codes |

Transactional integration fixtures roll back automatically. The concurrent test runs outside the
test transaction and cleans its own generated accounts and profiles. Fixture codes/emails have a
random per-test marker; phones are random numeric strings.

Local machines without Docker skip the SQL Server suites. Compilation and local unit/MVC results
do not establish that these integration assertions passed; see the implementation report for the
actual run results.
