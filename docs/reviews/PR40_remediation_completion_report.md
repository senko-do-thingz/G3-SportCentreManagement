# PR40 remediation completion report

Publication follow-up: the user subsequently authorized commit and push of all current changes. See `docs/reviews/PR40_publication_status.md` for the later publication state; statements below record the initial local remediation snapshot.

Date: 2026-10-14, Asia/Bangkok. Repository: senko-do-thingz/G3-SportCentreManagement. Task: BE-03.

The source fixes are implemented locally. Merge readiness is not verified. SQL Server integration execution, staging deployment evidence, reviewer governance, audit tracking, final-head CI and authorized PR actions remain outstanding.

## Revisions and preserved work

Starting and final committed HEAD: `22b762f0c2594580b8243e63c60b8974e8fbd003`, branch `feat/f1-manager-catalog`. Local and public remote main: `6e82388ece8c3a2ea35ae57b684e2d3adc83f3f3`. All changes from this task are local working files. No commit, staging, push, PR edit, Draft change, branch-protection change or merge was performed.

Public GitHub REST metadata was refreshed: PR40 is open, Draft and unmerged at the same head. Its live body retains the old placeholders. GitHub CLI has no authenticated account. The anonymous branch-protection request returned HTTP 401, so required approvals and bypass settings are unverified. CODEOWNERS still assigns all paths, workflows and migrations to `@senko-do-thingz`.

Pre-existing `docs/pr/BE-03_PR_DESCRIPTION.md`, the prior review deliverables and both historical DOCX audits were preserved. A new `docs/pr/PR40_REMEDIATED_PR_DESCRIPTION.md` reconciles the local description and ZIP proposal without overwriting user work. BE-03 Markdown audit history was extended by an append-only remediation entry. Audit documents remain ignored and unstaged under the existing repository policy.

## Findings

| Finding | Change and evidence | Status |
| --- | --- | --- |
| RV40-01 | Surefire/Failsafe 3.6.0 retain supported name/value `junit=false` provider properties. Unit run: 620 executed, no integration reports, no skips. Shared guard covers inherited tags and names such as FlywayMigrationTest and RepositoryTest. | Unit isolation passed; real integration execution blocked |
| RV40-02 | MemberCardResponse maps tierRank, upgradeRequest and replacesCardId. Existing member/staff history endpoints retain authorization. Frontend follows only explicit links and historical pricePaid. Actual and unrelated-history regressions both quote 500000. | Implemented and locally tested |
| RV40-03 | Frontend rejects orphan markers, missing links, cycles, more than 20 predecessor edges, missing amounts, duplicate IDs, another member and another term. Member UI cannot open or submit unknown upgrades; desk model also blocks them. Backend rejects null or invalid predecessor snapshots rather than returning partial credit. | Implemented and locally tested |
| RV40-04 | V22 requires predecessor evidence; CARD-MIG-NOPRED now expects false. Executable TC-BE03-70 covers promotion, historical increase, chain, ambiguity, pending payment, unrelated member and rerun. TC71 verifies activation after deactivation with requested price and payment validation. TC72 covers rank, historical credit and zero-fee audience policy. Decimal-money assertions remain exact. | Source and unit/frontend coverage complete; SQL Server execution blocked |
| RV40-05 | Prepared truthful PR body, audit, inventory and run-specific test evidence. Unsupported governance and no-production-data claims were removed from the new proposal. | Local documentation complete; remote B1-B5 pending |
| RV40-06 | Exact unique index/constraint evidence is required. SQL codes alone do not establish upgrade concurrency. Traversal handles causes, SQL next exceptions and cycles. Ten new parameterized cases verify mapping and preservation of unrelated failures. | Implemented and locally tested |

## Original review items

| Item | Current status | Evidence or remaining action |
| --- | --- | --- |
| B1 | Pending | Public PR is Draft. Ready-for-review change requires authorization and readiness evidence. |
| B2 | Unverified | Protection API 401; maintainer must verify owner review requirements and obtain an eligible review. No bypass claim is made. |
| B3 | Prepared locally | Replacement PR body has real local artifact paths and accurate checklist states. Live body remains untouched. |
| B4 | Pending tracking decision | Existing audit history retained, new audit produced, Markdown history appended. Ignored audit files are not in the PR. |
| B5 | Pending | No resulting head commit was pushed, so there is no final-source Actions run URL. Obtain exact run and report totals after authorized publication. |
| S1 | Implemented | Rank and explicit historical-chain contract is implemented across backend DTO/mapping, frontend types, fixtures and consumers. |
| S2 | Implemented locally | Exact 3.6.0 source and resolved dependencies explain Vintage detection; final unit selection and report guards pass. Failsafe selects all actual tagged suites but Docker is unavailable. |
| S3 | Implemented | Shared generic XML guard and required-suite manifest replace the workflow feature-class literal; 15 guard regressions pass. |
| S4 | Pending external validation | Narrowed migration and tests implemented. Staging dry-run and actual SQL Server migration remain unexecuted. |
| S5 | Pending maintainer acceptance | Deterministic whole-tier locking and existing concurrency tests preserved. Unbounded wait still holds a connection. No global timeout or optimistic locking was introduced. |
| S6 | Implemented and unit tested | Already-requested pending cards remain activatable after tier deactivation at the original requested price; payment validation remains active. |
| F1 | Implemented | BE-03 permanent test-count claims replaced with revision-specific execution evidence. Historical audit counts were retained as history. |
| F2 | Recommendation prepared | Suggested squash: `feat(be-03): add manager catalog and safe membership upgrade planning`. Maintainer selects merge strategy. |
| F3 | Implemented and tested | Constraint-specific SQL evidence, safe traversal, unrelated-error preservation. Existing Bean Validation defenses retained. |

