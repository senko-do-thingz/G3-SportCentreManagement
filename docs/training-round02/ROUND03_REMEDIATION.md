# Round 03 cross-PR remediation

Status: technical remediation for independent review; NOT READY TO MERGE while the approval/coordination gates below remain open.

| Review item | Implemented change | Evidence | Outstanding gate |
|---|---|---|---|
| 1. Flow 4 contract | Single endpoint/DTO comparison in FLOW4_CONTRACT.md; shared trainingContract.ts roots used by all three connected API adapters. Existing connected screens retained instead of importing unsupported duplicate clients. | R3_01 real authenticated Coach -> Member HTTP; R3_02 Reception -> Member and Coach result HTTP, SQL writes, role denial, cross-member privacy and conflicting correction. | CODEOWNER/FE/BE signatures and branch-owner confirmation that #173/#180/#193 overlapping clients will be retired or ported. No claim that those remote branches were rewritten. |
| 2. V37 collision | Preserve training V37; selectively import #193 photo SQL unchanged as demo V38. Update NX-04 source inventory fingerprint. Existing credited photo assets already match. | R3_03 and dev+demo Spring context startup on a fresh isolated database: V37/V38 applied once, three demo photos assigned, Flyway validate and replay pass. | #193 must retire its original V37 before any later merge. Schema-owner approval and target database history check remain required. Excluded from HOTFIX #194 by the recorded release decision. |
| 3. Approvals | Searched repository and accessible related PR discussions; recorded exact references and pending status in FLOW4_CONTRACT.md. Existing timing/correction implementation is unchanged. | NX-03 README; NX-04 human review record; #185 review and #193 release comment. | No signed endpoint/DTO, timing/correction or V37 schema approval found. Must be supplied by the responsible humans. |
| 4. Revalidation | Fetched origin; current main 646bd143c40d0e3721210433997e1316ae05cc1e is already an ancestor. Re-ran local validation below. | New run results, not reused Round 02 results. | Exact-head GitHub CI after push. This selected integration is not acceptance of all unrelated changes in the other PRs. |

## Executed local validation - 2026-11-03

- Backend unit: `mvn -o -B test` with JDK 25 and the existing external Maven cache: 2015 run, 0 failures/errors/skips.
- Real SQL Server: `mvn -o -B -DskipUnitTests=true -Dspring-boot.repackage.skip=true -Dit.test=TrainingWorkflowIntegrationTest verify`: 29 run, 0 failures/errors/skips. JDK 25, local SQL Server 2025; CI uses SQL Server 2022 Testcontainers and must independently pass.
- SQL target: fresh `sportify_training_test_20261103081404`, explicit `dev,demo` profiles; the migration rollback probe uses a separate generated `sportify_training_probe_*` database. No shared/demo application database was the target. The local helper creates a temporary SQL login, revokes database-create permission and disables it in finally; credentials are neither committed nor printed.
- R3_01 and R3_02 run a real random-port server and Bearer JWT authentication with SQL-backed accounts. No mocked training writer, HTTP response, or database. JWTs are issued by the real JwtService for fixture accounts; this is API integration coverage, not a password-login/browser test.
- Python CI: 104 run, all passed. Windows-only external runner selects installed Git Bash; repository guards and skip policies are unchanged.
- Frontend lint and production build passed. Full frontend: `node node_modules/vitest/vitest.mjs run --pool=threads --maxWorkers=2`: 97 files / 1781 tests passed. The initial default-worker attempt stalled during startup and was stopped without a result; the thread-worker run completed in 177.26 seconds. The Windows-only CLI override is not a repository configuration change. A redundant fork-worker rerun was stopped once the completed full-suite report was available; it is not counted as test evidence.
- Flyway fresh startup, validation and no-op replay passed in R3_03; the ordinary V37 transactional rollback/legacy data test remains R2_16.
- Git whitespace check passed. Added-lines ASCII scan against origin/main covered source, tests and documentation with zero violations. NX-04 current package guard returned no diagnostics after the additive V38 inventory update.

## Acceptance limits

No CODEOWNER decision is manufactured. No private audit material is committed. No new publish, result revision, class-wide plan audience, AI provider, enrollment lifecycle or compatibility route is invented. Cross-branch sign-off and exact-head CI remain separate gates from successful local execution. The prior private audit DOCX visual verification remains pending; this change does not claim to resolve it.
