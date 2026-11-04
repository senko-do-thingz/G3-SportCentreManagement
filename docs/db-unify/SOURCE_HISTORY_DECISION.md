# Historical data coverage and CODEOWNER decision

PROPOSED - PENDING CODEOWNER FINAL REVIEW. Data unification is NOT COMPLETE.

One runtime datasource is implemented and tested. Zero imported rows means zero
historical coverage from a separate source; it is not proof that all histories
are available inside `sportify`. No approval to discard or archive source records
is inferred. The previous archive-only proposal remains unaccepted.

## Per-source inventory status

| Source | Actual Flyway/catalog/rows | Current accessibility in canonical runtime | Required next evidence |
| --- | --- | --- | --- |
| sportify | UNKNOWN; no target-specific read authorization | Existing canonical rows only, subject to actual preflight | Authorized read-only inventory, backup, restored clone |
| sportify_demo | UNKNOWN; not inspected | No imported or federated legacy rows | Authorized table/history inventory and retention decision |
| sportify_qr_demo | UNKNOWN; not inspected | No imported or federated legacy rows | Authorized table/history inventory and retention decision |

`LEGACY_SOURCE_INVENTORY.sql` supplies read-only database identity, complete table
metadata/estimated counts and installed migration checksums for each separately
authorized source. Its absence-of-table results are material gaps. Metadata row
counts are estimates, not exact reconciliation. No query in that script has been
run against these protected names. Actual values must remain UNKNOWN until an
authorized operator produces evidence. Disposable fixture results cannot fill
these cells.

## Table-level coverage and proposed treatment

For EVERY row below, each of the three source databases has UNKNOWN actual
presence/count/checksum. Canonical access covers only rows already in sportify;
legacy-source coverage is ZERO. None of the proposed dispositions is approved.

| Domain | Tables requiring source-specific reconciliation | Proposed disposition / import dependency |
| --- | --- | --- |
| Identity and roles | role, user_account, member_profile, coach_profile, coach_sport, user_avatar | Preserve source identities; never merge equal numeric IDs or email alone; roles need explicit grants |
| Catalog and entitlements | sport, facility, sport_class, sport_package, membership_card_tier, member_card, sport_package_registration | Map catalog keys and synthetic origin; no synthetic entitlement usable in live classes |
| Bookings and waitlist | booking, waitlist_entry | Map member, session and registration first; validate status, capacity and credits; no operational replay by default |
| Attendance and results | check_in, attendance_record, attendance_correction, session_result, session_result_score, skill_metric | Preserve author, booking/session linkage, original time and corrections; historical/read-only namespace proposed |
| Training history | session_plan, session_plan_step, coach_feedback, support_request, support_request_message | Preserve ownership, coach assignment, conversation and operation receipts |
| Sessions and aliases | class_session, session_code_history, session_code_counter (including revision fields) | Never overwrite numeric target IDs, reuse retired codes or advance counters by copied values; preserve aliases in source namespace |
| Simulated settlement | payment, simulated_qr_session, simulated_qr_transition, simulated_payment_transaction, simulated_qr_rate_limit | Preserve immutable transaction/session provenance; archive rate limits rather than revive sessions; always SIMULATED |
| Receipts and refunds | invoice, invoice_line, refund_request | Preserve source receipt numbers under namespace, ownership/amount/item links; never permit simulated refund payout |
| Audit and cash | activity_log, cash_event, cash_coverage, cash_capture_ticket | Immutable historical archive; never recapture imported payments, reuse capture tickets or claim synthetic amounts as actual cash |
| Other discovered tables | Every additional table emitted by inventory | Explicit owner disposition required; an unclassified table blocks import completeness |

Names above describe the current repository schema; older sources may differ.
Inventory must record missing/renamed columns and tables before mapping. This is
an inventory checklist and gap matrix, not a claim about protected source contents.

## Decision and recovery matrix

| Option | Meets all historical data in sportify? | Preconditions | Rollback / retention |
| --- | --- | --- | --- |
| A: Accept fresh-runtime scope with external archives | NO; explicit scope reduction required | CODEOWNER accepts zero legacy availability and signs per-source retention/access ownership | Keep original sources and verified backups; revert application artifact if needed |
| B: Historical archive namespace inside sportify (recommended if single-DB history required) | Potentially, after implementation and reconciliation | Authorized snapshots, approved mapping/retention design, disposable importer tests, historical UI/API acceptance | Keep sources/backups; disable archive reader; retain immutable import batches, no destructive undo |
| C: Merge legacy rows into live operational tables | Not currently implemented or recommended | Explicit domain-by-domain authority, identity/credit/cash reconciliation, operational isolation and restore rehearsal | Coordinated backup/application restore; no ledger rewriting or row-delete rollback |

CODEOWNER must choose A with an explicit scope change, or require B/C before
calling data unification complete. Until then: DATA_UNIFICATION=INCOMPLETE,
IMPORT_EXECUTION=DISABLED, TARGET_READ/BACKUP/CLONE/CUTOVER=BLOCKED.

## Conditional importer design (not a write-enabled importer)

If historical import is required, use approved disposable snapshots as sources
and a new disposable target first. Keys are `(source_database, source_schema,
source_table, original_id)` with an immutable source fingerprint, batch ID,
target ID and row digest. Equal IDs across two sources must allocate distinct
archive identities. A repeated source fingerprint/digest must be a no-op; a
changed digest, duplicate receipt, unresolved FK, role escalation, alias collision,
or amount/provenance mismatch must abort the whole batch. Preserve original
timestamps, authors and source receipt references. Import audit/ledger histories
as historical records, never through live payment confirmation or cash writers.

Required tests before any such implementation can be accepted: two sources with
overlapping IDs, identical and changed replay batches, missing parents, renamed
schemas, historic session aliases, duplicate receipts, rollback on injected
failure, synthetic entitlement isolation, finance/refund exclusion, and backup /
restore equivalence. No data-moving command is provided while that design and
source authority remain undecided. This is an explicit unmet workstream gate.
