# Shared sportify runtime startup

This is the CODEOWNER-selected runtime-only release configuration for Flow 1-3,
Flow 4+ and QR Local Demo. All three use the same Spring datasource and SQL Server
database sportify. Historical migration is out of scope: leave sportify_demo,
sportify_qr_demo and every legacy record unchanged. No importer/archive/API or
new prototype is needed. Historical data unification remains INCOMPLETE.

Commands below are operator startup instructions, not commands executed by the
agent. Actual sportify activation, protected connections and provisioning remain
NOT RUN/UNAUTHORIZED here. Use only after separate exact-target authorization.
Passing disposable tests does not certify that target's current schema/history.

## Required configuration

| Setting | Canonical local value |
| --- | --- |
| SPRING_PROFILES_ACTIVE | dev,unified-demo |
| DB_URL | jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true |
| DB_USERNAME / DB_PASSWORD | Approved local SQL login; supply privately |
| JWT_SECRET | Private application secret meeting existing JWT requirements |
| SERVER_ADDRESS / SERVER_PORT | 127.0.0.1 / 8080 |
| CORS_ALLOWED_ORIGINS | http://127.0.0.1:5173 |
| PAYMENT_UNIFIED_DEMO_ENABLED | true |
| PAYMENT_UNIFIED_DEMO_TEST_ENABLED | false |
| PAYMENT_UNIFIED_DEMO_MEMBER_IDS | Reviewed comma-separated dedicated synthetic Member IDs; no spaces/duplicates |
| PAYMENT_LOCAL_DEMO_ENABLED / PAYMENT_SIMULATOR_ENABLED / QR_DEMO_ENABLED | false |
| ATTENDANCE_BACKFILL_ENABLED | false |
| VITE_API_BASE_URL / VITE_QR_PAYMENT_DEMO | http://127.0.0.1:8080/api/v1 / true |

Use Java 25, Maven and a supported Node version (CI uses Node 24). The local
SQL instance must expose the approved numeric loopback port; replace 1433 only
with its approved numeric port. Credentials never belong in DB_URL. Start from
a fresh terminal without conflicting SPRING_*, JAVA_TOOL_OPTIONS, command-line,
JNDI or Hikari overrides. Do not copy another worktree's .env. No demo, local-demo,
qr-demo, qr-simulator, prod or unknown profile may join the canonical pair.

Dedicated identities must already be provisioned through an authorized process:
ACTIVE MEMBER role, member_profile, email ending @unified-demo.invalid and IDs in
the allowlist. IDs mean user_account.id / member_profile.user_id, not a card ID,
payment ID or member_code. Do not substitute ordinary Members, staff or arbitrary example
IDs. The server rechecks owner/role/status/namespace and authoritative amounts.
No provisioning SQL or canonical seed is supplied or run by this work.

## Backend PowerShell

Use a free local port; preserve any existing application listener. These commands
do not create/drop databases or invoke a Flyway maintenance command. Normal Boot
startup validates history/schema and may apply pending forward migrations, so
actual startup itself needs target-specific authorization and a verified recovery
plan. Review the installed history first; no repair, clean, baseline or rewriting.

```powershell
Set-Location 'D:\G3-SportCentreManagement\SWP-DB-UNIFY'
# Supply DB_USERNAME, DB_PASSWORD, JWT_SECRET and reviewed IDs privately first.
foreach ($runtimeKey in @('DB_USERNAME','DB_PASSWORD','JWT_SECRET','PAYMENT_UNIFIED_DEMO_MEMBER_IDS')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($runtimeKey))) {
        throw "Missing required private/reviewed setting: $runtimeKey"
    }
}
if ($env:PAYMENT_UNIFIED_DEMO_MEMBER_IDS -notmatch '^[1-9][0-9]*(,[1-9][0-9]*)*$') {
    throw 'Use reviewed positive synthetic Member IDs separated by commas'
}
$env:SPRING_PROFILES_ACTIVE = 'dev,unified-demo'
$env:DB_URL = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5173'
$env:PAYMENT_UNIFIED_DEMO_ENABLED = 'true'
$env:PAYMENT_UNIFIED_DEMO_TEST_ENABLED = 'false'
$env:PAYMENT_LOCAL_DEMO_ENABLED = 'false'
$env:PAYMENT_SIMULATOR_ENABLED = 'false'
$env:QR_DEMO_ENABLED = 'false'
$env:ATTENDANCE_BACKFILL_ENABLED = 'false'
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is occupied. Preserve that process; choose an approved free port and matching frontend API URL.'
}
mvn -B -ntp -f backend/pom.xml package '-DskipUnitTests=true'
if ($LASTEXITCODE -ne 0) { throw 'Backend packaging failed' }
java -jar backend/target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar
```

