# BE-07 - Demo Accounts Test Cases

## Configuration and migration contract

Approved design: explicit `demo` profile, isolated demonstration database, separate Flyway location `classpath:db/demo`. The existing identity and catalog locations remain in their original order. `application-prod.yml`, existing migration files, authentication rules and `out-of-order=false` are unchanged.

Migration: `backend/src/main/resources/db/demo/V28__seed_demo_accounts.sql`. At implementation base `56f28c5`, the shared inventory contains 23 migrations and ends at V27. Version uniqueness is checked across identity, catalog and demo by the unit suite and CI migration guard. Ordinary `dev`, `prod` and `test` profiles do not discover V28. The guard rejects `prod,demo` in either order, including with `dev`, during bean factory post-processing, before a Flyway initializer is created or invoked.

Use a fresh isolated demo database when enabling the profile. Do not reuse one for production or remove its migration location. A later shared version can make an unapplied V28 incompatible with disabled out-of-order execution. Profile checks cannot identify the business purpose of a database connection. Operators must select the database correctly.

## Account specification

Credentials and exact display names are in the root README's `Demo Accounts` table. Tests parse the actual SQL account rows and read the documented password; a disconnected copy of the seed cannot make these tests pass.

| Account email | Role | Required relationship/scenario |
|---|---|---|
| lan.reception@example.com | RECEPTIONIST | ACTIVE user, no member/coach profile |
| quan.coach@example.com | COACH | Shared-key coach profile, primary BADMINTON, matching coach_sport |
| nam.coach@example.com | COACH | Shared-key coach profile, primary SWIMMING, matching coach_sport |
| ha.coach@example.com | COACH | Shared-key coach profile, primary BASKETBALL, matching coach_sport |
| an.gold@example.com | MEMBER | Shared-key member profile, GOLD physical card |
| mai.standard@example.com | MEMBER | Shared-key member profile, STANDARD physical card |
| bao.nocard@example.com | MEMBER | Shared-key member profile, zero member_card rows |

Member codes use `seq_member_code` and match `CodeFormatter` (`MEM-%04d`, extending beyond four digits without truncation). Fresh-database codes are MEM-0001 through MEM-0003 in account order. Existing profiles retain their codes, and subsequent allocation cannot reuse the numbers consumed by the demo seed. Sequences are not reset; failed/rolled-back attempts may leave gaps. Card codes `DEMO-CARD-GOLD` and `DEMO-CARD-STANDARD` do not collide with generated `CARD-%04d` values.

GOLD: ACTIVE, start 2026-10-01, inclusive end 2027-09-30, price snapshot 300000 VND, live tier discount 5%. STANDARD: ACTIVE, start 2026-10-01, no end date, price 0, discount 0%. These are imported demonstration entitlements with nullable payment_id, supported by V14 and the existing legacy no-payment service path. No payment, invoice or receipt is fabricated. They are not new sales. The GOLD term does not renew itself. Neither a future nor expired term grants benefits; single visits never receive discounts. A no-card member may authenticate, read their profile and obtain an empty own-card list.

## Non-Docker JUnit coverage

`com.sportify.core.config.DemoAccountsMigrationTest` has nine tests:

| Case | Verification |
|---|---|
| U01 | README password satisfies the real StrongPasswordValidator |
| U02 | ApplicationConfig PasswordEncoder matches the SQL BCrypt hash; wrong password fails; plaintext absent from SQL |
| U03 | Actual SQL has exactly seven unique example.com identities, ordered ordinals and 1/3/3 role mapping |
| U04 | Actual coach sports are three distinct codes present in V6 |
| U05 | GOLD, physical STANDARD and no-card specification; no payment fabrication/account overwrite |
| U06 | Existing member sequence and formatter conventions, including values above 9999 |
| U07 | Deterministic inclusive twelve-month GOLD dates, matching README |
| U08 | Every README table name/email/role/password/sport matches SQL |
| U09 | Resource presence, global migration version uniqueness and transactional/locking safeguards |

`com.sportify.core.config.DemoProfileSafetyConfigurationTest` has nine parameterized executions:

| Case | Profiles | Verification |
|---|---|---|
| U10-U12 | prod,demo; demo,prod; dev,prod,demo | Startup fails; real FlywayMigrationInitializer supplier and migration callback are never reached |
| U13-U16 | Default; dev; prod; test | Actual YAML binds only ordinary locations, resource discovery excludes V28, out-of-order stays false |
| U17-U18 | demo; dev,demo | Actual YAML adds demo location, discovers V27/V28, initialization allowed |

