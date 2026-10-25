# Error taxonomy and compatibility

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

## Global candidate taxonomy and applicability

Candidate review inventory of14 categories, not exhaustive runtime errors, emitted error list or wire enum. Applicability is per operation.

| State | Consumer meaning |
| --- | --- |
| APPLICABLE_PROPOSED | Relevant proposed failure meaning; HTTP/status/body/retry/UI mapping unsigned. |
| POLICY_DEPENDENT | Potential failure requires the linked actual decision/dimension; no selected outcome. |
| OBSERVED_EXISTING | Static current source evidence and observed status/body reference; not a newly executed runtime result. |
| NOT_APPLICABLE | No emitted failure, HTTP/code/body, retry advice or UI action for this operation. |

## Required side by side comparison

| Category | F4-PLAN-04 | F4-PLAN-03 | F4-ATTEND-04 | F4-ATTEND-05 |
| --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | APPLICABLE_PROPOSED | OBSERVED_EXISTING | OBSERVED_EXISTING |
| ROLE_DENIED | POLICY_DEPENDENT | POLICY_DEPENDENT | OBSERVED_EXISTING | OBSERVED_EXISTING |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| MISSING_RESOURCE | POLICY_DEPENDENT | POLICY_DEPENDENT | OBSERVED_EXISTING | NOT_APPLICABLE |
| INVALID_INPUT | APPLICABLE_PROPOSED | APPLICABLE_PROPOSED | OBSERVED_EXISTING | OBSERVED_EXISTING |
| STALE_REVISION | NOT_APPLICABLE | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| INVALID_TRANSITION | NOT_APPLICABLE | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| LATE_CORRECTION | NOT_APPLICABLE | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| PAGE_OR_SORT_INVALID | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |
| CONCURRENT_MUTATION | NOT_APPLICABLE | POLICY_DEPENDENT | NOT_APPLICABLE | NOT_APPLICABLE |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | APPLICABLE_PROPOSED | OBSERVED_EXISTING | OBSERVED_EXISTING |

The Member read has query/ownership/visibility failures but no stale write, participation duplicate or correction errors. Coach publication is an existing-target mutation with conditional stale revision, lifecycle and late-edit policy. Staff POST has only source-grounded HTTP failures plus a separate201/DENIED business outcome. Staff GET validates its actual filters and does not invent pagination, mutation failures or a Member-existence404.

## Per operation applicability

