# NX-01 decisions and followups

Round 2 remediation: unchanged source analysis revalidated at `d20833c3c16e4f7d95d12b6df21798fc528bd559`. All 220 prior source-file hashes match; 19 explicit high-impact samples pass semantic checks. Counts in this report describe static source, not deployed behavior. See [independent reconciliation](NX-01-inventory-reconciliation.md) and workbook reconciliation (`evidence/workbook-source-reconciliation.md` - local review reference, not published).

Report index (`NX-01-report-index.md` - local review reference, not published). Round 2. Source baseline: `d20833c3c16e4f7d95d12b6df21798fc528bd559`.

Source findings refer to fetched remote main, not the unchanged task checkout. Static evidence does not prove a deployed database or runtime response. NX-00 formal scope approval remains unverified.

Every decision remains UNRESOLVED in the inspected workbook and NX-00 handoff. Owners below are planning coordinators, not proof of sign-off authority. Dat and CODEOWNER @senko-do-thingz must identify the business/privacy/budget approvers. The current user authorizes this audit, not optional-flow implementation. EV-002 (`NX-01-evidence-index.md#ev-002` - local review reference, not published), EV-003 (`NX-01-evidence-index.md#ev-003` - local review reference, not published), EV-032 (`NX-01-evidence-index.md#ev-032` - local review reference, not published)


| Decision | Coordinator | Question | Impact | Evidence | Concrete requested outcome |
| --- | --- | --- | --- | --- | --- |
| D-01 | Dat | Arrival, booked participation, self-training ACK and coach attendance; linked records or extension? | F4-REQ-04/05/07; NX-02 -> approved attendance contract | EV-005 (`NX-01-evidence-index.md#ev-005` - local review reference, not published), EV-007 (`NX-01-evidence-index.md#ev-007` - local review reference, not published), EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published) | Approve normal/duplicate/cancelled/self-training examples and no extra debit. |
| D-02 | Dat + Hung | Personal/class/template/session plan identity, owner, version and edit/publication? | F4-REQ-01/06; NX-02/NX-03/NX-04 | EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published), EV-021 (`NX-01-evidence-index.md#ev-021` - local review reference, not published) | Resolve class_id versus session_id/member assignment before schema/API work. |
| D-03 | Dat + Truc + Hung | Metrics, result edits, correction windows, note privacy and retention? | F4-REQ-02/03/05/06 | EV-010 (`NX-01-evidence-index.md#ev-010` - local review reference, not published), EV-021 (`NX-01-evidence-index.md#ev-021` - local review reference, not published) | Approve dictionary, ranges/units, visibility and authorship/correction audit. |
| D-04 | Dat + Phat | Homework/notices in Flow 4 or cross-flow; recipients/channels/triggers? | F4-CAND-08; NX-16 | EV-013 (`NX-01-evidence-index.md#ev-013` - local review reference, not published), EV-026 (`NX-01-evidence-index.md#ev-026` - local review reference, not published) | Confirm placement and event/recipient/idempotency semantics; do not equate homework with notices. |
| D-05 | Dat | Adopt/defer Flow 5; provider/model/budget and privacy approver? | F5-REQ-01; NX-20 then approved NX-21..26 | EV-002 (`NX-01-evidence-index.md#ev-002` - local review reference, not published), EV-012 (`NX-01-evidence-index.md#ev-012` - local review reference, not published), EV-026 (`NX-01-evidence-index.md#ev-026` - local review reference, not published) | Record independent Go/Deferred/Not in Scope and budget/privacy controls. |
| D-06 | Dat + Truc + Hung | Coach-only or explicitly add Member-direct recommendation? | F5-CAND-02; NX-20/NX-24 | EV-002 (`NX-01-evidence-index.md#ev-002` - local review reference, not published), EV-003 (`NX-01-evidence-index.md#ev-003` - local review reference, not published) | Member candidate remains PROPOSED_ONLY until explicit approval. |
| D-07 | Dat + Hung | Coach approval before AI output becomes a plan; reject/modify/version rules? | F5-REQ-01; NX-20 | EV-012 (`NX-01-evidence-index.md#ev-012` - local review reference, not published) | plan_review DDL is not an approved or working state machine. |
| D-08 | Dat + Phat | Adopt/defer Flow 6; allowed knowledge, identity data, retention and support boundary? | F6-REQ-01; NX-27 then approved NX-28..33 | EV-011 (`NX-01-evidence-index.md#ev-011` - local review reference, not published), EV-017 (`NX-01-evidence-index.md#ev-017` - local review reference, not published), EV-026 (`NX-01-evidence-index.md#ev-026` - local review reference, not published) | Approve scoped retrieval, source freshness, fallback and no unauthorized business action. |
| D-09 | Dat | Separate release milestones, scope signers and demo acceptance? | NX-00/NX-02 and later release gates | EV-002 (`NX-01-evidence-index.md#ev-002` - local review reference, not published), EV-003 (`NX-01-evidence-index.md#ev-003` - local review reference, not published), EV-032 (`NX-01-evidence-index.md#ev-032` - local review reference, not published) | Name business approvers and record dated decisions; report authors cannot approve. |
| D-10 | Dat + FE team | Exact signed methods/paths/DTOs/errors and compatibility? | NX-03/NX-27 and FE integration | EV-002 (`NX-01-evidence-index.md#ev-002` - local review reference, not published), EV-004 (`NX-01-evidence-index.md#ev-004` - local review reference, not published), EV-006 (`NX-01-evidence-index.md#ev-006` - local review reference, not published), EV-017 (`NX-01-evidence-index.md#ev-017` - local review reference, not published) | Agree source-backed existing reuse and new contracts after scenarios, before integration. |

