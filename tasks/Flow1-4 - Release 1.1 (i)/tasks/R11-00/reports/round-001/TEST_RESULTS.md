# R11-00 test results

Reviewed base and HEAD: 1827a05b7393eb069ece5ae77c8f65d2a354466a. Windows, PowerShell, Java 25.0.2, Maven 3.9.15, Node v24.20.0, npm 12.2.0. Exact Java/Maven output is retained in version logs. Commands ran only in the isolated worktree.

## Focused baseline cases

| Case | Method and evidence | Result |
|---|---|---|
| R11-00-TC-01 | git status --porcelain=v1; git fetch origin main; git rev-parse origin/main; git remote -v; git worktree list --porcelain; original-status.txt, worktrees.txt, final-safety-check.json | PASS. Original edit retained; new branch starts at fetched main. |
| R11-00-TC-02 | Source/controller/test reads and rg searches; workbook ZIP/XML extraction; visual inspection of seven F1/F4 PNGs; topic4-cells.txt, figma-assets.txt, reviewed-source.txt | PASS. Workbook, source and real design assets resolved. |
| R11-00-TC-03 | Search all frontend routes/menu/API and backend controllers; inspect full report/Coach pages and actual services | PASS. Four page gaps and missing Manager overview/detail API distinguished from existing backfill. GAP_MATRIX.md. |
| R11-00-TC-04 | gh pr list --state open --limit 100 --json ...; gh api --paginate .../pulls/192/files; gh pr diff 180 | PASS for overlap inventory. Nine PR authors/heads recorded. No merge conflict resolution attempted; file overlap does not establish a textual conflict. |

These four cases are manual/static audit cases, not extra automated tests to add to suite totals.

## Local suites

| Suite | Command | Scheduled/reported | Passed | Assertion failures | Errors | Skipped | Exit / result |
|---|---|---:|---:|---:|---:|---:|---|
| Frontend install | npm ci | - | - | - | - | - | 0; npm-ci.log |
| ESLint | npm run lint | - | - | - | - | - | 0; lint.log |
| TypeScript | npx tsc -b | - | - | - | - | - | 0; typescript.log |
| Frontend full tests | npm test -- --reporter=default --reporter=json --outputFile=../tasks/r11-00-vitest-results.json | 1933 | 1932 | 1 timeout | 0 | 0 | 1; 107 files passed, 1 failed; frontend-tests.log |
| Frontend production build | npm run build | - | - | - | - | - | 0; build.log |
| Backend unit phase | mvn -B -ntp clean verify, Surefire | 2265 | 2265 | 0 | 0 | 0 | Unit phase PASS; full command exit 1 |
| Backend integration phase | Same command, Failsafe | 957 | 0 | 0 | 916 | 41 | FAIL infrastructure; no successful local SQL acceptance |

Counts come from 158 Surefire and 67 Failsafe XML suites in backend/target, summarized in test-counts.json. 957 is the runner's discovered/report count in this failed environment, not a claim that the complete 1,037 CI SQL cases ran. 916 errors are attempts that could not initialize their contexts; 41 cases were skipped by infrastructure conditions. No passing SQL case is claimed. Root error: "Could not find a valid Docker environment" and subsequent Docker detection failures. Docker CLI is absent; no approved disposable native SQL connection was configured. maven-verify.log contains the full failure output. Do not classify this as a successful mvn clean verify.

The frontend failure is src/pages/public/HomePage.test.tsx:140, "preserves selected sport and level through the existing login return flow", which timed out at 5000 ms. Full-run duration: 602.85 seconds. Maven and frontend tests overlapped on this machine, so resource contention is a possible cause, not a proven diagnosis. No timeout setting, test or application code was changed. A single focused file rerun, npm test -- src/pages/public/HomePage.test.tsx --reporter=default --reporter=json --outputFile=../tasks/r11-00-home-rerun.json, passed all 32 tests in 12.30 seconds with exit 0 (home-rerun.log). The timeout did not reproduce in isolation. This does not replace the failed full run or add unique cases to the full total. Further full-suite verification belongs to the allocated verification task; no source fix is proposed from this one non-reproducing timeout.

Focused existing finance, booking, role, attendance and training frontend/unit tests are included in the full suites. TrainingWorkflowIntegrationTest includes support idempotency/state transitions, progress ownership, operations date bounds and four-role negative cases; source presence alone is not local SQL execution proof.

## Exact audited-main CI evidence

Existing push workflow: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41968012911 at the exact audited SHA. main-ci.json and main-ci-jobs.json bind the run to that SHA. Job logs were downloaded read-only; one ancillary log was unavailable (gh reported log not found: 127049867039), so job-status evidence and captured test summaries must be distinguished from a complete archived workflow log.

| CI scope | Executed | Passed | Failed | Skipped | Evidence |
|---|---:|---:|---:|---:|---|
| Backend unit | 2265 | 2265 | 0 | 0 | Unit job log summary |
| SQL integration, SQL Server 2022 Testcontainers | 1037 | 1037 | 0 | 0 | Integration job summary and CI report guard |
| Frontend | 1933 | 1933 | 0 | 0 | Frontend log: 108 files |
| Frontend lint/build, migration guard, package, security, compose and CI gate | - | - | - | - | Job conclusions success in main-ci-jobs.json |

Dependency review is skipped on this push event; Security gate and CI gate succeed. No CI was newly triggered, no task PR created, and no new integration merge SHA exists. Existing main CI provides baseline evidence only; R11-03/R11-11 must collect fresh evidence after feature integration. Artifact publication is visible in CI logs, but artifacts were not downloaded or replayed as local evidence.

## Unavailable and inapplicable checks

- Local SQL persistence/migration execution: NOT_RUN successfully due to missing Docker/native fixture. No database was provisioned, seeded or modified by this audit. Protected sportify, sportify_demo and sportify_qr_demo were not used.
- Live four-role 1440x900 browser walkthrough: NOT_RUN. No routes changed and no owned live fixture was provisioned. Design PNG inspection is not runtime acceptance. Changed-route acceptance is NOT_APPLICABLE for this no-code task; final integrated rehearsal remains pending.
- step2-smoke-test.ps1: NOT_RUN. Script exists but fixes the API base at localhost:8080 and includes registration and membership POST setup. The active service cannot be proven to use an owned database; it was inspected without invoking it. Existing exact-main CI tests provide bounded baseline regression evidence, not a replacement live smoke claim.
- API negative controls locally: unit/controller mocks are part of the full passing unit phase. Real SQL cross-role/ownership/token-switch and persistence controls remain unverified locally; CI has the existing integration suites. Never infer an API 403 from menu hiding.
- Mobile, Flow 5-6 and AI Audit Log acceptance: NOT_APPLICABLE by explicit scope.
- Dependency install reports 10 moderate advisories; no unsolicited dependency update was made.

The no-code audit is reviewable; no release GO decision or all-green local verification is asserted.
