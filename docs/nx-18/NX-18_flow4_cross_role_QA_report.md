# NX-18 desktop Flow 4 QA

Status: PARTIAL. The exact 1440x900 SQL-backed browser rehearsal is BLOCKED.
Independent CODEOWNER sign-off is pending.

The revised scope uses the approved Flow 4 implementation on main at 1827a05.
It replaces the old mock browser matrix with the existing real browser runner
and a focused desktop mode. The latest scope waives mobile layout, mobile Menu
accessible-name and axe findings, dark mobile captures, and the private AI audit.
The old fixture screenshots and runner are excluded from this change.

| Required scenario | Exact desktop rerun | Checks in the runner |
| --- | --- | --- |
| Coach to Member | BLOCKED | Assign plan and exercises, save result and feedback, and verify that the booked Member sees the saved data. |
| Reception arrival and attendance | BLOCKED | Record arrival separately from participation, record and correct attendance, and verify Member readback and unchanged package credits and booking counts. |
| Account and role isolation | BLOCKED | Peer Member cannot open private plan or result, anonymous reads are denied, and wrong-role endpoints reject access. |
| Restart and persistence | BLOCKED | Read the same owned data after restarting the backend. |

No isolated SQL Server was listening at 127.0.0.1:1433 in the verification
environment. The 17 historical evaluator tests passed locally on b485c35.
Those tests only check the old QA verdict logic. The earlier real desktop SQL
rehearsal is reported in PR #206 and used 1440x1000.

Historical GitHub CI passed on PR #174 at b485c35 (2,243 unit and 997
integration tests) and on main at 1827a05, including frontend build and the CI
gate. These runs precede this change. Final-head CI is pending.

- PR CI: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41800926039
- Main CI: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41968012911
- Prior real Flow 4 rehearsal: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/206

Run from the repository root with the Java 25 backend JAR already built, frontend
dependencies installed, local SQL Server, sqlcmd, and Python Playwright with
Edge. Supply DB_PASSWORD and JWT_SECRET privately.

```powershell
$database = 'sportify_unify_test_' + (Get-Date -Format yyyyMMddHHmmss) + '_nx18'
python frontend/scripts/db-unify-check.py --flow4-only --database $database --out "scratch/$database" --jar backend/target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar
```

Review the exit code and private flow4-results.json before recording PASS.
