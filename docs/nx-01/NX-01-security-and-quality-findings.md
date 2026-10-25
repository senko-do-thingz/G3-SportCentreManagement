# NX-01 security and quality findings

Round 2 remediation: unchanged source analysis revalidated at `d20833c3c16e4f7d95d12b6df21798fc528bd559`. All 220 prior source-file hashes match; 19 explicit high-impact samples pass semantic checks. Counts in this report describe static source, not deployed behavior. See [independent reconciliation](NX-01-inventory-reconciliation.md) and workbook reconciliation (`evidence/workbook-source-reconciliation.md` - local review reference, not published).

Report index (`NX-01-report-index.md` - local review reference, not published). Round 2. Source baseline: `d20833c3c16e4f7d95d12b6df21798fc528bd559`.

Source findings refer to fetched remote main, not the unchanged task checkout. Static evidence does not prove a deployed database or runtime response. NX-00 formal scope approval remains unverified.

Findings are evidence-led. Capability/design gaps are distinguished from demonstrated vulnerabilities. No CRITICAL vulnerability was established, and no live exploit or production log was inspected. Severity reflects impact on future integration, not proof that absent optional endpoints are currently exploitable.


| Severity | Count |
| --- | --- |
| CRITICAL | 0 |
| HIGH | 3 |
| MEDIUM | 5 |
| LOW | 2 |
| INFO | 2 |


| ID | Severity | Title | Evidence state | Owner/next task | Gate impact |
| --- | --- | --- | --- | --- | --- |
| NX01-F01 | HIGH | Self-training attendance is an ACK, not a saved record | Confirmed capability gap | Dat + Hung; NX-02/D-01/D-13 then approved NX-09 | Later implementation blocker; does not prevent truthful audit handoff. |
| NX01-F02 | HIGH | Training design and migration contracts disagree | Confirmed design/source conflict | Dat + Hung + Truc; NX-02/NX-03/NX-04 | Blocks training implementation/contract approval, not inventory completion. |
| NX01-F03 | MEDIUM | Attendance booking/member/session consistency is not relationally enforced | Confirmed schema gap; runtime exploit not established | Dat; NX-04 after D-01/D-13 | Downstream data-contract blocker. |
| NX01-F04 | MEDIUM | Metrics, step ordering and score uniqueness need policy | Confirmed schema gap | Dat + Hung + Truc; NX-02/NX-04/D-03 | Downstream metric/data design gate. |
| NX01-F05 | MEDIUM | Training feedback has no publication or private-note boundary | Confirmed missing privacy model | Dat + Truc + Hung; D-03/D-12, NX-03/NX-04 | Blocks member training visibility design. |
| NX01-F06 | MEDIUM | Check-in text and self-training IDs lack complete DTO bounds | Confirmed validation omission; response needs runtime verification | Dat; later approved validation maintenance and NX-03 consistency | Does not block audit; needs downstream negative tests. |
| NX01-F07 | MEDIUM | Check-in history is unpaged and traverses lazy relationships | Confirmed query shape; performance NEEDS_VERIFICATION | Dat; approved paging/projection contract task with FE regression | Later performance work; not an audit blocker. |
| NX01-F08 | LOW | Exception messages may retain sensitive details in operator logs | NEEDS_VERIFICATION | Dat; security maintenance and D-14 logging policy | Not an audit blocker. |
| NX01-F09 | LOW | Entity callbacks and SQL defaults can use clocks outside the business Clock | Confirmed mixed time sources; runtime effect NEEDS_VERIFICATION | Dat; NX-03/NX-04 time convention and regression | Not an audit blocker. |
| NX01-F10 | INFO | Existing coach and member scoping are useful reuse patterns | Positive verified control | Dat; preserve in approved training/AI implementation | No blocker; regression control. |
| NX01-F11 | HIGH | AI schema and member data do not establish safe AI readiness | Confirmed missing AI runtime controls | Dat + relevant FE/privacy/budget owners; NX-20 and NX-27 independently | Blocks AI implementation/release readiness until decisions and validation. |
| NX01-F12 | INFO | SQL Server integration and full verify remain unexecuted | Verification limit | Dat + CODEOWNER; approved CI integration environment | Formal runtime/release verification blocked; static NX-01 findings remain reviewable. |

## NX01-F01 Self-training attendance is an ACK, not a saved record

Severity: **HIGH**. State: Confirmed capability gap.

