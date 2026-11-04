# Round 04 - replay compatibility and historical archive design v0.1

CODEOWNER fast-track scope update: this release is RUNTIME DATABASE UNIFICATION.
Historical migration/import/archive/API work is OUT OF SCOPE. Historical data
unification remains INCOMPLETE and is not a runtime release acceptance criterion.
Preserve all legacy databases and records. Protected-target access/activation is
not authorized. Use [current runtime configuration and checklist](RUNTIME_STARTUP_POWERSHELL.md).
Earlier-round historical proposals below are context, not current work instructions.

PROPOSED - PENDING CODEOWNER REVIEW. Historical data unification is INCOMPLETE.
No protected source was accessed, imported, modified or retired. Actual schemas,
row populations and migration histories for sportify, sportify_demo and
sportify_qr_demo remain UNKNOWN. This document advances an approval proposal;
it is not an executable production importer or an approval to run one.

## Persisted settlement and current entitlement

The version 1 demo-confirm response remains the original persisted payment,
transaction reference and invoice. SUCCESS describes settlement, independently of
current benefit availability. Current states come from the existing owner-scoped
membership-cards/my and packages/registrations/my APIs. No new response field or
contract version is required. Historical receipt reads remain available.

All accepted unified replays still require the same owned BANK_TRANSFER SUCCESS
payment, SIMULATED/SPORTIFY_SIMULATOR provenance, exactly one matching SUCCEEDED
transaction, settled inactive session, PAID invoice and single correctly linked
line, and exactly one consistent PAYMENT_CONFIRMED audit. Owner, amount, currency,
reference, invoice and item linkage are checked. The read-only proof does not
rewrite any historical record. Rate accounting remains separate from settlement.

| Current item | Additional evidence | Replay |
| --- | --- | --- |
| ACTIVE card | Unique paid invoice line references this owned card | Original SUCCESS |
| ACTIVE package | Same linkage and non-null activated_at | Original SUCCESS |
| EXPIRED card | Ordered start/end and maintenance updated_at date after end_date | Original SUCCESS; remains EXPIRED |
| EXPIRED package | Same date proof, non-null activated_at and updated_at >= activated_at | Original SUCCESS; remains EXPIRED |
| REPLACED card | Linked different successor, same owner and end_date, upgrade_request, supported successor state; zero-fee upgrade or successful payment and PAID invoice matching successor price | Original SUCCESS; remains REPLACED |
| PENDING_PAYMENT or CANCELLED | Contradicts this supported successful activation lifecycle | Deny without repair |
| Arbitrary EXPIRED or REPLACED | Future/missing term or missing successor/settlement evidence | Deny without repair |
| Package REFUNDED or missing activation | Not an authorized simulated transition | Deny without repair |
| Actual payment, including completed real refund | No simulated settlement proof | Deny simulation; keep real history |

These are consistency checks against existing persisted business evidence, not a
cryptographic event ledger. They cannot prove that a privileged database writer
never forged several matching records. No such stronger claim is made. The test
suite retains all nine inconsistent item fault cases from Round 03, including
fabricated EXPIRED and orphan REPLACED states, alongside normal ACTIVE replay,
concurrency, audit/amount/reference faults, rollback and safety coverage.

UnifiedReplayLifecycleIntegrationTest creates an independent owned SQL database
and new member identities. Purchases and initial settlement use real HTTP.
Expiration invokes the production ScheduledMaintenanceService and repositories
with an injected future business clock inside a transaction. It does not rewrite
paid dates, SQL Server time or system time. Paid card upgrade uses the official
Reception purchase API. Each transition then repeats confirmation twice and
compares the original response, full payment/invoice/line/session/transaction/
payment-audit snapshots, entitlement row and global finance counts/totals.
Replacement successor rows are also compared. No duplicate invoice, transaction,
confirmation audit, cash event or actual revenue is allowed on replay.

Refunds are traced separately: quote/create reject simulated packages with
SIMULATED_PAYMENT_NOT_REFUNDABLE before any refund. A different actual purchase
goes through staff confirmation, member refund request, manager approval and
staff completion over HTTP; its REFUNDED payment/invoice remain actual and cannot
be acknowledged as a simulated settlement. No simulated refund path is added.

## F-01 - real-paid mixed identity policy decision matrix

