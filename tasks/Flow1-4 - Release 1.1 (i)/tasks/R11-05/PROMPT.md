# R11-05 - Coach learner profile and Member progress F4-05

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T5 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P1.
- Suggested owner: Frontend agent.
- Dependencies: R11-00; P0 scope accepted.
- Source estimate: 1.5 person-days.
- Initial execution status: PLANNED.

## Objective

Provide one desktop Coach route to select an assigned class and Member, read existing goal/profile and progress and navigate back to real roster.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Reinspect current frontend for existing progress views; do not duplicate if already present.
- Reuse GET `/api/v1/coach/training/classes/{classId}/members/{memberId}/profile` and `/progress` and existing class/member selection APIs.
- Add `/coach/training/progress`, link it from owned class/member roster and preserve route role gate.
- Show level/goal, attendance history, session result/score/notes and existing Coach feedback only as actual returned fields.
- A simple table/small chart is sufficient if actual time-series data exists; do not fabricate progression or scores.
- Show clear saved progress/feedback state only after successful actual write/readback in the existing workflow.
- Prevent stale profile leakage on class/member/account switch.

## Explicit exclusions

Do not add invented data points, edit another Coach history, new training scoring algorithms, unauthorized Member direct queries, mobile UI or AI drill functionality.

## Acceptance criteria

1. Assigned Coach selects class and Member and sees consistent profile/progress from real APIs.
2. Assigned Coach cannot read a Member in another Coach class even by direct API URL.
3. Empty progress renders truthfully, no invented trend or dummy score.
4. Member self-progress endpoint remains private and unchanged.
5. Class/member switching clears stale data and errors appropriately.
6. Frontend full gates and focused authorization tests pass.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-05/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
