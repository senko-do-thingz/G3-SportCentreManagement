# Reproducing isolated regression

Current scope: runtime-only unification of Flow 1-3, Flow 4+ and QR Local Demo.
Historical migration and any new archive/import/API/prototype are OUT OF SCOPE.
The existing fictional archive tests are retained unchanged as prior regression,
not a release deliverable. Protected targets remain unauthorized and untouched.
See [startup and final checklist](RUNTIME_STARTUP_POWERSHELL.md) and
[scope/gates](status.json). Quarantine policy acceptance remains pending.

Use Java 25, Maven, Node 24, local SQL Server on numeric loopback, and explicit test credentials. Do not copy the shared working directory or its .env into this worktree. The fresh-fixture helper never reads DB_URL and never reuses or drops a database. Setting UNIFY_TEST_SQL_PORT selects local mode; leaving it absent uses SQL Server Testcontainers in CI.

```powershell
$env:UNIFY_TEST_SQL_PORT = '1433'
$env:UNIFY_TEST_DB_USER = '<fixture-login>'
$env:UNIFY_TEST_DB_PASSWORD = '<fixture-secret>'
Set-Location backend
mvn -B -ntp test
mvn -B -ntp verify '-DskipUnitTests=true' '-Dit.test=UnifiedDemoDatabaseIntegrationTest,UnifiedReplayLifecycleIntegrationTest,UnifyReconciliationIntegrationTest'
Set-Location ../frontend
npm ci
npm test
npm run lint
npm run build
```

Use the repository test timeouts and assertions unchanged. Record all failed attempts as well as reruns. The legacy local workflow and unified workflow share the same SQL-backed test contract; the original local profile/guard remains independently tested.

Browser harness, from repository root, needs Python Playwright, Pillow, zxing-cpp and Edge. Set DB_USERNAME, DB_PASSWORD and JWT_SECRET privately. It creates a new fixture, runs only loopback processes on free ports 18080/15173, initializes seeds under the new guard with an inert initial member allowlist, restricts owned synthetic identities, then restarts with the actual allowlist. Existing databases and occupied listeners are preserved.

```powershell
$testDb = 'sportify_unify_test_' + (Get-Date -Format yyyyMMddHHmmss) + '_browser'
python frontend/scripts/db-unify-check.py --database $testDb --out scratch/db-unify/browser-new --jar backend/target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar
```

It exercises real browser login, pending non-payable QR and independent fallback decoding, keyboard confirmation, network failure and refresh, persisted receipt, real finance exclusion, actual Manager date rescheduling with alias/revision/credit checks, cross-role screens, and actual backend restart. Screenshots cover 1440/768/390 in both themes; desktop is the primary acceptance target. JSON/logs/screenshots stay local and must be sanitized before sharing.

CI additionally runs the complete existing SQL integration manifest, immutable migration checks, frontend/security/package/compose gates, and all mandatory DB-UNIFY integration suites. A partial local selection does not replace full CI.

Round 04 adds required UnifiedReplayLifecycleIntegrationTest (five cases): real
card/package purchase and settlement followed by production maintenance expiry;
official desk card upgrade; prohibited simulated refund quote/create; and a full
authorized actual refund. Replays compare full original settlement responses,
historical rows, current benefit rows and global financial counts/totals. Existing
nine corrupt pending/inactive item cases remain required and denied. Run locally:

```powershell
mvn -B -ntp -f backend/pom.xml verify '-DskipUnitTests=true' '-Dit.test=UnifiedReplayLifecycleIntegrationTest,UnifiedDemoDatabaseIntegrationTest'
```

Round 02 adds `OperationalMemberPolicyTest` and extends
`UnifiedDemoDatabaseIntegrationTest` with a one-seat mixed real/synthetic fixture.
It verifies official real payment confirmation, unchanged real capacity, roster,
check-in, attendance, results, credit and cash/paid aggregates after simulation
and denied API attempts; staff-assisted booking and historical booking bypasses;
plan/attendance correction denial; persisted quarantine after flag removal/email
rename; cancellation recovery; and ordinary real booking/cancellation.

Historical replay fixtures cover CASH, mismatched transaction reference, owner,
amount, status, missing/mismatched audit and mismatched receipt amount. Invalid
transaction rows are inserted once in disposable fixtures, never edited around
the immutable ledger trigger. Missing transaction linkage is rejected by the
unchanged V36 constraint. Valid replay remains idempotent in the shared real
HTTP workflow. Existing full payment/refund/cash suites remain mandatory.

`LEGACY_SOURCE_INVENTORY.sql` is a read-only operator query template. Verify its
syntax on a newly created fixture only unless target-specific read authorization
has been given. Fixture evidence does not replace protected source inventories.
See `SOURCE_HISTORY_DECISION.md`: historical unification remains incomplete
until actual required source migration, historical access and independent
reconciliation are implemented and verified. Passing regression or accepting a
reduced runtime scope does not complete historical data unification.

## Fast-track shared runtime and Flow 1-3 evidence

The shared datasource test now also checks the identity/catalog/payment JPA
metamodel, one EntityManagerFactory/JpaTransactionManager, all JDBC/NamedParameter
templates and matching JPA/JDBC DB_NAME(). No runtime connection is added.

Full CI remains required on the final HEAD. It runs the existing authentication,
role/permission/account, member/profile/search, staff/Manager, booking/schedule,
training/attendance, payment/refund/cash/receipt and migration suites. These cover
Flow 1-3 alongside Flow 4+ and unified/legacy QR. Mandatory SQL manifest coverage
and zero skipped tests remain enforced. Local focused verification:

```powershell
mvn -B -ntp -f backend/pom.xml verify '-DskipUnitTests=true' '-Dit.test=UnifiedDemoDatabaseIntegrationTest,UnifiedReplayLifecycleIntegrationTest,UnifyReconciliationIntegrationTest'
```

The focused selection supports the explicit local UNIFY_TEST_* settings above.
Other existing Flow 1-3 SQL suites use Testcontainers and run in full CI (or with
a working local Docker daemon); they do not inherit those local connection settings.
All SQL targets must be new owned disposable databases or CI containers. Actual
sportify/sportify_demo/sportify_qr_demo connections are NOT RUN. Full QR CI and
the local db-unify-check.py browser/restart harness remain required; the latter
also covers cross-role Flow 1-3 screens and actual Manager rescheduling.
