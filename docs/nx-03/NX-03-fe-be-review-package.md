# FE and BE review package

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

Dat reviews Backend; Hung reviews Coach/Receptionist FE; Truc reviews Member FE. Phat joins D-04/cross-flow discussion. Actual business/privacy authority and CODEOWNER must decide the scoped policies. No review messages were sent.

| State | Consumer meaning |
| --- | --- |
| APPLICABLE_PROPOSED | Relevant proposed failure meaning; HTTP/status/body/retry/UI mapping unsigned. |
| POLICY_DEPENDENT | Potential failure requires the linked actual decision/dimension; no selected outcome. |
| OBSERVED_EXISTING | Static current source evidence and observed status/body reference; not a newly executed runtime result. |
| NOT_APPLICABLE | No emitted failure, HTTP/code/body, retry advice or UI action for this operation. |

## RV-PLAN-01 F4-PLAN-01

- Intent: Create scoped draft with permitted activity ordering; no publication implied.
- Kind: CREATE
- Requirements: ["F4-REQ-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C"]
- Scenarios: ["AS-01", "AS-02"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-001", "NX03-TC-002", "NX03-TC-003", "NX03-TC-004", "NX03-TC-005"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PLAN-02 F4-PLAN-02

- Intent: Edit permitted draft fields and origin linkage without silently changing published history.
- Kind: UPDATE_OR_CORRECTION
- Requirements: ["F4-REQ-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Scenarios: ["AS-01", "AS-02", "AS-11"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-006", "NX03-TC-007", "NX03-TC-008", "NX03-TC-009", "NX03-TC-010", "NX03-TC-091"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PLAN-03 F4-PLAN-03

- Intent: Publish or revise only after current publisher authority and audience/revision checks.
- Kind: PUBLISH_OR_REVISE
- Requirements: ["F4-REQ-01"]
- Use cases: ["UC-F4-01V"]
- Scenarios: ["AS-11"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-011", "NX03-TC-012", "NX03-TC-013", "NX03-TC-014", "NX03-TC-015", "NX03-TC-081"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PLAN-04 F4-PLAN-04

- Intent: Expose only entitled personal or class material, with no peer roster or private notes.
- Kind: READ
- Requirements: ["F4-REQ-06"]
- Use cases: ["UC-F4-06"]
- Scenarios: ["AS-03", "AS-10"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"}}
- Reviewer: ["Truc Member screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-016", "NX03-TC-017", "NX03-TC-018", "NX03-TC-019", "NX03-TC-020", "NX03-TC-082", "NX03-TC-090"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-RESULT-01 F4-RESULT-01

- Intent: Record observations for the authorized session/member with approved metric meanings.
- Kind: CREATE
- Requirements: ["F4-REQ-02"]
- Use cases: ["UC-F4-02"]
- Scenarios: ["AS-04"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-021", "NX03-TC-022", "NX03-TC-023", "NX03-TC-024", "NX03-TC-025", "NX03-TC-083"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-RESULT-02 F4-RESULT-02

- Intent: Correct an authorized result with traceable reason and no lost update.
- Kind: UPDATE_OR_CORRECTION
- Requirements: ["F4-REQ-02"]
- Use cases: ["UC-F4-02"]
- Scenarios: ["AS-04"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-026", "NX03-TC-027", "NX03-TC-028", "NX03-TC-029", "NX03-TC-030", "NX03-TC-084"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-RESULT-03 F4-RESULT-03

- Intent: Read own permitted outcomes and correction projection, distinguishing missing from zero.
- Kind: READ
- Requirements: ["F4-REQ-05"]
- Use cases: ["UC-F4-05"]
- Scenarios: ["AS-09"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"}}
- Reviewer: ["Truc Member screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-031", "NX03-TC-032", "NX03-TC-033", "NX03-TC-034", "NX03-TC-035"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-ATTEND-01 F4-ATTEND-01

- Intent: Record actual participation; never infer it from booking, arrival or acknowledgement.
- Kind: CREATE
- Requirements: ["F4-REQ-04"]
- Use cases: ["UC-F4-04"]
- Scenarios: ["AS-06"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-036", "NX03-TC-037", "NX03-TC-038", "NX03-TC-039", "NX03-TC-040", "NX03-TC-085", "NX03-TC-086"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-ATTEND-02 F4-ATTEND-02

- Intent: Apply authorized participation correction with reason and preserved prior context.
- Kind: UPDATE_OR_CORRECTION
- Requirements: ["F4-REQ-04"]
- Use cases: ["UC-F4-04C"]
- Scenarios: ["AS-07"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.HISTORY", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-041", "NX03-TC-042", "NX03-TC-043", "NX03-TC-044", "NX03-TC-045", "NX03-TC-087"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-ATTEND-03 F4-ATTEND-03

- Intent: Read own permitted participation with arrival and unknown/unrecorded meanings distinguished.
- Kind: READ
- Requirements: ["F4-REQ-05"]
- Use cases: ["UC-F4-05"]
- Scenarios: ["AS-09"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"}}
- Reviewer: ["Truc Member screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-046", "NX03-TC-047", "NX03-TC-048", "NX03-TC-049", "NX03-TC-050"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-ATTEND-04 F4-ATTEND-04

- Intent: Reuse current staff check-in unchanged; no Coach attendance authority or second debit.
- Kind: EXISTING_WRITE
- Requirements: ["F4-REQ-07"]
- Use cases: ["UC-F4-07"]
- Scenarios: ["AS-08"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "OBSERVED_PRIOR_ALLOWED_RECHECK", "meaning": "Session lock, refresh booking/session/package, then recheck mutable booking and prior ALLOWED. No expected revision input.", "mode": "EXISTING_SESSION_LOCK_RECHECK", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"], "write_guard": "OBSERVED_SESSION_LOCK"}, "idempotency_profile": {"client_key": "NOT_OBSERVED", "identical_response_replay": false, "meaning": "Current deduplication guard concerns prior ALLOWED; all attempts can be recorded. No identical-success replay contract.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"], "write_requirement": "NO_NEW_REQUIREMENT"}, "response_profile": {"business_result_http_status": 201, "container": "EXISTING_SINGLE_DTO", "denied_is_http_error": false, "meaning": "Single CheckInResponse on201 includes result ALLOWED or DENIED. DENIED is a saved business result, not an HTTP failure.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-RESPONSE"]}, "retry_profile": {"meaning": "Explicit booking retry may save DENIED with201. Omitting booking selection may choose another unchecked confirmed booking that day.", "mode": "EXISTING_ARRIVAL_ATTEMPTS", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"], "write_retry": "OBSERVED_NON_IDENTICAL_ATTEMPTS"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-051", "NX03-TC-052", "NX03-TC-053", "NX03-TC-054", "NX03-TC-055", "NX03-TC-092"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-ATTEND-05 F4-ATTEND-05

- Intent: Reuse current staff arrival history to confirm visit attempts, not Member participation.
- Kind: EXISTING_READ
- Requirements: ["F4-REQ-07"]
- Use cases: ["UC-F4-07"]
- Scenarios: ["AS-08"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY"], "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"}, "response_profile": {"container": "EXISTING_LIST", "meaning": "Existing authorized list may be empty and contains visit attempts, not participation.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"]}, "retry_profile": {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY"], "write_retry": "NOT_APPLICABLE"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-056", "NX03-TC-057", "NX03-TC-058", "NX03-TC-059", "NX03-TC-060"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PROGRESS-01 F4-PROGRESS-01

- Intent: Evaluate authorized observations and intentionally classify the permitted comment audience.
- Kind: CREATE
- Requirements: ["F4-REQ-03"]
- Use cases: ["UC-F4-03"]
- Scenarios: ["AS-05"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-061", "NX03-TC-062", "NX03-TC-063", "NX03-TC-064", "NX03-TC-065", "NX03-TC-089"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PROGRESS-02 F4-PROGRESS-02

- Intent: Correct permitted evaluation/feedback with attribution and privacy review.
- Kind: UPDATE_OR_CORRECTION
- Requirements: ["F4-REQ-03"]
- Use cases: ["UC-F4-03"]
- Scenarios: ["AS-05"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"}, "idempotency_profile": {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-066", "NX03-TC-067", "NX03-TC-068", "NX03-TC-069", "NX03-TC-070"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PROGRESS-03 F4-PROGRESS-03

- Intent: Read permitted history to support evaluation; current roster is not blanket historical access.
- Kind: READ
- Requirements: ["F4-REQ-03"]
- Use cases: ["UC-F4-03"]
- Scenarios: ["AS-05"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"}}
- Reviewer: ["Hung Coach/Reception screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-071", "NX03-TC-072", "NX03-TC-073", "NX03-TC-074", "NX03-TC-075"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## RV-PROGRESS-04 F4-PROGRESS-04

- Intent: Read own permitted evaluations/comments without staff-only narratives or peer data.
- Kind: READ
- Requirements: ["F4-REQ-06"]
- Use cases: ["UC-F4-06"]
- Scenarios: ["AS-03", "AS-10"]
- Question: Confirm operation-specific fields, empty/denied presentation, and only applicable failures. Do not implement UI error branches for NOT_APPLICABLE. For existing arrival distinguish saved DENIED201 from HTTP failures; for creation do not request an existing revision.
- Profiles: {"concurrency_profile": {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"}, "idempotency_profile": {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"}, "response_profile": {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "retry_profile": {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"}}
- Reviewer: ["Truc Member screens", "Dat BE", "Named business/privacy authority", "CODEOWNER"]
- Test ids: ["NX03-TC-076", "NX03-TC-077", "NX03-TC-078", "NX03-TC-079", "NX03-TC-080", "NX03-TC-088"]
- Approval: FE_REVIEW_PENDING / BE_REVIEW_PENDING
- Signature: Name ____ Role ____ Date/timezone ____ Exact document SHA ____ Option ____ Scope ____ Exceptions ____ Reference ____

## NX03-CD-01 Envelope and field names

- Options: Use project PageResponse on new collections versus purpose-specific projection
- Benefits risks: PageResponse consistency reduces FE adapters; dedicated projection minimizes fields but adds shapes.
- Recommendation: {"status": "NON_BINDING", "text": "TBD_AFTER_APPROVAL"}
- Reviewers: Dat, Hung, Truc
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-02 Presence unknown fields and precision

- Options: Reject unknown fields versus ignore safely; explicit missing/null distinction
- Benefits risks: Reject aids typo detection; ignore eases additive evolution but must never allow overposting. Units/precision require D-03.
- Recommendation: {"status": "NON_BINDING", "text": "Reject unknown mutation fields for review only; no runtime rule adopted"}
- Reviewers: Dat, Hung, Truc
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-03 Error and existence privacy

- Options: 403 for known forbidden objects versus indistinguishable404 for protected scope
- Benefits risks: 403 supports recovery;404 reduces existence disclosure. A consistent scoped lookup must cover counts and timing.
- Recommendation: {"status": "NON_BINDING", "text": "Uniform privacy-preserving policy for review; exact mapping unresolved"}
- Reviewers: Dat, privacy authority, Hung, Truc
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-04 Time representation

- Options: UTC instant plus facility zone versus explicit local date/time with offset
- Benefits risks: Instants support ordering; local schedule meaning needs facility context and gap/fold validity.
- Recommendation: {"status": "NON_BINDING", "text": "TBD_AFTER_APPROVAL"}
- Reviewers: Dat, Hung, Truc
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-05 Pagination and sort

- Options: Bounded zero-based pages versus opaque cursor with stable tie key
- Benefits risks: Pages fit observed wrappers; cursor resists shifting history but adds opaque-token lifecycle.
- Recommendation: {"status": "NON_BINDING", "text": "TBD_AFTER_APPROVAL"}
- Reviewers: Dat, Truc, Hung
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-06 Mutation precondition

- Options: Expected revision in body versus conditional request header
- Benefits risks: Body avoids current CORS header changes; header conventions aid intermediaries but current allowed/exposed headers need review.
- Recommendation: {"status": "NON_BINDING", "text": "Atomic expected-current precondition for review; no header chosen"}
- Reviewers: Dat, Hung
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-07 Retry identity and uniqueness

- Options: Semantic operation identity versus approved idempotency token
- Benefits risks: Tokens aid retry but need retention/scope/replay policy and CORS review; semantic keys must not conflate two sessions.
- Recommendation: {"status": "NON_BINDING", "text": "TBD_AFTER_APPROVAL"}
- Reviewers: Dat, Hung
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.

## NX03-CD-08 Audit consistency and cache

- Options: Atomic attributable audit versus reliable deferred audit; no-store candidate
- Benefits risks: Atomic audit helps accountability but availability changes; deferred audit needs failure/recovery policy. Sensitive cache must remain principal-scoped.
- Recommendation: {"status": "NON_BINDING", "text": "TBD_AFTER_APPROVAL"}
- Reviewers: Dat, privacy authority, CODEOWNER
- Requested decision: Select scoped field/error/permission option, constraints and exceptions; attach dated business and FE/BE evidence for this exact version.
- Acceptance: Exercise linked positive, denial, correction and error cases with the selected field/projection policy.
- Impact fe: Fields, labels, empty/denied states and retry UX cannot be fixed until signed.
- Impact be: Do not implement DTO/router/validator before contract sign-off.
- Impact nx04: Reconcile identity, revision, history and audit constraints without migration in NX03.
- Signature: {"date_timezone": null, "document_sha256": null, "exceptions": null, "external_reference": null, "name": null, "role": null, "scope": null, "selected_option": null}
- Semantic remediation note: Review only operation-applicable errors and kind-specific validation/retry. NOT_APPLICABLE has no emitted failure, status, retry advice or UI action. Existing201/DENIED is a saved result, never a proposed HTTP error.
