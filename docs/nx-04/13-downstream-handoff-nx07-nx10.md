# Downstream handoff NX07 through NX10

Status: TECHNICAL_DRAFT_READY_FOR_CODEOWNER_REVIEW. All new physical/model/security choices are PROPOSED. Schema and API implementation remain BLOCKED_PENDING_APPROVAL. Use NX-03 logical operation IDs, not invented routes or field contracts.

| Consumer | Reviewable design input | Required decisions and implementation evidence |
| --- | --- | --- |
| NX-07 plans | Exact revision, personal/class assignment, audience alternatives, optional legacy origin and session use in 03-06 | D-02 all eight dimensions, D-12 scope/history/fields, D-10/G-01; chosen physical plan and migration approval; no dummy class or mutable published history |
| NX-08 results/progress | Reuse result/feedback parents; attributable revision snapshots; governed metrics only if adopted; private/member text separation | D-03 all seven dimensions, D-12 rights/correction/history/fields, D-10/G-01; resolve missing legacy actor/unit/version evidence; no automatic rating policy |
| NX-09 attendance | Existing arrival unchanged; V16 current attendance/correction scaffold; uniqueness alternatives and create versus correction races | D-01 boundary, D-13 all nine dimensions, D-12, D-10/G-01; signed identity and eligibility; no package debit, automatic booking, inferred absence or ACK backfill |
| NX-10 authorization/tests | Role/object/action/field predicates, threat matrix, 60 runtime cases and safe source guard | Signed deny/projection/disclosure contracts; service/repository defenses; concurrent reassignment and SQL Server disposable runtime evidence |
| FE Hung / Truc | Conditional current/history concepts and separate field allowlists | Named FE/BE version signatures; missing versus zero/unrecorded display, correction summary, own-history privacy, explicit arrival selection/retry UX |

Implementation must begin by fetching current main and verifying new signed evidence. Do not copy P_ candidate aliases into production merely because they appear in an ERD. Reconcile fresh Flyway versions (currently maximum V33) and migration ownership; no number is reserved. Keep existing migrations byte-for-byte semantically/checksum immutable. The approved physical choice may change diagram/dictionary/test expectations and must be updated as one reviewed package.

Future persistence services should derive actors from current principals, scope all reads before disclosure/counting, bind every mutation to authorized Member/session/Coach context, and atomically preserve current values plus lineage. Initial creates resolve competing submissions; existing-target updates compare expected-current revision. Member reads are projections and never require write tokens. New endpoints, request fields, status values, client retry keys, retention and correction durations stay TBD_AFTER_APPROVAL.

Before reporting runtime acceptance, attach SQL Server disposable target evidence, full migration/compatibility results and role/object/field adversarial tests. Local static guard and existing unit success validate only the delivered review package and regression baseline. They do not exercise a Flow 4 service that does not yet exist.

No AI export, homework/notification implementation or other task work is included. D-04 and D-14 remain independent future scopes. Human CODEOWNER @senko-do-thingz controls PR creation/review/merge and deployment; this execution creates no PR automatically.