The packaging command assumes the final commit already passed regression; it is
not a replacement for tests. Flyway uses identity/catalog locations on the same
datasource. Clean remains disabled, baseline/out-of-order false, validation on
migrate true, future ignore patterns empty, and Hibernate ddl-auto=validate.
Checksum/unknown/future history or mapped schema mismatches stop startup. The
read-only UnifyPreflight fingerprint check additionally detects catalog drift
but must not be run against a protected target without authorization. In
particular, unknown V38 or demo seed history must not be repaired or ignored.

## Frontend PowerShell in a second terminal

```powershell
Set-Location 'D:\G3-SportCentreManagement\SWP-DB-UNIFY\frontend'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'
$env:VITE_QR_PAYMENT_DEMO = 'true'
$env:VITE_MANAGER_PREVIEW = 'false'
npm ci
if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Visit http://127.0.0.1:5173. Real users and staff use the same application/API;
only dedicated synthetic Members may confirm a local simulation. A QR contains
a non-payable synthetic reference; scanning cannot transfer money or settle.
Production keeps simulation off and never uses this local profile combination.
The original LocalDemoSafetyGuard is unchanged and is not expanded to sportify.

## Quarantine side effect and shutdown

The unchanged account-wide policy denies synthetic/reserved identities new
bookings, check-in, attendance, results and training writes, preserving real
capacity. Any real Member with historical simulated provenance is also blocked
on separately paid benefits, even with simulation disabled or email renamed.
Paid history/credits are preserved; own receipts and cancellation remain readable/
available. There is no approved exemption, transfer or same-account recovery.
CODEOWNER policy acceptance remains pending; this scope decision does not silently
approve that impact. Do not create new mixed accounts or delete provenance.

To stop, use Ctrl+C in each terminal. To remove simulation capability on a later
authorized restart, set PAYMENT_UNIFIED_DEMO_ENABLED=false and remove unified-demo
from SPRING_PROFILES_ACTIVE (use dev for ordinary local runtime). Changing only
one control while retaining the other is intentionally rejected. This does not
remove persisted quarantine. Never delete payment/invoice/audit/legacy records.

## Concise final verification checklist

- Final HEAD backend unit, complete real SQL integration, frontend/lint/build,
  migration/security/Compose and QR browser/restart checks pass with no skips.
- Shared-runtime SQL assertion proves one datasource/JPA factory/transaction
  manager for identity/catalog/payment/QR and Flyway; JPA and JDBC DB_NAME match
  the owned disposable fixture. The canonical guard accepts only sportify;
  legacy targets/profiles and alternate connections fail before initialization.
- Flow 1-3 authentication, role/profile/staff/member behavior and Flow 4+
  schedule/booking/training/payment regressions pass. Existing migrations and
  LocalDemoSafetyGuard remain byte-identical; drift/future/checksum failures deny.
- Dedicated synthetic owner succeeds; real Members/staff/wrong owners/unsupported
  methods deny. No actual revenue, cash event, refund or operational capacity
  change occurs from simulation; mixed-account quarantine impact stays explicit.
- One durable transaction/invoice/confirmation audit survives replay, receipt
  refresh and restart. Valid expiry/replacement replay never reactivates benefits;
  inconsistent histories deny. Existing timestamp proof is not a cryptographic
  lifecycle-event audit.
- Protected canonical startup/preflight/provisioning, merge, deployment and
  legacy modification/deletion remain NOT RUN. Historical migration is OUT OF
  SCOPE and INCOMPLETE, separate from verified runtime configuration.
