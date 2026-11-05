# R11-03 - Backend, frontend and CI verification

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T3 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P0.
- Suggested owner: QA/release agent.
- Dependencies: R11-00; repeat after R11-01/02.
- Source estimate: 0.5 person-day plus reruns.
- Initial execution status: PLANNED.

## Objective

Produce reproducible, SHA-scoped full backend and frontend test evidence for Release 1.1; repair only reproducible in-scope defects.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Record JDK 25, Maven, Node and SQL Server environment, target branch SHA and actual test profile.
- Execute from frontend: npm ci, npm run lint, npx tsc -b, npm test, npm run build.
- Execute from backend: mvn clean verify with real isolated SQL Server or approved Testcontainers.
- Verify presence/fitness of backend/scripts/step2-smoke-test.ps1 before use; run only against owned disposable data.
- Parse and report actual unit/integration counts, failures and skips; do not double-count overlapping targeted suites.
- Collect exact-HEAD GitHub Actions status and required gate on merged integration state.
- Document any blocked test as NOT_RUN, not green; leave old CI successes as historical context.

## Explicit exclusions

No broad dependency upgrades, infrastructure redesign, moving databases, bogus test fixtures, or writing verification logs into protected dataset.

## Acceptance criteria

1. Full test matrix reports actual command, SHA, environment and exit status.
2. Backend SQL integration has positive executed case count or clearly documented blocker.
3. Frontend lint/type/test/build all green on eligible SHA before signoff.
4. No skipped mandatory cases hidden by summary; exact-HEAD CI succeeds for accepted integration.
5. Any source fix stays isolated and reviewed; no undocumented code mutation.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-03/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
