# R11-02 - Manager operations report desktop

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T2 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P0.
- Suggested owner: Frontend agent.
- Dependencies: R11-00.
- Source estimate: 1 person-day.
- Initial execution status: PLANNED.

## Objective

Expose existing Manager operations member counts, class booking states and revenue on the current desktop reports page with shared from/to range.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Recheck main route and current report page for existing operations rendering.
- Inspect GET `/api/v1/manager/reports/operations?from=&to=` contract and docs/payment/Operations-report.md.
- Add typed client method to managerFinanceApi or focused module; prefer current `/manager/reports` over new navigation routes.
- Show registeredMembers, currentlyEnabledMembersRegistered, classBookings.total/confirmed/cancelled/upcoming/completed and existing revenue.
- Reuse report date controls; protect against stale request race on rapid from/to changes.
- Explain that upcoming/completed are subsets of confirmed, not an additive partition.
- Display empty/error/loading/forbidden responses and API-derived values without frontend recomputation.

## Explicit exclusions

Do not change financial calculation, SQL aggregation, source date semantics, synthetic exclusion rules, export semantics or backend schema.

## Acceptance criteria

1. Manager can change valid from/to range and view actual API values updating.
2. Displayed members, booking counters and revenue match response exactly (allow only presentation formatting).
3. Invalid date range is shown as a comprehensible error; no stale range data presented as current.
4. Non-Manager direct API access is denied and route remains role guarded.
5. Existing revenue report and CSV export still behave the same.
6. Frontend lint, TS, tests and build pass; SQL-backed acceptance compared with API.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-02/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
