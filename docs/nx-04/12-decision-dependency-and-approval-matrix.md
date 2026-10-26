# Decision dependencies and approval evidence

Status: BLOCKED_PENDING_APPROVAL for schema and application implementation. Evidence examined on 2026-10-26 against the baseline in [scope](01-scope-and-source-baseline.md). This matrix was established before proposing the model. Repository publication is authorized by this assignment; it is not a business signature.

| Gate or decision | Required authority | Exact evidence examined | Evidence date and scope | Finding |
| --- | --- | --- | --- | --- |
| G-00 / D-11 optional Flow 4 adoption | Named business authority, Dat, CODEOWNER | [NX-00 decision registry](../requirements/nx-00/05-conflicts-decisions-and-open-questions.md), D-11; task XLSM QA Gates!A6:H6 | XLSM and unsigned published registry inspected 2026-10-26; no dated adoption signature | PENDING |
| D-01 arrival versus participation | Dat, business authority, Hung, CODEOWNER | [NX-02 boundary](../nx-02/NX-02-attendance-check-in-boundary.md), D-01 options | Published round 3 design; no signed selected option | PENDING |
| D-02 plan policy | Dat, Hung, Truc for historical projection; business/privacy authority | [NX-02 completeness](../nx-02/NX-02-approval-completeness-matrix.md), D-02 sections | Eight dimensions and parent unresolved; no signed exception found | PENDING |
| D-03 metrics and corrections | Dat, Hung, Truc; metric steward and business/privacy authority to be named | Same completeness artifact, D-03 sections | Seven dimensions and parent unresolved | PENDING |
| D-12 record and field rights | Dat, FE owners, named business/privacy authority | Same completeness artifact, D-12 sections | Eight dimensions and parent unresolved | PENDING |
| D-13 attendance and financial boundaries | Dat, Hung, business authority, CODEOWNER | Same completeness artifact, D-13 sections | Nine dimensions and parent unresolved | PENDING |
| G-01 / D-10 FE/BE contract | Dat BE, Hung Coach/desk FE, Truc Member FE, business/privacy reviewers, CODEOWNER | [NX-03 review package](../nx-03/NX-03-fe-be-review-package.md), [handoff](../nx-03/NX-03-downstream-nx04-nx07-nx10-handoff.md); XLSM API Contracts rows 6-11 and QA Gates row 7 | Round 2 technical remediation accepted; fields, routes, errors and signatures pending | PENDING |
| NX-04 exact physical migration | Dat and CODEOWNER; business/privacy authority for changed semantics | Current docs/nx-04 design and repository approval search; workbook Tasks EN!Q10 | No authorized migration identifier, signed version/hash, date or scope found | NOT_FOUND |
| D-14 AI processing | Separate Flow 5/6 business/privacy/budget authorities | [NX-02 D-14](../nx-02/NX-02-decision-register.md) | AI adoption/export is outside NX-04; no inherited approval | PENDING, NOT_APPLICABLE_TO_THIS_DESIGN |

No record qualifies as VERIFIED_APPROVED or VERIFIED_SIGNED_DEFERRAL. This is a bounded search of the supplied workbook, current tracked design records and local prerequisite handoffs, not a claim about inaccessible chat/email. Task status Planned/Done, merged documentation and green CI cannot supply a signature. Authority for business/privacy policy must be named; repository CODEOWNER is evidenced in [CODEOWNERS](../../.github/CODEOWNERS).

## Required composite dimensions

Every item below remains PENDING. Each must receive a signed selection or VERIFIED_SIGNED_DEFERRAL identifying the reduced capability and exact scope. A parent approval alone is insufficient.

| Decision | Required dimension IDs | NX-04 impact |
| --- | --- | --- |
| D-02 | PLAN_SCOPE, RECIPIENTS, PUBLISH_AUTHORITY, REVISION_LIFECYCLE, EDITABLE_FIELDS, HISTORICAL_ACCESS, SESSION_RELINK, TEMPLATE_ORIGIN | Identity, audience, preserved revision and session-use records |
| D-03 | METRICS, VALIDATION, CORRECTION_MECHANISM, CORRECTION_WINDOW, RETENTION, COMMENT_VISIBILITY, RESULT_HISTORY | Result values, revision lineage, note separation and disposal |
| D-12 | RESOURCE_RIGHTS, FIELD_VISIBILITY, REASSIGNMENT, HISTORIC_ENTITLEMENT, CORRECTION_RIGHTS, AUDIT_ACCESS, MEMBER_CORRECTION_VIEW, PRIVACY_OPERATIONS | Object predicates, field allowlists and history access |
| D-13 | STATE_SEMANTICS, LIFECYCLE, ELIGIBILITY, TIMING_BOUNDARIES, UNIQUENESS, CORRECTION_AUTHORITY, HISTORY, VISIBILITY_IMPACT, FINANCIAL_INVARIANTS | Current-row identity, state transitions, timing and no financial coupling |

Fully qualified IDs use the parent prefix, for example D-02.PLAN_SCOPE and D-13.UNIQUENESS, as in the NX-02 artifact. D-01 has no fabricated composite subdecision set. D-10 also owns time serialization, revision wire tokens, error/disclosure behavior and pagination. D-04 homework/notifications remains outside this storage package.

## Exact evidence needed to open implementation

Record approver name and authority, timestamp with zone, explicit Go or scoped deferral, all applicable dimension selections, reviewed document Git SHA/hash, physical object/column/index list, existing-data disposition, deployment target classification and recovery approval. Attach FE and BE contract version signatures separately from migration review. Re-fetch main, recheck overlapping migrations and regenerate impact tests before coding. Until then no executable DDL, mappings, training API, new grants, data backfill or database tests are authorized.
