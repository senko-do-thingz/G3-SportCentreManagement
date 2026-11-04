# Runtime database unification

The CODEOWNER fast-track decision narrows this release to RUNTIME DATABASE
UNIFICATION: Flow 1-3, Flow 4+ and QR Local Demo share one SQL Server database
named sportify. Historical migration is OUT OF SCOPE. No production historical
importer, archive, historical API or additional prototype is part of this release.
Preserve sportify_demo, sportify_qr_demo and every legacy record unchanged.
Historical data unification remains INCOMPLETE; it is not the runtime acceptance
criterion. Earlier archive proposals are retained as prior-round context only.

See [exact configuration, PowerShell startup and checklist](RUNTIME_STARTUP_POWERSHELL.md),
[regression commands](REGRESSION.md), [review packet](REVIEW_PACKET.md) and
[machine-readable scope/gates](status.json). Protected databases were not accessed
or started by this work. Configuration and implementation are verified on
disposable databases; actual canonical activation requires separate authorization.

## One runtime datasource

| Module | Connection and transaction path |
| --- | --- |
| Flow 1-3 identity, roles and profiles | Shared JPA EntityManagerFactory and JpaTransactionManager |
| Flow 4+ catalog, scheduling, booking, training and payment | Same JPA context; injected JdbcTemplate/NamedParameterJdbcTemplate use the same datasource |
| Unified QR Local Demo | LocalDemoPaymentService and SqlQrStore reuse that datasource and transaction manager |
| Flyway | Boot's same datasource, identity/catalog migration locations only |

application.yml defines the single spring.datasource. application-unified-demo.yml
does not define another connection. The offline UnifyPreflight command is not a
Spring runtime bean or an importer. No historical test prototype is a runtime
component. UnifiedDemoDatabaseIntegrationTest asserts one datasource, one JPA
factory, one transaction manager, shared Flyway/JDBC wiring, the identity/catalog/
payment metamodel and matching JPA/JDBC DB_NAME() on a fresh disposable fixture.

Canonical local configuration requires exactly dev,unified-demo, explicit opt-in,
numeric loopback binding and an approved positive synthetic Member allowlist.
The canonical URL is:

```text
jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true
```

The unified guard rejects production/unknown/legacy profiles, demo seeds,
sportify_demo/sportify_qr_demo, alternate Hikari/JNDI/Flyway connections, unsafe
Flyway options, Hibernate writes, forwarded/separate listeners and attendance
backfill before database initialization. Test mode has separate explicit controls
and accepts only new sportify_unify_test_TIMESTAMP names, never sportify.
Production security and the original LocalDemoSafetyGuard are unchanged.

## Synthetic identities and policy impact

Use dedicated ACTIVE MEMBER accounts with member profiles, emails ending in
@unified-demo.invalid and reviewed IDs in PAYMENT_UNIFIED_DEMO_MEMBER_IDS.
Do not repurpose a real Member or staff identity. No automatic canonical seed or
provisioning is supplied. Existing owned pending positive BANK_TRANSFER card or
package purchases can simulate; initial card upgrades are excluded. Scanning
cannot settle. SIMULATED/SPORTIFY_SIMULATOR receipts and audit provenance persist;
actual revenue, cash events and real-money refunds exclude simulations.

Always-on account-wide quarantine prevents synthetic operations from affecting
real class capacity, attendance, results and training writes. It also blocks a
legitimate Member's separately paid operational benefits if that account has any
simulated history, even after disabling simulation or renaming its email. Own
receipt reads and cancellation remain available; paid history/credits are not
rewritten. No exemption, transfer or automatic recovery exists. This side effect
remains PENDING CODEOWNER POLICY ACCEPTANCE before rollout; the scope decision
does not approve it. Dedicated identities prevent creating new mixed accounts.

Historical replay returns the original version 1 settlement after verified normal
expiry or linked replacement without reactivation. Complete payment/transaction/
session/invoice/line/audit consistency remains required. Existing date evidence
is not a cryptographic lifecycle-event audit. See the retained Round 04 replay
section for that limit; its archive proposal is outside this release.

## Migration and operational boundary

Flyway validation and Hibernate validate remain enabled; clean is disabled,
baseline/out-of-order are false and future migration ignore patterns are empty.
Applied migrations and their checksums remain immutable through V40. Unknown V38,
future history, checksum or schema drift must fail closed. Never repair, clean,
baseline, renumber or rewrite history. Disposable migration/schema/backup tests
do not certify any protected target. No merge, deployment, protected connection,
legacy modification/deletion or live cutover is performed by this work.
