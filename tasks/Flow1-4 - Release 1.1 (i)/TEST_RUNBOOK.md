# Test and CI runbook

## Baseline and working directory

Run source checks in an isolated task worktree. All tools must use actual source locations, not stale root paths.

```powershell
git fetch origin main
git status --porcelain=v1
git rev-parse origin/main
git worktree list
```

## Frontend (run from frontend/)

```powershell
npm ci
npm run lint
npx tsc -b
npm test
npm run build
```

Do not claim frontend tests pass until counts/exit codes for the actual commit are collected. `npm test` maps to `vitest run` in current package.json. Use targeted tests during implementation, full suite before review.

## Backend (run from backend/)

```powershell
java -version
mvn -version
mvn clean verify
```

Use Java 25 and an isolated SQL Server/Testcontainers environment. If Docker is unavailable, use the approved disposable native SQL test setup. Capture Maven Surefire/Failsafe reports and verify integration tests actually execute; zero integration executions is not a pass.

## Smoke test

The supplied review proposes `backend/scripts/step2-smoke-test.ps1`. Before calling it, verify its existence, parameters, dataset and that it cannot mutate protected data. If absent or obsolete, report the discrepancy and substitute an equivalent documented safe isolated smoke run, without editing it blindly.

## Desktop browser acceptance

- Recommended viewport: 1440x900; one primary desktop browser is sufficient for this release.
- Accounts/roles: MANAGER, COACH, RECEPTIONIST, MEMBER.
- Use unique owned disposable database; seed non-sensitive data with appropriate profiles only after guard approval.
- Verify authenticated role switching and stale token/response protection. Test 403/unauthorized direct API calls independently from UI route redirects.
- Support: create -> detail -> transition -> filter; retry idempotency and invalid transitions.
- Operations: date range changes, counts agree with API, no manual revenue math.
- Attendance: filter -> session details, Coach/Member forbidden on Manager endpoints.
- Coach progress: class -> member -> profile/progress; coach from another class denied.
- Core regression: Manager class reschedule public Session ID, booking credits, revenue reports and Member private history remain intact.

## Evidence table required in reports

| Suite | Command/workflow URL | Commit SHA | Environment | Executed | Passed | Failed | Skipped | Result |
|---|---|---|---|---:|---:|---:|---:|---|
| Backend unit | ... | ... | ... | ... | ... | ... | ... | ... |
| SQL integration | ... | ... | ... | ... | ... | ... | ... | ... |
| Frontend test | ... | ... | ... | ... | ... | ... | ... | ... |
| Lint/TS/build | ... | ... | ... | - | - | - | - | ... |
| Desktop browser | ... | ... | ... | ... | ... | ... | ... | ... |

If a job is unavailable, record NOT_RUN and the reason. Do not sum overlapping targeted and full-suite tests as unique cases.
