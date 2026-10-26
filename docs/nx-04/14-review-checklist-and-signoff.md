# Review checklist and signoff

Status: TECHNICAL_DRAFT_READY_FOR_CODEOWNER_REVIEW. The checks below distinguish completed technical preparation from pending human acceptance. Blank review fields are not signatures.

| Review item | Technical preparation | Human acceptance |
| --- | --- | --- |
| Source/workbook scope and NX-00..03 reconciliation | Complete, pinned current source and workbook cells in 01/02 | Pending CODEOWNER review |
| Reuse alternatives and three model levels | Complete, PROPOSED diagrams and dictionary in 03-06 | D-02/D-03/D-13 selections pending |
| Authorization/object/history/field privacy | Complete, matrix and threats in 07 | D-12 and G-01 pending |
| Integrity, create races and stale corrections | Complete, candidate invariants in 08 | Identity/lifecycle/correction policy pending |
| Forward migration and rollback/compatibility | Complete plan in 09/10; no executable DDL | Specific migration approval NOT_FOUND |
| Test design and negative controls | 60 runtime specifications plus static verifier/self-tests in 11 | Runtime acceptance blocked pending signed scope and disposable SQL Server |
| Publication boundary | Curated docs and non-destructive CI checks only | Merge/release remain CODEOWNER controlled |

## Findings for owner disposition

| ID | Severity | Finding and reproduction | Disposition / decision |
| --- | --- | --- | --- |
| NX04-F01 | BLOCKER | No signed G-00/D-11, required policy dimensions or G-01 in 12 | Complete design; block schema/API implementation until named authorities sign exact scope |
| NX04-F02 | BLOCKER | No specific migration authorization or disposable SQL Server target | Keep migration runtime SPECIFIED_NOT_EXECUTED; environment owner must attest test target before coding/tests |
| NX04-F03 | HIGH | V16 required class plan cannot model personal-only assignment/history; inspect S04 | Review Option A/B and signed reduced-scope alternative; additive identity recommendation is PROPOSED |
| NX04-F04 | HIGH | Independent attendance/booking/result FKs do not establish matching Member/session/Coach; inspect S04 | Require explicit binding predicate and approved identity; no runtime training service currently found |
| NX04-F05 | HIGH | No result actor/version/visibility; feedback private/public text unclassified; audit details not globally redacted | Approve D-03/D-12 and attribution/backfill handling before publication or data migration |
| NX04-F06 | MEDIUM | Legacy timestamps are server-local defaults and app uses facility-local Clock; actual DB timezone unknown | No guessed UTC backfill; D-10 time contract and disposable precision/time tests required |
| NX04-F07 | LOW | Existing main CI only pushes on main; feature push alone does not run it | Dispatch existing CI on the published NX-04 ref and verify exact head SHA; do not change workflow grants |

## Human review record

CODEOWNER: pending name/date/timezone, exact reviewed Git SHA and accept/request changes/hold outcome.

Business authority: pending name/authority, D-11 Go/defer/not-in-scope, selected parent policies and all 32 dimension dispositions or signed scoped exceptions.

FE Coach/desk Hung: pending exact contract version/hash, scope and date. FE Member Truc: pending exact projection/history contract version/hash and date. BE Dat: pending matching contract and physical proposal review.

Privacy/metric/environment/finance authorities: pending named authority and applicable field, metric, time, disposal, test target and compatibility decisions.

Migration authorization: pending exact object/column/constraint/index scope, version/hash, backfill/roll-forward plan, disposable environment and deployment/recovery owner. Approval to review documentation or run static CI does not authorize a migration.

## Ready-to-copy PR text for human use

Title: `docs(nx-04): design Flow 4 persistence and authorization boundaries`

Body: Flow 4's existing V16 scaffold lacks personal plan scope, preserved revisions, result attribution and approved privacy/history semantics. This change publishes the current-source reuse audit, conceptual/logical/physical proposals, field dictionary, RBAC and integrity design, gated migration/rollback strategy, and 60 traceable runtime specifications. Non-destructive CI checks validate source fingerprints, links, decision/authorization labels and model traceability with mutation controls. No production source, migration, endpoint or role grant is changed. G-00/business, G-01 FE/BE, exact migration approval and disposable SQL Server runtime evidence remain pending. See README for executed validation and limitations. CODEOWNER decides review/merge; this text does not create a PR.
