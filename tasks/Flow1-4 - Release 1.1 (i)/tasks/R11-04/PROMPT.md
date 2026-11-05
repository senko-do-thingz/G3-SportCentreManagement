# R11-04 - Manager training attendance overview F4-10

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T4 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P1.
- Suggested owner: Backend+Frontend agent.
- Dependencies: R11-00; P0 scope accepted.
- Source estimate: 2 person-days.
- Initial execution status: PLANNED.

## Objective

Add the minimal read-only Manager attendance overview and per-session detail for the optional Flow 4 desktop demonstration.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Search all existing Manager, Coach and Reception attendance read endpoints and their row access before defining a new endpoint.
- If needed, design GET `/api/v1/manager/training/attendance/overview` with bounded date filters, optional class and Coach filters, counts by status and per-session links.
- If needed, add one minimal Manager-only GET session attendance detail endpoint, choosing canonical existing Session ID rules.
- Compute counts/denominator from actual persisted attendance and appropriate eligible booking roster; explicitly document handling unmarked, absent and cancelled statuses before claiming an attendance rate.
- Use efficient bounded SQL/JPA queries, avoid N+1 and verify zero-denominator handling.
- Add `/manager/training/attendance` desktop page and nav; retain separate existing `/manager/training/attendance/backfill`.
- Respect current F4-10/S4-ManagerDetails Figma cues and shared tokens; no redesign.
- Test direct role authorization and real SQL data for summary/detail.

## Explicit exclusions

No attendance backfill policy changes, booking debit, write/mutation, synthetic completion, new dashboard analytics platform, mobile view or role expansion.

## Acceptance criteria

1. Manager sees date-range and class/Coach-scoped attendance counts on real data.
2. Manager can select a listed session and view accurately scoped attendance detail.
3. Count denominator, unmarked and cancelled behavior documented and tested; zero denominator safe.
4. Coach/Member/Receptionist direct Manager API request denied.
5. Existing Manager backfill remains separate and unaffected.
6. Relevant backend unit/integration plus frontend full gates pass on exact HEAD.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-04/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