Evidence: EV-004 (`NX-01-evidence-index.md#ev-004` - local review reference, not published), EV-005 (`NX-01-evidence-index.md#ev-005` - local review reference, not published), EV-025 (`NX-01-evidence-index.md#ev-025` - local review reference, not published).

Impact: A staff user can invoke the attendance-named POST and receive confirmedAt although no save, attendance repository or debit occurs. The response does disclose persistence pending.

Reproduction or reasoning: Inspect recordSelfTrainingAttendance and existing no-save unit test. Do not claim recorded attendance from the response.

Recommended owner/task: Dat + Hung; NX-02/D-01/D-13 then approved NX-09.

Signoff impact: Later implementation blocker; does not prevent truthful audit handoff.

Suggested validation: After approval, verify durable exactly-once attendance, correct format/date/actor, no extra debit and correction audit.

## NX01-F02 Training design and migration contracts disagree

Severity: **HIGH**. State: Confirmed design/source conflict.

Evidence: EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published), EV-021 (`NX-01-evidence-index.md#ev-021` - local review reference, not published).

Impact: Session versus class plan linkage, attendance submission, result linkage, versions and feedback publication differ. Coding directly from design would target absent columns or duplicate tables.

Reproduction or reasoning: Compare design decisions and table definitions against V16 and all later migration alterations.

Recommended owner/task: Dat + Hung + Truc; NX-02/NX-03/NX-04.

Signoff impact: Blocks training implementation/contract approval, not inventory completion.

Suggested validation: Approve a canonical plan/attendance/result lifecycle and run migration/entity contract tests later.

## NX01-F03 Attendance booking/member/session consistency is not relationally enforced

Severity: **MEDIUM**. State: Confirmed schema gap; runtime exploit not established.

Evidence: EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published).

Impact: Independent attendance FKs allow references to individually valid but unrelated booking/session/member values. No runtime training writer exists to demonstrate exploitability.

Reproduction or reasoning: Compare V16 attendance_record FKs: no composite relation ties booking member/session to row member/session.

Recommended owner/task: Dat; NX-04 after D-01/D-13.

Signoff impact: Downstream data-contract blocker.

Suggested validation: In a disposable approved SQL Server, attempt mismatched triple; require approved service/constraint rejection.

## NX01-F04 Metrics, step ordering and score uniqueness need policy

Severity: **MEDIUM**. State: Confirmed schema gap.

Evidence: EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published).

Impact: Metric max_score has no positive CHECK; score lacks agreed range and result/metric uniqueness; step order is indexed but not unique. Duplicate or incoherent later progress is possible without approved writers.

Reproduction or reasoning: Inspect skill_metric, session_result_score and session_plan_step constraints. No SQL was executed.

Recommended owner/task: Dat + Hung + Truc; NX-02/NX-04/D-03.

Signoff impact: Downstream metric/data design gate.

Suggested validation: Approve units/ranges/order rules, test duplicate metrics, negative/out-of-range scores and repeated steps.

## NX01-F05 Training feedback has no publication or private-note boundary

Severity: **MEDIUM**. State: Confirmed missing privacy model.

Evidence: EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published), EV-021 (`NX-01-evidence-index.md#ev-021` - local review reference, not published), EV-022 (`NX-01-evidence-index.md#ev-022` - local review reference, not published).

Impact: A future broad projection could expose drafts, staffNote or private coach feedback. No existing member training endpoint was found, so current leakage is not asserted.

Reproduction or reasoning: Compare coach_feedback columns and MemberProfile staffNote with the design publication claim and missing runtime inventory.

Recommended owner/task: Dat + Truc + Hung; D-03/D-12, NX-03/NX-04.

Signoff impact: Blocks member training visibility design.

Suggested validation: Test principal-only reads, draft/private field omission, inactive and reassigned-coach cases after contracts.

## NX01-F06 Check-in text and self-training IDs lack complete DTO bounds

Severity: **MEDIUM**. State: Confirmed validation omission; response needs runtime verification.

Evidence: EV-006 (`NX-01-evidence-index.md#ev-006` - local review reference, not published), EV-007 (`NX-01-evidence-index.md#ev-007` - local review reference, not published), EV-145 (`NX-01-evidence-index.md#ev-145` - local review reference, not published), EV-173 (`NX-01-evidence-index.md#ev-173` - local review reference, not published), EV-243 (`NX-01-evidence-index.md#ev-243` - local review reference, not published).

