# R11-10 - Requirements traceability and four-role committee demo

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T10 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P1.
- Suggested owner: QA/presentation agent.
- Dependencies: R11-00; draft parallel; finalize after R11-01/02/04/05.
- Source estimate: 1 person-day.
- Initial execution status: PLANNED.

## Objective

Create a reviewer-readable traceability sheet and 10-15 minute real desktop script across all four actors.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Use original Topic 4 workbook and actual Figma filenames to map requirement -> actor -> page -> endpoint -> test -> evidence.
- Trace F1-16, Flow 2 booking, F3 operations, F4-10 and F4-05 plus existing required functions.
- Record source references and actual merge commits instead of stale PR head snapshots.
- Prepare a controlled disposable demo database with four roles, a support request, date-scoped operations data, Coach-class history, attendance data and booking.
- Write step-by-step desktop 10-15 minute script and safe fallback; no mock API substituted for integrated acceptance.
- Run full four-role dress rehearsal and capture actual results and blockers.

## Explicit exclusions

No invented data, real production credentials, external AI, mobile recording/tests, false screenshots, declaring release complete without evidence.

## Acceptance criteria

1. Every required Topic 4 row has route/API/test or explicit known gap.
2. Four-role demo steps can be run in 10-15 minutes on a safe disposable fixture.
3. Business state changes persist and can be read after reload/restart where relevant.
4. Failure and fallback procedure is documented honestly.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-10/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
