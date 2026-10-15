# BE 05 implementation report

## 1 Summary and base verification

Implemented all five backend operations on feat/f1-front-desk-queues, created directly from the fetched
origin/main at 89ee7dd (Feat/f2 sessions schedule, PR 51). The original working branch was
feat/f2-sessions-schedule; it had no uncommitted changes and the same tree as the fetched base.
main was not checked out, modified or pushed.

The base already includes member-card work from PR 39, package work from PR 38, member search and
check-in support. No business entity was missing. The direct user instruction to commit and push
takes precedence over the older no-commit/no-push instructions inside the task document.

| Operation | Delivered behavior |
| --- | --- |
| GET /api/v1/members | Existing query parameter and response fields preserved; literal substring search across code/phone/email/name; current tier and account status; bounded Pageable; sort allowlist |
| GET /api/v1/members/{id}/overview | Profile, current card, oldest pending card request, active/pending packages and five latest check-ins; standard 404 |
| GET /api/v1/membership-cards/pending | Only PENDING_PAYMENT; paginated oldest-first queue; member, tier, amount and request timestamp |
| GET /api/v1/packages/registrations/pending | Only PENDING_PAYMENT; paginated oldest-first queue; member, package, amount and request timestamp |
| POST /api/v1/members | Validated and normalized identity; sequence-generated code; hashed temporary password returned once; pre-check and database-race 409 |

All operations permit RECEPTIONIST and MANAGER and reject MEMBER/COACH with 403 and missing
authentication with 401. They reuse the existing JWT filter, method security and error envelope.

The exact existing model used by this task:

| Entity | Relevant fields and database columns |
| --- | --- |
| MemberProfile | id -> user_id, memberCode -> member_code, userAccount, currentLevel, joinedOn |
| UserAccount | id, fullName -> full_name, email, phone, status, passwordHash -> password_hash, role |
| MemberCard | member, tier, cardCode, status, startDate, endDate, pricePaid -> price_paid, createdAt -> created_at |
| MembershipCardTier | code, name, tierRank -> tier_rank |
| SportPackageRegistration | member, sportPackage, registrationCode, status, startDate, endDate, totalSessions, remainingSessions, paidAmount -> paid_amount, createdAt -> created_at |
| CheckIn | member, checkedInAt -> checked_in_at, result, denialReason, note |

CardStatus values: PENDING_PAYMENT, ACTIVE, EXPIRED, CANCELLED, REPLACED.
PackageRegistrationStatus values: PENDING_PAYMENT, ACTIVE, EXPIRED, CANCELLED, REFUNDED.
CheckInResult values: ALLOWED, DENIED. No new lifecycle value or pending-request entity was invented.

## 2 Files added or changed

Paths below are relative to the repository root. Production Java files use
backend/src/main/java/com/sportify; tests use backend/src/test/java/com/sportify.

| Layer | Files |
| --- | --- |
| Controllers | identity/controller/MemberController.java; catalog/controller/FrontDeskQueueController.java |
| Identity DTOs | identity/dto/MemberSearchResponse.java; MemberCreateRequest.java; MemberCreateResponse.java; MemberOverviewResponse.java |
| Catalog DTOs | catalog/dto/FrontDeskCardResponse.java; FrontDeskPackageResponse.java; FrontDeskCheckInResponse.java |
| MapStruct | identity/mapper/MemberSearchMapper.java; FrontDeskMemberMapper.java; catalog/mapper/FrontDeskMapper.java |
| Service interfaces | identity/service/FrontDeskMemberService.java; catalog/service/FrontDeskQueueService.java |
| Service implementations | identity/service/impl/MemberSearchServiceImpl.java; FrontDeskMemberServiceImpl.java; catalog/service/impl/FrontDeskQueueServiceImpl.java |
| Credential generation | identity/service/TemporaryPasswordGenerator.java |
| Repositories/projection | identity/repository/MemberProfileRepository.java; MemberSearchRow.java; catalog/repository/MemberCardRepository.java; SportPackageRegistrationRepository.java; CheckInRepository.java |
| Shared pagination | core/common/FrontDeskPaging.java |
| Migration | backend/src/main/resources/db/migration/catalog/V26__add_front_desk_queue_indexes.sql |
| Identity tests | identity/controller/MemberControllerTest.java; identity/repository/MemberProfileRepositoryIntegrationTest.java; identity/service/TemporaryPasswordGeneratorTest.java; identity/service/impl/FrontDeskMemberServiceImplTest.java; MemberSearchServiceImplTest.java |
| Catalog tests | catalog/controller/FrontDeskQueueControllerTest.java; catalog/service/impl/FrontDeskQueueServiceImplTest.java |
| CI registration | scripts/ci/required-integration-tests.txt |
| API documentation | docs/api/BE-05-front-desk.md |
| Verification cases | docs/tests/BE-05_test_cases.md |

The old identity/service/MemberSearchServiceTest.java was replaced by a stronger implementation test
in identity/service/impl, mirroring the main class. Unused legacy repository search methods were
removed when replacing their unescaped/manual-mapping implementation.

V26 adds status/created_at/id indexes for both queues and a member/time/id recent check-in index.
Existing email and phone unique constraints and seq_member_code are reused. No old migration was
modified. The highest version on the base was V23; remote feat/f3-refund-payment-link already uses
V24 and feat/f3-revenue-reports already uses V25. V26 is the next unallocated version.

## 3 API contract

[The confirmed API contract](../api/BE-05-front-desk.md) includes request and response JSON for every
operation, field semantics, null/empty behavior, page metadata, validation rules, ordering, and
400/401/403/404/409 error behavior. It also documents required changes to the existing proposed FE-05
adapters. No OpenAPI library exists in the base, so no new documentation dependency was introduced.

