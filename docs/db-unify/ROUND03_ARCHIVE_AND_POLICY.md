# Round 03 - archive contract and release policy

CODEOWNER fast-track scope update: this release is RUNTIME DATABASE UNIFICATION.
Historical migration/import/archive/API work is OUT OF SCOPE. Historical data
unification remains INCOMPLETE and is not a runtime release acceptance criterion.
Preserve all legacy databases and records. Protected-target access/activation is
not authorized. Use [current runtime configuration and checklist](RUNTIME_STARTUP_POWERSHELL.md).
Earlier-round historical proposals below are context, not current work instructions.

PROPOSED - PENDING CODEOWNER REVIEW. Historical data unification is INCOMPLETE.
Option B is an engineering recommendation, not approval. No legacy source is
retired, imported, exposed by the application or classified as disposable.

## Replay lifecycle contract

Historical Round 03 behavior below is superseded by Round 04's compatible
[persisted-settlement contract](ROUND04_REPLAY_AND_ARCHIVE_DESIGN.md). It described
the reviewed implementation; requiring ACTIVE on every retry was a regression.

HTTP 200 from unified demo-confirm acknowledges a currently ACTIVE entitlement.
A card must be ACTIVE; a package must be ACTIVE with persisted activated_at.
PENDING_PAYMENT, CANCELLED, EXPIRED, REPLACED cards and REFUNDED packages are
not valid unified confirmation replays. Exhausted credits alone do not invalidate
an ACTIVE package. Dates are not rewritten on replay. Ordinary historical receipt
GET remains available after expiration/cancellation; this stricter confirmation
contract does not erase or reactivate historical benefits. No SQL constraint
currently guarantees payment SUCCESS implies ACTIVE entitlement. Disposable
SQL/HTTP tests construct the inconsistent states without disabling constraints
and verify repeated 404 responses with payment, entitlement, session, transaction,
invoice/line and confirmation audit snapshots unchanged. Rate-limit accounting
still applies; it is not historical payment repair.

## Account-wide quarantine - acceptance and recovery

The always-on policy also affects a legitimate paid member who later acquires
any persisted simulated payment/transaction. That member cannot create operational
bookings, check-in or training writes, including staff-assisted writes, even after
the simulator is disabled or the email changes. Real paid entitlement balances,
payments, invoices and receipts are preserved. Other paid members without that
history retain normal booking/cancellation. The real SQL/HTTP mixed fixture now
proves the transition and the disabled-feature behavior, including receipt access.

This restrictive impact requires explicit RELEASE_POLICY_APPROVAL before rollout,
including a rollout with unified-demo disabled. No exemption, provenance deletion,
email rename or feature-toggle workaround is an accepted recovery mechanism.
There is no implemented path to restore booking access on the same quarantined
identity. Existing cancellation rules can release a historical seat without
erasing history; existing real-payment refund rules remain separate commercial
remedies, not identity reclassification.

Proposed non-destructive owner process: preserve the incident snapshot and all
simulated provenance; verify legitimate paid contracts and remaining balances;
obtain exact-target authorization for any operational action. CODEOWNER may
commission an audited entitlement-scoped policy with positive real-payment proof,
or a verified new identity and an explicitly approved real-entitlement transfer
and historical identity binding. Neither is implemented or approved here. Any
future recovery must prove no double credits, capacity changes, financial delta,
audit loss or access to another identity's history. Do not issue manual SQL
instructions to bypass the current guard.

## Executable archive prototype - deliberately limited

HistoricalArchiveContractIntegrationTest creates two new fictional source
databases and a new canonical-fixture target. A private test-only writer imports
the fictional fixture_history contract into archive_fixture. There is no runtime
bean, production migration, CLI import entry point, route or adapter accepting
real databases. Its source/target objects are owned disposable database records.
The test cannot establish any actual source's schema or historical coverage.

The prototype demonstrates source namespace plus original table/kind and ID;
distinct target IDs and mapped parent/owner FKs; repeated batches and rows without
provenance rewrites; immutable snapshot/row hashes and first-batch origin; abort
on changed hash, changed namespace origin, missing parent, receipt/alias collision
or injected mid-batch failure; retained old/current aliases; source-scoped reader
projection and a native SQL SELECT-only fixture user. Canonical payments,
invoices, actual paid/cash views, refunds, identities, bookings, attendance, aliases
and audit counts remain unchanged. Archived amounts are never recaptured as cash.