Impact: CheckInRequest requires only nonblank identifier; note has no Size despite 255-column mapping. SelfTrainingAttendanceRequest has NotNull but no Positive bookingId and its note is unused. Oversized check-in data can reach persistence and fail there.

Reproduction or reasoning: Read request DTOs, CheckIn entity and global conflict handler. Use test data only in a disposable test, never a live database.

Recommended owner/task: Dat; later approved validation maintenance and NX-03 consistency.

Signoff impact: Does not block audit; needs downstream negative tests.

Suggested validation: Use 256-character note, negative/missing booking ID, unknown/foreign ID and verify deliberate 400 behavior without persistence.

## NX01-F07 Check-in history is unpaged and traverses lazy relationships

Severity: **MEDIUM**. State: Confirmed query shape; performance NEEDS_VERIFICATION.

Evidence: EV-007 (`NX-01-evidence-index.md#ev-007` - local review reference, not published), EV-270 (`NX-01-evidence-index.md#ev-270` - local review reference, not published), EV-243 (`NX-01-evidence-index.md#ev-243` - local review reference, not published).

Impact: findForDay and findByMemberIdOrderByCheckedInAtDesc return unbounded entities; response accesses member account, booking, package and legacy membership. N+1 cost is plausible as distinct relationships grow.

Reproduction or reasoning: Inspect CheckInRepository and toResponse; no SQL plan/query-count benchmark was run.

Recommended owner/task: Dat; approved paging/projection contract task with FE regression.

Signoff impact: Later performance work; not an audit blocker.

Suggested validation: Measure bounded-size large history with query counts and indexes; preserve current staff client contract.

## NX01-F08 Exception messages may retain sensitive details in operator logs

Severity: **LOW**. State: NEEDS_VERIFICATION.

Evidence: EV-019 (`NX-01-evidence-index.md#ev-019` - local review reference, not published), EV-020 (`NX-01-evidence-index.md#ev-020` - local review reference, not published).

Impact: JWT exception message and SQL root cause are logged. Client error responses are sanitized, but malicious token/constraint-value details might reach operator logs. No actual production log disclosure was observed.

Reproduction or reasoning: Inspect filter catch and data integrity handler; do not copy token/SQL values to audit reports.

Recommended owner/task: Dat; security maintenance and D-14 logging policy.

Signoff impact: Not an audit blocker.

Suggested validation: Controlled malformed-token and duplicate-value tests with safe fixtures should assert redaction and useful correlation.

## NX01-F09 Entity callbacks and SQL defaults can use clocks outside the business Clock

Severity: **LOW**. State: Confirmed mixed time sources; runtime effect NEEDS_VERIFICATION.

Evidence: EV-027 (`NX-01-evidence-index.md#ev-027` - local review reference, not published), EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published), EV-242 (`NX-01-evidence-index.md#ev-242` - local review reference, not published), EV-243 (`NX-01-evidence-index.md#ev-243` - local review reference, not published), EV-244 (`NX-01-evidence-index.md#ev-244` - local review reference, not published).

Impact: Supported service paths inject the center Clock, while entity fallbacks use LocalDateTime.now and DDL uses SYSDATETIME. Different host/database zones could affect later ordering/day boundaries.

Reproduction or reasoning: Compare ClockConfig, CheckIn/ClassSession/Booking callbacks and migration audit defaults.

Recommended owner/task: Dat; NX-03/NX-04 time convention and regression.

Signoff impact: Not an audit blocker.

Suggested validation: Use fixed clock and differing server-zone fixtures around midnight; approve representation before persistence work.

## NX01-F10 Existing coach and member scoping are useful reuse patterns

Severity: **INFO**. State: Positive verified control.

Evidence: EV-005 (`NX-01-evidence-index.md#ev-005` - local review reference, not published), EV-008 (`NX-01-evidence-index.md#ev-008` - local review reference, not published), EV-009 (`NX-01-evidence-index.md#ev-009` - local review reference, not published), EV-017 (`NX-01-evidence-index.md#ev-017` - local review reference, not published).

Impact: Coach service rechecks active current actor, restricts own sessions and repeats ownership in the PII query. Member booking/eligibility uses principal ID. These protections do not automatically cover future records.

Reproduction or reasoning: Review service checks and executed corresponding unit/controller suites.

