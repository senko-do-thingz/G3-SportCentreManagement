# BE-06 Scheduled Maintenance Tests

## Scope and rules

Four lifecycle updates share one injected Clock snapshot converted explicitly to Asia/Ho_Chi_Minh. The existing midnight trigger is reused. Pending creation dates older than seven calendar days cancel; day seven remains pending. ACTIVE end dates before today expire. No zero-credit expiration, financial effects, booking mutation or BE-06 business audit events.

## Unit tests (no database)

| Suite | Coverage |
|---|---|
| ScheduledMaintenanceServiceTest | Five UTC/Vietnam boundary instants including leap day and year rollover; eight creation-day examples; one instant across midnight; exact cutoff/date/now arguments and invocation order; actual returned counts; zero rows; failure propagation; isolated write transaction and absence of audit dependency |
| MembershipScheduledJobsTest | One unchanged daily Vietnam cron; no duplicate trigger; retained legacy delegation and common date; real SLF4J count messages after service return; no success log after maintenance failure; accurate committed counts even if later legacy work fails |
| MembershipCardServiceImplTest / SportPackageServiceImplTest | Round 2: 24 new executions across direct/payment-confirmation entry points; refresh converts cached PENDING_PAYMENT into current terminal statuses before validation; payment-less CANCELLED rows; lock failure propagates without save, SUCCESS, receipt or audit. Existing success-order tests now assert payment lock, item read, locked refresh, item save, SUCCESS and receipt order. |
| ScheduledMaintenanceQueryTest | Parse/bind each actual repository mutation using Hibernate entity metadata and SQLServerDialect without JDBC; verify mandatory transaction plus automatic flush/clear |

Unit tests do not emulate SQL updates or claim persisted-state/concurrency coverage. Those assertions run against real SQL Server below.

## SQL Server integration suite

Required class: `com.sportify.catalog.service.impl.ScheduledMaintenanceIntegrationTest` (18 methods, 41 parameter-expanded executions prepared).

Fixtures use unique codes and generated identities, real migrated tables and a fixed UTC Clock whose instant is Vietnam midnight. Records are committed for visibility across worker transactions and cleaned only by the fixture's member, manager, sport and package ids. Shared demo accounts are not modified. No generated-id or test-order assumptions. Worker coordination uses latches/barriers and bounded waits, without sleeps.

| Cases | Expected evidence |
|---|---|
| Pending cards and registrations, creation age 0/1/6/7/8/15 days, each midnight and 23:59:59 | Ages 0..7 unchanged, ages 8/15 cancelled; two affected rows only for eligible days; persisted status and repeat-zero checks |
| ACTIVE card/package end yesterday/today/tomorrow | Only yesterday expires; updated_at equals supplied Vietnam timestamp; every other column preserved |
| Null card end, GOLD and permanent STANDARD | Entire rows unchanged |
| Day-seven PENDING_PAYMENT item with yesterday end date, card and package | Entire row unchanged; pending items do not expire |
| Null registration end | Actual NOT NULL constraint rejects invalid fixture; no permissive schema replacement |
| Card EXPIRED/CANCELLED/REPLACED; registration EXPIRED/CANCELLED/REFUNDED | No status, metadata or timestamp changes |
| ACTIVE package with zero credits and future end | Stays ACTIVE |
| All four eligible operations together | Exactly one count each; repeat total zero; every related payment, invoice, invoice line, booking, check-in and session row unchanged; audit row count unchanged; price, link and cancellation snapshots retained |
| Failure in fourth statement | All prior card and package changes rolled back, including timestamps |
| Flush/clear | Pending managed price update flushes, bulk update clears stale entity, fresh load is CANCELLED and retains price |
| Two simultaneous scheduler calls | Aggregate affected total four, individual totals four and zero, subsequent zero |
| Cancellation commits before confirmation, card and package | CANCELLED retained; PENDING payment; no invoice; activation rejected |
| Confirmation commits first, card and package | ACTIVE retained; SUCCESS payment and one invoice unaffected by maintenance |
| Activation reads cached pending item, maintenance cancels, activation resumes, card and package | Must reload and reject CANCELLED; no resurrection, payment SUCCESS or invoice. The owner-approved Round 2 item-lock/refresh safeguard is implemented; actual SQL Server execution remains pending CI. |
| Expiration vs atomic credit debit | Final EXPIRED; credits equal six minus the actual zero/one debit, never negative or double-changed |

## Regression requirements

Retain every existing required integration suite. In particular run PaymentVerificationIntegrationTest, ReceiptIntegrationTest, DeskSaleRollbackIntegrationTest, RefundPaymentLinkIntegrationTest, BookingRulesIntegrationTest, BookingV20IntegrationTest, ManagerSessionIntegrationTest, MemberScheduleIntegrationTest, CoachScheduleIntegrationTest and MyProfileIntegrationTest. Existing membership/package unit tests remain enabled, with EntityManager mocks, stronger success-order checks and additional Round 2 regression cases. No original assertion is removed.

## Commands and truthful status

From repository root, PowerShell:

```powershell
mvn -B -ntp -f backend/pom.xml test '-Dtest=MembershipCardServiceImplTest,SportPackageServiceImplTest,ScheduledMaintenanceServiceTest,MembershipScheduledJobsTest,ScheduledMaintenanceQueryTest'
mvn -B -ntp -f backend/pom.xml test '-Dtest=!*IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -B -ntp -f backend/pom.xml test-compile -DskipTests
python scripts/ci/check-test-reports.py unit backend/target/surefire-reports
```

On Docker-capable GitHub Actions, from backend:

```text
mvn -B -ntp verify -DskipUnitTests=true
```

Then run the existing integration report manifest guard and no-skipped-tests guard. Compilation is not SQL execution. Docker is unavailable locally; no new SQL Server scenario has been executed locally. The owner-approved safeguard is implemented and covered by local regression tests; SQL Server race behavior must still be verified by the unchanged integration assertions in CI.

## Repository document policy verification

Verify all discovered AI Audit Log paths are absent from the index, still present locally with unchanged SHA-256, and matched by precise ignores. Check application audit Java/tests, activity_log schema and BE-04 documentation remain tracked and unignored. Use path/status-only staged inspection; never print or export deletion bodies. Non-confidential implementation patches must use an explicit allowlist excluding all AI document paths and tasks artifacts. Historical Git revisions are outside the approved scope.
