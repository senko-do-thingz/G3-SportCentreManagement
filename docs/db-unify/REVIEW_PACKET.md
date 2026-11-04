# CODEOWNER review packet

Round 04: see [replay compatibility, mixed-identity policy and archive mapping v0.1](ROUND04_REPLAY_AND_ARCHIVE_DESIGN.md)
and [machine-readable gates](status.json). Unified SUCCESS replay returns the
persisted settlement after verified expiry or replacement without reactivation.
The executable archive contract
uses fictional disposable schemas only; production migration, historical API and
real-source reconciliation remain unimplemented. Historical data unification is
INCOMPLETE and account-wide quarantine acceptance remains PENDING CODEOWNER.

Round 02 status: runtime consolidation is implemented; historical data unification
is INCOMPLETE. Zero imports do not meet a requirement that all legacy histories be
available in sportify. Archive-only disposition is proposed and unapproved. See
[SOURCE_HISTORY_DECISION.md](SOURCE_HISTORY_DECISION.md) for per-source/table gaps,
retention/import options, conditional mapping design and the explicit owner gate.
Target-specific read, backup, restored-clone rehearsal and cutover remain
BLOCKED_FOR_CODEOWNER, independently of development CI results.

Synthetic operations use an always-on default-deny policy: reserved Member IDs,
reserved email namespace or any persisted simulator payment/transaction provenance
quarantine the account from new bookings, check-ins, attendance, corrections,
results, plans, feedback and class discussion writes. This also applies with demo
profiles disabled and after email renaming. Mixed real/synthetic accounts remain
quarantined; ordinary real members remain eligible. Cancellation and historical
receipt reads stay available. No operational demo class namespace is approved.
There is no exception switch. CODEOWNER must accept this operational policy before
canonical activation. Unified SUCCESS replay validates BANK_TRANSFER, complete
transaction/session/receipt/item/audit linkage; malformed histories are denied
without repair. Legacy QR guard and all applied migrations remain unchanged.

This is a proposed isolated development candidate, not an approved architecture or live deployment. The master work authorization permits reversible development and disposable testing through Tasks01-06; operational target changes remain blocked.

Read [architecture and configuration](README.md), [migration/checksum compatibility](MIGRATION_COMPATIBILITY.md), [read-only reconciliation](RECONCILIATION.md), [regression commands](REGRESSION.md) and [operator cutover and rollback](OPERATOR_CUTOVER_POWERSHELL.md). All six private task reports have Round 04 updates under ignored tasks/DB-UNIFY (i)/NN/reports/round-004 directories. Earlier rounds remain preserved. The single consolidated FULL_CODEOWNER_HANDOFF.md is under Task06/reports/round-004. Private instructions, AI audit records, raw logs, screenshots and database backups are not committed.

Task03 adds a distinct fail-closed unified-demo profile and synthetic Member restrictions while retaining the original local-demo guard. Task04 adds read-only preflight, an unaccepted zero-import proposal, disposable upgrades/negative fixtures and native backup/restore proof. Task05 adds shared real HTTP/SQL contract coverage, explicit safe local test fixtures, mandatory CI suites and a real browser/restart harness. Task06 supplies the cutover/recovery/retention boundary and final evidence packet.

No historical SQL migration changed; V38 was not imported. No second runtime datasource or QR database was added. Production never enables the new capability. Ordinary members and unsupported methods cannot confirm synthetic payments; actual cash/revenue/refund boundaries remain enforced. No legacy row is selected for import.

Before approval, inspect the exact PR HEAD checks and final local handoff for actual pass/fail/skip totals, failed attempts and unrun gates. Full CI and browser results must be from that commit. Restore proof on empty synthetic fixtures does not certify an existing user database or authorize canonical activation. Physical JDBC connection abort and real Manager browser rescheduling are included in the final regression selectors; their actual results, along with remaining manual acceptance, are recorded in the handoff. No agent waiver applies.

CODEOWNER decisions: review the distinct unified-demo guard and always-on synthetic quarantine; approve synthetic Member identity/provisioning policy; choose an explicit historical scope reduction versus a required namespace-aware history import and its retention/access policy; authorize any exact local target only after target-specific preflight and native backup/restore rehearsal. Request Create a merge commit only if later approved. Preserve the branch. No self-approval, merge, deployment or branch deletion is performed.
