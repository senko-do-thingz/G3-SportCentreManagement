# R11-00 - Baseline and scope gap audit

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: NEW in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P0.
- Suggested owner: CODEOWNER / assigned agent.
- Dependencies: None.
- Source estimate: 0.25-0.5 day.
- Initial execution status: READY.

## Objective

Audit fresh origin/main against the supplied Topic 4 review, identify actually missing desktop features and trace to live code. Produce a no-code baseline before changing functionality.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Fetch current main, record SHA and source drift from the package baseline.
- Verify original Topic 4 workbook availability and exact requirement cells; identify real Figma assets, routes, controller API contracts, tests and overlapping open PRs.
- Produce per-flow requirement-to-API-to-page gap status: IMPLEMENTED, PARTIAL, MISSING, DEFERRED, or UNKNOWN.
- Prioritize R11-01, R11-02, R11-03 and optional R11-04/05 against actual code.
- Check migration versions and database configuration; do not modify schema or data.

## Explicit exclusions

No feature implementation, migrations, force-push, merge, deployment, changes to production/shared database or altered requirements.

## Acceptance criteria

1. A SHA-stamped inventory distinguishes uploaded assessment snapshot from fresh main facts.
2. All five functional tasks are classified with exact source paths and existing test references.
3. Open PR overlaps and risks are recorded, including who owns each branch.
4. Proposes minimal, not speculative, implementation boundaries and permissions.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-00/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
