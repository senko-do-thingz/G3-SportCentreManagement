# NX-01 test cases

Report index (`NX-01-report-index.md` - local review reference, not published). Round 2. Audited origin/main `d20833c3c16e4f7d95d12b6df21798fc528bd559`.

Statuses distinguish fresh static/tooling/unit evidence from historical Round 1 runs and unexecuted downstream cases. A negative control PASS means the checker returned the expected failure (1) or explicit unsupported status (2); checker failure is not a failed negative test.


| ID | Purpose | Type | Priority | Result |
| --- | --- | --- | --- | --- |
| NX01-R2-TC-001 | Fresh remote-main and safe synchronization | static review | P0 | PASS |
| NX01-R2-TC-002 | Working tree and historical preservation | static review | P0 | PASS |
| NX01-R2-TC-003 | Qualified dbo table recognized | audit tooling unit | P0 | PASS |
| NX01-R2-TC-004 | Bracket and mixed qualification recognized | audit tooling unit | P0 | PASS |
| NX01-R2-TC-005 | Multiline table recognized | audit tooling unit | P0 | PASS |
| NX01-R2-TC-006 | SQL comments and strings do not count as DDL | audit tooling unit | P0 | PASS |
| NX01-R2-TC-007 | Omitted table is detected | audit tooling unit | P0 | PASS |
| NX01-R2-TC-008 | Omitted/new route is detected | audit tooling unit | P0 | PASS |
| NX01-R2-TC-009 | All migration files covered | static review | P0 | PASS |
| NX01-R2-TC-010 | Controller and mapping coverage | static review | P0 | PASS |
| NX01-R2-TC-011 | Exact official XLSM versus XLSX | source reconciliation | P0 | BLOCKED |
| NX01-R2-TC-012 | Canonical DOCX real and editable | artifact verification | P0 | PASS |
| NX01-R2-TC-013 | Review copy byte-identical | artifact verification | P0 | PASS |
| NX01-R2-TC-014 | Round 1 remains unchanged | static review | P0 | PASS |
| NX01-R2-TC-015 | Semantic evidence spot checks | static review | P0 | PASS |
| NX01-R2-TC-016 | NX-00 human sign-off attribution | human evidence review | P0 | BLOCKED |
| NX01-R2-TC-017 | Fresh safe Maven unit execution | unit/web slice | P0 | PASS |
| NX01-R2-TC-018 | SQL Server and full verify limits | integration | P0 | BLOCKED |
| NX01-R2-TC-019 | No unauthorized production or Git change | static review | P0 | PASS |
| NX01-R2-TC-020 | Complete package and links | artifact verification | P0 | PASS |
| NX01-R2-TC-021 | Original scanner blind spot reproduced | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-022 | Added qualified table detected | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-023 | Duplicate route record detected | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-024 | Alias and HTTP-method cross product | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-025 | Unsupported dynamic/custom/inherited forms | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-026 | Nested-comment limitation explicit | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-027 | Java fake annotations ignored | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-028 | Quoted identifiers and dialect qualification | audit tooling negative control | P0 | PASS |
| NX01-R2-TC-029 | Class/session/coach/facility and balance reuse | static review | P0 | PASS |
| NX01-R2-TC-030 | Flow 5 and Flow 6 absence and privacy boundary | static review | P0 | PASS |
| NX01-R2-TC-041 | Personal and class plan ownership | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-042 | Plan templates and versions | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-043 | Training results validity and attendance binding | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-044 | Result correction and retention | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-045 | Progress calculation and note visibility | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-046 | Coach session attendance and corrections | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-047 | Member attendance history isolation | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-048 | Member plan and comment isolation | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-049 | Durable self-training and no double debit | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-050 | Notifications and homework | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-051 | AI provider unavailable and fallback | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-052 | AI privacy and authorized input | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-053 | Recommendation grounding and structured output | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-054 | Human approval before promotion | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-055 | Member recommendation audience gate | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-056 | Assistant prompt injection | proposed future test | P0 | NOT_RUN |
| NX01-R2-TC-057 | Assistant authorized current member context | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-058 | Unsupported questions and missing context | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-059 | Assistant outage, rate/cost and retention | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-060 | Required flow end-to-end regression | proposed future test | P1 | NOT_RUN |
| NX01-R2-TC-061 | Booking RBAC and method selection | security | P0 | PASS |
| NX01-R2-TC-062 | Booking object ownership and negative IDs | security | P0 | PASS |
| NX01-R2-TC-063 | Coach roster owner isolation | security | P0 | PASS |
| NX01-R2-TC-064 | Member eligibility owner boundary | security | P0 | PASS |
| NX01-R2-TC-065 | Duplicate arrival and denial paths | unit | P1 | PASS |
| NX01-R2-TC-066 | Cancellation cutoff and balance | regression | P1 | PASS |
| NX01-R2-TC-067 | Self-training ACK regression | unit | P1 | PASS |
| NX01-R2-TC-068 | Class and session validation/conflicts | unit | P1 | PASS |
| NX01-R2-TC-069 | Null/malformed inputs and error envelope | unit | P1 | PASS |
| NX01-R2-TC-070 | JWT and account state | security | P0 | PASS |
| NX01-R2-TC-071 | Required flow payment/refund regression subset | regression | P1 | PASS |
| NX01-R2-TC-072 | Audit and business clock | unit | P1 | PASS |

