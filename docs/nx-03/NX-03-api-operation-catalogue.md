# API logical operation catalogue

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

| Operation | Semantic kind | Actor | Requirements / direct scenarios | Route status |
| --- | --- | --- | --- | --- |
| F4-PLAN-01 Author personal or class draft | CREATE | Coach | F4-REQ-01, AS-01, AS-02 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PLAN-02 Edit permitted draft content | UPDATE_OR_CORRECTION | Coach | F4-REQ-01, AS-01, AS-02, AS-11 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PLAN-03 Publish or revise authorized plan | PUBLISH_OR_REVISE | Coach | F4-REQ-01, AS-11 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PLAN-04 Read own permitted plans and revisions | READ | Member | F4-REQ-06, AS-03, AS-10 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-RESULT-01 Record session outcome | CREATE | Coach | F4-REQ-02, AS-04 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-RESULT-02 Correct session outcome | UPDATE_OR_CORRECTION | Coach | F4-REQ-02, AS-04 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-RESULT-03 Read own outcome history | READ | Member | F4-REQ-05, AS-09 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-01 Record Coach participation | CREATE | Coach | F4-REQ-04, AS-06 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-02 Correct Coach participation | UPDATE_OR_CORRECTION | Coach | F4-REQ-04, AS-07 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-03 Read own participation history | READ | Member | F4-REQ-05, AS-09 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-04 Reuse staff arrival recording | EXISTING_WRITE | Receptionist | F4-REQ-07, AS-08 | POST /api/v1/check-ins |
| F4-ATTEND-05 Reuse staff arrival history | EXISTING_READ | Receptionist | F4-REQ-07, AS-08 | GET /api/v1/check-ins |
| F4-PROGRESS-01 Evaluate progress and write feedback | CREATE | Coach | F4-REQ-03, AS-05 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PROGRESS-02 Correct progress assessment | UPDATE_OR_CORRECTION | Coach | F4-REQ-03, AS-05 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PROGRESS-03 Review scoped training history | READ | Coach | F4-REQ-03, AS-05 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PROGRESS-04 Read own visible feedback | READ | Member | F4-REQ-06, AS-03, AS-10 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |

## Complete per operation records

Canonical model version2.0.0 is an internal documentation format, not an approved API version. Supporting evidence is abbreviated below by source ID; exact file/symbol/range/SHA is retained in each source_observation and the source ledger. No legacy universal errors or generic validation dictionary is consumed.

### F4-PLAN-01 Author personal or class draft

