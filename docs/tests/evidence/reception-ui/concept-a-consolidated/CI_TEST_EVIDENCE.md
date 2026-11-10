# CI and Test Evidence

Worktree: `D:\SWP391\rcp-consolidated` (clean worktree from origin/main `fd4e48b0`). Frontend dir: `D:\SWP391\rcp-consolidated\frontend`.

## Local runs (exact exit codes)

| Code commit | Command | Exit | Result |
|---|---|---|---|
| `b7d2bbe2` | `npm ci` | 0 | installed |
| `b7d2bbe2` | `npm run lint` / `npm run build` / `npm test` | 0 / 0 / 0 | 124 files, 2000 tests passed |
| `ccf05195` (last code change) | `npm run lint` / `npm run build` / `npm test` | 0 / 0 / 0 | 124 files, 2000 tests passed |

Commits after `ccf05195` change only `docs/` (reports and PNGs); no frontend source changed.
Baseline on main `48bba20e` before the RUI work: 123 files, 1996 tests. Added by this PR: `receptionConcept.test.ts` (3) and one inbox assertion.

## GitHub CI

Run for docs commit `0f232a64` (frontend identical to `ccf05195`): https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/42764652425
- PASS: Change impact, Project conventions and workflow lint, Frontend checks and build, Unit tests (no Docker), Unit test report, Flyway migration guard, Compose smoke, Security (CodeQL, Frontend CodeQL, Dependency review, Security gate), CodeQL.
- Integration tests (Testcontainers, SQL Server 2022): pending when this file was written.

The final head SHA and its CI conclusion are recorded in the PR #305 handoff comment, since committing this file starts a new run.
Previous green CI on the 12 closed PRs is not used as evidence for this branch.
