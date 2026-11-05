# R11-11 - Consolidated release candidate gate

- Round: round-001. Status: PASS for candidate verification; CODEOWNER review PENDING.
- Base origin/main: 1827a05b7393eb069ece5ae77c8f65d2a354466a.
- Reviewed candidate source snapshot: aedd882012d893f8759a2c7f80d105271196efe7.
- Local full suites started at: d17c785b6d6871dd766c55c9ff77e5d0c8893557. Later changes are identified by Git diff; do not transfer a test result to changed application code.
- Branch: feat/release-1-1-desktop.
- Worktree: D:/G3-SportCentreManagement/SWP-R11-RELEASE.
- Consolidated PR: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/217.

Prepare one consolidated PR for review. Record source versus report-only commits and CI outcomes. Final merged-main acceptance, CODEOWNER signature and deployment are separate and not performed.

Focused evidence: Full regression, real SQL/browser rehearsal, restart and immutable CI evidence. No approval or merge by the agent.

| Evidence | Result |
|---|---|
| Local full backend unit, Java 25 | {'tests': 2268, 'failures': 0, 'errors': 0, 'skipped': 0, 'passed': 2268} |
| Local full native SQL integration | {'tests': 1040, 'failures': 0, 'errors': 0, 'skipped': 0, 'passed': 1040}; full verify exit 0, 52:27 min |
| Local full frontend | 1942/1942 passed; failures 0; skipped 0 |
| Local lint / TypeScript / build | See r11-frontend-exits.txt; never infer from test success |
| Real desktop browser | PASS; 35/35 checks; source aedd882012d893f8759a2c7f80d105271196efe7 |
| Owned backend restart | PASS |
| Candidate CI | https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41988848995; SHA 9184e9132946b78d85d958de9800da03433b2fad; success |

Full local suites above ran at d17c785b6d6871dd766c55c9ff77e5d0c8893557. The final fixes at 9184e9132946b78d85d958de9800da03433b2fad were rerun locally (3 support frontend, 3 backend unit/controller and 3 native SQL cases, all passed) and in full PR CI. CI checked source head 9184e9132946b78d85d958de9800da03433b2fad in integration merge 8d62a3aec5c9affcce695b2753dca23593e36973; the package verification job logged that EXPECTED_SHA. Final report-only commits must also retain green exact-head/merge checks; see the final submitted PR checks and submitted-head evidence JSON. Overlapping reruns are not extra unique tests.

Database safety: all SQL fixtures are new timestamped owned databases; master is used only for CREATE/server metadata. No protected application database, Flyway history or production/shared row was accessed or modified. No new migration, force push, merge, self-approval or deployment.

Scope: desktop only. No mobile, AI Audit Log, Flow 5-6 implementation, dynamic RBAC, Waitlist or legacy removal. R11-00 acceptance and the consolidated continuous-execution override are recorded in EXECUTION_AUTHORIZATION.md.

Known limits and the release decision are recorded under R11-11. Full suite results supersede overlapping focused counts; they are not added together as unique tests. A passed old main run is not evidence for newly changed code.
