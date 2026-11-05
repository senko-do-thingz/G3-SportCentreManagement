# R11-04 test results

3 new unit/controller and 3 new native SQL cases; 9 focused frontend cases with backfill regression; real desktop summary/detail with SQL comparison.

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

## Commands and environment

Windows 11 amd64, PowerShell, Java 25.0.2, Maven 3.9.15, Node 24.20.0, npm 12.2.0. Native SQL Server version 16.0.1210.3 is reachable only through loopback port 1433 for these fixtures. Docker is absent locally.

Frontend from frontend/: npm ci; npm run lint; npx tsc -b; npm test -- --reporter=default --reporter=json --outputFile=../r11-vitest-results.json; npm run build.
Backend from backend/: Java 25, mvn -B -ntp clean verify with UNIFY_TEST_SQL_PORT=1433 and private UNIFY_TEST_DB_USER/PASSWORD environment values. The helper creates new databases; credentials are never committed.
Focused native baseline: mvn verify -DskipUnitTests=true -Dit.test=TrainingWorkflowIntegrationTest (143 passed). Focused attendance: mvn verify -Dtest=ManagerAttendanceReadServiceTest,ManagerAttendanceReadControllerTest -Dit.test=TrainingWorkflowIntegrationTest#R11* (3 unit + 3 SQL passed).
CI infrastructure: python -m unittest discover -s scripts/ci -p 'test_*.py' (104 passed).
Desktop: python frontend/scripts/db-unify-check.py --database <new timestamped owned name> --out <new evidence folder> --jar <built JAR> --release11. Explicit private credentials and R11_DEMO_PASSWORD are supplied via the process environment.

## Negative controls and unavailable checks

Support retry key and state-machine rules, stale class/member clearing, zero/unmarked distinctions, role denial, invalid date range, other-Coach hidden assignment and terminal support rejection are covered by focused/unit/SQL/browser evidence as applicable. UI unit fixtures are tests, not replacements for real SQL/browser acceptance.

Full local native SQL errors or skips must be read alongside the command exit and XML counts. Container-only suites cannot be called passing locally; exact candidate CI independently verifies the Docker path. step2-smoke-test.ps1 was inspected but NOT_RUN because it hardcodes a localhost application target and mutates setup data; the new guarded native browser run is the safe integrated substitute.

The full native suite did execute all 1040 SQL cases successfully; Docker was not required for this native run. It took 52:27 minutes, including the unchanged 50001-event cash-export boundary fixture. No case was truncated or disabled to speed it up. The local full snapshot predates the final date-year validation and support refresh; those affected tests were rerun and full updated-head CI passed.

Browser attempts are retained separately: browser/ passed 29 checks, browser-final/ passed 35, and browser-candidate/ failed at the Manager navigation wait after its support checks passed. The harness was corrected to accept SPA query strings and wait for DOM navigation rather than all assets, with failure diagnostics. browser-verified/ is the final rerun. No application assertion or permission check was removed to obtain a pass.

Mobile and excluded feature acceptance: NOT_APPLICABLE. Final merged-main SHA and deployment checks: NOT_RUN by design; the agent is prohibited from merging/deploying. This is review-candidate evidence.
