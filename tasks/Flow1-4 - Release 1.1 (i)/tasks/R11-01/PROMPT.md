# R11-01 - Receptionist Support Requests desktop F1-16

## Agent assignment

- Release: Flow 1-4 Release 1.1.
- Source task: T1 in `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.
- Priority: P0.
- Suggested owner: Frontend agent.
- Dependencies: R11-00.
- Source estimate: 1-1.5 person-days.
- Initial execution status: PLANNED.

## Objective

Expose the existing Reception support intake workflow in one real desktop page. Support an existing Member, create/view/filter/update requests while enforcing current backend rules.

## Mandatory preflight

1. Read package README.md, GOVERNANCE.md, BASELINE_VERIFICATION.md, TASK_BOARD.md and RELEASE_GATES.md.
2. Read this task's TEST_CASES.md and inspect actual current source, associated tests, real Figma assets and Topic 4 requirements.
3. Fetch latest origin/main, record its SHA, git status and worktree inventory, and check conflicts/overlaps with open PRs. Never modify uncommitted work.
4. Work in a separate worktree and one task branch only if this task is approved to execute. CODEOWNER controls merge.
5. If already implemented, stop with evidence classified ALREADY_DONE. If incomplete contracts or required business decisions are missing, report BLOCKED and request approval rather than inventing semantics.

## Implementation and evidence scope

- Recheck support page/route/menu absence on fresh main.
- Inspect ReceptionSupportController, ReceptionSupportService request/response DTOs, limits and status rules before writing API typing.
- Reuse member search; add a focused typed frontend API client and route `/reception/support` with navigation entry.
- Use existing F1-16 Figma asset and shared design tokens, not a broad redesign.
- List/filter by memberId and status, view detail, create support request, update allowed states with resolution note where required.
- Create a unique UUID Idempotency-Key for each distinct POST intent, reuse the same key only for retry of identical payload, guard double-clicks.
- Preserve existing server-side RECEPTIONIST role restriction and Member own read contract; do not add new backend endpoints without proven necessity.

## Explicit exclusions

Do not implement Member support page, CRM, attachments, live messaging, AI chatbot or changing existing support state machine. Do not invent unsupported category/status.

## Acceptance criteria

1. Authenticated Receptionist can search existing Member, create a valid ticket and see persisted ID/details.
2. Filter by member and status; support a valid transition and reject invalid/terminal transitions with clear errors.
3. Duplicate request retry with identical Idempotency-Key does not create duplicate tickets; distinct payload collision is handled.
4. Direct access by Member/Coach is denied by API and correct navigation/route guards apply.
5. Empty, error, loading and retry states work at desktop 1440x900.
6. Frontend lint, type check, full tests and build pass; real SQL-backed acceptance captured without fake backend data.

## Required verification

- Execute focused task tests and the relevant full frontend/backend tests according to TEST_RUNBOOK.md.
- On backend changes, show actual SQL integration test counts on isolated database; PR CI at exact HEAD is required.
- On frontend changes, run npm run lint, npx tsc -b, npm test and npm run build.
- Obtain desktop 1440x900 role walkthrough evidence for changed routes, including negative access cases.
- Note clearly NOT_RUN for any unavailable test; no fake green claims.

## Reporting and handoff

Write all reports to `tasks/R11-01/reports/round-001/` or next-unused round-NNN:

- FINAL_REPORT.md
- TEST_RESULTS.md
- CHANGED_FILES.md
- CODEOWNER_HANDOFF.md

Include base SHA, reviewed HEAD SHA, PR URL, all changed paths, test commands, counts and result, SQL database safety, known limitations and explicit PASS/FAIL/BLOCKED/ALREADY_DONE status.

Do not approve or merge PR, force push, deploy or modify protected database. End your work after this task and hand off to CODEOWNER.
