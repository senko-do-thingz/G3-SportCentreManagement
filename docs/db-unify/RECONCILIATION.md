# Read-only reconciliation and recovery

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

The proposed architecture moves zero legacy records. Canonical business data is authoritative; legacy demonstration users, settlements, invoices, entitlements and session aliases stay in their source database. Consequently no source-to-target ID map, import endpoint or write-enabled cutover command is supplied. A future import requires a separately approved namespace/key map and provenance policy.

`UnifyPreflight` validates the repository Flyway catalog without migration, requires V40 with no pending/unknown/checksum-mismatched history, and compares schema metadata against the fresh canonical fingerprint. It rejects foreign-key/check-constraint drift, invoice ownership/amount/duplicate/missing-receipt anomalies, inconsistent simulated provenance, absent entitlement associations, simulated cash and current session identity mismatches. The output contains aggregate counts, diagnostic table checksums and no personal rows or credentials. CHECKSUM_AGG is only a diagnostic and cannot prove a backup or collision-free equivalence. Execute against a quiescent snapshot for stable comparison.

The schema fingerprint covers columns, FK topology, checks/defaults, indexes, triggers and views. Differences require investigation, never automatic repair. The optional demo history is accepted only on timestamped disposable fixtures. Canonical preflight rejects installed V28/V29 missing from its catalog as well as V38; do not broaden runtime Flyway locations to force compatibility.

Example on a newly created fixture after explicit credential setup:

```powershell
$env:UNIFY_PREFLIGHT_DB_USER = '<test-login>'
$env:UNIFY_PREFLIGHT_DB_PASSWORD = '<test-secret>'
./scripts/db-unify/Invoke-UnifyPreflight.ps1 -Database '<sportify_unify_test_TIMESTAMP_suffix>' -OutputFile '<new-evidence-path.json>'
```

No protected database is read unless the operator supplies `-ProtectedReadAuthorized` after target-specific authorization. The script never imports, migrates, drops, repairs or edits history. A successful report still says `liveCutover=BLOCKED_FOR_CODEOWNER` and `proposedImports=0`.

SQL integration fixtures cover fresh/repeated V40 validation, pre-V39/V40 upgrade preserving numeric references and checksums, legitimately installed incompatible fixture V38, duplicate invoices, FK orphan rejection, session identity collision, injected transaction rollback, and native disposable backup/VERIFYONLY/actual restore. Test V38 contains only an independent SELECT fixture; it is not imported PR185/193 code. New fixture databases and backups are retained for inspection.

For a real cutover, require exact target fingerprint, recent native backup WITH CHECKSUM, VERIFYONLY and actual restored-clone rehearsal. Compare restored schema, migration catalog, counts, financial aggregates, receipt ownership and historical aliases. Restore tests on synthetic empty databases do not certify a user's backup. Recovery uses an approved backup and coordinated application artifact; never clean, repair, baseline, outOfOrder, history edits, row deletion or ledger rewriting.
