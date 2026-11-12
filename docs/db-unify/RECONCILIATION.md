# Read-only reconciliation and recovery

CODEOWNER fast-track scope update: this release is RUNTIME DATABASE UNIFICATION.
Historical migration/import/archive/API work is OUT OF SCOPE. Historical data
unification remains INCOMPLETE and is not a runtime release acceptance criterion.
Preserve all legacy databases and records. Protected-target access/activation is
not authorized. Use [current runtime configuration and checklist](RUNTIME_STARTUP_POWERSHELL.md).
Earlier-round historical proposals below are context, not current work instructions.

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

`UnifyPreflight` validates the repository Flyway catalog without migration, requires V48 with no pending/unknown/checksum-mismatched history, and compares schema metadata against the fresh canonical fingerprint. It rejects foreign-key/check-constraint drift, invoice ownership/amount/duplicate/missing-receipt anomalies, inconsistent simulated provenance, absent entitlement associations, simulated cash and current session identity mismatches. The output contains aggregate counts, diagnostic table checksums and no personal rows or credentials. CHECKSUM_AGG is only a diagnostic and cannot prove a backup or collision-free equivalence. Execute against a quiescent snapshot for stable comparison.

The schema fingerprint covers columns, FK topology, checks/defaults, indexes, triggers and views. Differences require investigation, never automatic repair. The optional demo history is accepted only on timestamped disposable fixtures. Canonical preflight rejects installed V28/V29 missing from its catalog as well as V38; do not broaden runtime Flyway locations to force compatibility.

Round 02 corrects fingerprint reproducibility: SQL Server-generated PK/UQ names
are represented by constraint type and their complete ordered column signature.
Explicit names, index type, uniqueness, disabled state, key/include columns,
directions and filters remain checked. Module CRLF/LF checkout differences are
normalized. Two independently migrated fresh canonical databases must match the
committed fingerprint; a new named index or table must still fail preflight.
Compare hashes only with their generating source SHA. Updating this repository
fingerprint from fresh fixtures does not change any database or Flyway history.
Format `DB_UNIFY_SCHEMA_V2` includes database/column collation and column position,
sorts normalized metadata independently of server catalog ordering, and uses
`Latin1_General_CI_AS` on newly created disposable canonical reference databases.
A protected target's collation remains UNKNOWN until authorized inspection. A
different target collation is a review blocker; never alter it merely to pass.

Example on a newly created fixture after explicit credential setup:

```powershell
$env:UNIFY_PREFLIGHT_DB_USER = '<test-login>'
$env:UNIFY_PREFLIGHT_DB_PASSWORD = '<test-secret>'
./scripts/db-unify/Invoke-UnifyPreflight.ps1 -Database '<sportify_unify_test_TIMESTAMP_suffix>' -OutputFile '<new-evidence-path.json>'
```

No protected database is read unless the operator supplies `-ProtectedReadAuthorized` after target-specific authorization. The script never imports, migrates, drops, repairs or edits history. A successful report still says `liveCutover=BLOCKED_FOR_CODEOWNER` and `proposedImports=0`.

SQL integration fixtures cover fresh/repeated V46 validation, pre-V39/V46 upgrade preserving numeric references and checksums, legitimately installed incompatible fixture V38, duplicate invoices, FK orphan rejection, session identity collision, injected transaction rollback, and native disposable backup/VERIFYONLY/actual restore. Test V38 contains only an independent SELECT fixture; it is not imported PR185/193 code. New fixture databases and backups are retained for inspection.

For a real cutover, require exact target fingerprint, recent native backup WITH CHECKSUM, VERIFYONLY and actual restored-clone rehearsal. Compare restored schema, migration catalog, counts, financial aggregates, receipt ownership and historical aliases. Restore tests on synthetic empty databases do not certify a user's backup. Recovery uses an approved backup and coordinated application artifact; never clean, repair, baseline, outOfOrder, history edits, row deletion or ledger rewriting.

Release V46 addendum: the expected fingerprint was obtained from two independently created owned SQL Server fixtures containing the five additive release migrations. An old V40 database is now rejected as V46_REQUIRED without changing its history. This updates the read-only tool contract only; protected-source coverage remains UNKNOWN and live cutover remains BLOCKED_FOR_CODEOWNER. Historical round reports describing V40 remain historical evidence.

Release V47 and V48 addendum: identity V47 (user_refresh_token, PR 235) and catalog V48 (Flow 5, PR 234: member_sport_level and saved_recommendation) are additive and change no existing tables or rows. PR 234 first used V47 and was renumbered to V48 after PR 235 took V47 in the same Flyway history. The pin moves to V48 and the expected fingerprint is the value computed by the CI Testcontainers SQL Server 2022 fixture. A V47 database is now rejected as V48_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V49 addendum: catalog V49 (MW-02, PR 251 stacked into PR 250: member_health_assessment) is additive and changes no existing tables or rows. The pin moves to V49 and the expected fingerprint is the value computed by the CI Testcontainers SQL Server 2022 fixture. A V48 database is now rejected as V49_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V50 addendum: catalog V50 (MW-03, PR 253: member_wallet and wallet_ledger_entry) is additive and changes no existing tables or rows. The pin moves to V50 and the expected fingerprint is the value computed by the CI Testcontainers SQL Server 2022 fixture. A V49 database is now rejected as V50_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V51 addendum: catalog V51 (MW-04, PR 255: wallet top-up requests) is additive and changes no existing tables or rows. The pin moves to V51 and the expected fingerprint is the value computed by the CI Testcontainers SQL Server 2022 fixture. A V50 database is now rejected as V51_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V52 addendum: catalog V52 (MW-05, PR 258: WALLET settlement method and payment_wallet_settlement) extends ck_payment_method with WALLET and adds one table; it changes no existing rows. The pin moves to V52 and the expected fingerprint is the value computed by UnifyReconciliationIntegrationTest on a disposable SQL Server fixture, whose V51 result matched the CI value. A V51 database is now rejected as V52_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V53 addendum: catalog V53 (MW-06, PR 259: member_payout_account and wallet_withdrawal) is additive and changes no existing tables or rows. The pin moves to V53 and the expected fingerprint is the value computed by UnifyReconciliationIntegrationTest on a disposable SQL Server fixture, whose V51 result matched the CI value. A V52 database is now rejected as V53_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V54 addendum: catalog V54 (PR 317: operational_qr_settlement) is additive and changes no existing tables or rows. The pin moves to V54 and the expected fingerprint is the value computed by UnifyReconciliationIntegrationTest on disposable SQL Server fixtures in CI. A V53 database is now rejected as V54_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V55/V56 addendum: catalog V55 adds optional VietQR gateway fields and unique indexes for provider order and transaction identifiers. V56 extends the payment provenance constraint to accept VietQR sandbox settlement records while preserving the existing simulator and actual-settlement invariants. The pin moves to V56 and the expected fingerprint is computed by UnifyReconciliationIntegrationTest on disposable SQL Server fixtures in CI. A V55 database is now rejected as V56_REQUIRED without changing its history. This updates the read-only tool contract only and requires CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.

Release V57 addendum: catalog V57 extends the payment provenance constraint to accept local VietQR demo settlement records in the sandbox. The pin moves to V57 and the expected fingerprint is computed by UnifyReconciliationIntegrationTest on disposable SQL Server fixtures in CI. A V56 database is rejected as V57_REQUIRED without changing its history. The read-only tool contract remains subject to CODEOWNER approval; live cutover remains BLOCKED_FOR_CODEOWNER.