Recommended owner/task: Dat; preserve in approved training/AI implementation.

Signoff impact: No blocker; regression control.

Suggested validation: Retain negative foreign-owner, unassigned, inactive/deleted actor and reassignment tests.

## NX01-F11 AI schema and member data do not establish safe AI readiness

Severity: **HIGH**. State: Confirmed missing AI runtime controls.

Evidence: EV-011 (`NX-01-evidence-index.md#ev-011` - local review reference, not published), EV-012 (`NX-01-evidence-index.md#ev-012` - local review reference, not published), EV-013 (`NX-01-evidence-index.md#ev-013` - local review reference, not published), EV-022 (`NX-01-evidence-index.md#ev-022` - local review reference, not published), EV-026 (`NX-01-evidence-index.md#ev-026` - local review reference, not published).

Impact: No provider adapter, consent boundary, scoped grounding, timeout, structured validation, retention, rate/cost control, fallback or working review pipeline was found. This is a future implementation gate, not an exposed chatbot vulnerability.

Reproduction or reasoning: Bounded absence search plus complete controller/entity/package inventory.

Recommended owner/task: Dat + relevant FE/privacy/budget owners; NX-20 and NX-27 independently.

Signoff impact: Blocks AI implementation/release readiness until decisions and validation.

Suggested validation: Approve data minimization and knowledge sources; run downstream outage, cross-user, injection, grounding, budget and human-review cases.

## NX01-F12 SQL Server integration and full verify remain unexecuted

Severity: **INFO**. State: Verification limit.

Evidence: EV-024 (`NX-01-evidence-index.md#ev-024` - local review reference, not published), EV-025 (`NX-01-evidence-index.md#ev-025` - local review reference, not published), EV-030 (`NX-01-evidence-index.md#ev-030` - local review reference, not published), EV-031 (`NX-01-evidence-index.md#ev-031` - local review reference, not published).

Impact: Unit/web slices passed, but runtime FK, locks, race behavior, rollback and migration compatibility were not executed locally.

Reproduction or reasoning: Failsafe verify includes integration; source base uses Testcontainers. No Docker was started.

Recommended owner/task: Dat + CODEOWNER; approved CI integration environment.

Signoff impact: Formal runtime/release verification blocked; static NX-01 findings remain reviewable.

Suggested validation: Run required CI integration and no-skip gates before any affected code release.

## Additional reviewed controls and limits

Queries in the scoped schedule/booking/cancellation repositories use bound JPQL parameters or JDBC placeholders. No raw user-string concatenation into executable SQL was found in these reviewed paths. Request DTO allowlists and explicit service assignments limit mass assignment; ownership is still an independent requirement. No global SQL injection absence or whole-system penetration test is claimed. EV-005 (`NX-01-evidence-index.md#ev-005` - local review reference, not published), EV-009 (`NX-01-evidence-index.md#ev-009` - local review reference, not published), EV-028 (`NX-01-evidence-index.md#ev-028` - local review reference, not published)

Sessions and balances use transactions, row/range locks, conditional writes and filtered uniqueness. Java-level checks alone cannot prove SQL Server race behavior. Booking has CONFIRMED/CANCELLED while ClassSession has DRAFT/PUBLISHED/CANCELLED/COMPLETED; neither status set is training attendance. Audit writes are transaction-aware, but no training correction/AI prompt retention policy is implemented. Scheduled midnight maintenance is not a notification event-delivery service. EV-005 (`NX-01-evidence-index.md#ev-005` - local review reference, not published), EV-014 (`NX-01-evidence-index.md#ev-014` - local review reference, not published), EV-015 (`NX-01-evidence-index.md#ev-015` - local review reference, not published), EV-028 (`NX-01-evidence-index.md#ev-028` - local review reference, not published), EV-029 (`NX-01-evidence-index.md#ev-029` - local review reference, not published), EV-026 (`NX-01-evidence-index.md#ev-026` - local review reference, not published)

## Audit evidence corrections

The twelve application/design findings above remain source-valid at the unchanged SHA; F01-F07 were explicitly sampled semantically. Runtime exploit, query-count/N+1 and SQL race claims remain unverified as stated. Separate CODEOWNER audit-quality findings are tracked in resolution register (`NX-01-codeowner-findings-resolution.md` - local review reference, not published), including the three-table scanner omission and count-based completeness defect; these are not silently added to application vulnerability severity totals.