The existing search request remains query=...; q is not added. Page responses use Spring Page,
including content, number, size, totalElements and totalPages. No old catalog operation changes shape.

## 4 Design decisions and assumptions

- Pending queues can grow without bound, so they use pages of 20 by default and at most 100 rows.
  Client sort cannot override createdAt ASC, id ASC. Every pending request is available across pages.
- Member search accepts id, userId, memberCode, fullName, phone, email and status sorting. An id
  tie-breaker makes pages deterministic. Unknown properties return 400 before repository access.
- Search uses the default SQL Server case-insensitive collation and escapes percent, underscore,
  opening bracket and backslash with an explicit ESCAPE clause. It does not wrap columns in LOWER.
- A scalar projection avoids lazy loading during search. A page with a count uses two statements;
  an incomplete page may omit the count. The integration test verifies a full-page count path.
- A current card is ACTIVE and valid today. Highest tierRank wins, then greatest card id. A pending
  card is the oldest unpaid request. Missing cards return null.
- Active packages follow the existing admission rules: ACTIVE, within inclusive dates and a positive
  remaining session balance. All PENDING_PAYMENT packages are returned separately.
- Overview uses five queries and to-one fetch joins. The package lists are complete rather than
  truncated; recent check-ins are limited by the repository query to five, with stable timestamp ties.
- Code generation reuses seq_member_code and CodeFormatter, yielding MEM-0001-style values. Sequence
  allocation is race-safe and may have gaps after rollbacks.
- Temporary passwords contain 20 SecureRandom characters, including lowercase, uppercase, digit and
  special characters. The existing PasswordEncoder hashes them. Creation returns plaintext once with
  Cache-Control: no-store; toString excludes it and no entity stores it.
- The identity module has no first-login mandatory password-change mechanism. No parallel flag or
  authentication behavior was invented.
- Account and profile creation share one transaction. Both identity fields are checked in advance,
  then saveAndFlush exposes database uniqueness races inside the service for standard 409 handling.
- The base uses Java 25 and Spring Boot 3.5.16, despite the older task text mentioning 17/3.2.
  The existing stack and dependency versions were retained.

## 5 Verification results

Final PowerShell command in backend: mvn clean verify.

| Verification | Result |
| --- | --- |
| Final local unit and MVC tests | 791 passed; zero failures, errors or skips |
| Local integration discovery | 151 cases skipped because Docker is unavailable |
| GitHub Actions unit tests | Passed |
| GitHub Actions SQL Server 2022 integration tests | 183 passed; zero failures, errors or skips |
| BE-05 SQL Server suite | 19 passed, including wildcard search, queue order, uniqueness, sequence concurrency and statement counts |
| CI frontend lint, tests and build | Passed |
| CI application packaging and final gate | Passed |
| CI project conventions/workflow lint | Passed |
| Local conventions, migration guard and unit report guard | Passed |
| git diff --check | Passed |

[Successful CI run 38689223087](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/38689223087)
ran on commit 51e9887, which contains the final backend and tests. This implementation report is a
subsequent documentation-only commit. The CI migration job is PR-only and therefore skipped on
workflow_dispatch; its migration guard was executed locally against origin/main.

The local skipped count and CI executed count differ because disabled parameterized integration
methods are reported before parameter expansion. None of the SQL Server integration tests ran locally;
they did run successfully in CI. JaCoCo output is available in backend/target/site/jacoco.

## 6 Conventional commits and delivery

The user explicitly requested pushing the completed work, so the following commits were made:

- 6c75470 feat(front-desk): add member overview creation and pending queues
- 183a811 test(front-desk): cover member workflows security and SQL Server queries
- 51e9887 docs(front-desk): document API contracts and verification cases
- docs(front-desk): record verified BE-05 delivery and remaining integration notes

All work is pushed to origin/feat/f1-front-desk-queues. No PR was created, no merge was performed,
and main was not pushed.

## 7 Risks and follow-up work

- FE-05 currently expects a proposed member/cards/registrations overview and unpaged arrays containing
  older catalog DTOs. Its adapters must adopt this documented backend contract. Creation must present
  temporaryPassword once without retaining it in browser storage or logs. Frontend changes were not
  part of this backend task.
- Leading-wildcard substring search still requires scans. Existing indexes and the new queue indexes
  reduce avoidable work, but statement counts alone do not prove production latency. Representative
  execution plans and data volumes are still needed for capacity decisions.
- Pending queue pages use offset pagination; concurrent insertions or payment transitions can move
  rows between page requests. Refresh the queue after a mutation.
- V24 and V25 are reserved on other branches. Coordinate migration merge/deployment ordering before
  deploying V26: out-of-order migrations are disabled in the existing configuration.
- The base has no mandatory first-login password change or temporary-password recovery endpoint.
  Losing the creation response cannot recover that plaintext from this API.
- Daily check-in filtering mentioned in FE-05 comments is outside the BE-05 task specification.
  It was not added or represented as implemented.
- Audit DOCX structure and a paragraph-layout preview were checked locally. Native Word automation
  stalled and was stopped; the document skill container renderer was unavailable in this Windows
  session, so exact native Word/LibreOffice rendering was not verified.

No unrelated application bug was changed.

## 8 AI Audit Log

The local audit was updated at logs/ai_audit/BE-05/AI_Audit_Log.docx with scope, authorization,
implementation, test evidence, delivery and follow-up notes. Every XML package part was validated and
the local preview was inspected. git check-ignore confirms that the audit directory is ignored.
The DOCX, rendering files and CI logs are not committed or pushed.