The profile tests use a controlled Flyway initializer callback without a database; they prove startup ordering and discovery, not execution of T-SQL. The BCrypt checks use the actual encoder bean factory rather than a prefix check alone.

`com.sportify.core.config.SqlServerScriptReplayTest` adds 11 Round 2 JDBC protocol regressions: a control reproducing the legacy execute-only omission, three delayed SQL errors (51071/51072/51073), mixed result sets/update counts including zero, final no-result termination, errors after a result set, streaming/immediate/update-count errors, and propagation through an actual rollback-only Spring transaction manager. These use the real JdbcTemplate with mocked JDBC transport; they do not execute SQL Server or replace the integration cases.

## SQL Server integration coverage - CI required

Required suite: `com.sportify.core.config.DemoAccountsIntegrationTest`, registered in `scripts/ci/required-integration-tests.txt`. It extends `AbstractIntegrationTest`, inherits the integration tag, enables dev/demo/test, and creates a unique disposable catalog inside the Testcontainers SQL Server instance. Hikari selects that catalog before Flyway and Hibernate initialize. No production database is contacted. Other integration suites retain their original data. The test context is closed after the suite; container lifecycle removes the disposable catalog. Destructive fixture setup is limited to rollback transactions in that catalog, never shared cleanup.

The suite defines 60 test executions after Round 2: all original 59 plus one real SQL Server late-error/rollback regression.

| Case group | Executions | Actual verification |
|---|---:|---|
| Fresh migration | 1 | Correct catalog, seven accounts, profiles, cards, manager, trusted FKs, V28 success and ordered history |
| Successful authentication | 7 | AuthenticationService authenticates every account using README password; signed access/refresh tokens, subject, resolved authorities and current-user role |
| Stored password | 7 | Actual DB hash matches configured encoder and is not plaintext |
| Wrong password | 7 | AuthenticationService rejects each account's incorrect credential |
| Disabled account | 7 | INACTIVE status cannot authenticate; rollback restores ACTIVE |
| Coach relationships | 1 | Three shared-key profiles and valid distinct primary-sport links |
| Card behavior | 1 | Real membership service reads both physical cards, correct dates/prices/null payment, no-card empty list and profile access; GOLD 5%, STANDARD/no-card 0%, single visit 0% |
| Date behavior | 1 | Expired/future GOLD gives no discount at fixed test date 2026-10-15 |
| Replay preservation | 1 | Execute actual SQL twice; retain rotated hash, phone, biography, notes, certification, manager and unrelated user/card/payment, full row snapshots unchanged |
| Partial states | 8 | Missing receptionist user, coach user, coach profile, coach link, member profile, GOLD card, STANDARD card, or entire demo fixture restored; second replay unchanged |
| Conflicts | 8 | Wrong name, role, inactive status, primary sport, card tier, no-card with card, altered tier terms, card-code owner fail and roll back without overwrite |
| Dependency | 1 | Inactive required sport fails without inventing sports |
| Future code allocation | 1 | Next actual sequence code is unique and existing three member codes are distinct/valid |
| Inconsistent sequence | 1 | Occupied next sequence code fails rather than taking over another member |
| Concurrent replay | 1 | Two independent transactions execute actual SQL and serialize with sp_getapplock; snapshots unchanged |
| Standalone replay | 1 | SQL opens its own transaction and remains idempotent |
| Database constraints | 5 | Real unique email/member-code/coach-sport/card-code constraints and sport FK reject invalid writes |
| Late SQL Server error | 1 | UPDATE emits an intermediate count before THROW 51074; replay must surface the real SQL exception and rollback must restore the full snapshot |

Assertions avoid logging plaintext credentials, stored hash values, or complete JWTs. Snapshot comparisons use digests. Account identifiers in test names are public demo emails only.

## Round 2 - JDBC replay error propagation

The supplied CI artifacts contain 338 integration executions: 328 passed, 10 failed, zero errors/skips. All 10 failures were missing expected exceptions in the demo suite's eight conflicts, inactive required sport and occupied next member-code cases. The same artifacts contain 1138 passing unit tests and confirm Flyway applied V28 successfully. This is historical evidence from the original implementation, not a post-remediation pass.

The former replay helper called JdbcTemplate.execute(String), whose callback calls Statement.execute once and then closes the statement. A multi-statement SQL Server batch can return an intermediate update count before its validation error. Omitting getMoreResults leaves that later exception unobserved; a rollback-only callback can then return normally. The SQL was submitted, not skipped. The script's account-table INSERT precedes its validations and can produce such a count. Flyway uses its own result-draining executor, so this finding does not require changing V28 or an applied checksum.