## Preserved supplemental NX-00 questions

D-11 remains the independent optional Flow 4 adoption choice. D-12 remains the actor/action/field/state permission matrix including reassignment and historical/private notes. D-13 remains authoritative training linkage/state/correction and session-balance behavior. D-14 remains cross-AI privacy, consent, retention, cost, uncertainty and action boundaries. These IDs originated in NX-00, not in the task workbook. No decision is resolved here. EV-299 (`NX-01-evidence-index.md#ev-299` - local review reference, not published)

## Missing capabilities and proposed follow-up register

| Follow-up | Missing capability/deliverable | Existing seam | Owner | Approval/dependency | Validation |
| --- | --- | --- | --- | --- | --- |
| NX-02 | Approved scenarios, state and role/field ownership matrix | This evidence matrix | Dat + Hung + Truc | NX-00 acceptance; D-01..04/09 and D-11..13 | Review current-day arrival, self-training, absent/cancelled, personal/class plan and reassignment examples |
| NX-03 | Signed API/DTO/error/version/compatibility contracts | Existing auth/errors/array-vs-page behavior | Dat + affected FE owners | Approved NX-02 and D-10 | Contract checks for null/foreign IDs, visibility, paging and existing clients |
| NX-04 | Reviewed schema-to-design reconciliation, no duplicate tables | V16-V19 and existing identity/session/booking keys | Dat | Approved scenarios/API; CODEOWNER migration review | Disposable migration/entity/FK/uniqueness tests; no production data assumption |
| Approved Flow 4 plan task | Personal/class plan authoring/assignment/publish/version | session_plan/step scaffolding | Dat + Hung | D-02/D-12 and approved NX-03/04 | Owner/edit/version/member publication tests |
| Approved results/progress task | Session results, metrics, correction, member history/comments | V16 result/score/feedback | Dat + Truc + Hung | D-03/D-12/D-13 | Metric bounds, attendance binding, draft/private and foreign-owner tests |
| NX-09 subject to scope | Durable coach/self-training attendance and correction | Existing own roster; ACK; arrival separate | Dat + Hung | D-01/D-13 and approved contracts | Exactly-once, no second debit, cancellation and audit tests |
| NX-16 | Notices/homework scope and delivery/inbox if adopted | V19 definitions and current schedule events | Dat + Phat | D-04 first | Recipient isolation, delivery retry/dedup, no unintended PII |
| NX-17 and NX-34 | Training integration plus required Flow 1-3 regression | Current 1882 unit tests and tagged integration suites | Dat + CODEOWNER | Approved isolated SQL Server/CI environment | No skips; lock/race/rollback/refund/invoice and cancellation regressions |
| NX-20 then approved NX-21..26 | Coach AI workflow and safety/cost/review controls | Goals/level and V18 schema only | Dat + Hung | Independent optional adoption; D-05..07/D-14 | Outage, grounding, schema validation, consent, budget, review lifecycle |
| NX-24 | Member recommendation candidate only if approved | No approved official Member requirement | Dat + Truc | Explicit D-06 approval | Audience/ownership and Member-specific contract tests |
| NX-27 then approved NX-28..33 | Member assistant, scoped knowledge and fallback | Catalog/own schedule reads and V17 schema | Dat + Phat + Truc | Independent D-08/D-14 adoption and privacy approval | Prompt injection, cross-user denial, unsupported questions, missing context, retention |
| Approved maintenance task | Validation, history query/paging, logging/time conventions | NX01-F06..F09 | Dat | Separate scoped authorization and FE compatibility review | Negative payload, query-count, log-redaction and fixed-zone checks |

## Remaining unknowns and approval boundary

The exact XLSM is now available and was reconciled against the Round 1 XLSX; two Status-cell differences remain for human disposition. The official source workbook was checked directly, while translation/allocation still needs human review. No dated approval superseding NX-00's explicit unsigned state was found in the scoped handoff. Deployed schema/history, SQL Server race behavior, production clocks/logs and external services remain unverified. No AI provider, retention period, metric range, training schema change or endpoint is selected here.

All architecture suggestions are PROPOSED_ONLY and NOT IMPLEMENTED. Manual review of this package does not itself adopt an optional flow or approve future runtime work.

## Current source status conflict

The official XLSM is present in Downloads. Tasks EN O6 labels NX-00 Done where the prior XLSX says Ready; O7 labels NX-01 In Progress where the XLSX says Planned. Tasks VI remains unchanged. These are planning-status differences, not dated human scope acceptance. No owner, dependency, priority, DoD, optional capability, D-01..D-10 definition, API group or QA gate changed in the compared cells. Dat and CODEOWNER must resolve the status/sign-off record explicitly.
