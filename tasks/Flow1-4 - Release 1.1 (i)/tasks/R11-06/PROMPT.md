# R11-06 - Fixed-role RBAC release decision

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T6 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P3.
- Suggested owner: CODEOWNER.
- Dependencies: R11-00.
- Source estimate: 0.5 person-day for docs only.
- Initial execution status: DEFERRED_DECISION_ONLY.

## Objective

Record the fixed four-role model as intentional and verify role boundaries; dynamic role changes are out of Release 1.1 scope.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. This task is DEFERRED_DECISION_ONLY. Do not create a feature implementation branch unless CODEOWNER explicitly approves a documentation-only update. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Document why MEMBER, COACH, RECEPTIONIST and MANAGER remain fixed during account creation.
- Confirm existing @PreAuthorize, frontend guards and audit coverage.
- Flag any evidence showing a required lecturer capability missing, but do not implement automatic role mutation.
- Obtain CODEOWNER signoff for DEFERRED_DYNAMIC_RBAC.

## Explicit exclusions

No PATCH role API, role-grant migration, dynamic permissions UX, active account demotion/upgrade logic or self-elevation.

## Acceptance criteria

1. Decision is documented with limitations and current role tests.
2. Any expansion requires a new separately approved PR and threat model.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-06/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
