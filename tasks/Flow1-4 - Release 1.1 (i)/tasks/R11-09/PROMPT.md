# R11-09 - README, TODO and CHANGELOG synchronization

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T9 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P1.
- Suggested owner: Documentation agent.
- Dependencies: R11-00; final merged features.
- Source estimate: 0.5 person-day.
- Initial execution status: PLANNED.

## Objective

Update documentation to reflect actual merged Flow 1-4 behavior and accepted Release 1.1 gaps, avoiding stale schema-only claims.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Re-read current README.md TODO.md CHANGELOG.md at new integration main SHA.
- Update Training & Progress description from obsolete schema-only to actual implemented training plan/attendance/results/feedback as verified.
- Update historic 2026-10-08 TODO state and clearly mark old task estimates as historical.
- Create concise Flow 1-4 status table with supported features, demo scope and known gaps; distinguish required Flow 1-3 from optional Flow 4.
- Document fixed role model, deferred waitlist and legacy deprecation.
- Reference actual PRs/tests with SHA/run URLs; avoid unsourced 100 percent claims.
- Re-check README instructions and no credentials/secrets committed.

## Explicit exclusions

No unrelated doc rewrite, new functionality, AI Audit Log, unverified numerical claims or production operational approval.

## Acceptance criteria

1. README/TODO/CHANGELOG no longer contradict verified implementation.
2. Flow 1-4 summary and deferred scope are visible and traceable to source paths/PRs.
3. All feature statuses correspond to actual final reviewed main SHA.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-09/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
