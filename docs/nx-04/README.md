# NX-04 Flow 4 data model and authorization design

Status: TECHNICAL_DRAFT_READY_FOR_CODEOWNER_REVIEW. The complete design is available for review; physical schema, new APIs and new role grants remain BLOCKED_PENDING_APPROVAL. No production application or migration file is changed. Recommendations are PROPOSED, not signed business selections.

| Area | Result |
| --- | --- |
| Scope/current source and reuse audit | Complete at origin/main 4cdc0874fde95ae0b1500f0625c1521f428a6bec; 27 standard + 2 demo migrations, 62 CREATE TABLE declarations, 27 JPA entities; not a deployed census |
| Conceptual/logical/physical options | Three Mermaid ERDs, dictionary, two persistence alternatives and hybrid reuse recommendation |
| RBAC and integrity | Four operation families, six actor columns, row/field/history predicates, concurrency and finance invariants |
| Business adoption and policy | G-00/D-11 and D-01/02/03/12/13 plus 32 required dimensions pending |
| FE/BE contract | D-10/G-01 pending, NX-03 technical remediation is not a signature |
| Physical migration approval/runtime | NOT_FOUND / NOT_RUN_APPROVAL_BLOCKED; no disposable local SQL Server target established |
| Validation | EXECUTED_PASS: 1,882 Maven unit tests and 62 shared Python tests; focused NX-04 suite 24 tests including 23 negative controls; three ERDs parsed/rendered; no failures/errors/skips |
| Publication/review | Dedicated NX-04 branch only; commit/push/CI evidence retained separately; human CODEOWNER controls PR and merge |

## Contents

- [01 Scope and source baseline](01-scope-and-source-baseline.md)
- [02 Existing schema reuse inventory](02-existing-schema-reuse-inventory.md)
- [03 Conceptual data model](03-conceptual-data-model.md)
- [04 Logical ERD and relationships](04-logical-erd-and-relationships.md)
- [05 Physical design options](05-physical-design-options.md)
- [06 Data dictionary and field classification](06-data-dictionary-and-field-classification.md)
- [07 Ownership RBAC and privacy matrix](07-ownership-rbac-and-privacy-matrix.md)
- [08 Integrity uniqueness and concurrency](08-integrity-uniqueness-and-concurrency.md)
- [09 Migration plan and deployment gates](09-migration-plan-and-deployment-gates.md)
- [10 Rollback and compatibility strategy](10-rollback-and-compatibility-strategy.md)
- [11 Test specifications and negative controls](11-test-specifications-and-negative-controls.md)
- [12 Decision dependency and approval matrix](12-decision-dependency-and-approval-matrix.md)
- [13 Downstream handoff NX07 through NX10](13-downstream-handoff-nx07-nx10.md)
- [14 Review checklist and signoff](14-review-checklist-and-signoff.md)
- [Curated source inventory and immutable SQL fingerprints](source-inventory.json)
- [Proposed physical model validation contract](design-contract.json)

## Recommendation and limits

Reuse V16 current attendance/result/feedback identities and preserve Receptionist arrival behavior. Add separate plan identity/assignment/revision/session-use and attributable result/feedback history only after the exact physical choice is approved. Alternative extending V16 in place has fewer objects but greater semantic/nullability and historical-content risks. Separate identities must not create dual authoritative attendance or plan writers. Personal Coach scope, audience snapshots versus dynamic enrollment, historical rights, metrics/units, correction windows, retention, timing and current-row identity remain human decisions.

Source and static checks cannot prove deployed SQL constraints, engine collation/isolation, row distributions, query plans, lock duration, backups or future runtime authorization. Sixty traceable runtime specifications are SPECIFIED_NOT_EXECUTED. Current authoring uses English/ASCII, no live DB writes and no Docker. Internal evidence and audit artifacts are excluded from publication.

## Executed authoring validation

On 2026-10-26, the static guard passed and Mermaid 11.12.0 parsed and rendered all three ERDs, which were visually inspected; an intentionally malformed relationship was rejected. The NX-04 self-tests passed 24 tests: a clean package control and 23 independent mutations of actual inputs covering authorization, decisions, immutable migrations, traceability, links, classification and private paths. The shared Python suite passed 62 tests, including that focused suite. Existing Maven `test` passed 1,882 tests with zero failures/errors/skips, the independent unit report guard confirmed those counters, and `mvn -B -ntp -DskipUnitTests=true -DskipITs=true package` completed with exit 0. All commands returned exit 0. Packaging intentionally reused completed unit evidence and did not run integrations.

New runtime migration/authorization cases remain NOT_RUN_APPROVAL_BLOCKED; full local Maven verify was not eligible because integration containers require unavailable Docker and new migration execution lacks signed scope/disposable target. CI runs and their actual commit SHAs are reported in the publication handoff, without treating CI success as a business signature.
