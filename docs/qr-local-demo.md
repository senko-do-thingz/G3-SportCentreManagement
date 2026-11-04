# QR Local Demo MVP

The member QR route displays one non-payable QR and one explicit simulation action. QRServer supplies the image; an offline SVG from the existing `qrcode` dependency appears if that request fails. The image contains only a synthetic public reference. Scanning never confirms a payment.

## Local startup

Use an isolated disposable SQL Server database with the current Flyway history. Do not point this mode at `sportify`, shared data, or a remote server. Keep credentials in ignored `backend/.env`. The demo profile seeds synthetic accounts on a fresh database; it does not repair an existing database's migration history.

Backend terminal:

```powershell
Set-Location 'D:\G3-SportCentreManagement\SWP\backend'
$env:SPRING_PROFILES_ACTIVE = 'dev,demo,local-demo'
$env:PAYMENT_LOCAL_DEMO_ENABLED = 'true'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:DB_URL = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_qr_demo;encrypt=false;trustServerCertificate=true'
mvn spring-boot:run
```

Frontend terminal:

```powershell
Set-Location 'D:\G3-SportCentreManagement\SWP\frontend'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'
$env:VITE_QR_PAYMENT_DEMO = 'true'
npm run dev -- --host 127.0.0.1
```

Open `http://127.0.0.1:5173`, sign in as a seeded demo Member, create a pending package/card purchase, and open its QR Demo. Confirm, open the receipt, and refresh to see persisted `Paid - Simulated` provenance. If a confirmation response is lost, refresh to reconcile with the backend before retrying.

## API and financial boundaries

- `GET /api/v1/payments/local-demo/capability`: authenticated Member, Receptionist, or Manager; enabled only with the explicit profile, flag, loopback binding and approved database.
- `POST /api/v1/payments/{paymentId}/demo-confirm`: active Member and owned pending eligible purchase. Client amounts, member IDs and settlement provenance are ignored.
- Settlement locks Payment before its item, creates a V35 session and simulated transaction, activates the entitlement, persists one invoice, and records `PAYMENT_CONFIRMED` atomically. Retries return the persisted settlement.
- In unified mode, a verified normal expiry or linked card upgrade does not invalidate that persisted settlement. `status=SUCCESS` describes Payment, not current benefit availability. Read `/api/v1/membership-cards/my` or `/api/v1/packages/registrations/my` for current status. Replay never reactivates benefits; pending or inconsistent histories are denied. See [Round 04 state proofs and tests](db-unify/ROUND04_REPLAY_AND_ARCHIVE_DESIGN.md). The version 1 response shape is unchanged.
- `GET /api/v1/payments/{paymentId}` includes `invoiceId` and `invoiceNumber`, preserving the receipt link after refresh.
- Simulations remain excluded from actual paid revenue, cash events and real-money refunds. No migration is altered by this work.
- The legacy HTTPS session API remains available under its existing simulator profile. That profile alone does not enable the local confirmation action.

## Verification without Docker

The optional Maven profile separates generated verification classes from an active IDE's incremental compiler. It uses the same sources and tests within this repository:

```powershell
Set-Location 'D:\G3-SportCentreManagement\SWP\backend'
mvn -B -ntp -Pqr-local-verification verify -DskipITs
```

The opt-in SQL test is named `LocalDemoDatabaseIntegrationIT` so normal unit discovery cannot silently write to a workstation database. Create a fresh test database with the constrained harness, then select it explicitly:

```powershell
Set-Location 'D:\G3-SportCentreManagement\SWP'
$testDb = 'sportify_qr04_test_' + (Get-Date -Format 'yyyyMMddHHmmss')
python frontend/scripts/qr-local-demo-check.py --database $testDb --out scratch/qr-local-verification/evidence --create-only
$env:QR_LOCAL_DEMO_TEST_DB_URL = "jdbc:sqlserver://127.0.0.1:1433;databaseName=$testDb;encrypt=false;trustServerCertificate=true"
Set-Location backend
mvn -B -ntp -Pqr-local-verification test -Dtest=LocalDemoDatabaseIntegrationIT
```

The test creates a purchase through the real API, overlaps confirmation requests, repeats the call, reloads the receipt association, and asserts one invoice/transaction/audit entry, entitlement activation, unchanged actual totals, and refund rejection. It leaves the database intact for inspection.

After a frontend build with the localhost API URL and QR flag above, run the browser harness from the repository root. It needs Python Playwright, Pillow, zxing-cpp, and Microsoft Edge. Ports 8080 and 5173 must be free:

```powershell
python frontend/scripts/qr-local-demo-check.py --database $testDb --out scratch/qr-local-verification/evidence --jar scratch/qr-local-verification/backend-target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar
```

The harness starts and stops its own processes, checks real SQL-backed browser confirmation and refresh, verifies light/dark layouts at 390px and desktop, decodes the fallback independently, and checks real-finance isolation. It neither drops databases nor changes existing fixtures. CI additionally runs the full Testcontainers suite and legacy HTTPS browser/restart gate.

## Review and rollback

Keep `PAYMENT_LOCAL_DEMO_ENABLED` unset outside the isolated demo. Disable it and restart to remove the local action. CODEOWNER approval is required before merging the QR branch. Do not delete branches, rewrite migration history, deploy, or start DB-UNIFY as part of this work.
