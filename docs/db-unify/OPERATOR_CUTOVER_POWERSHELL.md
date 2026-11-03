# Operator cutover and rollback

PROPOSED - PENDING CODEOWNER FINAL REVIEW. Live operations BLOCKED_FOR_CODEOWNER. Code merge, local capability enablement, schema upgrade, data import, legacy archive, production deployment and real QR integration are separate decisions. This work performs none of those live operations. No legacy import is selected.

## Preflight and go or no go

Record the approved repository SHA, exact SQL instance/port/database fingerprint, operator identity and authorization. Quiesce the demonstration target. Obtain read-only target authorization separately; an existing credential is not write authorization. Run the read-only preflight, compare every script/checksum including V37/V39/V40, confirm V38 absence and investigate any seed-history mismatch. Do not broaden locations or change Flyway history to pass.

No go if any history/schema/FK/identity/receipt anomaly exists, any required test/CI gate fails or was not run, the target backup is unverified, the synthetic Member IDs/email/role/status differ from approval, or any alternate connection/listener exists. Full CI and desktop SQL-backed browser evidence must reference the final proposed commit.

## Backup and isolated rehearsal

These commands are templates only. They remain disabled until the operator records explicit target authorization. Keep credentials outside scripts/arguments and Git. The approval Boolean is deliberately false; do not turn it on as part of code review.

```powershell
$LiveDatabaseOperationAuthorized = $false
if (-not $LiveDatabaseOperationAuthorized) { throw 'BLOCKED_FOR_CODEOWNER: exact target and recovery authorization required' }
# Populate these only from the separately signed operational record.
$env:SQLCMDPASSWORD = '<approved-secret>'
$login = '<approved-login>'
$backupFile = '<approved-new-server-backup-path-with-guid>.bak'
# Execute using the approved SQL client connection on numeric loopback.
# BACKUP DATABASE [sportify] TO DISK=N'<new-path>' WITH CHECKSUM;
# RESTORE VERIFYONLY FROM DISK=N'<new-path>' WITH CHECKSUM;
# RESTORE FILELISTONLY FROM DISK=N'<new-path>';
# RESTORE DATABASE [sportify_unify_test_TIMESTAMP_restore] FROM DISK=N'<new-path>'
#   WITH CHECKSUM, MOVE N'<logical-data>' TO N'<new-data-path>',
#   MOVE N'<logical-log>' TO N'<new-log-path>';
```

Never reuse a database/file, use REPLACE, drop/rename a database, or rewrite history. Check DB_ID is null and all restore paths are new before an authorized restore. VERIFYONLY alone is insufficient; compare the actual restored clone's catalog, schema, every table count/diagnostic aggregate, financial totals, receipt ownership and historical aliases. Diagnostic checksums are not collision-free or a substitute for native backup verification. The automated synthetic restore test does not verify a real target backup.

Run the approved V40 application artifact against the isolated clone with simulation disabled first. Verify Flow 1 role/profile isolation; Manager schedule creation and allowed rescheduling with unchanged numeric key, old alias, revision conflict and preserved bookings/credits; Coach/Reception attendance and results/date boundaries; Member own history; actual payment/receipt/refund/cash behavior. Do not activate Manager backfill. Where a current target is below V39, rehearse forward V39/V40 first and preserve all applied scripts/checksums.

## Authorized local configuration

Only after independent operational approval and restored-clone evidence, configure the following in the operator's ignored environment. Do not run it in this agent workstream.

```powershell
# Configuration template only; no application start command is supplied here.
$env:SPRING_PROFILES_ACTIVE = 'dev,unified-demo'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:DB_URL = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true'
$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5173'
$env:PAYMENT_UNIFIED_DEMO_ENABLED = 'true'
$env:PAYMENT_UNIFIED_DEMO_TEST_ENABLED = 'false'
$env:PAYMENT_UNIFIED_DEMO_MEMBER_IDS = '<approved-dedicated-synthetic-member-ids>'
$env:PAYMENT_LOCAL_DEMO_ENABLED = 'false'
$env:PAYMENT_SIMULATOR_ENABLED = 'false'
$env:ATTENDANCE_BACKFILL_ENABLED = 'false'
```

Use normal datasource credentials for the approved local instance. No `demo` seed profile on canonical sportify; no alternate Hikari/Flyway/JNDI target. The existing QR frontend flag remains opt-in. Verify backend health and the single datasource/DB_NAME, authenticated capability, synthetic Member restrictions, non-payable payload, explicit confirmation, one transaction/invoice/audit, persisted receipt after restart and zero actual-cash/revenue/refund changes.

## Rollback and restore

Any unexpected financial delta, unauthorized capability, changed historical numeric key/alias, duplicate receipt/transaction, failed migration or loss of ownership is a zero-tolerance stop. Disable PAYMENT_UNIFIED_DEMO_ENABLED and restart the owned application. Preserve logs and database evidence; do not delete audit/payment/entitlement rows. Stop writes for a data incident and restore the separately approved verified backup with its coordinated application artifact under a fresh incident authorization. Restoring over live data is not authorized by this document.

## Legacy retention

Keep sportify_demo and sportify_qr_demo untouched. Archive/decommission needs separate CODEOWNER approval of provenance reconciliation, backup and actual restore, retention period, no active connections and an explicit target inventory. Branches and private reports are preserved. If eventually approved, merge using Create a merge commit only; this work does not merge, deploy, self-approve or delete branches.
