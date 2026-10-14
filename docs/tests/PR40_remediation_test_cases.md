# PR40 remediation test cases and execution evidence

Date: 2026-10-14, Asia/Bangkok. Revision: working files based on `22b762f0c2594580b8243e63c60b8974e8fbd003`. Main: `6e82388ece8c3a2ea35ae57b684e2d3adc83f3f3`. No commit or push was performed.

Environment: Windows, Oracle JDK 25.0.2, Maven 3.9.15, Surefire/Failsafe 3.6.0, Jupiter 5.12.2, Testcontainers 1.21.4, frontend dependencies already installed. Docker is unavailable. Evidence root: `D:/G3-SportCentreManagement/.pr40-work`.

## Commands and actual results

| Command and location | Exit | Actual result | Evidence |
| --- | --- | --- | --- |
| Backend baseline: `mvn -B -ntp clean test` | 0 | 608 tests; 0 failures/errors/skips | backend-baseline.log; baseline-surefire/ |
| Isolated committed backend with ZIP provider-property deletion: `mvn -B -ntp clean test` | 0 | 730 discovered; 608 executed; 122 skipped; 22 integration reports leaked into Surefire | backend-zip-selection.log; zip-candidate/backend/target/surefire-reports/ |
| ZIP candidate report guard and existing no-skipped guard | 1 / 1 | Rejected skipped integration discovery; negative reproduction passed | Candidate reports and guard output |
| Final backend: `mvn -B -ntp clean test` | 0 | 620 executed; 0 failures/errors/skips | backend-final-unit-2.log; final-surefire/ |
| `python scripts/ci/check-test-reports.py unit backend/target/surefire-reports` before integration clean | 0 | 620 tests; no integration-tagged reports | final-surefire/ preserved copy |
| `bash scripts/ci/check-no-skipped-tests.sh backend/target/surefire-reports` before integration clean | 0 | No skipped tests | final-surefire/ preserved copy |
| `mvn -B -ntp clean verify -DskipUnitTests=true` | 0 | 122 discovered; 0 executed; 122 skipped; 22 integration suite reports | backend-integration.log; final-failsafe/ |
| Integration report guard and existing no-skipped guard | 1 / 1 | Correctly reject all-skipped integration result; merge validation Blocked | final-failsafe/; integration-no-skipped.log |
| `python -m unittest discover -s scripts/ci -p 'test_*.py'` | 0 | 15 tests passed; 0 failed/errors/skips | scripts/ci/test_report_guard.py; audit command ledger |
| Frontend: `npm.cmd run test -- --maxWorkers=1 --testTimeout=15000 --reporter=json --outputFile=D:/G3-SportCentreManagement/.pr40-work/frontend-final-2.json` | 0 | 419 tests passed; 0 failed/errors/skips | frontend-final-2.json; frontend-final-2.log |
| Frontend `npm.cmd run lint` and `npm.cmd run build` | 0 / 0 | ESLint and production build passed | frontend-lint-final.log; frontend-build-final.log |
| `git diff --check` | 0 | No whitespace errors | Final working diff |
| `bash scripts/ci/check-conventions.sh origin/main` and `bash scripts/ci/check-migrations.sh origin/main` | 0 / 0 | Committed-head guards pass; local diff checked separately | Audit command ledger; working-validation.log |

The first remediation backend run had 620 tests with 0 failures, 1 error and 0 skips because an old pure chain fixture omitted member identity and term dates. The fixture was corrected; final whole-suite results above supersede that run. Initial frontend runs were 389/399 and 410/418, including old fixture failures and timing failures while Maven ran concurrently. After correction and bounded local concurrency, 418/418 passed; the orphan UI regression brought the final suite to 419/419. Local CLI timeout/concurrency settings did not alter CI, skip tests or weaken report guards.

## Executable regressions

All cases below use the environment and working revision stated above. Passed means actual executable coverage exists in the final reports. Source inspection and blocked SQL Server execution are explicitly distinguished.