The default-safe account-wide operational quarantine remains unchanged. It
persists with simulation disabled and after email renaming. Actual paid benefits
and money are preserved, but their owner cannot make new operational writes if
any canonical simulated provenance exists. Cancellation and own receipt reads
remain available. Round 03's real SQL/HTTP paid-member/control test remains a
required regression; Round 04 does not infer policy acceptance from passing it.

| Owner decision | Current implementation | Consequence and approval needed |
| --- | --- | --- |
| Accept account-wide quarantine | Implemented default | Explicit CODEOWNER policy acceptance before any rollout, even with simulator disabled; communicate paid-user impact |
| Limit operations by individual real entitlement provenance | Not implemented or approved | Design identity/class/result isolation and staff-write policy; prove no synthetic operational contamination before changing default |
| Audited transfer to another verified identity | Not implemented or approved | Verify ownership, account mapping, zero duplicate credits/finance and full immutable provenance; separate exact-target authorization |
| Same-account recovery/exemption | Not implemented or approved | Requires separate approved model and regression proof; no silent exemption, email rename or provenance removal |

No option was selected on CODEOWNER's behalf. RELEASE_POLICY_APPROVAL remains
PENDING_CODEOWNER. No production recovery or transfer action is supplied here.

## F-02 - source adapter mapping draft v0.1

This version is grounded in repository canonical table definitions through V40
(33 canonical migrations, with existing gaps), not inspected source databases.
LEGACY_SOURCE_INVENTORY.sql remains an approval-gated inventory template. The
fictional archive test validates a narrow contract and is not a real-table
exporter/importer. Source adapters must be versioned independently per namespace
and inspected schema/checksum fingerprint. Never assume the same table/column
shape, ID, member email or migration version across the three sources.

Proposed target is a separate read-only historical archive namespace in sportify,
not inserts into live business tables or revenue views. Original payloads and
namespaced references are authoritative; normalized projections are for reading.
Production archive DDL, adapters, binding/API/UI and operator entrypoints are NOT
IMPLEMENTED. Every row below is a proposed family adapter awaiting actual schema
mapping, field coverage review and owner approval.

| Adapter v0.1 / candidate source tables | Archive projection and immutable fields | Prerequisites |
| --- | --- | --- |
| Identity: user_account, role, member_profile | Source-local identity, role, member code and status; sensitive fields under approved access/retention policy | Namespace, snapshot, approved principal binding |
| Catalog: membership_card_tier, sport, sport_package, class/session and facilities | Original tier/package/type/price/duration/rank, catalog versions, source labels | Namespace, referenced catalog parents |
| Settlement: payment, simulated_payment_transaction, simulated_qr_session | Original IDs/codes, owner, amount/type/provider/method/status, session/reference/currency and every timestamp | Identity; circular links staged as a graph |
| Receipt: invoice, invoice_line | Original invoice number/status/amount/tax/discount, item reference/type, quantity and line totals | Payment, identity, product/item graph |
| Benefit: member_card, sport_package_registration | Original status, prices, credits, dates, activation/payment/upgrade/replaces links | Catalog, identity, payment graph |
| Aliases: every source alias table discovered by inventory | Current and retired alias, original target/type and validity times | Referenced namespaced entity; duplicate policy explicit |
| Operations: bookings, attendance, results, plans, feedback and discussions discovered in source | Original ownership, session relationships, status, credit mutations and timeline | Identity, benefit, class/session and referenced operation parents |
| Finance/audit: cash_event, refund_request, activity_log and any discovered ledger tables | Original real/simulated classification, refund/payment links, actor/entity details and timestamp precision | Payment/receipt/benefit/identity; preserve opaque details |
| Other source tables/columns | Explicit preserve/map/exclude decision with rationale and owner signoff | Complete inventory; unknown tables block completeness |

Candidate names are not assertions that protected sources contain these tables.
An actual adapter manifest must list every source table, every source column and
SQL type, null semantics, target column/payload path, transform version, PK/FK,
identity binding and test vectors. No omitted column is implicitly disposable.
Passwords/tokens/PII require explicit retention and access decisions; no secret
export is authorized by this draft. Original financial types and decimal scale
must survive projections rather than be coerced into current defaults.

## Dependency and migration matrix