### F4-PLAN-01

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Initial create races recheck current scope and any eventual uniqueness rule without requiring an existing resource revision. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-PLAN-02

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | POLICY_DEPENDENT | Existing target has changed since the authorized editor loaded it; reject stale A after B without choosing a version field or HTTP status. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | POLICY_DEPENDENT | Existing resource stage may restrict this edit/correction/publication; approved lifecycle and field allowlist are required. | [{"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | POLICY_DEPENDENT | Only emitted if the relevant human policy adopts a time restriction; no cutoff, exception or duration selected. | [{"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Reject stale expected-current context without lost update or partial correction/publication; mechanism remains unapproved. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-PLAN-03

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | POLICY_DEPENDENT | Existing target has changed since the authorized editor loaded it; reject stale A after B without choosing a version field or HTTP status. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | POLICY_DEPENDENT | Existing resource stage may restrict this edit/correction/publication; approved lifecycle and field allowlist are required. | [{"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | POLICY_DEPENDENT | Only emitted if the relevant human policy adopts a time restriction; no cutoff, exception or duration selected. | [{"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Reject stale expected-current context without lost update or partial correction/publication; mechanism remains unapproved. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-PLAN-04

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate selectors, parsing and allowed filters. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | History range and time representation require agreement; no write deadline or transition is imposed. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | POLICY_DEPENDENT | Select allowlisted filters, page/cursor and stable sort without leaking counts. No numeric defaults or limits adopted. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-RESULT-01

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Initial create races recheck current scope and any eventual uniqueness rule without requiring an existing resource revision. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-RESULT-02

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | POLICY_DEPENDENT | Existing target has changed since the authorized editor loaded it; reject stale A after B without choosing a version field or HTTP status. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | POLICY_DEPENDENT | Existing resource stage may restrict this edit/correction/publication; approved lifecycle and field allowlist are required. | [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | POLICY_DEPENDENT | Only emitted if the relevant human policy adopts a time restriction; no cutoff, exception or duration selected. | [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_WINDOW", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Reject stale expected-current context without lost update or partial correction/publication; mechanism remains unapproved. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-RESULT-03

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate selectors, parsing and allowed filters. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | History range and time representation require agreement; no write deadline or transition is imposed. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | POLICY_DEPENDENT | Select allowlisted filters, page/cursor and stable sort without leaking counts. No numeric defaults or limits adopted. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-ATTEND-01

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.UNIQUENESS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.TIMING_BOUNDARIES", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Initial create races recheck current scope and any eventual uniqueness rule without requiring an existing resource revision. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-ATTEND-02

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | POLICY_DEPENDENT | Existing target has changed since the authorized editor loaded it; reject stale A after B without choosing a version field or HTTP status. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.HISTORY", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.UNIQUENESS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | POLICY_DEPENDENT | Existing resource stage may restrict this edit/correction/publication; approved lifecycle and field allowlist are required. | [{"decision_id": "D-13", "dimension": "D-13.LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | POLICY_DEPENDENT | Only emitted if the relevant human policy adopts a time restriction; no cutoff, exception or duration selected. | [{"decision_id": "D-13", "dimension": "D-13.TIMING_BOUNDARIES", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.TIMING_BOUNDARIES", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Reject stale expected-current context without lost update or partial correction/publication; mechanism remains unapproved. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.HISTORY", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-ATTEND-03

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate selectors, parsing and allowed filters. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | History range and time representation require agreement; no write deadline or transition is imposed. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | POLICY_DEPENDENT | Select allowlisted filters, page/cursor and stable sort without leaking counts. No numeric defaults or limits adopted. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-ATTEND-04

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | OBSERVED_EXISTING | Current authenticated staff route and configured entrypoint. | [] | 401 | ["SRC-ENTRY", "SRC-CHECKIN", "SRC-ERRORS"] |
| ROLE_DENIED | OBSERVED_EXISTING | Only MANAGER or RECEPTIONIST may invoke this source route. | [] | 403 | ["SRC-DENIED", "SRC-CHECKIN", "SRC-ERRORS"] |
| RESOURCE_SCOPE_HIDDEN | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| MISSING_RESOURCE | OBSERVED_EXISTING | Unknown Member or a missing locked class session throws ResourceNotFoundException. | [] | 404 | ["SRC-ARRIVAL", "SRC-CHECKIN", "SRC-ERRORS"] |
| INVALID_INPUT | OBSERVED_EXISTING | NotBlank identifier, malformed body and type binding; no note-length or bookingId positivity inferred. | [] | 400 | ["SRC-ERRORS", "SRC-CHECKIN", "SRC-ERRORS"] |
| STALE_REVISION | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| TEMPORAL_MISMATCH | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Existing arrival has no participation/revision/correction API. Duplicate, cancellation, day/package eligibility and concurrent booking changes can produce a saved DENIED business result with201, not this HTTP error. | [] | None | [] |
| DEPENDENCY_FAILURE | OBSERVED_EXISTING | Uncaught repository/service failure can reach safe generic500 handler; no deployment/runtime claim. | [] | 500 | ["SRC-ERRORS", "SRC-CHECKIN", "SRC-ERRORS"] |

```json
[
  {
    "kind": "SAVED_BUSINESS_RESULT_NOT_HTTP_ERROR",
    "http_status": 201,
    "results": [
      "ALLOWED",
      "DENIED"
    ],
    "conditions": "Known Member arrival attempt; duplicate, booking-day/status/package eligibility may save DENIED; unknown Member or missing locked session instead404.",
    "denied_is_http_error": false,
    "supporting_evidence": [
      "SRC-CHECKIN",
      "SRC-ARRIVAL",
      "SRC-CHECKIN-RESPONSE"
    ]
  }
]
```

### F4-ATTEND-05

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | OBSERVED_EXISTING | Current authenticated staff route and configured entrypoint. | [] | 401 | ["SRC-ENTRY", "SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"] |
| ROLE_DENIED | OBSERVED_EXISTING | Only MANAGER or RECEPTIONIST may invoke this source route. | [] | 403 | ["SRC-DENIED", "SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"] |
| RESOURCE_SCOPE_HIDDEN | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| MISSING_RESOURCE | NOT_APPLICABLE | Source queries history directly and can return an empty list; no Member existence404 check is established for these filters. | [] | None | [] |
| INVALID_INPUT | OBSERVED_EXISTING | At least memberId or date; ISO date/Long parsing and storage date range. | [] | 400 | ["SRC-ERRORS", "SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"] |
| STALE_REVISION | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| TEMPORAL_MISMATCH | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Existing history is a non-mutating unpaged attempt list. No revision, participation eligibility, correction, duplicate-write or pagination failure is established. | [] | None | [] |
| DEPENDENCY_FAILURE | OBSERVED_EXISTING | Uncaught repository/service failure can reach safe generic500 handler; no deployment/runtime claim. | [] | 500 | ["SRC-ERRORS", "SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"] |

### F4-PROGRESS-01

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Initial creation has no existing target revision, prior-state transition or correction deadline. A conditional upsert is not specified or approved. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Initial create races recheck current scope and any eventual uniqueness rule without requiring an existing resource revision. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-PROGRESS-02

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate the selected body families and cross-field context, without invented wire names, ranges or enums. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | POLICY_DEPENDENT | Existing target has changed since the authorized editor loaded it; reject stale A after B without choosing a version field or HTTP status. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | POLICY_DEPENDENT | A repeated create/correction/publication submission needs operation identity/reconciliation; participation uniqueness applies only to participation writes. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_TRANSITION | POLICY_DEPENDENT | Existing resource stage may restrict this edit/correction/publication; approved lifecycle and field allowlist are required. | [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INELIGIBLE_ROSTER | POLICY_DEPENDENT | Where this operation binds a class/session target, revalidate permitted Coach/member/session context at mutation; no arbitrary roster or timing rule selected. | [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| LATE_CORRECTION | POLICY_DEPENDENT | Only emitted if the relevant human policy adopts a time restriction; no cutoff, exception or duration selected. | [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_WINDOW", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | Session/context timing and occurrence versus recorded-time semantics require human selection. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | NOT_APPLICABLE | No operation-specific emitted failure established for this category. | [] | None | [] |
| CONCURRENT_MUTATION | POLICY_DEPENDENT | Reject stale expected-current context without lost update or partial correction/publication; mechanism remains unapproved. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-PROGRESS-03

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate selectors, parsing and allowed filters. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | History range and time representation require agreement; no write deadline or transition is imposed. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | POLICY_DEPENDENT | Select allowlisted filters, page/cursor and stable sort without leaking counts. No numeric defaults or limits adopted. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

### F4-PROGRESS-04

| Category | Applicability | Reason | Decision / dimension | Observed HTTP only | Source |
| --- | --- | --- | --- | --- | --- |
| AUTH_MISSING_OR_EXPIRED | APPLICABLE_PROPOSED | Authenticate current principal before scoped access; future HTTP/body mapping remains unsigned. | [] | TBD_AFTER_APPROVAL | ["SRC-JWT", "SRC-ENTRY"] |
| ROLE_DENIED | POLICY_DEPENDENT | Role/action rights require a scoped business decision; no implicit Manager training privilege. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| RESOURCE_SCOPE_HIDDEN | POLICY_DEPENDENT | Deny peer or foreign-assignment data without an existence oracle; select403/404 semantics only after approval. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| MISSING_RESOURCE | POLICY_DEPENDENT | An explicitly selected authorized context can be missing; distinguish that from an empty authorized collection. Exact lookup/disclosure mapping is unsigned. | [{"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| INVALID_INPUT | APPLICABLE_PROPOSED | Validate selectors, parsing and allowed filters. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| STALE_REVISION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DUPLICATE_PARTICIPATION_OR_RETRY | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INVALID_TRANSITION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| INELIGIBLE_ROSTER | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| LATE_CORRECTION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| TEMPORAL_MISMATCH | POLICY_DEPENDENT | History range and time representation require agreement; no write deadline or transition is imposed. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| PAGE_OR_SORT_INVALID | POLICY_DEPENDENT | Select allowlisted filters, page/cursor and stable sort without leaking counts. No numeric defaults or limits adopted. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | [] |
| CONCURRENT_MUTATION | NOT_APPLICABLE | Pure history/projection read; no write, participation submission, correction window or state transition is performed. | [] | None | [] |
| DEPENDENCY_FAILURE | APPLICABLE_PROPOSED | Return a client-safe failure and no fabricated accepted data; future HTTP/code/retry and atomicity remain unsigned. | [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}] | TBD_AFTER_APPROVAL | ["SRC-ERRORS"] |

Generic observed handler409/405 mappings remain project reference behavior; they are not automatically assigned to a route. New applicable/policy-dependent meanings have TBD_AFTER_APPROVAL mappings.403-versus404 privacy semantics require D-12/D-10; no source-derived status is automatically an approved future training status. NOT_APPLICABLE has null status/code/body/retry/UI fields and emitted_failure=false.