The reader test receives a trusted fictional source principal. Production login,
source-to-live-identity binding, staff RBAC and HTTP/UI authorization do NOT exist
in this prototype. Fixture payloads are fictional JSON, not schema adapters or a
lossless exporter for unknown historical tables. Concurrency/restart-scale batch
recovery and independent real-snapshot reconciliation remain follow-up work.
The archive fixture schema is intentionally not part of the canonical schema
fingerprint or approved Flyway history. It must not be copied into a live target
or accommodated by weakening preflight.

## Source and implementation gaps

| Requirement | sportify | sportify_demo | sportify_qr_demo | Required closure |
| --- | --- | --- | --- | --- |
| Authorized inventory, version/checksum, schema, rows and owner | UNKNOWN | UNKNOWN | UNKNOWN | Separately authorized read-only inventory and preserved snapshot |
| Identity and role binding | UNKNOWN | UNKNOWN | UNKNOWN | Approved source namespace, immutable origin fingerprint and explicit live identity binding |
| Booking, attendance, results, training and discussions | Existing target UNKNOWN | Legacy coverage ZERO | Legacy coverage ZERO | Per-version adapters, parent/owner FK maps, timestamps and author preservation |
| Session IDs, aliases, counters and tombstones | UNKNOWN | UNKNOWN | UNKNOWN | Archive-only original codes; never assign an archive code to live sessions/counters |
| Payment, invoice, refund, audit and cash histories | UNKNOWN | Legacy coverage ZERO | Legacy coverage ZERO | Namespace receipt keys, immutable provenance, separate historical totals and zero live financial delta |
| Production exporter/importer and batch ledger | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED | Approved schema contract, disposable snapshot rehearsal and independent reconciliation |
| Historical read API/UI and authentication/RBAC | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED | Approved identity binding, own-history/staff scopes and real HTTP negative tests |
| Backup, restored clone and actual cutover | NOT RUN | NOT RUN | NOT RUN | Exact-target authorization, CHECKSUM/VERIFYONLY/actual new clone, before/after comparison |

## Scoped follow-up implementation plan

1. Obtain separate read-only inventory and backup/clone authorization for each
   exact instance/database. Collect every table/column, migration checksum,
   constraint, row count, ownership domain and reference collision from preserved
   snapshots. Sources remain online/intact until a separately approved retirement.
2. CODEOWNER chooses historical retention/access scope and accepts or rejects B.
   Approve namespace keys, source fingerprint, source-schema versions, historical
   identity bindings, privacy/retention, receipt collisions and staff read scopes.
3. Implement versioned read-only exporters against approved disposable restored
   snapshots. Canonical row hashing must specify decimal/date/binary/null formats,
   original IDs, all columns, and stable ordering. Abort on unknown/unclassified
   tables, changed source fingerprints or inconsistent ownership/financial links.
4. Implement an approved archive schema and immutable batch/ID map in disposable
   targets only. Stage, validate and atomically publish batches; serialize namespace
   writers, retain failure receipts, and test restart/concurrency/idempotency.
   Do not call live payment/entitlement/cash writers or rewrite original aliases.
5. Implement read-only scoped API/UI and audited source-to-live identity bindings.
   Never infer identity from equal numeric IDs or email. Test unauthenticated,
   cross-source, wrong-member and staff access with actual HTTP authentication.
6. Independently reconcile every source row/hash, FK, ownership, invoice/reference,
   session alias, timestamp, credit and historical financial aggregate. Prove zero
   operational/actual-finance delta, complete historical accessibility and native
   backup/restore equivalence on disposable rehearsals. Review any new migration
   and fingerprint contract explicitly; preserve all applied scripts.
7. Only after separate operational authorization, rehearse the exact real target's
   backup and restored clone. Approve cutover and retention independently. Rollback
   disables archive access and retains immutable batches; any restore is separately
   authorized. No automatic source deletion, archive deletion or ledger rewriting.

## Status vocabulary

DEVELOPMENT_VALIDATION is tied to exact-HEAD evidence, never inferred from this
design. HISTORICAL_DATA_UNIFICATION=INCOMPLETE;
PROTECTED_TARGET_PREFLIGHT=NOT_RUN_UNAUTHORIZED; LIVE_CUTOVER=BLOCKED_FOR_CODEOWNER;
RELEASE_POLICY_APPROVAL=PENDING_CODEOWNER. status.json provides the same machine
gate and does not claim a production importer exists.
