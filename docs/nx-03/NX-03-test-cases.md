# Contract test specifications

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

92 detailed contract specifications follow. All are SPECIFIED_NOT_EXECUTED; no Backend runtime or CI tests were run. Fixture assumptions do not approve policy.

## NX03-TC-001 Authorized author personal or class draft

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C"]
- Acceptance scenarios: ["AS-01", "AS-02"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform author personal or class draft
- Expected: Conditionally accept one authorized scoped record with server provenance only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-01

## NX03-TC-002 Invalid or missing family for F4-PLAN-01

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C"]
- Acceptance scenarios: ["AS-01", "AS-02"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Missing PLAN.TARGET or wrong typed observation, null or blank conditional required value
- Action: Submit malformed author personal or class draft
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-01

## NX03-TC-003 Authority revoked before F4-PLAN-01

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C"]
- Acceptance scenarios: ["AS-01", "AS-02"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit author personal or class draft with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-PLAN-01.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-01

## NX03-TC-004 Concurrent initial creation for F4-PLAN-01

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C"]
- Acceptance scenarios: ["AS-01", "AS-02"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Two authorized initial-create submissions for the same synthetic intended target/context; no pre-existing target record.
- Action: Interleave scope validation and first-create acceptance; recheck current authority and any eventual duplicate identity.
- Expected: Apply only the eventual signed create-identity/uniqueness policy. Do not produce uncontrolled duplicate accepted records or financial effects; do not require revision A/B or silently update an existing target. Response/status and reconciliation are TBD_AFTER_APPROVAL under D-10 and scoped business policy.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-01

## NX03-TC-005 Retry and duplicate for F4-PLAN-01

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-01"]
- Use cases: ["UC-F4-01P", "UC-F4-01C"]
- Acceptance scenarios: ["AS-01", "AS-02"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry author personal or class draft after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-01

## NX03-TC-006 Authorized edit permitted draft content

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-02"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Acceptance scenarios: ["AS-01", "AS-02", "AS-11"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform edit permitted draft content
- Expected: Conditionally accept prior/current value and reason under correction policy only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02,AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-02

## NX03-TC-007 Invalid or missing family for F4-PLAN-02

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-02"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Acceptance scenarios: ["AS-01", "AS-02", "AS-11"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Missing PLAN.TARGET or wrong typed observation, null or blank conditional required value
- Action: Submit malformed edit permitted draft content
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02,AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-02

## NX03-TC-008 Authority revoked before F4-PLAN-02

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-02"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Acceptance scenarios: ["AS-01", "AS-02", "AS-11"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit edit permitted draft content with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-PLAN-02.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02,AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-02

## NX03-TC-009 Intervening revision for F4-PLAN-02

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-02"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Acceptance scenarios: ["AS-01", "AS-02", "AS-11"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Expected revision A; authorized concurrent update made B current
- Action: Submit write still based on A for F4-PLAN-02
- Expected: No lost update or partial commit; reload only authorized context. Expected revision/token mechanism and new conflict HTTP/code require D-10; no locking implementation prescribed. Preserve existing-target stale-write coverage: stale A must not overwrite B; the eventual atomic guard and HTTP mapping require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02,AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-02

## NX03-TC-010 Retry and duplicate for F4-PLAN-02

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-02"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Acceptance scenarios: ["AS-01", "AS-02", "AS-11"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry edit permitted draft content after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02,AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-02

## NX03-TC-011 Authorized publish or revise authorized plan

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-03"]
- Use cases: ["UC-F4-01V"]
- Acceptance scenarios: ["AS-11"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform publish or revise authorized plan
- Expected: Conditionally accept publication audience/history under D-02 only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: PUBLISH_OR_REVISE
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-03

## NX03-TC-012 Invalid or missing family for F4-PLAN-03

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-03"]
- Use cases: ["UC-F4-01V"]
- Acceptance scenarios: ["AS-11"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Missing PLAN.TARGET or wrong typed observation, null or blank conditional required value
- Action: Submit malformed publish or revise authorized plan
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: PUBLISH_OR_REVISE
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-03

## NX03-TC-013 Authority revoked before F4-PLAN-03

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-03"]
- Use cases: ["UC-F4-01V"]
- Acceptance scenarios: ["AS-11"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit publish or revise authorized plan with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-PLAN-03.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: PUBLISH_OR_REVISE
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-03

## NX03-TC-014 Intervening revision for F4-PLAN-03

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-03"]
- Use cases: ["UC-F4-01V"]
- Acceptance scenarios: ["AS-11"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Expected revision A; authorized concurrent update made B current
- Action: Submit write still based on A for F4-PLAN-03
- Expected: No lost update or partial commit; reload only authorized context. Expected revision/token mechanism and new conflict HTTP/code require D-10; no locking implementation prescribed. Preserve existing-target stale-write coverage: stale A must not overwrite B; the eventual atomic guard and HTTP mapping require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: PUBLISH_OR_REVISE
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-03

## NX03-TC-015 Retry and duplicate for F4-PLAN-03

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-03"]
- Use cases: ["UC-F4-01V"]
- Acceptance scenarios: ["AS-11"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry publish or revise authorized plan after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: PUBLISH_OR_REVISE
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-03

## NX03-TC-016 Authorized scoped read

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 own context or C-101 explicitly permitted scope; synthetic available F4-PLAN-04 data
- Action: Read read own permitted plans and revisions
- Expected: Return only eventual allowlisted projection; history and audience selection require signed D-10,D-02,D-11,D-12; new wire fields/status remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-017 Empty authorized view

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: empty
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Same scope, no eligible read own permitted plans and revisions records
- Action: Read empty permitted collection
- Expected: Represent a deliberate empty state after authorization, not denial or absent outcome; envelope and count shape require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-018 Guessed peer record

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Actor substitutes M-202 context while otherwise using valid filters
- Action: Read guessed foreign record through F4-PLAN-04
- Expected: Deny before projection/count with no peer identifiers or existence oracle; 403 versus404 remains D-12/D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-019 Historic scope withdrawal

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: history
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Current assignment withdrawn; historical entitlement not yet signed
- Action: Read previous read own permitted plans and revisions
- Expected: Do not infer perpetual rights from authorship/enrollment; select permitted projection only after D-12 and relevant history dimension approval.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-020 Stable pagination and safe sort

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: pagination
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Equal occurrence times, corrected record, attempted nonallowlisted sort and next-page selection
- Action: Page the read own permitted plans and revisions view
- Expected: Reject unsafe sort or malformed selection after FE/BE choice; stable unique tie ordering avoids repeats/skips; count cannot reveal M-202. D-10 defaults/limits unresolved.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-021 Authorized record session outcome

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-01"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform record session outcome
- Expected: Conditionally accept one authorized scoped record with server provenance only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-01

## NX03-TC-022 Invalid or missing family for F4-RESULT-01

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-01"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Missing COMMON.CONTEXT or wrong typed observation, null or blank conditional required value
- Action: Submit malformed record session outcome
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-01

## NX03-TC-023 Authority revoked before F4-RESULT-01

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-01"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit record session outcome with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-RESULT-01.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-01

## NX03-TC-024 Concurrent initial creation for F4-RESULT-01

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-01"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Two authorized initial-create submissions for the same synthetic intended target/context; no pre-existing target record.
- Action: Interleave scope validation and first-create acceptance; recheck current authority and any eventual duplicate identity.
- Expected: Apply only the eventual signed create-identity/uniqueness policy. Do not produce uncontrolled duplicate accepted records or financial effects; do not require revision A/B or silently update an existing target. Response/status and reconciliation are TBD_AFTER_APPROVAL under D-10 and scoped business policy.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-01

## NX03-TC-025 Retry and duplicate for F4-RESULT-01

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-01"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry record session outcome after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-01

## NX03-TC-026 Authorized correct session outcome

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-02"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform correct session outcome
- Expected: Conditionally accept prior/current value and reason under correction policy only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-RESULT-02

## NX03-TC-027 Invalid or missing family for F4-RESULT-02

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-02"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Missing COMMON.CONTEXT or wrong typed observation, null or blank conditional required value
- Action: Submit malformed correct session outcome
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-RESULT-02

## NX03-TC-028 Authority revoked before F4-RESULT-02

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-02"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit correct session outcome with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-RESULT-02.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-RESULT-02

## NX03-TC-029 Intervening revision for F4-RESULT-02

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-02"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Expected revision A; authorized concurrent update made B current
- Action: Submit write still based on A for F4-RESULT-02
- Expected: No lost update or partial commit; reload only authorized context. Expected revision/token mechanism and new conflict HTTP/code require D-10; no locking implementation prescribed. Preserve existing-target stale-write coverage: stale A must not overwrite B; the eventual atomic guard and HTTP mapping require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-RESULT-02

## NX03-TC-030 Retry and duplicate for F4-RESULT-02

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-02"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry correct session outcome after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-RESULT-02

## NX03-TC-031 Authorized scoped read

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-RESULT-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 own context or C-101 explicitly permitted scope; synthetic available F4-RESULT-03 data
- Action: Read read own outcome history
- Expected: Return only eventual allowlisted projection; history and audience selection require signed D-10,D-03,D-11,D-12; new wire fields/status remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-03

## NX03-TC-032 Empty authorized view

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-RESULT-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: empty
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Same scope, no eligible read own outcome history records
- Action: Read empty permitted collection
- Expected: Represent a deliberate empty state after authorization, not denial or absent outcome; envelope and count shape require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-03

## NX03-TC-033 Guessed peer record

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-RESULT-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Actor substitutes M-202 context while otherwise using valid filters
- Action: Read guessed foreign record through F4-RESULT-03
- Expected: Deny before projection/count with no peer identifiers or existence oracle; 403 versus404 remains D-12/D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-03

## NX03-TC-034 Historic scope withdrawal

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-RESULT-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: history
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Current assignment withdrawn; historical entitlement not yet signed
- Action: Read previous read own outcome history
- Expected: Do not infer perpetual rights from authorship/enrollment; select permitted projection only after D-12 and relevant history dimension approval.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-03

## NX03-TC-035 Stable pagination and safe sort

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-RESULT-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: pagination
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Equal occurrence times, corrected record, attempted nonallowlisted sort and next-page selection
- Action: Page the read own outcome history view
- Expected: Reject unsafe sort or malformed selection after FE/BE choice; stable unique tie ordering avoids repeats/skips; count cannot reveal M-202. D-10 defaults/limits unresolved.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-03

## NX03-TC-036 Authorized record coach participation

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform record coach participation
- Expected: Conditionally accept one authorized scoped record with server provenance only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-037 Invalid or missing family for F4-ATTEND-01

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Missing COMMON.CONTEXT or wrong typed observation, null or blank conditional required value
- Action: Submit malformed record coach participation
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-038 Authority revoked before F4-ATTEND-01

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit record coach participation with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-ATTEND-01.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-039 Concurrent initial creation for F4-ATTEND-01

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Two authorized initial-create submissions for the same synthetic intended target/context; no pre-existing target record.
- Action: Interleave scope validation and first-create acceptance; recheck current authority and any eventual duplicate identity.
- Expected: Apply only the eventual signed create-identity/uniqueness policy. Do not produce uncontrolled duplicate accepted records or financial effects; do not require revision A/B or silently update an existing target. Response/status and reconciliation are TBD_AFTER_APPROVAL under D-10 and scoped business policy.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-040 Retry and duplicate for F4-ATTEND-01

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry record coach participation after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-041 Authorized correct coach participation

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-02"]
- Use cases: ["UC-F4-04C"]
- Acceptance scenarios: ["AS-07"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform correct coach participation
- Expected: Conditionally accept prior/current value and reason under correction policy only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-07"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-ATTEND-02

## NX03-TC-042 Invalid or missing family for F4-ATTEND-02

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-02"]
- Use cases: ["UC-F4-04C"]
- Acceptance scenarios: ["AS-07"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Missing COMMON.CONTEXT or wrong typed observation, null or blank conditional required value
- Action: Submit malformed correct coach participation
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-07"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-ATTEND-02

## NX03-TC-043 Authority revoked before F4-ATTEND-02

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-02"]
- Use cases: ["UC-F4-04C"]
- Acceptance scenarios: ["AS-07"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit correct coach participation with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-ATTEND-02.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-07"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-ATTEND-02

## NX03-TC-044 Intervening revision for F4-ATTEND-02

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-02"]
- Use cases: ["UC-F4-04C"]
- Acceptance scenarios: ["AS-07"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Expected revision A; authorized concurrent update made B current
- Action: Submit write still based on A for F4-ATTEND-02
- Expected: No lost update or partial commit; reload only authorized context. Expected revision/token mechanism and new conflict HTTP/code require D-10; no locking implementation prescribed. Preserve existing-target stale-write coverage: stale A must not overwrite B; the eventual atomic guard and HTTP mapping require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-07"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-ATTEND-02

## NX03-TC-045 Retry and duplicate for F4-ATTEND-02

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-02"]
- Use cases: ["UC-F4-04C"]
- Acceptance scenarios: ["AS-07"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry correct coach participation after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-07"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-ATTEND-02

## NX03-TC-046 Authorized scoped read

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-ATTEND-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 own context or C-101 explicitly permitted scope; synthetic available F4-ATTEND-03 data
- Action: Read read own participation history
- Expected: Return only eventual allowlisted projection; history and audience selection require signed D-10,D-01,D-11,D-12,D-13; new wire fields/status remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-03

## NX03-TC-047 Empty authorized view

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-ATTEND-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: empty
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Same scope, no eligible read own participation history records
- Action: Read empty permitted collection
- Expected: Represent a deliberate empty state after authorization, not denial or absent outcome; envelope and count shape require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-03

## NX03-TC-048 Guessed peer record

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-ATTEND-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Actor substitutes M-202 context while otherwise using valid filters
- Action: Read guessed foreign record through F4-ATTEND-03
- Expected: Deny before projection/count with no peer identifiers or existence oracle; 403 versus404 remains D-12/D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-03

## NX03-TC-049 Historic scope withdrawal

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-ATTEND-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: history
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Current assignment withdrawn; historical entitlement not yet signed
- Action: Read previous read own participation history
- Expected: Do not infer perpetual rights from authorship/enrollment; select permitted projection only after D-12 and relevant history dimension approval.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-03

## NX03-TC-050 Stable pagination and safe sort

- Requirements: ["F4-REQ-05"]
- Operations: ["F4-ATTEND-03"]
- Use cases: ["UC-F4-05"]
- Acceptance scenarios: ["AS-09"]
- Role: Member
- Category: pagination
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Equal occurrence times, corrected record, attempted nonallowlisted sort and next-page selection
- Action: Page the read own participation history view
- Expected: Reject unsafe sort or malformed selection after FE/BE choice; stable unique tie ordering avoids repeats/skips; count cannot reveal M-202. D-10 defaults/limits unresolved.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-09"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-03

## NX03-TC-051 Allowed current-day arrival

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-04"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Existing staff arrival source behavior only; known Member, selected booking/package conditions vary by case. No new revision input.
- Synthetic inputs: Synthetic identifier M-201 and explicit confirmed booking B-501 with active valid package
- Action: Staff records a visit for B-501
- Expected: Source specifies 201 with ALLOWED result and staff attribution; no participation record or additional package consumption.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_WRITE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-04

## NX03-TC-052 Blank identifier validation

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-04"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Existing staff arrival source behavior only; known Member, selected booking/package conditions vary by case. No new revision input.
- Synthetic inputs: identifier is blank; no other Member detail
- Action: Staff submits blank identifier
- Expected: Observed NotBlank failure400; no visit save; no invented note limit.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_WRITE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-04

## NX03-TC-053 Coach denied staff route

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-04"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: role
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Existing staff arrival source behavior only; known Member, selected booking/package conditions vary by case. No new revision input.
- Synthetic inputs: C-101 current Coach uses Reception check-in operation
- Action: Attempt existing staff write as Coach
- Expected: Observed role denial403 before service; current Coach assignment does not grant desk role.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_WRITE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-04

## NX03-TC-054 Explicit duplicate retry

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-04"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Existing staff arrival source behavior only; known Member, selected booking/package conditions vary by case. No new revision input.
- Synthetic inputs: B-501 already has ALLOWED arrival; same explicit booking submitted again
- Action: Reception retries after a lost response
- Expected: Source specifies saved DENIED attempt with201, not409 or identical-success replay; no second debit or participation.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_WRITE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-04

## NX03-TC-055 Omitted booking with two sessions

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-04"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: boundary
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Existing staff arrival source behavior only; known Member, selected booking/package conditions vary by case. No new revision input.
- Synthetic inputs: M-201 has two confirmed today bookings; first has ALLOWED arrival
- Action: Reception submits identifier with no booking selection
- Expected: Source may choose next unchecked booking; FE must decide explicit-selection UX under D-01, no promised same-booking idempotency.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_WRITE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-04

## NX03-TC-056 Combined date and Member filter

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-05"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Synthetic memberId201 and valid ISO date for S-401
- Action: Staff requests combined history
- Expected: Source uses date query retaining Member filter; returns attempt list, not participation.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-05

## NX03-TC-057 No matching visits

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-05"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: empty
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Valid authorized filters with no visits
- Action: Staff reads empty history
- Expected: Source specifies200 with empty list; no fabricated absent participation.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-05

## NX03-TC-058 Member cannot read staff visits

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-05"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 requests own visits through staff-only operation
- Action: Attempt current staff GET as Member
- Expected: Observed403; Member training-history contract is separate and unsigned.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-05

## NX03-TC-059 Both filters absent

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-05"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Neither memberId nor date supplied
- Action: Staff requests unfiltered history
- Expected: Observed BusinessRule400 requiring memberId or date; no unbounded implicit export.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-05

## NX03-TC-060 Malformed date

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-05"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: temporal
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: date is an invalid calendar date string
- Action: Staff requests date-filtered visits
- Expected: Observed date binding400; no assumption that all valid dates imply time-range entitlement.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-05

## NX03-TC-061 Authorized evaluate progress and write feedback

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-01"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform evaluate progress and write feedback
- Expected: Conditionally accept one authorized scoped record with server provenance only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-01

## NX03-TC-062 Invalid or missing family for F4-PROGRESS-01

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-01"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Missing COMMON.CONTEXT or wrong typed observation, null or blank conditional required value
- Action: Submit malformed evaluate progress and write feedback
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-01

## NX03-TC-063 Authority revoked before F4-PROGRESS-01

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-01"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit evaluate progress and write feedback with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-PROGRESS-01.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-01

## NX03-TC-064 Concurrent initial creation for F4-PROGRESS-01

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-01"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Two authorized initial-create submissions for the same synthetic intended target/context; no pre-existing target record.
- Action: Interleave scope validation and first-create acceptance; recheck current authority and any eventual duplicate identity.
- Expected: Apply only the eventual signed create-identity/uniqueness policy. Do not produce uncontrolled duplicate accepted records or financial effects; do not require revision A/B or silently update an existing target. Response/status and reconciliation are TBD_AFTER_APPROVAL under D-10 and scoped business policy.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-01

## NX03-TC-065 Retry and duplicate for F4-PROGRESS-01

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-01"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry evaluate progress and write feedback after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-01

## NX03-TC-066 Authorized correct progress assessment

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-02"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 currently authorized; all relevant field families supplied as conditional review values
- Action: Perform correct progress assessment
- Expected: Conditionally accept prior/current value and reason under correction policy only after applicable approvals; no booking, balance, arrival, payment or refund effects. Wire status and fields remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PROGRESS-02

## NX03-TC-067 Invalid or missing family for F4-PROGRESS-02

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-02"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Missing COMMON.CONTEXT or wrong typed observation, null or blank conditional required value
- Action: Submit malformed correct progress assessment
- Expected: Reject without partial state after signed presence/type/precision rules; missing differs from zero; D-10 and relevant business dimensions must select exact violations/status.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PROGRESS-02

## NX03-TC-068 Authority revoked before F4-PROGRESS-02

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-02"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: revocation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: C-101 loaded permitted context; assignment or enabled account revoked before submission
- Action: Submit correct progress assessment with stale UI/JWT context
- Expected: Recheck current account, role and object authority at mutation; deny safely without state change. Former authorship does not authorize F4-PROGRESS-02.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PROGRESS-02

## NX03-TC-069 Intervening revision for F4-PROGRESS-02

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-02"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Expected revision A; authorized concurrent update made B current
- Action: Submit write still based on A for F4-PROGRESS-02
- Expected: No lost update or partial commit; reload only authorized context. Expected revision/token mechanism and new conflict HTTP/code require D-10; no locking implementation prescribed. Preserve existing-target stale-write coverage: stale A must not overwrite B; the eventual atomic guard and HTTP mapping require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PROGRESS-02

## NX03-TC-070 Retry and duplicate for F4-PROGRESS-02

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-02"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: retry
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Response lost for first accepted write; same scoped content submitted again
- Action: Retry correct progress assessment after identity/scope refresh
- Expected: No uncontrolled duplicate authoritative record or financial effect. Idempotency token versus semantic reconciliation and uniqueness require D-10/D-13; no new header assumed.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PROGRESS-02

## NX03-TC-071 Authorized scoped read

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-03"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 own context or C-101 explicitly permitted scope; synthetic available F4-PROGRESS-03 data
- Action: Read review scoped training history
- Expected: Return only eventual allowlisted projection; history and audience selection require signed D-10,D-03,D-11,D-12; new wire fields/status remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-03

## NX03-TC-072 Empty authorized view

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-03"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: empty
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Same scope, no eligible review scoped training history records
- Action: Read empty permitted collection
- Expected: Represent a deliberate empty state after authorization, not denial or absent outcome; envelope and count shape require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-03

## NX03-TC-073 Guessed peer record

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-03"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Actor substitutes M-202 context while otherwise using valid filters
- Action: Read guessed foreign record through F4-PROGRESS-03
- Expected: Deny before projection/count with no peer identifiers or existence oracle; 403 versus404 remains D-12/D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-03

## NX03-TC-074 Historic scope withdrawal

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-03"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: history
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Current assignment withdrawn; historical entitlement not yet signed
- Action: Read previous review scoped training history
- Expected: Do not infer perpetual rights from authorship/enrollment; select permitted projection only after D-12 and relevant history dimension approval.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-03

## NX03-TC-075 Stable pagination and safe sort

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-03"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: pagination
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Equal occurrence times, corrected record, attempted nonallowlisted sort and next-page selection
- Action: Page the review scoped training history view
- Expected: Reject unsafe sort or malformed selection after FE/BE choice; stable unique tie ordering avoids repeats/skips; count cannot reveal M-202. D-10 defaults/limits unresolved.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-03

## NX03-TC-076 Authorized scoped read

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PROGRESS-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: positive
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 own context or C-101 explicitly permitted scope; synthetic available F4-PROGRESS-04 data
- Action: Read read own visible feedback
- Expected: Return only eventual allowlisted projection; history and audience selection require signed D-10,D-03,D-11,D-12; new wire fields/status remain TBD.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-04

## NX03-TC-077 Empty authorized view

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PROGRESS-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: empty
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Same scope, no eligible read own visible feedback records
- Action: Read empty permitted collection
- Expected: Represent a deliberate empty state after authorization, not denial or absent outcome; envelope and count shape require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-04

## NX03-TC-078 Guessed peer record

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PROGRESS-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Actor substitutes M-202 context while otherwise using valid filters
- Action: Read guessed foreign record through F4-PROGRESS-04
- Expected: Deny before projection/count with no peer identifiers or existence oracle; 403 versus404 remains D-12/D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-04

## NX03-TC-079 Historic scope withdrawal

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PROGRESS-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: history
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Current assignment withdrawn; historical entitlement not yet signed
- Action: Read previous read own visible feedback
- Expected: Do not infer perpetual rights from authorship/enrollment; select permitted projection only after D-12 and relevant history dimension approval.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-04

## NX03-TC-080 Stable pagination and safe sort

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PROGRESS-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: pagination
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Equal occurrence times, corrected record, attempted nonallowlisted sort and next-page selection
- Action: Page the read own visible feedback view
- Expected: Reject unsafe sort or malformed selection after FE/BE choice; stable unique tie ordering avoids repeats/skips; count cannot reveal M-202. D-10 defaults/limits unresolved.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-04

## NX03-TC-081 AS-11 stale publication

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-03"]
- Use cases: ["UC-F4-01V"]
- Acceptance scenarios: ["AS-11"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: P-301 current B but publisher submits A
- Action: Attempt publication and revision separately
- Expected: Reject stale A without replacing B; both AS-11.N1 revoked authority and N2 concurrency need eventual runtime tests. Preserve existing-target stale-write coverage: stale A must not overwrite B; the eventual atomic guard and HTTP mapping require D-10.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: PUBLISH_OR_REVISE
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-03

## NX03-TC-082 Class audience change

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: history
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: M-201 formerly enrolled, M-202 newly enrolled after publication
- Action: Read same published revision as each Member
- Expected: D-02.RECIPIENTS/HISTORICAL_ACCESS and D-12 select snapshot/dynamic entitlement; neither receives peer narratives.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-083 Metric precision and units

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-01"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: precision
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Observation missing, zero, fractional value, incompatible unit and large value fixtures
- Action: Compare validation across each observation fixture
- Expected: D-03.METRICS/VALIDATION select units/precision/range; do not copy max_score10 as adopted policy or coerce missing to zero.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-RESULT-01

## NX03-TC-084 Late correction and audit failure

- Requirements: ["F4-REQ-02"]
- Operations: ["F4-RESULT-02"]
- Use cases: ["UC-F4-02"]
- Acceptance scenarios: ["AS-04"]
- Role: Coach
- Category: correction
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Result exists before unspecified policy cutoff; audit dependency fails
- Action: Request correction with reason then simulate audit failure
- Expected: Correction timing and atomic write/audit behavior need D-03.CORRECTION_WINDOW/D-12; never invent deadline or claim correction committed if audit policy unresolved.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-04"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-RESULT-02

## NX03-TC-085 Arrival is not participation

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: boundary
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: B-501 confirmed and visit ALLOWED but Coach has not recorded participation
- Action: Read or submit Coach participation context
- Expected: Booking/ALLOWED visit/ACK is not participation; no automatic PRESENT or package debit; D-01 and D-13 state semantics required.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-086 Concurrent session attendance

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-01"]
- Use cases: ["UC-F4-04"]
- Acceptance scenarios: ["AS-06"]
- Role: Coach
- Category: concurrency
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Two authorized writers submit same S-401/M-201 context simultaneously
- Action: Interleave current-scope validation and commit attempts
- Expected: No two authoritative participation outcomes; D-13.UNIQUENESS and D-10 mechanism decide reconciliation; financial state unchanged.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-06"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-01

## NX03-TC-087 Cancelled or rescheduled session

- Requirements: ["F4-REQ-04"]
- Operations: ["F4-ATTEND-02"]
- Use cases: ["UC-F4-04C"]
- Acceptance scenarios: ["AS-07"]
- Role: Coach
- Category: temporal
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: S-401 cancellation/reschedule follows initial roster load
- Action: Submit participation correction using former date/context
- Expected: Revalidate eligibility and timing; no automatic absence/refund or rewritten historical context; D-13 required.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-07"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-ATTEND-02

## NX03-TC-088 Staff narrative redaction

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PROGRESS-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: privacy
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Own visible feedback plus restricted reason and peer narrative in storage fixture
- Action: Project Member feedback and correction summary
- Expected: Exclude peer and unapproved restricted text; D-03.COMMENT_VISIBILITY and D-12.FIELD_VISIBILITY select permitted Member correction fields.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-04

## NX03-TC-089 Dependency failure is client safe

- Requirements: ["F4-REQ-03"]
- Operations: ["F4-PROGRESS-01"]
- Use cases: ["UC-F4-03"]
- Acceptance scenarios: ["AS-05"]
- Role: Coach
- Category: error
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. No existing target record or target revision is required; this is initial creation, not edit or upsert.
- Synthetic inputs: Assessment dependency throws synthetic database/internal exception
- Action: Attempt evaluation under unavailable dependency
- Expected: No stack trace/raw SQL/token/narrative or partial accepted result; status/code/retry need D-10 and atomicity review.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-03", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-05"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: CREATE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PROGRESS-01

## NX03-TC-090 Facility midnight and DST conversion

- Requirements: ["F4-REQ-06"]
- Operations: ["F4-PLAN-04"]
- Use cases: ["UC-F4-06"]
- Acceptance scenarios: ["AS-03", "AS-10"]
- Role: Member
- Category: temporal
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Read-only selector/projection fixture; there is no mutation revision, transition, correction reason or duplicate-participation submission.
- Synthetic inputs: Facility-local day near midnight; future zone gap/fold fixture and nullable recorded time
- Action: Select history range and serialize permitted times
- Expected: Current Asia/Ho_Chi_Minh observation does not choose new API offset semantics; D-10/D-13 must select inclusive boundaries, gap/fold handling and nullability.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-MEMBER-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-03,AS-10"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: READ
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-PLAN-04

## NX03-TC-091 Unknown fields cannot spoof author

- Requirements: ["F4-REQ-01"]
- Operations: ["F4-PLAN-02"]
- Use cases: ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"]
- Acceptance scenarios: ["AS-01", "AS-02", "AS-11"]
- Role: Coach
- Category: validation
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. An existing authorized target has revision A; an intervening authorized write may make B current for the stale-write case. Exact token or header remains unapproved.
- Synthetic inputs: Review request contains extra actor, audience override and unknown field
- Action: Validate attempted overposting
- Expected: Principal is authoritative; explicit unknown-field reject/ignore choice under D-10 must never turn extra fields into authority.
- Behavior: PROPOSED_CONDITIONAL
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Per-operation applicability applies; NOT_APPLICABLE categories emit no error/code/retry/UI action. New applicable mappings remain TBD_AFTER_APPROVAL.
- Decisions: ["D-10", "D-02", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-COACH-SCOPE", "SRC-ERRORS", "SRC-CLOCK", "NX-02 AS-01,AS-02,AS-11"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: UPDATE_OR_CORRECTION
- Revision precondition: PROPOSED_EXPECTED_CURRENT
- Profile reference: F4-PLAN-02

## NX03-TC-092 Existing booking and finance preserved

- Requirements: ["F4-REQ-07"]
- Operations: ["F4-ATTEND-04"]
- Use cases: ["UC-F4-07"]
- Acceptance scenarios: ["AS-08"]
- Role: Receptionist
- Category: financial
- Preconditions: Synthetic current C-101, own M-201, unrelated M-202 and session S-401; only the explicit scoped rights assumed for this fixture, never human approval. Existing staff arrival source behavior only; known Member, selected booking/package conditions vary by case. No new revision input.
- Synthetic inputs: ALLOWED visit followed by booking cancellation attempt; compare pre/post credits
- Action: Specify compatibility probes against existing booking workflow
- Expected: Existing cancellation restrictions remain; no new debit/refund/booking creation by Flow4. Source tests reviewed, these probes not executed.
- Behavior: OBSERVED_EXISTING_SOURCE_SPEC
- Authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Error visibility: Observed source failure/result distinctions only; no new codes or fields.
- Decisions: ["D-10", "D-01", "D-11", "D-12", "D-13"]
- Status: SPECIFIED_NOT_EXECUTED
- Evidence: ["SRC-CHECKIN", "SRC-ARRIVAL", "SRC-CHECKIN-REQUEST", "SRC-CHECKIN-RESPONSE", "SRC-ENTRY", "SRC-DENIED", "NX-02 AS-08"]
- Policy needed: Signed applicable parent decisions and required dimensions; FE/BE status, fields and projection agreement
- Semantic kind: EXISTING_WRITE
- Revision precondition: NOT_APPLICABLE
- Profile reference: F4-ATTEND-04
