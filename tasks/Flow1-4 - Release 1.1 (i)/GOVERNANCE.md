# Agent governance - mandatory

Execution override: CODEOWNER explicitly authorized continuous sequential Release 1.1 execution and one consolidated isolated branch/PR. EXECUTION_AUTHORIZATION.md supersedes the original one-task-per-PR and per-task stop/approval sequence for this run only. All data, migration, scope, no-merge, no-force-push and no-deployment constraints remain mandatory.

## Source, branch and isolation

1. Fetch the latest `origin/main` before each task. Record base SHA and source path inventory.
2. Inspect `git status --porcelain=v1`, remotes, worktrees, open PR overlap and other agents' active directories.
3. Preserve all untracked and uncommitted edits. Never reset, clean, stash, move, or overwrite user work without explicit CODEOWNER permission. In particular, do not touch another agent's worktree or active database.
4. Create a separate git worktree and a task-specific branch from the latest main. Keep one task per PR. No direct commit or push to protected main.
5. Resolve rebase/update-branch conflicts only on your isolated branch; do not force push, rewrite another author's history, or silently merge unrelated features.
6. Agent may commit/push and open PR if allowed. Agent MUST NOT merge PR, approve its own PR, deploy, alter protected databases, or change repository permissions.
7. If a feature is already present at fresh main HEAD, stop implementation and submit `ALREADY_DONE` evidence plus relevant tests.

## Scope

- Desktop UI only, minimum Topic 4 functionality and committee demo. No mobile-specific styling, screenshots or test gate.
- No AI Audit Log requirement. No Flow 5/6, LLM, waitlist, dynamic RBAC, legacy migration removal, payment provider work or generic redesign.
- All new source code, code comments, commit messages, PR descriptions, task reports and test documentation must be English and ASCII punctuation. Use `-` and `->`; no em dashes, en dashes, emojis or decorative symbols.
- Reuse existing backend/REST contract, frontend components and tokens. No hard-coded/raw design colors. Do not invent a mock backend as substitute for real integrated acceptance.
- Preserve historical financial, booking, membership, attendance and audit behavior.
- Do not claim 100 percent completion until each relevant requirement has testable evidence.

## Data and migration safety

- Normal development runtime may use the canonical `sportify` schema configured by the owner, but Agent must not mutate user-owned/production/shared data for tests.
- Create disposable SQL databases with unique owned names for integration tests, clean up only those owned by the agent, and retain evidence of migration and preflight. Do not alter `sportify`, `sportify_demo` or `sportify_qr_demo` without separate permission.
- Flyway migrations, if truly required, must be forward-only, uniquely numbered after checking current main, additive, isolated-tested, and separately approved by CODEOWNER. Never rewrite applied migration or run Flyway clean/repair/baseline on protected databases.
- Demo fixtures must be local, opt-in and non-destructive. Never embed credentials or tokens in committed files.

## Evidence and execution

- Run focused unit tests on every implementation change.
- Frontend changed: `npm run lint`, `npx tsc -b`, `npm test`, `npm run build` from `frontend/`.
- Backend changed: `mvn clean verify` from `backend/` with Java 25 and real SQL Server/Testcontainers setup. Verify actual SQL integration coverage, not just compile success.
- Final required GitHub CI must pass at exact submitted HEAD and relevant integration merge SHA. A green PR workflow alone is not release acceptance.
- If local infrastructure unavailable, report `NOT_RUN` with exact blocker; do not invent green tests or use screenshots as proof of SQL persistence.
- Browser checks: desktop viewport (recommend 1440x900), four roles, empty/error/access-denied states. No mobile-specific acceptance.

## Reports and rounds

For each task use: `tasks/<ID>/reports/round-001/` or the next-unused `round-NNN` directory.

Mandatory files for each active round:

- `FINAL_REPORT.md` - status: PASS, FAIL, BLOCKED, or ALREADY_DONE; base and HEAD SHA; worktree; changed files; behavior; risks.
- `TEST_RESULTS.md` - commands, execution environment, real counts, successes/failures/skips, links to exact-HEAD CI artifacts.
- `CODEOWNER_HANDOFF.md` - PR URL, review request, blockers, residual risks, desktop walkthrough, rollback notes.
- `CHANGED_FILES.md` - every touched path and why; state if source unchanged.

On final integration, R11-11 also writes `RELEASE_DECISION.md`, `REQUIREMENT_TRACEABILITY.md`, `DEMO_REHEARSAL.md` and `KNOWN_GAPS.md`.

## Stop conditions

Stop and request CODEOWNER review when a task requires broader business semantics, a new irreversible migration, production data access, dynamic role grant, payment calculations, or forced synchronization with a conflicting teammate PR.