| Phase | Depends on | Required gate and failure behavior |
| --- | --- | --- |
| 0. Read-only inventory and native preserved backup | Explicit authorization for each exact source/target | UNKNOWN until authorized; verify snapshot/restore without changing originals |
| 1. Adapter manifest and namespace registry | Source schema + migration fingerprints, approved snapshot | Immutable namespace -> source identity/snapshot; reject namespace rebinding |
| 2. Identity/catalog archive staging | Phase 1 and approved sensitive-data scope | Preserve overlapping IDs using (namespace, entity_type, original_id); never numeric/email merge |
| 3. Payment/receipt/benefit graph | Phase 2, row and edge manifests | Stage circular references, resolve all intra-source links, then validate; missing parents fail closed |
| 4. Operations/refund/cash/audit/aliases | Phase 3 and discovered dependencies | Preserve actor/time/history and retired aliases; no live operational or finance writes |
| 5. Immutable publish | All table/column coverage, hash/count/FK/type checks | Atomic batch publish; interrupted staging stays unreadable; identical retry no-op, conflicting retry rejected |
| 6. Owner-bound historical reads | Independently approved identity binding and read policy | Read-only projection; unauthorized/cross-source/live-ID guess denied |
| 7. Reconciliation and owner acceptance | Independent verifier, backup/restore test, UI/API coverage | No completeness claim until actual required sources/histories verified and accepted |

Target-side constraints must enforce namespace-scoped parents. Source originals
and archive mappings are immutable; corrections append superseding provenance,
never overwrite imported facts. Preserve the original source identifier even if
an internal surrogate is introduced. Existing applied Flyway files stay intact;
any eventual archive DDL needs a new approved migration, never checksum repair.

## Immutable verification design

Before implementation, freeze a schema-aware canonical encoding specification:
include namespace/table/PK, column names/types/order, null markers, length-prefixed
strings/binary, exact decimal unscaled value + scale, timestamp precision/offset,
and original financial classification. Do not hash concatenated ambiguous text,
rounded floats, inferred local dates or only selected display columns.

Hash each complete original row and sorted typed FK edge set with SHA-256; derive
per-table and snapshot digests from ordered row identities and hashes. Save
source schema/migration fingerprints, export manifest version, source snapshot ID
and hashes in an immutable batch manifest. A separately implemented read-only
verifier must reconstruct source and target representations, compare every row,
count, edge, alias and full digest, and report missing/extra/changed entries.
Importer-generated success flags alone are not independent reconciliation.

Verify source-source ID/code collisions and row classifications separately; no
collision may silently deduplicate financial or identity history. Report actual
vs simulated payment/refund/cash counts and exact amount sums by original source,
currency, method, type and status, plus invoice/line relationships and timestamp
precision. Compare canonical live payment/cash/revenue/operations before/after:
archive import and reads must have zero delta. Require identical batch replay,
new-batch identical source mapping, changed-row/hash rejection, namespace rebinding
rejection, foreign/missing parent rejection, transaction fault recovery, reader
write denial and backup/restore verification on an authorized disposable clone.

## Owner-scoped read-only API proposal - not implemented

Proposed GET /api/v1/historical-sources/{namespace}/receipts/{originalId} and
GET /api/v1/historical-sources/{namespace}/timeline would read only a published
archive batch through a SQL principal without DML privileges. Authentication
must map current principal -> reviewed source principal binding in that namespace.
Matching email or numeric ID is not binding evidence. Never accept a caller's
memberId as authority. Staff access requires a separate audited permission.
Return original source/type/status/time plus clearly labeled historical origin;
do not present archived simulation as actual revenue or active entitlement.
Invoice lines, current/retired aliases and related payment links stay in the
same namespace. Wrong owner, source, unpublished batch or missing binding fails
closed. No write, refund, confirmation or live-benefit activation route is
proposed. This is a design, not an available API or verified authentication layer.

## Explicit blockers and owner decisions

Actual source inventory/authorization, preserved backups, real table/column
manifests, retention and identity binding, production archive schema/importer,
owner-scoped API/UI, independent real-source reconciliation and restored-clone
rehearsal remain outstanding. Historical data unification is INCOMPLETE, actual
legacy imported rows remain zero, protected preflight is NOT_RUN_UNAUTHORIZED,
live cutover is BLOCKED_FOR_CODEOWNER and policy approval is PENDING_CODEOWNER.
Development validation can pass independently; it does not waive these gates.