## NX01-R2-TC-001 Fresh remote-main and safe synchronization

- Requirement/review finding: Audit start gate.
- Type: static review. Priority: P0.
- Target component: Git and original/historical files.
- Environment/preconditions: Origin/network and dirty original developer branch.
- Procedure/commands: git status --short --branch; git fetch origin main --prune; git rev-parse origin/main; create a new detached worktree at the just-fetched SHA.
- Expected result/pass-fail condition: Fetch exit 0; pinned worktree SHA agrees, no pull onto developer branch.
- Observed result/evidence: d20833c3c16e4f7d95d12b6df21798fc528bd559 confirmed; original branch/head preserved.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. EV/R2 git evidence; no mutation of user files.
- Evidence/defect reference: session.json (`evidence/session.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-002 Working tree and historical preservation

- Requirement/review finding: NX01-REV-03; audit safety.
- Type: static review. Priority: P0.
- Target component: Git and original/historical files.
- Environment/preconditions: Start hashes in preservation-start.json.
- Procedure/commands: Compare every historical and original file hash at end; inspect original/source status and tracked/index diff before ordinary worktree remove.
- Expected result/pass-fail condition: All original bytes unchanged, no unauthorized source or Git mutation.
- Observed result/evidence: Final actual preservation/cleanup details recorded in final-state.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Required final integrity control, verified again at sealing.
- Evidence/defect reference: final-state.json (`evidence/final-state.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-003 Qualified dbo table recognized

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling unit. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated evidence/fixtures; no production mutations.
- Procedure/commands: Run test_inventory_controls.py test_01 on fixtures/qualified.sql.
- Expected result/pass-fail condition: dbo.cash_event is recognized with schema/table/line.
- Observed result/evidence: Executed control suite: 16 tests, zero failures/errors/skips; per-checker exits in negative-controls.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. Expected checker failure is a successful negative test.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-004 Bracket and mixed qualification recognized

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling unit. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated evidence/fixtures; no production mutations.
- Procedure/commands: Run test_01 with [dbo].[training_plan] and [dbo].training_result.
- Expected result/pass-fail condition: Both identifiers are normalized independently and retained.
- Observed result/evidence: Executed control suite: 16 tests, zero failures/errors/skips; per-checker exits in negative-controls.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. Expected checker failure is a successful negative test.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-005 Multiline table recognized

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling unit. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated evidence/fixtures; no production mutations.
- Procedure/commands: Run test_01 with CREATE TABLE newline dbo.multi_line_table.
- Expected result/pass-fail condition: Full declaration is recognized with exact start line.
- Observed result/evidence: Executed control suite: 16 tests, zero failures/errors/skips; per-checker exits in negative-controls.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. Expected checker failure is a successful negative test.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-006 SQL comments and strings do not count as DDL

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling unit. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated evidence/fixtures; no production mutations.
- Procedure/commands: Run test_03 on fixtures/false-declarations.sql.
- Expected result/pass-fail condition: Primary and independent pass produce zero actual CREATE declarations.
- Observed result/evidence: Executed control suite: 16 tests, zero failures/errors/skips; per-checker exits in negative-controls.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. Expected checker failure is a successful negative test.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-007 Omitted table is detected

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling unit. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated evidence/fixtures; no production mutations.
- Procedure/commands: Run test_05; remove one table record from a fixture export only; invoke comparator CLI.
- Expected result/pass-fail condition: Checker exit 1 with missing dbo.cash_event; negative test PASS.
- Observed result/evidence: Executed control suite: 16 tests, zero failures/errors/skips; per-checker exits in negative-controls.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. Expected checker failure is a successful negative test.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-008 Omitted/new route is detected

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling unit. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated evidence/fixtures; no production mutations.
- Procedure/commands: Run test_07/test_08 on isolated fixture copies.
- Expected result/pass-fail condition: Checker exit 1 for missing route or added controller; negative test PASS.
- Observed result/evidence: Executed control suite: 16 tests, zero failures/errors/skips; per-checker exits in negative-controls.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed. Expected checker failure is a successful negative test.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-009 All migration files covered

- Requirement/review finding: NX01-REV-01/02.
- Type: static review. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Current tracked source; primary and independent file enumeration.
- Procedure/commands: python inventory_primary.py <source> <evidence>; python inventory_compare.py <source> <evidence> <evidence>. Compare file identities, not expected totals.
- Expected result/pass-fail condition: No SQL file missing/extra; nonstandard names and gaps explicit.
- Observed result/evidence: 29 files reconciled; 27 default + 2 opt-in.
- Execution result: **PASS**.
- Execution requirement/blocker: Diagnostic totals are not the completeness gate.
- Evidence/defect reference: schema-inventory-reconciliation.json (`evidence/schema-inventory-reconciliation.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-010 Controller and mapping coverage

- Requirement/review finding: NX01-REV-02.
- Type: static review. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Current source; independent Git file list.
- Procedure/commands: Compare controller/config paths, class/method annotation identities and composed HTTP/path/params records.
- Expected result/pass-fail condition: Identity/multiplicity differences fail; unsupported forms explicitly registered.
- Observed result/evidence: 37 controllers, 103 method declarations, 105 static records; independent match.
- Execution result: **PASS**.
- Execution requirement/blocker: No live Spring registration claim.
- Evidence/defect reference: api-mapping-reconciliation.json (`evidence/api-mapping-reconciliation.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-011 Exact official XLSM versus XLSX

- Requirement/review finding: NX01-REV-04.
- Type: source reconciliation. Priority: P0.
- Target component: Official workbook reconciliation.
- Environment/preconditions: Exact XLSM in Downloads; previous XLSX unchanged.
- Procedure/commands: python reconcile_workbooks.py; compare all nonempty cells, formulas/types, actual headings and coordinates without Excel/macro execution.
- Expected result/pass-fail condition: Equivalence only if relevant values/formulas/types match; otherwise differences and affected approval gate blocked.
- Observed result/evidence: 1703 cell pairs; 1701 exact matches, 2 Status differences; macro-enabled container present, no VBA project.
- Execution result: **BLOCKED**.
- Execution requirement/blocker: Technical comparison completed; equivalence/status acceptance remains blocked pending human disposition.
- Evidence/defect reference: workbook-cell-reconciliation.json (`evidence/workbook-cell-reconciliation.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-012 Canonical DOCX real and editable

- Requirement/review finding: NX01-REV-03.
- Type: artifact verification. Priority: P0.
- Target component: Canonical and review-copy DOCX.
- Environment/preconditions: Current-round canonical path only.
- Procedure/commands: Open canonical with python-docx; ZIP CRC, content types, readable paragraphs/tables and enforced document protection checks.
- Expected result/pass-fail condition: Valid editable OOXML, not renamed text/PDF.
- Observed result/evidence: Actual structure/hash data in docx-verification.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Fresh artifact verification; old log preserved.
- Evidence/defect reference: docx-verification.json (`evidence/docx-verification.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-013 Review copy byte-identical

- Requirement/review finding: NX01-REV-03.
- Type: artifact verification. Priority: P0.
- Target component: Canonical and review-copy DOCX.
- Environment/preconditions: Canonical and review-copy files.
- Procedure/commands: Compare byte counts and SHA-256; compare bytes directly.
- Expected result/pass-fail condition: Both files have equal bytes and hashes.
- Observed result/evidence: MATCH; see docx-verification.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Copy is under the current round/review-copy.
- Evidence/defect reference: docx-verification.json (`evidence/docx-verification.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-014 Round 1 remains unchanged

- Requirement/review finding: NX01-REV-03.
- Type: static review. Priority: P0.
- Target component: Git and original/historical files.
- Environment/preconditions: 74 historic artifacts hashed at start.
- Procedure/commands: Rehash all previous report files and Round 1 canonical DOCX; compare actual old DOCX hash with claimed value.
- Expected result/pass-fail condition: No historic file changes; canonical hash MATCH.
- Observed result/evidence: Round 1 DOCX MATCH; final preservation checks revalidate all 74 historical artifacts.
- Execution result: **PASS**.
- Execution requirement/blocker: No old report/tooling/audit log edited.
- Evidence/defect reference: final-state.json (`evidence/final-state.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-015 Semantic evidence spot checks

- Requirement/review finding: NX01-REV-06.
- Type: static review. Priority: P0.
- Target component: Pinned source claims and hash reproduction.
- Environment/preconditions: Pinned Git objects and saved source sample predicates.
- Procedure/commands: python verify_claims.py; powershell -File source-evidence-reproduction.ps1 -RepoPath <repo> -AllHashes.
- Expected result/pass-fail condition: Actual source text supports selected claims, not just valid line numbers.
- Observed result/evidence: 19/19 semantic samples PASS; 220 previous source hashes unchanged; PowerShell reproduction exit 0.
- Execution result: **PASS**.
- Execution requirement/blocker: Static source semantics only; mismatches retained if any.
- Evidence/defect reference: source-claim-spot-checks.json (`evidence/source-claim-spot-checks.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-016 NX-00 human sign-off attribution

- Requirement/review finding: NX01-REV-05.
- Type: human evidence review. Priority: P0.
- Target component: NX-00 human approval evidence.
- Environment/preconditions: Merged/local NX-00 and exact XLSM.
- Procedure/commands: Inspect dated human acceptance/exception, approved commit/version and decision coverage. Do not infer it from workbook status or merge.
- Expected result/pass-fail condition: Explicit dated human decision, or honest NX00_SIGNOFF_PENDING.
- Observed result/evidence: No dated human acceptance found in inspected project records. Official Done label conflicts with unsigned handoff.
- Execution result: **BLOCKED**.
- Execution requirement/blocker: HUMAN_APPROVAL_PENDING: Dat and CODEOWNER, not a technical test failure.
- Evidence/defect reference: nx00-approval-search.json (`evidence/nx00-approval-search.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-017 Fresh safe Maven unit execution

- Requirement/review finding: NX01-REV-06.
- Type: unit/web slice. Priority: P0.
- Target component: Backend Maven unit suite.
- Environment/preconditions: JDK25/Maven; no worktree .env; integration tag excluded.
- Procedure/commands: python run_maven.py invokes mvn -B -ntp test in audited backend; inspect fresh Surefire XML and existing CI count checker.
- Expected result/pass-fail condition: Exit 0; failure/error/skip counts accurately reported.
- Observed result/evidence: Exit 0; 128 suites; {'tests': 1882, 'failures': 0, 'errors': 0, 'skipped': 0}; CI checker 0.
- Execution result: **PASS**.
- Execution requirement/blocker: Freshly executed, not carried from Round 1.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-018 SQL Server and full verify limits

- Requirement/review finding: NX01-REV-06.
- Type: integration. Priority: P0.
- Target component: SQL Server/full application runtime.
- Environment/preconditions: No authorized disposable SQL Server; Docker prohibited.
- Procedure/commands: Inspect Failsafe verify and integration-tag/test container configuration. Do not run database-dependent verification blindly.
- Expected result/pass-fail condition: NOT_RUN/BLOCKED unless actually run in an authorized isolated environment.
- Observed result/evidence: SQL Server integration, migrated constraints, full verify and complete app runtime mapping enumeration were not executed.
- Execution result: **BLOCKED**.
- Execution requirement/blocker: Unit mocks/static comparisons do not prove runtime schema or locks.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-019 No unauthorized production or Git change

- Requirement/review finding: Audit safety.
- Type: static review. Priority: P0.
- Target component: Git and original/historical files.
- Environment/preconditions: Original/source Git states recorded.
- Procedure/commands: Inspect git status, git diff --stat and --cached; compare HEAD and historical/user hashes; check generated paths.
- Expected result/pass-fail condition: Only new report round and new audit DOCX outputs; no production/data/Git mutations.
- Observed result/evidence: Final-state.json records original unchanged HEAD/status and clean source cleanup.
- Execution result: **PASS**.
- Execution requirement/blocker: No commit/push/merge/approval.
- Evidence/defect reference: final-state.json (`evidence/final-state.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-020 Complete package and links

- Requirement/review finding: All review findings.
- Type: artifact verification. Priority: P0.
- Target component: Complete report package.
- Environment/preconditions: 18 reports, actual evidence and canonical/review DOCX.
- Procedure/commands: Run validate_package.py and final integrity seal; check links, evidence fields, data identity reconciliations and hashes.
- Expected result/pass-fail condition: All mandatory files exist; no broken local link, stale counts or fake evidence; blockers remain explicit.
- Observed result/evidence: Final validator output in validation-results.json.
- Execution result: **PASS**.
- Execution requirement/blocker: Required final gate, executed after artifacts are complete.
- Evidence/defect reference: validation-results.json (`evidence/validation-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-021 Original scanner blind spot reproduced

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_02
- Expected result/pass-fail condition: Original recognizer returns one of five fixture declarations, new passes recognize all five.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-022 Added qualified table detected

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_06
- Expected result/pass-fail condition: Stale fixture export versus new qualified CREATE returns checker 1.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-023 Duplicate route record detected

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_15
- Expected result/pass-fail condition: Multiplicity mismatch returns checker 1.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-024 Alias and HTTP-method cross product

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_09
- Expected result/pass-fail condition: 2 bases x 2 paths x 2 methods plus 2 aliases = 10 fixture records; profile preserved.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-025 Unsupported dynamic/custom/inherited forms

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_11/test_12/test_13
- Expected result/pass-fail condition: Dynamic DDL, custom mapping or controller inheritance returns checker 2/UNVERIFIED.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-026 Nested-comment limitation explicit

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_14
- Expected result/pass-fail condition: Flat comparator does not silently certify nested comments; returns UNVERIFIED.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-027 Java fake annotations ignored

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_16
- Expected result/pass-fail condition: Comment/string fake mapping excluded; real mapping retained.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-028 Quoted identifiers and dialect qualification

- Requirement/review finding: NX01-REV-01/02.
- Type: audit tooling negative control. Priority: P0.
- Target component: SQL/API audit scanner and comparator.
- Environment/preconditions: Isolated fixtures only.
- Procedure/commands: python test_inventory_controls.py: test_04
- Expected result/pass-fail condition: Double-quoted names recognized; CREATE TABLE IF NOT EXISTS syntax flagged nonstandard T-SQL, not executed.
- Observed result/evidence: Fresh 16-test control suite PASS; expected checker exit and test-layer status retained.
- Execution result: **PASS**.
- Execution requirement/blocker: No production fixture or source mutation.
- Evidence/defect reference: negative-controls.json (`evidence/negative-controls.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-029 Class/session/coach/facility and balance reuse

- Requirement/review finding: F4-REQ-01/04/07; D-01/D-02.
- Type: static review. Priority: P0.
- Target component: Pinned source claims and hash reproduction.
- Environment/preconditions: Same-SHA entities/services and current unit suite.
- Procedure/commands: Inspect class versus session coach keys, manager conflict/qualification checks, booking debit/cancel, arrival lock and source inventory.
- Expected result/pass-fail condition: Do not conflate roster/booking/arrival/coach attendance or add a second debit.
- Observed result/evidence: Fresh semantic samples and existing tests agree with unchanged source; no implementation added.
- Execution result: **PASS**.
- Execution requirement/blocker: Source and unit scope only.
- Evidence/defect reference: source-claim-spot-checks.json (`evidence/source-claim-spot-checks.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-030 Flow 5 and Flow 6 absence and privacy boundary

- Requirement/review finding: F5-REQ-01; F5-CAND-02; F6-REQ-01; D-05..08.
- Type: static review. Priority: P0.
- Target component: Pinned source claims and hash reproduction.
- Environment/preconditions: All runtime Java plus pom.xml; migrations excluded from absence population.
- Procedure/commands: Repeat bounded absence regex in absence-search.json and compare controller/entity inventories with V17-V19.
- Expected result/pass-fail condition: Schema scaffolding is distinct from provider/chat/notification workflows; Member recommender stays PROPOSED_ONLY.
- Observed result/evidence: NOT_FOUND_IN_AUDITED_SCOPE for runtime AI/training/notice adapters; no external service/global absence claim.
- Execution result: **PASS**.
- Execution requirement/blocker: Provider, consent, budgets, retention, review and optional Go decisions not invented.
- Evidence/defect reference: absence-search.json (`evidence/absence-search.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-041 Personal and class plan ownership

- Requirement/review finding: F4-REQ-01; D-02/D-12.
- Type: proposed future test. Priority: P0.
- Target component: Personal and class plan ownership.
- Environment/preconditions: Two coaches C1/C2, members M1/M2, own/foreign classes; approved personal/class contract.
- Procedure/commands: Create/read/edit plans for own and foreign assignments with valid and foreign IDs. Repeat after session reassignment.
- Expected result/pass-fail condition: Only approved owners act; personal/class links remain correct; no inherited Manager access unless approved.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-042 Plan templates and versions

- Requirement/review finding: F4-REQ-01/06; D-02.
- Type: proposed future test. Priority: P1.
- Target component: Plan templates and versions.
- Environment/preconditions: Approved template/publish/version/edit rules and version fixtures.
- Procedure/commands: Create template, instantiate, publish, edit concurrently with stale version and read historic member assignment.
- Expected result/pass-fail condition: Approved transitions and conflict response; old versions and assignments not silently rewritten.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-043 Training results validity and attendance binding

- Requirement/review finding: F4-REQ-02; D-03/D-13.
- Type: proposed future test. Priority: P1.
- Target component: Training results validity and attendance binding.
- Environment/preconditions: Approved metrics and attendance preconditions.
- Procedure/commands: Submit null/unknown/foreign session/member, absent/cancelled participant, duplicate metric and negative/out-of-range score.
- Expected result/pass-fail condition: Deliberate validation; no unrelated result/member binding; no duplicate metric or unauthorized write.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-044 Result correction and retention

- Requirement/review finding: F4-REQ-02/03; D-03.
- Type: proposed future test. Priority: P1.
- Target component: Result correction and retention.
- Environment/preconditions: Approved correction window, author and retention rules.
- Procedure/commands: Correct a result within and outside allowed policy; retry same command; inspect history and retention behavior.
- Expected result/pass-fail condition: Attributable correction, stable idempotency/version semantics and approved retention; original history preserved as specified.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-045 Progress calculation and note visibility

- Requirement/review finding: F4-REQ-03/06; D-03/D-12.
- Type: proposed future test. Priority: P1.
- Target component: Progress calculation and note visibility.
- Environment/preconditions: Approved metric aggregation and note classifications.
- Procedure/commands: Seed test results for M1/M2 plus draft/private/published feedback; read as each member/coach and compare aggregates.
- Expected result/pass-fail condition: Only authorized data contributes; private/draft fields excluded; computations follow approved units/ranges.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-046 Coach session attendance and corrections

- Requirement/review finding: F4-REQ-04; D-01/D-13.
- Type: proposed future test. Priority: P1.
- Target component: Coach session attendance and corrections.
- Environment/preconditions: Approved PRESENT/ABSENT/LATE/EXCUSED/submission policy; C1/C2 and roster fixtures.
- Procedure/commands: Mark own roster, foreign/unassigned/cancelled session, unknown/foreign booking; repeat and correct with reason.
- Expected result/pass-fail condition: Correctly authorized durable record and correction audit; independent FKs cannot create mismatched relationships.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-047 Member attendance history isolation

- Requirement/review finding: F4-REQ-05; D-12.
- Type: proposed future test. Priority: P0.
- Target component: Member attendance history isolation.
- Environment/preconditions: M1/M2 have different attendance/results; approved publication rules.
- Procedure/commands: Request own history and try foreign owner in path/query/body; test pagination/date boundaries and unpublished records.
- Expected result/pass-fail condition: Principal-only authorized records, deterministic paging and deliberate forbidden/not-found behavior; no private data.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-048 Member plan and comment isolation

- Requirement/review finding: F4-REQ-06; D-02/D-12.
- Type: proposed future test. Priority: P0.
- Target component: Member plan and comment isolation.
- Environment/preconditions: Personal and shared plans plus private/published notes.
- Procedure/commands: Read assigned own plan, unassigned plan, foreign member plan and previous versions as M1/M2.
- Expected result/pass-fail condition: Visibility follows assignment/publication and history policy; no coach-private note leakage.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-049 Durable self-training and no double debit

- Requirement/review finding: F4-REQ-04/07; D-01/D-13.
- Type: proposed future test. Priority: P0.
- Target component: Durable self-training and no double debit.
- Environment/preconditions: Approved self-training behavior and package snapshot.
- Procedure/commands: Book once, arrive/check-in, record self-training, retry, correct attendance and cancel where allowed; compare balances.
- Expected result/pass-fail condition: Exactly one existing booking debit; no extra debit for arrival/attendance; approved cancel/credit semantics.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-050 Notifications and homework

- Requirement/review finding: F4-CAND-08; D-04.
- Type: proposed future test. Priority: P1.
- Target component: Notifications and homework.
- Environment/preconditions: Candidate explicitly adopted; recipient/channel/content rules approved.
- Procedure/commands: Deliver to own class, retry delivery, remove recipient, read as foreign member, test due/expiry change and delete/retention.
- Expected result/pass-fail condition: Approved recipients only, deduplicated delivery and read markers; no private comments in broadcast payload.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-051 AI provider unavailable and fallback

- Requirement/review finding: F5-REQ-01; D-05.
- Type: proposed future test. Priority: P1.
- Target component: AI provider unavailable and fallback.
- Environment/preconditions: Optional Flow 5 adopted; fake provider only, approved timeout/budget.
- Procedure/commands: Simulate timeout, unavailable provider, malformed/oversized output, cancellation and budget exhaustion.
- Expected result/pass-fail condition: Bounded timeout/retry and safe error/fallback; no partial plan creation or cost runaway.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-052 AI privacy and authorized input

- Requirement/review finding: F5-REQ-01; D-05/D-06/D-14.
- Type: proposed future test. Priority: P0.
- Target component: AI privacy and authorized input.
- Environment/preconditions: Approved consent/data fields; M1/M2 and C1/C2.
- Procedure/commands: Attempt recommendations with foreign member ID, hidden notes, revoked consent and stale history; inspect fake-provider payload/logs.
- Expected result/pass-fail condition: Only approved minimized authorized fields leave boundary; no other member, credentials, staffNote or private feedback.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-053 Recommendation grounding and structured output

- Requirement/review finding: F5-REQ-01; D-05/D-07.
- Type: proposed future test. Priority: P1.
- Target component: Recommendation grounding and structured output.
- Environment/preconditions: Approved goals/level/history and exercise source; fake provider.
- Procedure/commands: Return invented exercise IDs, invalid metrics/schema, contradictory history and unsupported advice.
- Expected result/pass-fail condition: Reject invalid/ungrounded output according to approved contract; no automatic trusted plan promotion.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-054 Human approval before promotion

- Requirement/review finding: F5-REQ-01; D-07.
- Type: proposed future test. Priority: P0.
- Target component: Human approval before promotion.
- Environment/preconditions: Approved review policy, reviewer identity and plan bridge.
- Procedure/commands: Try direct publish, foreign reviewer, duplicate approval, modify/reject/reapprove and stale version.
- Expected result/pass-fail condition: Only approved reviewer transitions; provenance/version retained and no duplicate plan publication.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-055 Member recommendation audience gate

- Requirement/review finding: F5-CAND-02; D-06.
- Type: proposed future test. Priority: P1.
- Target component: Member recommendation audience gate.
- Environment/preconditions: Explicit candidate inclusion required before test endpoint exists.
- Procedure/commands: Review signed decision first; if included test Member own requests and reject foreign/Coach-only actions.
- Expected result/pass-fail condition: Unapproved candidate remains absent; adopted audience follows its separate approved contract.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-056 Assistant prompt injection

- Requirement/review finding: F6-REQ-01; D-08/D-14.
- Type: proposed future test. Priority: P0.
- Target component: Assistant prompt injection.
- Environment/preconditions: Optional Flow 6 adopted; fake model and adversarial source fixtures.
- Procedure/commands: Put instructions in user questions and retrieved content to reveal secrets, another member or execute bookings/payments.
- Expected result/pass-fail condition: Data authorization enforced outside model; untrusted content cannot grant data access or business actions.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-057 Assistant authorized current member context

- Requirement/review finding: F6-REQ-01; D-08/D-12.
- Type: proposed future test. Priority: P1.
- Target component: Assistant authorized current member context.
- Environment/preconditions: M1/M2 schedules, active/inactive sessions and caller principal.
- Procedure/commands: Ask for own schedule then inject M2 IDs, stale token/disabled account and conversation ID substitution.
- Expected result/pass-fail condition: Current principal controls every retrieval/conversation; foreign data denied and inactive caller rejected.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-058 Unsupported questions and missing context

- Requirement/review finding: F6-REQ-01; D-08.
- Type: proposed future test. Priority: P1.
- Target component: Unsupported questions and missing context.
- Environment/preconditions: Approved knowledge sources and uncertainty behavior.
- Procedure/commands: Ask unsupported service/exercise question, ambiguous member schedule and empty/stale corpus.
- Expected result/pass-fail condition: Explain supported boundary or request relevant context; avoid invented facts and unauthorized retrieval.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-059 Assistant outage, rate/cost and retention

- Requirement/review finding: F6-REQ-01; D-08/D-14.
- Type: proposed future test. Priority: P1.
- Target component: Assistant outage, rate/cost and retention.
- Environment/preconditions: Fake provider, approved budgets/retention/logging.
- Procedure/commands: Simulate timeout, excessive requests, malicious long prompt; expire/delete conversation and inspect logs.
- Expected result/pass-fail condition: Bounded resource use and safe fallback; approved retention/deletion and minimized logs.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-060 Required flow end-to-end regression

- Requirement/review finding: NX-34, D-09.
- Type: proposed future test. Priority: P1.
- Target component: Required flow end-to-end regression.
- Environment/preconditions: Approved disposable backend/FE test environment.
- Procedure/commands: Register/login, purchase/pay/activate package, browse/book/cancel, check-in, session cancel, refund/invoice; compare balances, identities and existing response shapes.
- Expected result/pass-fail condition: Required flows preserve approved behavior independently of optional Flow 4-6 adoption/failure.
- Observed result/evidence: PROPOSED_FUTURE; no optional-flow runtime or application test was added.
- Execution result: **NOT_RUN**.
- Execution requirement/blocker: Downstream only after human scope/contracts/implementation approval.
- Evidence/defect reference: test-cases.json (`evidence/test-cases.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-061 Booking RBAC and method selection

- Requirement/review finding: Coach/Member/Receptionist roles; D-10.
- Type: security. Priority: P0.
- Target component: Booking RBAC and method selection.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=BookingControllerTest,BookingRulesControllerTest,ReceptionBookingControllerTest,RefreshedCatalogAuthorizationTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: MEMBER own booking; staff operations permitted; COACH/anonymous forbidden as defined by current controller contracts.
- Observed result/evidence: 4 matching fresh suites; 78 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-062 Booking object ownership and negative IDs

- Requirement/review finding: F4-REQ-05/07; D-12.
- Type: security. Priority: P0.
- Target component: Booking object ownership and negative IDs.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=BookingServiceImplTest,ReceptionBookingServiceTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Spoofed member/foreign package/cancel by foreign owner rejected before forbidden writes; invalid/missing IDs rejected.
- Observed result/evidence: 2 matching fresh suites; 82 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-063 Coach roster owner isolation

- Requirement/review finding: F4-REQ-04; D-12.
- Type: security. Priority: P0.
- Target component: Coach roster owner isolation.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=CoachScheduleControllerTest,CoachScheduleServiceImplTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Foreign/unassigned roster denied to coach; own ID accepted; manager exception explicit; inactive/deleted actors rejected.
- Observed result/evidence: 2 matching fresh suites; 62 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-064 Member eligibility owner boundary

- Requirement/review finding: F4-REQ-05; D-12.
- Type: security. Priority: P0.
- Target component: Member eligibility owner boundary.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=MemberScheduleControllerTest,ReceptionBookingControllerTest,MemberScheduleServiceImplTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Principal binds member eligibility; staff-target route independent; no member-selected foreign owner.
- Observed result/evidence: 3 matching fresh suites; 113 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-065 Duplicate arrival and denial paths

- Requirement/review finding: F4-REQ-07; D-01.
- Type: unit. Priority: P1.
- Target component: Duplicate arrival and denial paths.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=CheckInControllerTest,CheckInServiceTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Allowed check-in saved; duplicate denied; unknown/missing booking or invalid coverage handled according to current contract, no extra package debit.
- Observed result/evidence: 2 matching fresh suites; 23 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-066 Cancellation cutoff and balance

- Requirement/review finding: D-01/D-13; Flow 1-3 regression.
- Type: regression. Priority: P1.
- Target component: Cancellation cutoff and balance.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=BookingRulesServiceTest,BookingServiceImplTest,ManagerSessionServiceImplTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Checked-in/cancelled/started/cutoff cases reject as coded; active credit restoration and seat release use existing commands.
- Observed result/evidence: 3 matching fresh suites; 165 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-067 Self-training ACK regression

- Requirement/review finding: D-01/D-13.
- Type: unit. Priority: P1.
- Target component: Self-training ACK regression.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=BookingServiceImplTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: recordSelfTrainingAttendance_Success_ReturnsAcknowledgedResponseAndNoSaveCalled passes; no durable training completion claimed.
- Observed result/evidence: 1 matching fresh suites; 42 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-068 Class and session validation/conflicts

- Requirement/review finding: F4-REQ-01/04; reuse control.
- Type: unit. Priority: P1.
- Target component: Class and session validation/conflicts.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=ManagerClassControllerTest,ManagerSessionControllerTest,ManagerClassServiceImplTest,ManagerSessionServiceImplTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Role, qualification, capacity/date/time/facility and conflict cases match current contracts; no implicit attendance workflow.
- Observed result/evidence: 4 matching fresh suites; 170 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-069 Null/malformed inputs and error envelope

- Requirement/review finding: Audit validation; D-10.
- Type: unit. Priority: P1.
- Target component: Null/malformed inputs and error envelope.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=BookingControllerTest,CheckInControllerTest,CoachScheduleQueryTest,GlobalExceptionHandlerTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Malformed body, missing/invalid params, business conflicts and forbidden cases return documented stable errors in tested slices.
- Observed result/evidence: 4 matching fresh suites; 40 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-070 JWT and account state

- Requirement/review finding: Audit security; D-12.
- Type: security. Priority: P0.
- Target component: JWT and account state.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=ActuatorSecurityTest,InactiveUserLoginTest,JwtServiceTest,SecurityConfigCorsTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Access/refresh separation, inactive user and public/protected/CORS behavior match source tests.
- Observed result/evidence: 4 matching fresh suites; 30 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-071 Required flow payment/refund regression subset

- Requirement/review finding: Flow 1-3 regression.
- Type: regression. Priority: P1.
- Target component: Required flow payment/refund regression subset.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=RefundServiceImplTest,PaymentRefundServiceImplTest,PaymentServiceImplTest,PaymentVerificationServiceImplTest,ReceiptServiceImplTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Existing mocked payment/refund/invoice behavior stays green; does not certify database rollback or concurrency.
- Observed result/evidence: 5 matching fresh suites; 109 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.

## NX01-R2-TC-072 Audit and business clock

- Requirement/review finding: D-03/D-13; audit control.
- Type: unit. Priority: P1.
- Target component: Audit and business clock.
- Environment/preconditions: Existing Mockito/MockMvc fixtures in freshly executed current suite.
- Procedure/commands: Existing suites included in the executed full mvn -B -ntp test run. Reproduce subset from backend with mvn -B -ntp -Dtest=MembershipScheduledJobsTest,AuditConfigTest,AuditServiceImplTest,ClockConfigTest test. Read the suite methods in evidence/unit-results.json and corresponding existing test source.
- Expected result/pass-fail condition: Transaction selection, business zone and maintenance orchestration tests pass without running scheduled production work.
- Observed result/evidence: 4 matching fresh suites; 21 test invocations passed; no focused subset rerun claimed.
- Execution result: **PASS**.
- Execution requirement/blocker: Existing negative RBAC/ownership/validation/cancel/payment tests ran as part of fresh Maven suite. No live SQL proof.
- Evidence/defect reference: unit-results.json (`evidence/unit-results.json` - local review reference, not published); no external issue was created.
