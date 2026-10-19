# BE-22 finance report regression coverage

The implementation selectively restores historical PR #59 application contracts on current main using new V30. It preserves BE-21 and adds bounded CSV streaming plus whitespace-aware formula protection. No historical migration is changed.

## Local and CI evidence

Local focused verification: 92 tests passed, zero failures/errors/skips. The final clean Maven package and project guards are recorded in the external Round 2 evidence package. Unit/controller tests are supplemental; they do not prove SQL views. Docker is unavailable locally. All SQL Server cases below are compiled but NOT RUN locally, and remain mandatory CI gates with zero skipped tests.

| Suite | Coverage | Execution requirement |
|---|---|---|
| `FinanceMigrationSourceTest` | Globally unique V30, existing 25-migration content fingerprint, installed Flyway SQL Server parser and separate view batches | Local unit |
| `FinanceReportRepositoryTest` | Bound SQL parameters, escaped LIKE literals, COUNT_BIG, long offsets/stable ordering, TOP export limit | Local unit |
| `FinanceReportServiceImplTest` | Overview/rounding/empty denominators, trend/zero months, date/enum/page/range bounds, group shares, exact CSV/UTF-8/money/formula/50000-row boundaries, stale pre-count | Local unit |
| `ManagerFinanceControllerTest` | All five routes: manager 200, anonymous/malformed-token 401, MEMBER/COACH/RECEPTIONIST 403; parameter binding/errors; CSV headers/bytes; server error not a successful download | Local WebMvc with real security filters and mocked JWT service |
| `FinanceReportIntegrationTest` | Real Spring/SQL fixtures, overview/refund-period fallback, all groupings, status/type/method/search/page filters, CSV and date boundaries; actual BE-21 submit/approve/complete partial and full refunds | Mandatory SQL Server 2022 CI |
| `FinanceMigrationCompatibilityIntegrationTest` | Fresh normal/demo paths; normal V27 and demo V29 upgrade preserving rows/checksums/history; repeat migrate no-op; each conflicting view/index rejected without overwrite; real second-connection insert between count and export stream | Mandatory SQL Server 2022 CI |

The SQL suites are listed in `scripts/ci/required-integration-tests.txt`. Existing BE-21/receipt/verification/demo/Flyway suites remain required. The only existing test expectation changed is demo history's latest prefix: V30,V29,V28,V27.

## Reproduction

From backend, `mvn -B -ntp clean package` compiles every test and runs non-integration tests without skip flags. With Docker running, `mvn -B -ntp verify` runs both unit and integration phases. From repository root run both report guards on `backend/target/failsafe-reports`:

```text
python scripts/ci/check-test-reports.py integration backend/target/failsafe-reports
bash scripts/ci/check-no-skipped-tests.sh backend/target/failsafe-reports
```

CI must prove 24 normal migrations and 26 demo migrations on fresh databases; upgrade baselines have 23 and 25 respectively, then apply exactly V30 once. New migration-path tests create randomly named databases only inside their own ephemeral Testcontainers SQL Server, with generated credentials. They never clean, repair, drop existing databases or hand-edit Flyway history.

## Financial acceptance

Paid revenue is current SUCCESS payment amount by payment_time, not gross receipts or net revenue. Existing March 2001 fixtures produce 2000000.00 from three SUCCESS rows and 250000.00 completed refunds separately. The actual BE-21 lifecycle tests approve/complete 100000.00 of P1's 300000.00 and all 500000.00 of P2. Approval keeps paid revenue unchanged. Completion removes 300000.00 or 500000.00 respectively from that original month and attributes the approved refund amount to the completion month. Finance reads must preserve payment/refund snapshots. Recognized net revenue, retained partial balances and a separate payout workflow require future product decisions; none is silently introduced here.

FE-13 PR #82's current client sends the supported paths, field names and enum values. Static contract compatibility is recorded separately from browser/runtime proof. Merge approval requires successful SQL Server CI and reviewer acceptance of the documented SUCCESS-only semantics.