## Executed results

Evidence root: `D:/G3-SportCentreManagement/.pr40-work`. JDK: Oracle 25.0.2. Maven: 3.9.15. Surefire/Failsafe: 3.6.0. Jupiter: 5.12.2, platform API: 1.12.2. Testcontainers 1.21.4 brings transitive JUnit 4.13.2.

| Run | Exit | Tests | Failures | Errors | Skipped | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Baseline `mvn -B -ntp clean test` | 0 | 608 | 0 | 0 | 0 | backend-baseline.log; baseline-surefire/ |
| Isolated ZIP test-selection configuration | 0 | 730 | 0 | 0 | 122 | backend-zip-selection.log; zip-candidate/backend/target/surefire-reports/ |
| ZIP report guard / no-skipped guard | 1 / 1 | N/A | N/A | N/A | N/A | Guards correctly reject leaked/skipped integration suites |
| First remediation backend unit run | 1 | 620 | 0 | 1 | 0 | backend-final-unit.log; incomplete old chain fixture caused a NullPointerException and was corrected |
| Final `mvn -B -ntp clean test` | 0 | 620 | 0 | 0 | 0 | backend-final-unit-2.log; final-surefire/ |
| Final unit report / no-skipped guards | 0 / 0 | 620 | 0 | 0 | 0 | final-surefire/ validated before clean verify |
| `mvn -B -ntp clean verify -DskipUnitTests=true` | 0 | 122 discovered; 0 executed | 0 | 0 | 122 | backend-integration.log; final-failsafe/; 22 actual integration class reports |
| Integration report / no-skipped guards | 1 / 1 | 0 executed | 0 | 0 | 122 | Integration result is Blocked, not Passed |
| `python -m unittest discover -s scripts/ci -p 'test_*.py'` | 0 | 15 | 0 | 0 | 0 | Shared report-guard regressions |
| Final frontend suite | 0 | 419 | 0 | 0 | 0 | frontend-final-2.json and frontend-final-2.log |
| Frontend lint and production build | 0 / 0 | N/A | N/A | N/A | N/A | frontend-lint-final.log; frontend-build-final.log |

Frontend full-suite command: `npm.cmd run test -- --maxWorkers=1 --testTimeout=15000 --reporter=json --outputFile=D:/G3-SportCentreManagement/.pr40-work/frontend-final-2.json`. Initial frontend runs had fixture failures and timing failures while Maven ran concurrently: 389/399 and 410/418 passed. A subsequent 418/418 run passed; one explicit orphan-upgrade UI regression was added and the final 419/419 run passed. CLI concurrency and timeout options were local execution settings; CI and its no-skipped policy were not weakened.

`git diff --check`, committed-head convention guard and committed-head migration guard returned 0. A separate working-diff ASCII and migration immutability check covers local additions; execution details are retained in the audit companion. Official guards inspect committed revisions and do not alone prove working-diff compliance.

## Mechanism and migration limits

Surefire 3.6.0 auto-adds Vintage when it detects transitive JUnit 4. Its provider uses category filtering instead of Jupiter tag filtering when the Vintage-detected flag is set. `junit=false` disables that auto-addition. The supported name/value property shape retains working tag isolation; selecting JUnitPlatformProvider alone does not solve it. References: [exact Maven Mojo source](https://raw.githubusercontent.com/apache/maven-surefire/surefire-3.6.0/maven-surefire-common/src/main/java/org/apache/maven/plugin/surefire/AbstractSurefireMojo.java), [exact provider filter source](https://raw.githubusercontent.com/apache/maven-surefire/surefire-3.6.0/surefire-providers/surefire-junit-platform/src/main/java/org/apache/maven/surefire/junitplatform/JUnitPlatformProvider.java).

V22 is absent from verified main and is a new migration under the repository's immutable-after-merge guard, so the local pre-merge revision was edited. Persistent environment application history is unknown. Deployment must verify it has not already been applied; otherwise preserve the applied checksum and use a successor migration. The runbook now distinguishes post-migration queries from pre-migration inspection. No staging counts or absence of production data are asserted.

Docker is not available on PATH; expected system locations are absent, and the user-profile DockerDesktop directory contains only a deletion remnant. Testcontainers reports Docker unavailable. No authorized external SQL Server environment was found. H2 or mocks were not substituted for SQL Server migration or concurrency evidence.

## Deliverables and remaining actions

- Audit: `docs/audit/PR40_Remediation_AI_Audit_Log.docx` and Markdown companion.
- Inventory: `docs/reviews/PR40_remediation_modified_files_paths.md`.
- Test cases: `docs/tests/PR40_remediation_test_cases.md`.
- Proposed PR body: `docs/pr/PR40_REMEDIATED_PR_DESCRIPTION.md`.

The normal document dependency loader and bundled Office renderer are unavailable. The audit uses deterministic OOXML with explicit page breaks and matching GDI previews. All preview pages are inspected, but this fallback does not verify pagination in Word or LibreOffice. This limitation is recorded rather than presented as normal-renderer verification.

Before merge, run the revised SQL Server suite and guards on working Docker, obtain a staging migration record and persistent-history decision, obtain explicit S5 acceptance, settle governance and audit tracking, publish only with authorization, verify exact final-head CI including required repository checks, update the live PR body, and mark Ready for review only when authorized. PR40 must remain classified as not yet verified merge-ready.
