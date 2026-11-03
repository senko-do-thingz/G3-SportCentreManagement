# Proposed canonical local demonstration

PROPOSED - PENDING CODEOWNER FINAL REVIEW. No live cutover is authorized by this implementation.

The application continues to use one SQL Server datasource. The new `unified-demo` capability reuses the existing authenticated local confirmation API and `SIMULATED` / `SPORTIFY_SIMULATOR` transaction, receipt and audit contracts. It does not import legacy demonstration data. `LocalDemoSafetyGuard` and its throwaway allowlist remain unchanged.

Canonical configuration requires exactly `dev,unified-demo`, `PAYMENT_UNIFIED_DEMO_ENABLED=true`, `SERVER_ADDRESS=127.0.0.1`, an explicit loopback CORS origin, and this URL grammar (a numeric port and Boolean TLS settings):

```text
jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true
```

IPv6 `[::1]` is also accepted. Named instances, hostname resolution, URL aliases/escaping, duplicate properties and extra JDBC parameters are deliberately unsupported. Configure credentials through the normal datasource properties, never inside the JDBC URL. Alternate Hikari/JNDI/Flyway connections, migration locations, unsafe Flyway options, Hibernate writes, other profiles, legacy simulation flags and attendance backfill are rejected before database singleton initialization. A separate management listener and forwarded-header strategy are forbidden.

`PAYMENT_UNIFIED_DEMO_MEMBER_IDS` must list reviewed positive Member IDs with no duplicates. Each confirming account must also be ACTIVE, have role MEMBER and an email in `@unified-demo.invalid`. Provisioning identities or enabling this mode on an existing database requires separate operational authorization and a restored backup rehearsal. There is no automatic canonical seed. Ordinary members cannot confirm a simulation. Only owned pending BANK_TRANSFER purchases with clean settlement provenance can settle; card upgrades are excluded. Authoritative amount and ownership come from the database.

The unified profile also clears Flyway's default future-migration ignore pattern. Any attempt to restore a nonempty ignore list is rejected, so unknown future history cannot silently pass startup validation.

The existing frontend `VITE_QR_PAYMENT_DEMO=true` and capability version 1 `LOCAL_DEMO` contract are retained. The server decides whether confirmation is available. QR contains only a non-payable synthetic reference, scanning cannot settle, and no real bank callback is introduced. Disable `PAYMENT_UNIFIED_DEMO_ENABLED` and restart to remove the capability.

Test mode additionally requires `unified-demo-test`, `PAYMENT_UNIFIED_DEMO_TEST_ENABLED=true`, and `sportify_unify_test_YYYYMMDDHHMMSS[_suffix]`. The test pair never accepts `sportify`. The optional `demo` seed profile is allowed only in this disposable mode. SQL tests create new databases, reject name reuse, and leave them for inspection. CI uses SQL Server containers; local verification requires an explicit loopback test port and credentials. No protected application database is a test target.

History remains forward-only through V40. V37, V39 and V40 are unchanged; the separate unmerged demo-photo V38 is excluded. Installed V38 or unexpected checksums are blockers, never repaired or renumbered. See the reconciliation and operator guides for the read-only preflight and cutover boundary.