| ID | Findings | Preconditions | Steps or executable test | Expected behavior | Actual outcome | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PR40-TC-01 | RV40-01, S2 | Docker-free backend, supported provider properties | Clean unit run and unit report guard | No inherited integration-tagged test discovered or reported, including Flyway and repository tests | 620 executed, zero integration reports/skips; guard exit 0 | Passed | final-surefire/; backend-final-unit-2.log |
| PR40-TC-02 | RV40-01, S2 | Separate committed-source snapshot, provider properties removed as in ZIP | Clean unit run and both guards | Demonstrate leakage and reject it | 730 discovered, 122 skipped, 22 integration reports; guards exit 1 | Passed negative reproduction | zip-candidate/backend/target/surefire-reports/ |
| PR40-TC-03 | RV40-01, S3 | Temporary report/source fixtures | `test_report_guard.py`: missing directory/report, zero counts, missing/invalid/blank manifest, skip element/counter, failure/error, valid reports, inherited/method/nested tags, unit leakage into Failsafe | Reject invalid evidence, accept valid unit and required integration reports | 15 tests passed | Passed | scripts/ci/test_report_guard.py |
| PR40-TC-04 | RV40-02, TC-BE03-72 | Predecessor paid 200000, active upgrade paid 300000, target 1000000 | cardsModel `RV40-FE-01` | Actual chain quotes 500000 | Upgrade price 500000 | Passed | frontend-final-2.json |
| PR40-TC-05 | RV40-02 | Same chain plus unrelated REPLACED card paid 100000 with same member/end date | `RV40-FE-02` | Unrelated history does not reduce quote | Price remains 500000 | Passed | frontend-final-2.json |
| PR40-TC-06 | RV40-03 | Upgrade marker without predecessor, or explicit predecessor absent from history | `RV40-FE-03`: orphan and missing predecessor | No actionable partial fee for member or desk | unknown, price null | Passed | frontend-final-2.json |
| PR40-TC-07 | RV40-03 | Explicit cycle, missing paid snapshot/metadata, duplicate IDs, another member or term | Remaining `RV40-FE-03` parameterized rows | Fail closed with no invented credit | unknown, price null for both audiences | Passed | frontend-final-2.json |
| PR40-TC-08 | RV40-03 | 20 versus 21 predecessor edges; each row paid 100, target 1000000 | `RV40-FE-04` | Allow 20; reject 21 rather than truncate | 20-edge price 997900; 21-edge unknown/null | Passed | frontend-final-2.json |
| PR40-TC-09 | RV40-02, S1, TC-BE03-72 | SILVER rank 2 paid 550000; GOLD rank 3 price 500000 | `TC-BE03-72 RV40-FE-05`, plus existing backend zero-fee member/staff tests | Classify by rank; member blocked at zero, desk valid upgrade at zero | blocked/0 and upgrade/0; backend purchase policy preserved | Passed | frontend-final-2.json; final-surefire/ |
| PR40-TC-10 | RV40-02 | Actual historical chain; catalog price changes | `RV40-FE-06` and backend chain test | Historical snapshots remain credit source | Quote remains 500000 despite live price edits | Passed | frontend-final-2.json; final-surefire/ |
| PR40-TC-11 | RV40-02, RV40-04 | Deliberate GOLD/VIP ranks and codes; historical 300000.10, target 600000.30 | `TC-FE04-012` | Decimal precision retained | Exact price 300000.2 | Passed | frontend-final-2.json |
| PR40-TC-12 | RV40-02 | Unknown/invalid rank; deactivated known tier absent from active catalog | `RV40-FE-07`, `RV40-FE-08`, `TC-FE04-011` | Invalid rank cannot infer upgrade from price; mapped valid rank works without live catalog entry | unknown/null for invalid ranks; valid inactive-tier quote 500000 | Passed | frontend-final-2.json |
| PR40-TC-13 | RV40-03 | Member screen with unknown rank or orphan upgrade | `TC-FE04-069`, `RV40-UI-01` | No actionable upgrade overlay or purchase POST | Button/dialog absent, no purchase POST | Passed | frontend-final-2.json |
| PR40-TC-14 | RV40-02, B3 | Same-member card history and explicit replacement link | Backend `upgradePreviewMetadata_preservesExplicitHistoricalChain` | DTO serializes mapped rank, marker, predecessor ID and original paid amounts; reads actor's own history | DTO fields and repository member ID assertions pass | Passed | final-surefire/TEST-com.sportify.catalog.service.MembershipCardServiceImplTest.xml |
| PR40-TC-15 | RV40-04, S6, TC-BE03-71 | Pending card at 123456.78; tier then inactive and priced 450000; linked pending payment | Backend `tcBE0371_activateAfterTierDeactivation_retainsRequestedPriceAndPaymentValidation` | Honor request, preserve amount and confirm payment | ACTIVE at 123456.78; markPaymentSuccess called with actor and CASH | Passed unit test | final-surefire/TEST-com.sportify.catalog.service.MembershipCardServiceImplTest.xml |
| PR40-TC-16 | RV40-06, F3 | Upgrade purchase encounters nested/chained SQL errors | Ten rows in `constraintFailures` | Map intended index only; preserve other 2601/2627, PK, missing name, index-name substring and unidentifiable messages; terminate cyclic SQL chains | All ten cases pass with original cause retained | Passed | final-surefire/TEST-com.sportify.catalog.service.MembershipCardServiceImplTest.xml |
| PR40-TC-17 | RV40-04, S4, TC-BE03-70 | Real SQL Server V21 database, seeded legacy rows | `testMigrationV22_backfillUpgradeChainAndIdempotency`: migrate V22, assert promotion/history/no-predecessor false, valid links, ambiguity, pending, other member, snapshots and rerun | Narrowed marker/link rules and idempotency | Test compiles; Failsafe discovers it but skips without Docker | Blocked | final-failsafe/TEST-com.sportify.catalog.controller.ManagerCatalogIntegrationTest.xml |
| PR40-TC-18 | RV40-01, B5 | Working Docker and SQL Server image | Clean verify, integration report guard, no-skipped guard | Positive executed coverage for actual tagged suites and all required manifest entries; zero skips | This environment emits 22 suite reports with 122 skips and 0 execution | Blocked | final-failsafe/ |
| PR40-TC-19 | RV40-04, S4 | Staging production copy and persistent migration history access | Follow revised operator runbook before deployment | Record dry-run counts; preserve an applied V22 checksum if applicable | No authorized staging environment or persistent history available | Not run | docs/database/02-catalog-and-membership.md |
| PR40-TC-20 | B1-B5, S5 | GitHub governance access, authorized publication, maintainer acceptance | Verify final head CI, eligible review, audit tracking, Draft transition and S5 decision | Confirm actual readiness, never infer it from a proposed checklist | PR open/Draft; protection 401; no publication/final-head run; S5 acceptance pending | Verified by source/API inspection only | remote-pr.json; completion report |

Pure model fixtures are not evidence of catalog/database validity. The zero-fee model scenario does not bypass manager price-monotonicity validation. The migrated SQL Server data scenarios are integration tests, not mocks or an H2 substitution.