| Contract field | Value |
| --- | --- |
| operation_id | F4-PLAN-01 |
| group | F4-PLAN |
| name | Author personal or class draft |
| business_intent | Create scoped draft with permitted activity ordering; no publication implied. |
| requirements | ["F4-REQ-01"] |
| use_cases | ["UC-F4-01P", "UC-F4-01C"] |
| acceptance_scenarios | ["AS-01", "AS-02"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["PLAN.TARGET", "PLAN.ACTIVITIES", "PLAN.AUDIENCE"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-02", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-02.PLAN_SCOPE", "D-02.RECIPIENTS", "D-02.PUBLISH_AUTHORITY", "D-02.REVISION_LIFECYCLE", "D-02.EDITABLE_FIELDS", "D-02.HISTORICAL_ACCESS", "D-02.SESSION_RELINK", "D-02.TEMPLATE_ORIGIN", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PLAN-01", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-001", "NX03-TC-002", "NX03-TC-003", "NX03-TC-004", "NX03-TC-005"] |
| example_ids | ["F4-PLAN-valid", "F4-PLAN-empty", "F4-PLAN-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Create scoped draft with permitted activity ordering; no publication implied. |
| semantic_kind | CREATE |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-001", "NX03-TC-002", "NX03-TC-003", "NX03-TC-004", "NX03-TC-005"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["PLAN.TARGET", "PLAN.ACTIVITIES", "PLAN.AUDIENCE"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-PLAN-02 Edit permitted draft content

| Contract field | Value |
| --- | --- |
| operation_id | F4-PLAN-02 |
| group | F4-PLAN |
| name | Edit permitted draft content |
| business_intent | Edit permitted draft fields and origin linkage without silently changing published history. |
| requirements | ["F4-REQ-01"] |
| use_cases | ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"] |
| acceptance_scenarios | ["AS-01", "AS-02", "AS-11"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["PLAN.TARGET", "PLAN.ACTIVITIES", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-02", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-02.PLAN_SCOPE", "D-02.RECIPIENTS", "D-02.PUBLISH_AUTHORITY", "D-02.REVISION_LIFECYCLE", "D-02.EDITABLE_FIELDS", "D-02.HISTORICAL_ACCESS", "D-02.SESSION_RELINK", "D-02.TEMPLATE_ORIGIN", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.HISTORIC_ENTITLEMENT", "D-12.CORRECTION_RIGHTS", "D-12.AUDIT_ACCESS", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PLAN-02", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-006", "NX03-TC-007", "NX03-TC-008", "NX03-TC-009", "NX03-TC-010", "NX03-TC-091"] |
| example_ids | ["F4-PLAN-valid", "F4-PLAN-empty", "F4-PLAN-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Edit permitted draft fields and origin linkage without silently changing published history. |
| semantic_kind | UPDATE_OR_CORRECTION |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-006", "NX03-TC-007", "NX03-TC-008", "NX03-TC-009", "NX03-TC-010", "NX03-TC-091"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["PLAN.TARGET", "PLAN.ACTIVITIES", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "Time-bound rights only if selected by humans; no deadline chosen.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "Permitted existing-resource edit/correction/publication stage requires approved policy.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"} |
| retry_profile | {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-PLAN-03 Publish or revise authorized plan

| Contract field | Value |
| --- | --- |
| operation_id | F4-PLAN-03 |
| group | F4-PLAN |
| name | Publish or revise authorized plan |
| business_intent | Publish or revise only after current publisher authority and audience/revision checks. |
| requirements | ["F4-REQ-01"] |
| use_cases | ["UC-F4-01V"] |
| acceptance_scenarios | ["AS-11"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["PLAN.TARGET", "PLAN.AUDIENCE", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-02", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-02.PLAN_SCOPE", "D-02.RECIPIENTS", "D-02.PUBLISH_AUTHORITY", "D-02.REVISION_LIFECYCLE", "D-02.EDITABLE_FIELDS", "D-02.HISTORICAL_ACCESS", "D-02.SESSION_RELINK", "D-02.TEMPLATE_ORIGIN", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.HISTORIC_ENTITLEMENT", "D-12.CORRECTION_RIGHTS", "D-12.AUDIT_ACCESS", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PLAN-03", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-011", "NX03-TC-012", "NX03-TC-013", "NX03-TC-014", "NX03-TC-015", "NX03-TC-081"] |
| example_ids | ["F4-PLAN-valid", "F4-PLAN-empty", "F4-PLAN-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Publish or revise only after current publisher authority and audience/revision checks. |
| semantic_kind | PUBLISH_OR_REVISE |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-011", "NX03-TC-012", "NX03-TC-013", "NX03-TC-014", "NX03-TC-015", "NX03-TC-081"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["PLAN.TARGET", "PLAN.AUDIENCE", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.PLAN_SCOPE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "Time-bound rights only if selected by humans; no deadline chosen.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "Permitted existing-resource edit/correction/publication stage requires approved policy.", "policy_dependencies": [{"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.REVISION_LIFECYCLE", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-02", "dimension": "D-02.EDITABLE_FIELDS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"} |
| retry_profile | {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-PLAN-04 Read own permitted plans and revisions

| Contract field | Value |
| --- | --- |
| operation_id | F4-PLAN-04 |
| group | F4-PLAN |
| name | Read own permitted plans and revisions |
| business_intent | Expose only entitled personal or class material, with no peer roster or private notes. |
| requirements | ["F4-REQ-06"] |
| use_cases | ["UC-F4-06"] |
| acceptance_scenarios | ["AS-03", "AS-10"] |
| primary_actor | Member |
| affected_subject | Own principal Member |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Truc Member screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Principal Member and historical entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "COMMON.HISTORY_WINDOW", "COMMON.PAGE_SELECTION"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition. |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED minimized access logging if adopted under D-12; no write before/after revision or correction-reason input required for a read. |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-02", "D-11", "D-12"] |
| blocked_subdecisions | ["D-02.RECIPIENTS", "D-02.REVISION_LIFECYCLE", "D-02.HISTORICAL_ACCESS", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.HISTORIC_ENTITLEMENT", "D-12.MEMBER_CORRECTION_VIEW", "D-12.PRIVACY_OPERATIONS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PLAN-04", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-016", "NX03-TC-017", "NX03-TC-018", "NX03-TC-019", "NX03-TC-020", "NX03-TC-082", "NX03-TC-090"] |
| example_ids | ["F4-PLAN-valid", "F4-PLAN-empty", "F4-PLAN-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Expose only entitled personal or class material, with no peer roster or private notes. |
| semantic_kind | READ |
| role | Member |
| ownership_scope | Principal Member and historical entitlement |
| test_case_ids | ["NX03-TC-016", "NX03-TC-017", "NX03-TC-018", "NX03-TC-019", "NX03-TC-020", "NX03-TC-082", "NX03-TC-090"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"meaning": "Pure read; no mutation body, expected revision, correction reason or participant state input.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "cross_field": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-MEMBER-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Page/cursor, count visibility and bounds TBD_AFTER_APPROVAL.", "mode": "TBD_AFTER_APPROVAL", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"at_least_one_of": null, "combined_filters_supported": "TBD_AFTER_APPROVAL", "meaning": "Validate permitted context/history selectors and query parsing; wire parameter names and limits remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "sort": {"meaning": "Stable allowlisted ordering and tie key require FE/BE choice.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Typed selector and null/blank/filter rules only; no mutation state or observation metric rules.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "unknown_fields": {"meaning": "Allowlisted query/selector and unknown-filter handling require agreement; no mutation body exists.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"} |
| idempotency_profile | {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-RESULT-01 Record session outcome

| Contract field | Value |
| --- | --- |
| operation_id | F4-RESULT-01 |
| group | F4-RESULT |
| name | Record session outcome |
| business_intent | Record observations for the authorized session/member with approved metric meanings. |
| requirements | ["F4-REQ-02"] |
| use_cases | ["UC-F4-02"] |
| acceptance_scenarios | ["AS-04"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "RESULT.OBSERVATIONS"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-03.METRICS", "D-03.VALIDATION", "D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-RESULT-01", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-021", "NX03-TC-022", "NX03-TC-023", "NX03-TC-024", "NX03-TC-025", "NX03-TC-083"] |
| example_ids | ["F4-RESULT-valid", "F4-RESULT-empty", "F4-RESULT-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Record observations for the authorized session/member with approved metric meanings. |
| semantic_kind | CREATE |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-021", "NX03-TC-022", "NX03-TC-023", "NX03-TC-024", "NX03-TC-025", "NX03-TC-083"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["COMMON.CONTEXT", "RESULT.OBSERVATIONS"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.METRICS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-RESULT-02 Correct session outcome

| Contract field | Value |
| --- | --- |
| operation_id | F4-RESULT-02 |
| group | F4-RESULT |
| name | Correct session outcome |
| business_intent | Correct an authorized result with traceable reason and no lost update. |
| requirements | ["F4-REQ-02"] |
| use_cases | ["UC-F4-02"] |
| acceptance_scenarios | ["AS-04"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "RESULT.OBSERVATIONS", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-03.METRICS", "D-03.VALIDATION", "D-03.CORRECTION_MECHANISM", "D-03.CORRECTION_WINDOW", "D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.HISTORIC_ENTITLEMENT", "D-12.CORRECTION_RIGHTS", "D-12.AUDIT_ACCESS", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-RESULT-02", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-026", "NX03-TC-027", "NX03-TC-028", "NX03-TC-029", "NX03-TC-030", "NX03-TC-084"] |
| example_ids | ["F4-RESULT-valid", "F4-RESULT-empty", "F4-RESULT-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Correct an authorized result with traceable reason and no lost update. |
| semantic_kind | UPDATE_OR_CORRECTION |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-026", "NX03-TC-027", "NX03-TC-028", "NX03-TC-029", "NX03-TC-030", "NX03-TC-084"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["COMMON.CONTEXT", "RESULT.OBSERVATIONS", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "Time-bound rights only if selected by humans; no deadline chosen.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_WINDOW", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "Permitted existing-resource edit/correction/publication stage requires approved policy.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.METRICS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"} |
| retry_profile | {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-RESULT-03 Read own outcome history

| Contract field | Value |
| --- | --- |
| operation_id | F4-RESULT-03 |
| group | F4-RESULT |
| name | Read own outcome history |
| business_intent | Read own permitted outcomes and correction projection, distinguishing missing from zero. |
| requirements | ["F4-REQ-05"] |
| use_cases | ["UC-F4-05"] |
| acceptance_scenarios | ["AS-09"] |
| primary_actor | Member |
| affected_subject | Own principal Member |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Truc Member screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Principal Member and historical entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "COMMON.HISTORY_WINDOW", "COMMON.PAGE_SELECTION"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition. |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED minimized access logging if adopted under D-12; no write before/after revision or correction-reason input required for a read. |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12"] |
| blocked_subdecisions | ["D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.HISTORIC_ENTITLEMENT", "D-12.MEMBER_CORRECTION_VIEW", "D-12.PRIVACY_OPERATIONS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-RESULT-03", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-031", "NX03-TC-032", "NX03-TC-033", "NX03-TC-034", "NX03-TC-035"] |
| example_ids | ["F4-RESULT-valid", "F4-RESULT-empty", "F4-RESULT-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Read own permitted outcomes and correction projection, distinguishing missing from zero. |
| semantic_kind | READ |
| role | Member |
| ownership_scope | Principal Member and historical entitlement |
| test_case_ids | ["NX03-TC-031", "NX03-TC-032", "NX03-TC-033", "NX03-TC-034", "NX03-TC-035"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"meaning": "Pure read; no mutation body, expected revision, correction reason or participant state input.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "cross_field": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-MEMBER-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Page/cursor, count visibility and bounds TBD_AFTER_APPROVAL.", "mode": "TBD_AFTER_APPROVAL", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"at_least_one_of": null, "combined_filters_supported": "TBD_AFTER_APPROVAL", "meaning": "Validate permitted context/history selectors and query parsing; wire parameter names and limits remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "sort": {"meaning": "Stable allowlisted ordering and tie key require FE/BE choice.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Typed selector and null/blank/filter rules only; no mutation state or observation metric rules.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "unknown_fields": {"meaning": "Allowlisted query/selector and unknown-filter handling require agreement; no mutation body exists.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"} |
| idempotency_profile | {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-ATTEND-01 Record Coach participation

| Contract field | Value |
| --- | --- |
| operation_id | F4-ATTEND-01 |
| group | F4-ATTEND |
| name | Record Coach participation |
| business_intent | Record actual participation; never infer it from booking, arrival or acknowledgement. |
| requirements | ["F4-REQ-04"] |
| use_cases | ["UC-F4-04"] |
| acceptance_scenarios | ["AS-06"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "ATTEND.MEANING"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-01", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.PRIVACY_OPERATIONS", "D-13.STATE_SEMANTICS", "D-13.LIFECYCLE", "D-13.ELIGIBILITY", "D-13.TIMING_BOUNDARIES", "D-13.UNIQUENESS", "D-13.CORRECTION_AUTHORITY", "D-13.HISTORY", "D-13.VISIBILITY_IMPACT", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-ATTEND-01", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-036", "NX03-TC-037", "NX03-TC-038", "NX03-TC-039", "NX03-TC-040", "NX03-TC-085", "NX03-TC-086"] |
| example_ids | ["F4-ATTEND-valid", "F4-ATTEND-empty", "F4-ATTEND-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Record actual participation; never infer it from booking, arrival or acknowledgement. |
| semantic_kind | CREATE |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-036", "NX03-TC-037", "NX03-TC-038", "NX03-TC-039", "NX03-TC-040", "NX03-TC-085", "NX03-TC-086"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["COMMON.CONTEXT", "ATTEND.MEANING"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "Choose participation uniqueness and correction identity; do not create competing authoritative outcomes.", "policy_dependencies": [{"decision_id": "D-13", "dimension": "D-13.UNIQUENESS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.STATE_SEMANTICS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-ATTEND-02 Correct Coach participation

| Contract field | Value |
| --- | --- |
| operation_id | F4-ATTEND-02 |
| group | F4-ATTEND |
| name | Correct Coach participation |
| business_intent | Apply authorized participation correction with reason and preserved prior context. |
| requirements | ["F4-REQ-04"] |
| use_cases | ["UC-F4-04C"] |
| acceptance_scenarios | ["AS-07"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "ATTEND.MEANING", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-01", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.HISTORIC_ENTITLEMENT", "D-12.CORRECTION_RIGHTS", "D-12.AUDIT_ACCESS", "D-12.PRIVACY_OPERATIONS", "D-13.STATE_SEMANTICS", "D-13.LIFECYCLE", "D-13.ELIGIBILITY", "D-13.TIMING_BOUNDARIES", "D-13.UNIQUENESS", "D-13.CORRECTION_AUTHORITY", "D-13.HISTORY", "D-13.VISIBILITY_IMPACT", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-ATTEND-02", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-041", "NX03-TC-042", "NX03-TC-043", "NX03-TC-044", "NX03-TC-045", "NX03-TC-087"] |
| example_ids | ["F4-ATTEND-valid", "F4-ATTEND-empty", "F4-ATTEND-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Apply authorized participation correction with reason and preserved prior context. |
| semantic_kind | UPDATE_OR_CORRECTION |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-041", "NX03-TC-042", "NX03-TC-043", "NX03-TC-044", "NX03-TC-045", "NX03-TC-087"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["COMMON.CONTEXT", "ATTEND.MEANING", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-13", "dimension": "D-13.ELIGIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "Choose participation uniqueness and correction identity; do not create competing authoritative outcomes.", "policy_dependencies": [{"decision_id": "D-13", "dimension": "D-13.UNIQUENESS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "Time-bound rights only if selected by humans; no deadline chosen.", "policy_dependencies": [{"decision_id": "D-13", "dimension": "D-13.TIMING_BOUNDARIES", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "Permitted existing-resource edit/correction/publication stage requires approved policy.", "policy_dependencies": [{"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.HISTORY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.STATE_SEMANTICS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.CORRECTION_AUTHORITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-13", "dimension": "D-13.HISTORY", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"} |
| retry_profile | {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-ATTEND-03 Read own participation history

| Contract field | Value |
| --- | --- |
| operation_id | F4-ATTEND-03 |
| group | F4-ATTEND |
| name | Read own participation history |
| business_intent | Read own permitted participation with arrival and unknown/unrecorded meanings distinguished. |
| requirements | ["F4-REQ-05"] |
| use_cases | ["UC-F4-05"] |
| acceptance_scenarios | ["AS-09"] |
| primary_actor | Member |
| affected_subject | Own principal Member |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Truc Member screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Principal Member and historical entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "COMMON.HISTORY_WINDOW", "COMMON.PAGE_SELECTION"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition. |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED minimized access logging if adopted under D-12; no write before/after revision or correction-reason input required for a read. |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-01", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.HISTORIC_ENTITLEMENT", "D-12.MEMBER_CORRECTION_VIEW", "D-12.PRIVACY_OPERATIONS", "D-13.STATE_SEMANTICS", "D-13.HISTORY", "D-13.VISIBILITY_IMPACT"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-ATTEND-03", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-046", "NX03-TC-047", "NX03-TC-048", "NX03-TC-049", "NX03-TC-050"] |
| example_ids | ["F4-ATTEND-valid", "F4-ATTEND-empty", "F4-ATTEND-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Read own permitted participation with arrival and unknown/unrecorded meanings distinguished. |
| semantic_kind | READ |
| role | Member |
| ownership_scope | Principal Member and historical entitlement |
| test_case_ids | ["NX03-TC-046", "NX03-TC-047", "NX03-TC-048", "NX03-TC-049", "NX03-TC-050"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"meaning": "Pure read; no mutation body, expected revision, correction reason or participant state input.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "cross_field": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-MEMBER-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Page/cursor, count visibility and bounds TBD_AFTER_APPROVAL.", "mode": "TBD_AFTER_APPROVAL", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"at_least_one_of": null, "combined_filters_supported": "TBD_AFTER_APPROVAL", "meaning": "Validate permitted context/history selectors and query parsing; wire parameter names and limits remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "sort": {"meaning": "Stable allowlisted ordering and tie key require FE/BE choice.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Typed selector and null/blank/filter rules only; no mutation state or observation metric rules.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "unknown_fields": {"meaning": "Allowlisted query/selector and unknown-filter handling require agreement; no mutation body exists.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"} |
| idempotency_profile | {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-ATTEND-04 Reuse staff arrival recording

| Contract field | Value |
| --- | --- |
| operation_id | F4-ATTEND-04 |
| group | F4-ATTEND |
| name | Reuse staff arrival recording |
| business_intent | Reuse current staff check-in unchanged; no Coach attendance authority or second debit. |
| requirements | ["F4-REQ-07"] |
| use_cases | ["UC-F4-07"] |
| acceptance_scenarios | ["AS-08"] |
| primary_actor | Receptionist |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "DENY_EXISTING_STAFF_ROUTE", "Guest": "DENY", "Manager": "OBSERVED_EXISTING_STAFF_ONLY", "Member": "DENY_EXISTING_STAFF_ROUTE", "Other": "DENY", "Receptionist": "OBSERVED_EXISTING_STAFF_ONLY"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Existing staff operation for resolved Member/booking only |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | Existing staff filtered visit history only. A new Member training-history projection and historical entitlement remain unsigned. |
| classification | REUSE_EXISTING |
| approval_status | NOT_APPROVED |
| method | POST |
| path | /api/v1/check-ins |
| aliases | ["/api/v1/check-in"] |
| existing_route_reference | SRC-CHECKIN |
| route_approval_evidence | None |
| existing_behavior_status | OBSERVED_EXISTING_UNCHANGED |
| request_field_families | ["EXISTING.CHECKIN_REQUEST"] |
| response_field_families | ["EXISTING.CHECKIN_RESPONSE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Observed staff method role gate and requested subject/filter selection; POST service checks booking/package context. No additional per-Member staff ownership guard verified. |
| field_authorization | Observed CheckInResponse visit fields only; no training results, plans or Coach narratives. No new field privilege approved. |
| ownership_denial | Deny non-staff callers with observed role403. Existing staff can select the Member/booking or history filters; no new training permission or per-Member restriction inferred. |
| sensitivity | Private Member training/visit information |
| audit | Observed auditService.record on saved arrival; immutability and consistency guarantees NOT_VERIFIED. |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-01", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-ATTEND-04", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-051", "NX03-TC-052", "NX03-TC-053", "NX03-TC-054", "NX03-TC-055", "NX03-TC-092"] |
| example_ids | ["EXISTING-ARRIVAL-request", "EXISTING-ARRIVAL-denied"] |
| model_schema_version | 2.0.0 |
| intent | Reuse current staff check-in unchanged; no Coach attendance authority or second debit. |
| semantic_kind | EXISTING_WRITE |
| role | Receptionist |
| ownership_scope | Existing staff operation for resolved Member/booking only |
| test_case_ids | ["NX03-TC-051", "NX03-TC-052", "NX03-TC-053", "NX03-TC-054", "NX03-TC-055", "NX03-TC-092"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | OBSERVED_EXISTING_UNCHANGED |
| authorization_enforcement_points | ["Observed request authentication and staff method role gate", "Observed booking/session/package/prior ALLOWED recheck after session lock; no actor-role recheck at commit verified"] |
| business_result_observations | [{"conditions": "Known Member arrival attempt; duplicate, booking-day/status/package eligibility may save DENIED; unknown Member or missing locked session instead404.", "denied_is_http_error": false, "http_status": 201, "kind": "SAVED_BUSINESS_RESULT_NOT_HTTP_ERROR", "results": ["ALLOWED", "DENIED"], "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-RESPONSE"]}] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |
| SRC-CHECKIN | backend/src/main/java/com/sportify/catalog/controller/CheckInController.java CheckInController | [20, 48] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-ARRIVAL | backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java recordCheckIn | [48, 145] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-CHECKIN-REQUEST | backend/src/main/java/com/sportify/catalog/dto/CheckInRequest.java CheckInRequest | [7, 12] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-CHECKIN-RESPONSE | backend/src/main/java/com/sportify/catalog/dto/CheckInResponse.java CheckInResponse | [10, 22] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-ENTRY | backend/src/main/java/com/sportify/identity/security/CustomAuthenticationEntryPoint.java commence | [17, 28] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-DENIED | backend/src/main/java/com/sportify/identity/security/CustomAccessDeniedHandler.java handle | [17, 28] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-ERRORS | backend/src/main/java/com/sportify/core/exception/GlobalExceptionHandler.java GlobalExceptionHandler | [29, 183] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"allowed_fields": ["identifier", "bookingId", "note"], "meaning": "identifier required NotBlank; bookingId and note optional. No observed positive bookingId or note-length annotation.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN-REQUEST"]}, "cross_field": {"meaning": "Resolve Member; confirmed booking for today, active date-valid linked package. Save ALLOWED or DENIED attempt; missing Member/session can throw404.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"]}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Prior ALLOWED booking leads to saved DENIED attempt with201. No client idempotency-key replay contract.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL", "SRC-CHECKIN"]}, "identity_scope": {"meaning": "Observed MANAGER/RECEPTIONIST role gate. POST attributes recordedBy to server principal; GET applies requested filters. No additional per-Member staff ownership restriction is verified.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Bean request validation and JSON binding only as sourced; optional does not prove global serializer null policy.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN-REQUEST", "SRC-ERRORS"]}, "unknown_fields": {"meaning": "JSON unknown-field and null serializer policy NOT_VERIFIED; no reject/ignore behavior inferred.", "policy_dependencies": [], "state": "NOT_VERIFIED", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "OBSERVED_PRIOR_ALLOWED_RECHECK", "meaning": "Session lock, refresh booking/session/package, then recheck mutable booking and prior ALLOWED. No expected revision input.", "mode": "EXISTING_SESSION_LOCK_RECHECK", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"], "write_guard": "OBSERVED_SESSION_LOCK"} |
| retry_profile | {"meaning": "Explicit booking retry may save DENIED with201. Omitting booking selection may choose another unchecked confirmed booking that day.", "mode": "EXISTING_ARRIVAL_ATTEMPTS", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"], "write_retry": "OBSERVED_NON_IDENTICAL_ATTEMPTS"} |
| idempotency_profile | {"client_key": "NOT_OBSERVED", "identical_response_replay": false, "meaning": "Current deduplication guard concerns prior ALLOWED; all attempts can be recorded. No identical-success replay contract.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL"], "write_requirement": "NO_NEW_REQUIREMENT"} |
| response_profile | {"business_result_http_status": 201, "container": "EXISTING_SINGLE_DTO", "denied_is_http_error": false, "meaning": "Single CheckInResponse on201 includes result ALLOWED or DENIED. DENIED is a saved business result, not an HTTP failure.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-RESPONSE"]} |

#### Error applicability

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

### F4-ATTEND-05 Reuse staff arrival history

| Contract field | Value |
| --- | --- |
| operation_id | F4-ATTEND-05 |
| group | F4-ATTEND |
| name | Reuse staff arrival history |
| business_intent | Reuse current staff arrival history to confirm visit attempts, not Member participation. |
| requirements | ["F4-REQ-07"] |
| use_cases | ["UC-F4-07"] |
| acceptance_scenarios | ["AS-08"] |
| primary_actor | Receptionist |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "DENY_EXISTING_STAFF_ROUTE", "Guest": "DENY", "Manager": "OBSERVED_EXISTING_STAFF_ONLY", "Member": "DENY_EXISTING_STAFF_ROUTE", "Other": "DENY", "Receptionist": "OBSERVED_EXISTING_STAFF_ONLY"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Existing staff operation for resolved Member/booking only |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | Existing staff filtered visit history only. A new Member training-history projection and historical entitlement remain unsigned. |
| classification | REUSE_EXISTING |
| approval_status | NOT_APPROVED |
| method | GET |
| path | /api/v1/check-ins |
| aliases | ["/api/v1/check-in"] |
| existing_route_reference | SRC-CHECKIN |
| route_approval_evidence | None |
| existing_behavior_status | OBSERVED_EXISTING_UNCHANGED |
| request_field_families | ["EXISTING.CHECKIN_FILTERS"] |
| response_field_families | ["EXISTING.CHECKIN_RESPONSE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Observed staff method role gate and requested subject/filter selection; POST service checks booking/package context. No additional per-Member staff ownership guard verified. |
| field_authorization | Observed CheckInResponse visit fields only; no training results, plans or Coach narratives. No new field privilege approved. |
| ownership_denial | Deny non-staff callers with observed role403. Existing staff can select the Member/booking or history filters; no new training permission or per-Member restriction inferred. |
| sensitivity | Private Member training/visit information |
| audit | No per-read audit call established in inspected history method; future access-log/retention policy remains unsigned. |
| retention | TBD_POLICY_D-12; no duration selected |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-01", "D-11", "D-12"] |
| blocked_subdecisions | ["D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.HISTORIC_ENTITLEMENT", "D-12.PRIVACY_OPERATIONS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-ATTEND-05", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-056", "NX03-TC-057", "NX03-TC-058", "NX03-TC-059", "NX03-TC-060"] |
| example_ids | ["EXISTING-ARRIVAL-empty-history"] |
| model_schema_version | 2.0.0 |
| intent | Reuse current staff arrival history to confirm visit attempts, not Member participation. |
| semantic_kind | EXISTING_READ |
| role | Receptionist |
| ownership_scope | Existing staff operation for resolved Member/booking only |
| test_case_ids | ["NX03-TC-056", "NX03-TC-057", "NX03-TC-058", "NX03-TC-059", "NX03-TC-060"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | OBSERVED_EXISTING_UNCHANGED |
| authorization_enforcement_points | ["Observed request authentication and staff method role gate", "Observed optional Member/date filter selection; no Coach/Member staff-history grant"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |
| SRC-CHECKIN | backend/src/main/java/com/sportify/catalog/controller/CheckInController.java CheckInController | [20, 48] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-ARRIVAL-HISTORY | backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java getCheckInHistory and getCheckInsOn | [157, 190] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-CHECKIN-TEST | backend/src/test/java/com/sportify/catalog/controller/CheckInControllerTest.java CheckInControllerTest.dateOnlyQueryIsSupported; dateAndMemberCanBeCombinedAndLegacyMemberHistoryStillWorks; missingFiltersAndInvalidDatesReturn400 | [85, 151] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | READ_TEST_SOURCE_NOT_EXECUTED |
| SRC-ERRORS | backend/src/main/java/com/sportify/core/exception/GlobalExceptionHandler.java GlobalExceptionHandler | [29, 183] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-ENTRY | backend/src/main/java/com/sportify/identity/security/CustomAuthenticationEntryPoint.java commence | [17, 28] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |
| SRC-DENIED | backend/src/main/java/com/sportify/identity/security/CustomAccessDeniedHandler.java handle | [17, 28] | 04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea | STATIC_SOURCE_INSPECTION |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"meaning": "Pure read; no mutation body, expected revision, correction reason or participant state input.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "cross_field": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "identity_scope": {"meaning": "Observed MANAGER/RECEPTIONIST role gate. POST attributes recordedBy to server principal; GET applies requested filters. No additional per-Member staff ownership restriction is verified.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "UNPAGED_EXISTING_LIST; no introduced envelope, page size or ceiling.", "mode": "UNPAGED_EXISTING_LIST", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY"]}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"at_least_one_of": ["memberId", "date"], "combined_filters_supported": true, "meaning": "Require at least one of memberId or date; when both are present retain both filters. ISO LocalDate/Long binding; date storage range checked.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"]}, "sort": {"meaning": "No new stable sort or tie-key guarantee. Current member-history repository method orders by checkedInAt descending; date-filter query ordering not generalized.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-ARRIVAL-HISTORY"]}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Parse existing optional memberId/date and reject absent filters or malformed dates.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-CHECKIN-TEST"]}, "unknown_fields": {"meaning": "Unknown query parameter handling is not verified; no JSON mutation body applies.", "policy_dependencies": [], "state": "NOT_VERIFIED", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY"], "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY"], "write_retry": "NOT_APPLICABLE"} |
| idempotency_profile | {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"} |
| response_profile | {"container": "EXISTING_LIST", "meaning": "Existing authorized list may be empty and contains visit attempts, not participation.", "policy_dependencies": [], "state": "OBSERVED_EXISTING", "supporting_evidence": ["SRC-CHECKIN", "SRC-ARRIVAL-HISTORY", "SRC-CHECKIN-TEST"]} |

#### Error applicability

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

### F4-PROGRESS-01 Evaluate progress and write feedback

| Contract field | Value |
| --- | --- |
| operation_id | F4-PROGRESS-01 |
| group | F4-PROGRESS |
| name | Evaluate progress and write feedback |
| business_intent | Evaluate authorized observations and intentionally classify the permitted comment audience. |
| requirements | ["F4-REQ-03"] |
| use_cases | ["UC-F4-03"] |
| acceptance_scenarios | ["AS-05"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "PROGRESS.ASSESSMENT", "PROGRESS.CLASSIFICATION"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-03.METRICS", "D-03.VALIDATION", "D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PROGRESS-01", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-061", "NX03-TC-062", "NX03-TC-063", "NX03-TC-064", "NX03-TC-065", "NX03-TC-089"] |
| example_ids | ["F4-PROGRESS-valid", "F4-PROGRESS-empty", "F4-PROGRESS-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Evaluate authorized observations and intentionally classify the permitted comment audience. |
| semantic_kind | CREATE |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-061", "NX03-TC-062", "NX03-TC-063", "NX03-TC-064", "NX03-TC-065", "NX03-TC-089"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["COMMON.CONTEXT", "PROGRESS.ASSESSMENT", "PROGRESS.CLASSIFICATION"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.METRICS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "PROPOSED_SCOPE_AND_DUPLICATE_RECHECK", "meaning": "Initial creation races recheck scope and conditional uniqueness atomically. No existing target revision is supplied or required.", "mode": "INITIAL_CREATE_RACE", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": false, "revision_wire_name": null, "stale_write": "NOT_APPLICABLE", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Reconcile uncertain initial-create outcome before resubmission; no existing revision or silent update/upsert is assumed.", "mode": "CREATE_SUBMISSION_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-PROGRESS-02 Correct progress assessment

| Contract field | Value |
| --- | --- |
| operation_id | F4-PROGRESS-02 |
| group | F4-PROGRESS |
| name | Correct progress assessment |
| business_intent | Correct permitted evaluation/feedback with attribution and privacy review. |
| requirements | ["F4-REQ-03"] |
| use_cases | ["UC-F4-03"] |
| acceptance_scenarios | ["AS-05"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "PROGRESS.ASSESSMENT", "PROGRESS.CLASSIFICATION", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED server actor, target, context, action, occurrence/recorded time, before/after revision, approved reason, outcome, correlation reference; atomicity with write TBD_POLICY_D-12 |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12", "D-13"] |
| blocked_subdecisions | ["D-03.METRICS", "D-03.VALIDATION", "D-03.CORRECTION_MECHANISM", "D-03.CORRECTION_WINDOW", "D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.HISTORIC_ENTITLEMENT", "D-12.CORRECTION_RIGHTS", "D-12.AUDIT_ACCESS", "D-12.PRIVACY_OPERATIONS", "D-13.FINANCIAL_INVARIANTS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PROGRESS-02", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-066", "NX03-TC-067", "NX03-TC-068", "NX03-TC-069", "NX03-TC-070"] |
| example_ids | ["F4-PROGRESS-valid", "F4-PROGRESS-empty", "F4-PROGRESS-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Correct permitted evaluation/feedback with attribution and privacy review. |
| semantic_kind | UPDATE_OR_CORRECTION |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-066", "NX03-TC-067", "NX03-TC-068", "NX03-TC-069", "NX03-TC-070"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"families": ["COMMON.CONTEXT", "PROGRESS.ASSESSMENT", "PROGRESS.CLASSIFICATION", "COMMON.EXPECTED_REVISION", "COMMON.CORRECTION_REASON"], "meaning": "Conditional body families; actual wire names, structure and field location remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "cross_field": {"meaning": "Bind and recheck current Coach, target Member, assignment and relevant session; no booking creation or financial effects.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.RESOURCE_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "Select create/correction/publication identity and retry reconciliation; no idempotency header selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "Time-bound rights only if selected by humans; no deadline chosen.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_WINDOW", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "pagination": {"meaning": "Single response, not a paginated collection.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"meaning": "No query input is established for this single write.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "sort": {"meaning": "No collection ordering contract for this single action.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "state_transition": {"meaning": "Permitted existing-resource edit/correction/publication stage requires approved policy.", "policy_dependencies": [{"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "type_and_presence": {"meaning": "Permitted family types, presence/null/blank, length and ordering; no selected values. Metric units/precision apply only to outcome/evaluation families.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.VALIDATION", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.METRICS", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "unknown_fields": {"meaning": "Reject versus safe ignore and presence/null/blank/coercion rules require FE/BE agreement.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Expected-current existing resource A versus intervening B; do not overwrite B. Wire token, header and status remain TBD.", "mode": "EXPECTED_CURRENT_REVISION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-03", "dimension": "D-03.CORRECTION_MECHANISM", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.CORRECTION_RIGHTS", "status": "PENDING_HUMAN_DECISION"}], "requires_existing_revision": true, "revision_wire_name": "TBD_AFTER_APPROVAL", "stale_write": "PROPOSED_REJECT_STALE_A_AFTER_B", "state": "POLICY_DEPENDENT", "supporting_evidence": [], "upsert_mode": "NOT_SPECIFIED_NOT_APPROVED", "write_guard": "TBD_AFTER_APPROVAL"} |
| retry_profile | {"meaning": "After an uncertain response, refresh authorization and reconcile the operation identity before retry. Never silently replay a stale existing-target write.", "mode": "REVISION_WRITE_RECONCILIATION", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_retry": "POLICY_PENDING"} |
| idempotency_profile | {"client_key": "TBD_AFTER_APPROVAL", "identical_response_replay": "TBD_AFTER_APPROVAL", "meaning": "Semantic duplicate handling versus scoped client token remains unsigned. No CORS/header/expiry policy or exactly-once guarantee selected.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": [], "write_requirement": "PROPOSED_SAFE_RECONCILIATION"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Return only the authorized created/corrected/published projection with server provenance, after eventual acceptance. No wire envelope/status chosen.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-PROGRESS-03 Review scoped training history

| Contract field | Value |
| --- | --- |
| operation_id | F4-PROGRESS-03 |
| group | F4-PROGRESS |
| name | Review scoped training history |
| business_intent | Read permitted history to support evaluation; current roster is not blanket historical access. |
| requirements | ["F4-REQ-03"] |
| use_cases | ["UC-F4-03"] |
| acceptance_scenarios | ["AS-05"] |
| primary_actor | Coach |
| affected_subject | Explicit target Member/session or entitled class audience |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Hung Coach/Reception screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "DENY_PEER_AND_MUTATION", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Current scoped Coach assignment plus separate historical/correction entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "COMMON.HISTORY_WINDOW", "COMMON.PAGE_SELECTION"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition. |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED minimized access logging if adopted under D-12; no write before/after revision or correction-reason input required for a read. |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12"] |
| blocked_subdecisions | ["D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.REASSIGNMENT", "D-12.HISTORIC_ENTITLEMENT", "D-12.PRIVACY_OPERATIONS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PROGRESS-03", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-071", "NX03-TC-072", "NX03-TC-073", "NX03-TC-074", "NX03-TC-075"] |
| example_ids | ["F4-PROGRESS-valid", "F4-PROGRESS-empty", "F4-PROGRESS-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Read permitted history to support evaluation; current roster is not blanket historical access. |
| semantic_kind | READ |
| role | Coach |
| ownership_scope | Current scoped Coach assignment plus separate historical/correction entitlement |
| test_case_ids | ["NX03-TC-071", "NX03-TC-072", "NX03-TC-073", "NX03-TC-074", "NX03-TC-075"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"meaning": "Pure read; no mutation body, expected revision, correction reason or participant state input.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "cross_field": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-COACH-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Page/cursor, count visibility and bounds TBD_AFTER_APPROVAL.", "mode": "TBD_AFTER_APPROVAL", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"at_least_one_of": null, "combined_filters_supported": "TBD_AFTER_APPROVAL", "meaning": "Validate permitted context/history selectors and query parsing; wire parameter names and limits remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "sort": {"meaning": "Stable allowlisted ordering and tie key require FE/BE choice.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Typed selector and null/blank/filter rules only; no mutation state or observation metric rules.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "unknown_fields": {"meaning": "Allowlisted query/selector and unknown-filter handling require agreement; no mutation body exists.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"} |
| idempotency_profile | {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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

### F4-PROGRESS-04 Read own visible feedback

| Contract field | Value |
| --- | --- |
| operation_id | F4-PROGRESS-04 |
| group | F4-PROGRESS |
| name | Read own visible feedback |
| business_intent | Read own permitted evaluations/comments without staff-only narratives or peer data. |
| requirements | ["F4-REQ-06"] |
| use_cases | ["UC-F4-06"] |
| acceptance_scenarios | ["AS-03", "AS-10"] |
| primary_actor | Member |
| affected_subject | Own principal Member |
| supporting_actors | [] |
| approval_authority | Named business/privacy authority and CODEOWNER; runtime correction/publish authority remains unsigned |
| FE_consumers | ["Truc Member screens"] |
| roles | {"Admin": "NO_VERIFIED_ADMIN_ROLE_GRANT", "Coach": "DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY", "Guest": "DENY", "Manager": "MANAGER_TBD", "Member": "CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY", "Other": "DENY", "Receptionist": "DENY_TRAINING_READ_WRITE"} |
| JWT_identity_source | Authenticated server principal resolved through current account; never trust client actor or stale role claim |
| resource_owner | Principal Member and historical entitlement |
| target_subject_binding | Server verifies member/session/booking/Coach relationships; class target never exposes peer narratives |
| historical_access | TBD_POLICY_D-12; current assignment does not imply perpetual access |
| classification | BLOCKED_POLICY |
| approval_status | NOT_APPROVED |
| method | TBD_AFTER_APPROVAL |
| path | TBD_AFTER_APPROVAL |
| aliases | [] |
| existing_route_reference | None |
| route_approval_evidence | None |
| existing_behavior_status | NO_NEW_ROUTE_AUTHORIZED |
| request_field_families | ["COMMON.CONTEXT", "COMMON.HISTORY_WINDOW", "COMMON.PAGE_SELECTION"] |
| response_field_families | ["READ.PROJECTION", "COMMON.PROVENANCE"] |
| redacted_fields | ["peer Member identifiers/names", "private narratives", "tokens", "internal diagnostics", "staff-only correction reasons unless approved"] |
| object_authorization | PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition. |
| field_authorization | Explicit audience allowlist; do not serialize peer rows or all entity fields |
| ownership_denial | Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10 |
| sensitivity | Private Member training/visit information |
| audit | PROPOSED minimized access logging if adopted under D-12; no write before/after revision or correction-reason input required for a read. |
| retention | TBD_POLICY_D-03 and TBD_POLICY_D-12 |
| cache | PROPOSED no-store for sensitive projections; no shared cross-user cache; existing endpoint cache behavior NOT_VERIFIED |
| logging | No JWT, password, raw narrative or raw request/response payload; redacted server diagnostics only |
| source_refs | ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK"] |
| business_source | Project!E5; Requirement Map; NX-02 Round 3 direct owned scenarios |
| blocked_decisions | ["D-10", "D-03", "D-11", "D-12"] |
| blocked_subdecisions | ["D-03.RETENTION", "D-03.COMMENT_VISIBILITY", "D-03.RESULT_HISTORY", "D-12.RESOURCE_RIGHTS", "D-12.FIELD_VISIBILITY", "D-12.HISTORIC_ENTITLEMENT", "D-12.MEMBER_CORRECTION_VIEW", "D-12.PRIVACY_OPERATIONS"] |
| approver | None |
| approval_record | None |
| review_ids | ["RV-PROGRESS-04", "RV-COMMON"] |
| FE_confirmation | FE_REVIEW_PENDING |
| BE_confirmation | BE_REVIEW_PENDING |
| implementation_readiness | BLOCKED |
| financial_side_effects | [] |
| arrival_is_participation | False |
| peer_data_allowed | False |
| test_ids | ["NX03-TC-076", "NX03-TC-077", "NX03-TC-078", "NX03-TC-079", "NX03-TC-080", "NX03-TC-088"] |
| example_ids | ["F4-PROGRESS-valid", "F4-PROGRESS-empty", "F4-PROGRESS-invalid"] |
| model_schema_version | 2.0.0 |
| intent | Read own permitted evaluations/comments without staff-only narratives or peer data. |
| semantic_kind | READ |
| role | Member |
| ownership_scope | Principal Member and historical entitlement |
| test_case_ids | ["NX03-TC-076", "NX03-TC-077", "NX03-TC-078", "NX03-TC-079", "NX03-TC-080", "NX03-TC-088"] |
| review_status | TECHNICAL_DRAFT_REVIEW_PENDING |
| wire_contract_status | DRAFT_NOT_APPROVED |
| authorization_enforcement_points | ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"] |
| business_result_observations | [] |

#### Source observation

| Source | File and symbol | Lines | Pinned SHA | Evidence type |
| --- | --- | --- | --- | --- |

#### Kind specific profiles

| Profile | State and exact meaning |
| --- | --- |
| validation_profile | {"body_inputs": {"meaning": "Pure read; no mutation body, expected revision, correction reason or participant state input.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "cross_field": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_participation": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "duplicate_submit": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "identity_scope": {"meaning": "Current server principal and permitted object/field scope; never a client-submitted actor.", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": ["SRC-JWT", "SRC-MEMBER-SCOPE"]}, "late_correction": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "pagination": {"meaning": "Page/cursor, count visibility and bounds TBD_AFTER_APPROVAL.", "mode": "TBD_AFTER_APPROVAL", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "path_inputs": {"meaning": "No concrete new resource path has approval.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "query_inputs": {"at_least_one_of": null, "combined_filters_supported": "TBD_AFTER_APPROVAL", "meaning": "Validate permitted context/history selectors and query parsing; wire parameter names and limits remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "sort": {"meaning": "Stable allowlisted ordering and tie key require FE/BE choice.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}, "state_transition": {"meaning": "No mutation is performed by this operation.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": []}, "type_and_presence": {"meaning": "Typed selector and null/blank/filter rules only; no mutation state or observation metric rules.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "APPLICABLE_PROPOSED", "supporting_evidence": []}, "unknown_fields": {"meaning": "Allowlisted query/selector and unknown-filter handling require agreement; no mutation body exists.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []}} |
| concurrency_profile | {"atomic_create_guard": "NOT_APPLICABLE", "meaning": "Read consistency and visibility only; no write guard or stale-write exception.", "mode": "READ_CONSISTENCY_ONLY", "policy_dependencies": [], "requires_existing_revision": false, "stale_write": "NOT_APPLICABLE", "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_guard": "NOT_APPLICABLE"} |
| retry_profile | {"meaning": "Read can be requested again after authentication/scope refresh; no write replay, mutation identity or exactly-once promise.", "mode": "READ_ONLY_RETRY", "policy_dependencies": [], "state": "APPLICABLE_PROPOSED", "supporting_evidence": [], "write_retry": "NOT_APPLICABLE"} |
| idempotency_profile | {"client_key": "NOT_APPLICABLE", "identical_response_replay": "NOT_APPLICABLE", "meaning": "Read has no write idempotency key, duplicate participation or mutation replay requirement.", "policy_dependencies": [], "state": "NOT_APPLICABLE", "supporting_evidence": [], "write_requirement": "NOT_APPLICABLE"} |
| response_profile | {"container": "TBD_AFTER_APPROVAL", "meaning": "Empty authorized projection differs from denial/missing selected context. Field and historical visibility remain unsigned.", "policy_dependencies": [{"decision_id": "D-10", "dimension": null, "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.FIELD_VISIBILITY", "status": "PENDING_HUMAN_DECISION"}, {"decision_id": "D-12", "dimension": "D-12.HISTORIC_ENTITLEMENT", "status": "PENDING_HUMAN_DECISION"}], "state": "POLICY_DEPENDENT", "supporting_evidence": []} |

#### Error applicability

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