SqlServerScriptReplay now submits the actual unchanged SQL through a StatementCallback on the same transaction-bound connection. It consumes result-set rows, advances with getMoreResults(CLOSE_CURRENT_RESULT), and terminates only when there is no result set and getUpdateCount is -1. A zero update count is not termination. SQLExceptions propagate through JdbcTemplate's existing translation before the callback completes; the existing TransactionTemplate rollback restores fixtures. No separate connection, manual commit, SQL splitting, dependency repair or exception suppression is introduced.

All original exception type/message and preservation assertions remain. The ten rejection cases additionally require a real SQLException with the expected vendor error: 51071 for missing sport/tier terms, 51072 for identity/profile/card conflicts, 51073 for the sequence collision. The added SQL Server regression uses error 51074. All 60 database cases still require an actual CI rerun. The migration, demo profile, production security configuration and required-suite manifest are unchanged.

## Commands and acceptance gates

Run from `backend/` in PowerShell:

```powershell
mvn -B -ntp '-Dtest=SqlServerScriptReplayTest,DemoAccountsMigrationTest,DemoProfileSafetyConfigurationTest' test
mvn -B -ntp clean test
mvn -B -ntp -DskipTests test-compile
```

Surefire excludes the inherited `integration` tag. Do not replace this with filename-only exclusions: existing database suites also have repository names without IntegrationTest in their filenames. Docker is unavailable locally, so the SQL Server suite must not be reported as passed or locally skipped execution.

In GitHub CI with Docker available, run from `backend/`:

```powershell
mvn -B -ntp verify -DskipUnitTests=true
```

Then run from repository root:

```powershell
python scripts/ci/check-test-reports.py integration backend/target/failsafe-reports
```

The required demo suite must have 60 executed test cases, zero failures/errors/skips, and actual login cases for all seven emails. Review Failsafe XML, not just a green Maven exit. All existing required SQL Server suites, migrations, frontend checks, security checks and applicable Compose smoke tests must remain green. Compose ordinary startup counts only migrations under the ordinary location; the new opt-in location does not change its expected count.

Exact local command results and environment limitations are exported in the BE-07 report directory. Compilation does not prove SQL syntax, database relationships, locking, transaction behavior or authentication against SQL Server. Those acceptance items remain unverified until the CI integration job actually executes.

## Round 3 - Compose smoke control flow

The public GitHub job metadata for commit a37847fe12fc6adb3921149f93bfc85aed71b61c records a successful SQL Server integration job and a failed Compose password-policy assertion (run 38777966978). Normal Compose startup was skipped. Its failed diagnostic step is distinct from the original raw-Docker assertion. Raw primary-step logs were unavailable during remediation, so no specific Docker/SQL Server runtime trigger is asserted as proven.

The Compose workflow now prepares generated, masked CI credentials first and retains the environment through both negative checks. The IMAGE_TAG check requires its specific interpolation error. A separate disposable SQL Server container must exit nonzero with an explicit password validation failure; pull/start/wait/inspect errors, timeout, OOM, signal exit, successful exit or unrelated logs cannot satisfy it. Image pulling precedes the runtime deadline. Container removal runs on success and failure, and cleanup failure remains a failure.

Failure diagnostics and stack cleanup provide CI-only parsing defaults when IMAGE_TAG, DB_PASSWORD or deploy/.env is absent. They never start services. Raw password-policy output stays in RUNNER_TEMP until failure diagnostics print it with the deliberately invalid candidate redacted. Generated database/JWT credentials are registered with GitHub masking before use. Existing environment files survive diagnostics; temporary files are removed, and final cleanup removes the disposable test environment.

Run from repository root:

```powershell
python -m unittest discover -s scripts/ci -p 'test_*.py' -v
bash -n scripts/ci/compose-smoke.sh
git diff --check
```

`test_compose_smoke.py` executes the real helper and the workflow's IMAGE_TAG shell block with fake Docker/timeout transports in temporary directories. It exercises negative failure classifications, large logs, credential preparation/masking, missing/invalid environment values, diagnostic failures and cleanup. The existing CI Python discovery automatically runs it. These tests require Bash, Python and OpenSSL; they do not use or validate a Docker daemon, Compose interpolation, image startup or SQL Server policy enforcement.

GitHub Actions must still execute the full Compose job successfully: invalid-password rejection, valid-password SQL Server health, db-init creation/idempotency, sa login, no published port 1433, non-sa rejection, invalid JWT rejection, backend health and ordinary-location Flyway counts. Production Compose and all BE-07 SQL/profile files are unchanged. Re-run the two focused demo configuration suites locally to check profile safety; do not report Docker integration as locally passed.
