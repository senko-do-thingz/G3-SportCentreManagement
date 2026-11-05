# R11-11 - Final integrated CODEOWNER acceptance

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: NEW in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P0.
- Suggested owner: CODEOWNER / final QA agent.
- Dependencies: R11-01/02/03/04/05/09/10 as approved.
- Source estimate: 0.5 person-day or until gates satisfied.
- Initial execution status: PLANNED.

## Objective

Verify the exact integrated main release candidate and publish a GO/NO_GO desktop demo recommendation, leaving merge/deploy authorization with CODEOWNER.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Freeze reviewed set of PRs and record merged main SHA, non-merged optional items and known gaps.
- Re-run full frontend and backend/SQL tests and required CI against the integrated state.
- Execute the four-role 10-15 minute desktop rehearsal against an owned disposable database, capture actual IDs and status readback.
- Confirm new support and operations routes, optional Manager/Coach pages, security denial tests, billing/booking/session regression.
- Verify README/TODO/CHANGELOG and requirement traceability against final main.
- Produce consolidated handoff and one of GO_FOR_DESKTOP_DEMO, GO_WITH_KNOWN_GAPS or NO_GO; do not self-merge or deploy.

## Explicit exclusions

No after-the-fact fiction about CI. No protected database writes, forced PR merge, new feature scope or production deployment.

## Acceptance criteria

1. Final main exact-SHA required CI green.
2. Actual test counts, DB identity and results provided; no hidden skips.
3. All P0 and approved P1 gates pass or explicitly classified with CODEOWNER risk acceptance.
4. Four-role desktop demo rehearsed with real API/SQL and no relevant placeholder.
5. CODEOWNER receives a self-contained truthful release decision packet.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-11/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
