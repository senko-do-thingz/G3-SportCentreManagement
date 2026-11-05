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

## Execution status and CI evidence

The desktop run did not start. The local environment had no SQL Server
listener at 127.0.0.1:1433 and no built backend JAR. The configured viewport
is 1440x900. The observed viewport, exit code, check count, failed check names,
and post-restart readback are unavailable. All required scenarios above
remain BLOCKED.

GitHub CI passed on reviewed runner commit e4edb7e, including frontend build,
packaging and the CI gate. All 2,265 unit and 1,037 integration tests passed
with zero failed or skipped. The QR browser workflow passed on the same commit
and executes qr04-ci-verification.py. The desktop --flow4-only command is a
separate acceptance check. PR #206 provides historical browser coverage
at 1440x1000. Revalidate the desktop command on the final HEAD.

- Repository CI: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41980923772
- QR browser CI: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41980923831
- Prior Flow 4 rehearsal: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/206

## Reproduction

Run from the repository root with the Java 25 backend JAR already built, frontend
dependencies installed, local SQL Server, sqlcmd, and Python Playwright with
Edge. Supply DB_PASSWORD and JWT_SECRET privately.

```powershell
$database = 'sportify_unify_test_' + (Get-Date -Format yyyyMMddHHmmss) + '_nx18'
python frontend/scripts/db-unify-check.py --flow4-only --database $database --out "scratch/$database" --jar backend/target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar
```

Review the exit code and private flow4-results.json before recording PASS.
