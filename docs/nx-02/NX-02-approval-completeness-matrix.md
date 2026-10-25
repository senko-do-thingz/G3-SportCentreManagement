# NX-02 required approval completeness matrix

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception.

All 32 dimensions are REQUIRED_BEFORE_APPROVAL. All selected options are UNRESOLVED; approved=0, signed deferrals=0. No optional dimension is used to hide a mandatory policy gap. Parent strategic selections are also unsigned. Candidate options below are proposals for review and do not choose values, rights, thresholds or durations.

| Decision | Required | Approved | Explicit signed deferral | Unresolved | Overall status |
| --- | --- | --- | --- | --- | --- |
| D-02 | 8 | 0 | 0 | 8 | PENDING_HUMAN_DECISION |
| D-03 | 7 | 0 | 0 | 7 | PENDING_HUMAN_DECISION |
| D-12 | 8 | 0 | 0 | 8 | PENDING_HUMAN_DECISION |
| D-13 | 9 | 0 | 0 | 9 | PENDING_HUMAN_DECISION |

## D-02.PLAN_SCOPE

- Parent decision: D-02
- Question: How are personal and class plan ownership/assignments distinguished?
- Candidate options: Distinct personal/class assignments; Per-session copies; Reduced scope only with signed exception
- Selected option: UNRESOLVED
- Proposed recommendation: Distinct scoped assignments, subject to review
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A7:H7; Project!E5 Coach 3; S11/S14
- Rationale: Original capability includes personal and class while schema/design linkage differs
- UC impact: UC-F4-01P, UC-F4-01C, UC-F4-01V
- AS impact: AS-01, AS-02
- Test impact: TC-01, TC-02
- Permission impact: PM-01, PM-02, PM-05, PM-06
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.RECIPIENTS

- Parent decision: D-02
- Question: Is a published class audience a recipient snapshot or current enrollment?
- Candidate options: Snapshot when published; Dynamic current enrollment; Per-member assignment copies
- Selected option: UNRESOLVED
- Proposed recommendation: Snapshot as a review option, not adopted
- Owner/coordinator: Dat + Hung; Truc reviews Member visibility
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A7:H7; NX-02 original section 8; S05/S11
- Rationale: Membership changes affect entitlement without implying peer data access
- UC impact: UC-F4-01C, UC-F4-06, UC-F4-01V
- AS impact: AS-02, AS-10, AS-11
- Test impact: TC-02, TC-05, TC-37, TC-64, TC-65, TC-66
- Permission impact: PM-06, PM-18, PM-34
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.PUBLISH_AUTHORITY

- Parent decision: D-02
- Question: Who may expose a plan revision to its audience?
- Candidate options: Current assigned authorized Coach; Separate explicitly authorized reviewer; UNRESOLVED delegation model
- Selected option: UNRESOLVED
- Proposed recommendation: Current scoped publisher, subject to review
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A7:H7; NX-00 D-12; S05/S14
- Rationale: Source capability does not grant publish/oversight rights
- UC impact: UC-F4-01V, UC-F4-06
- AS impact: AS-03, AS-10, AS-11
- Test impact: TC-03, TC-08, TC-64, TC-65, TC-66
- Permission impact: PM-04, PM-18
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.REVISION_LIFECYCLE

- Parent decision: D-02
- Question: How do draft/published/superseded/archived revisions preserve history?
- Candidate options: Preserved published revisions; Mutable published content; Freeze after publication
- Selected option: UNRESOLVED
- Proposed recommendation: Preserved revisions, no state enumeration adopted
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A7:H7; S11/S14
- Rationale: Session history must not be silently rewritten by a later plan edit
- UC impact: UC-F4-01V, UC-F4-06
- AS impact: AS-03, AS-10, AS-11
- Test impact: TC-04, TC-64, TC-65, TC-66
- Permission impact: PM-03, PM-04, PM-07, PM-19
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.EDITABLE_FIELDS

- Parent decision: D-02
- Question: Which plan fields/order may change at each chosen lifecycle stage?
- Candidate options: Draft editable and published revision copied for changes; Explicit field-by-stage allowlist; UNRESOLVED field/order rules
- Selected option: UNRESOLVED
- Proposed recommendation: Explicit reviewed field/order allowlist
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Original NX-02 sections 6/8; S11/S14
- Rationale: No approved editable field set or ordering contract exists
- UC impact: UC-F4-01P, UC-F4-01C, UC-F4-01V
- AS impact: AS-01, AS-02, AS-11
- Test impact: TC-04, TC-07, TC-64, TC-65, TC-66
- Permission impact: PM-03, PM-07
- Downstream blockers: NX-03, NX-04, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.HISTORICAL_ACCESS

- Parent decision: D-02
- Question: What prior plan revisions remain visible after assignment withdrawal?
- Candidate options: Previously entitled history retained subject to retention; Current entitlement only; UNRESOLVED exception policy
- Selected option: UNRESOLVED
- Proposed recommendation: Separate historical entitlement from future audience
- Owner/coordinator: Dat + Hung; Truc reviews history
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-02/D-12; S11/S14
- Rationale: Former membership does not define perpetual access
- UC impact: UC-F4-06, UC-F4-01V
- AS impact: AS-10
- Test impact: TC-05, TC-37
- Permission impact: PM-19, PM-34
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.SESSION_RELINK

- Parent decision: D-02
- Question: What happens to a plan link on session reschedule/cancel/reassignment?
- Candidate options: Keep historical link and deliberately assign future use; Relink only after authorized review; UNRESOLVED cancellation exceptions
- Selected option: UNRESOLVED
- Proposed recommendation: Deliberate future relink with preserved history
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; original NX-02 section 8; S11/S14
- Rationale: Reschedule/cancel must not manufacture attendance or revise historic use silently
- UC impact: UC-F4-01C, UC-F4-01V
- AS impact: AS-02, AS-10
- Test impact: TC-04, TC-22
- Permission impact: PM-06, PM-07, PM-16
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-02.TEMPLATE_ORIGIN

- Parent decision: D-02
- Question: Which permitted content may be cloned and how is its origin retained?
- Candidate options: Clone shared permitted content with provenance; No templates in signed initial scope; UNRESOLVED template entitlement
- Selected option: UNRESOLVED
- Proposed recommendation: Exclude personal narratives and retain permitted origin
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A7:H7; S11 is_template scaffold
- Rationale: Template presence supplies no right to clone private records
- UC impact: UC-F4-01P, UC-F4-01C, UC-F4-01V
- AS impact: AS-01, AS-02
- Test impact: TC-06
- Permission impact: PM-07
- Downstream blockers: NX-03, NX-04, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.METRICS

- Parent decision: D-03
- Question: Who defines permitted observations, units and meaning?
- Candidate options: Approved sport metric dictionary; Narrative-only outcomes; Governed Coach-defined metrics
- Selected option: UNRESOLVED
- Proposed recommendation: Human-owned dictionary if numerical outcomes adopted
- Owner/coordinator: Dat + Truc + Hung; metric steward OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; S13
- Rationale: Schema defaults do not approve any metric or numeric limit
- UC impact: UC-F4-02, UC-F4-03
- AS impact: AS-04, AS-05
- Test impact: TC-09, TC-11, TC-12
- Permission impact: PM-08, PM-10
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.VALIDATION

- Parent decision: D-03
- Question: How are missing/coerced values, units and incomplete results handled?
- Candidate options: Reject incomplete required metrics; Save explicitly incomplete result; Narrative-only result when approved
- Selected option: UNRESOLVED
- Proposed recommendation: Distinguish missing from zero; no coercion
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; S13; original NX-02 section 8
- Rationale: No dictionary/range/unit rules are yet signed
- UC impact: UC-F4-02, UC-F4-03
- AS impact: AS-04, AS-05
- Test impact: TC-11, TC-12, TC-17
- Permission impact: PM-08, PM-10
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.CORRECTION_MECHANISM

- Parent decision: D-03
- Question: How are result/comment corrections authorized and recorded?
- Candidate options: Retained prior/current revision with reason; Separate reviewed correction request; UNRESOLVED amendment mechanism
- Selected option: UNRESOLVED
- Proposed recommendation: Retain attributable prior/current values
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; S13; NX-00 D-03
- Rationale: A result schema row is not an accountable correction workflow
- UC impact: UC-F4-02, UC-F4-03
- AS impact: AS-04, AS-05
- Test impact: TC-13, TC-14
- Permission impact: PM-09, PM-11
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.CORRECTION_WINDOW

- Parent decision: D-03
- Question: Is correction time-bounded or permitted through review at any time?
- Candidate options: Owner-defined bounded window plus later review; Accountable correction without time cutoff; UNRESOLVED duration/exception rules
- Selected option: UNRESOLVED
- Proposed recommendation: No duration recommended until human policy review
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; original NX-02 section 8
- Rationale: Late-entry rights cannot follow merely from a strategic metric option
- UC impact: UC-F4-02, UC-F4-03
- AS impact: AS-04, AS-05
- Test impact: TC-13, TC-59, TC-60
- Permission impact: PM-09, PM-11
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.RETENTION

- Parent decision: D-03
- Question: How long are results/comments and their revisions retained?
- Candidate options: UNRESOLVED duration; Differentiated policy by record purpose; Common justified policy with explicit exceptions
- Selected option: UNRESOLVED
- Proposed recommendation: No duration selected; privacy authority must be named
- Owner/coordinator: Dat + Truc + Hung; privacy/legal authority OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; original NX-02 section 8; NX-00 D-03/D-12
- Rationale: Operational history and privacy obligations require a human duration/exception decision
- UC impact: UC-F4-02, UC-F4-03, UC-F4-05, UC-F4-06
- AS impact: AS-04, AS-05, AS-09, AS-10
- Test impact: TC-19, TC-37, TC-59
- Permission impact: PM-09, PM-19, PM-22
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.COMMENT_VISIBILITY

- Parent decision: D-03
- Question: Which Coach comments are member-visible versus restricted?
- Candidate options: Intentional visible feedback only; Visible feedback plus justified restricted notes; UNRESOLVED classification
- Selected option: UNRESOLVED
- Proposed recommendation: Explicit audience; no private-note capability granted
- Owner/coordinator: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; S13/S14; Project!E5 Member 7
- Rationale: Member comment capability does not expose every Coach narrative
- UC impact: UC-F4-03, UC-F4-06
- AS impact: AS-05, AS-10
- Test impact: TC-18, TC-19
- Permission impact: PM-11, PM-22, PM-27
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-03.RESULT_HISTORY

- Parent decision: D-03
- Question: What corrected result/evaluation history may Member view?
- Candidate options: Current value plus correction indicator; Own revision history with reason redaction; UNRESOLVED history projection
- Selected option: UNRESOLVED
- Proposed recommendation: Current outcome with explicit permitted correction indication
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: Decision Log!A8:H8; NX-00 D-03/D-12
- Rationale: Correction history and private reason visibility are separate choices
- UC impact: UC-F4-02, UC-F4-03, UC-F4-05
- AS impact: AS-04, AS-05, AS-09
- Test impact: TC-13, TC-15, TC-32
- Permission impact: PM-09, PM-20, PM-21
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.RESOURCE_RIGHTS

- Parent decision: D-12
- Question: What record operations may each actor perform for each owned/assigned resource?
- Candidate options: Explicit actor/action/resource predicates; UNRESOLVED oversight/delegation grants
- Selected option: UNRESOLVED
- Proposed recommendation: Deny unspecified rights and review each permission row
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; S02/S05/S08/S09
- Rationale: Authentication and Manager operational exceptions do not grant broad training access
- UC impact: UC-F4-01P, UC-F4-01C, UC-F4-02, UC-F4-03, UC-F4-04, UC-F4-05, UC-F4-06, UC-F4-07, UC-F4-01V
- AS impact: AS-01, AS-02, AS-03, AS-04, AS-05, AS-06, AS-08, AS-09, AS-11
- Test impact: TC-30, TC-31, TC-39, TC-50, TC-52, TC-64, TC-65, TC-66
- Permission impact: PM-01, PM-08, PM-12, PM-18, PM-24, PM-30, PM-31
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.FIELD_VISIBILITY

- Parent decision: D-12
- Question: Which plan/result/comment/correction fields may each actor read?
- Candidate options: Explicit minimum field projection per actor/context; UNRESOLVED restricted-note and audit visibility
- Selected option: UNRESOLVED
- Proposed recommendation: Separate shared steps, personal outcomes and restricted narratives
- Owner/coordinator: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; S05/S13/S17
- Rationale: Record permission alone does not authorize all fields
- UC impact: UC-F4-03, UC-F4-05, UC-F4-06, UC-F4-01V
- AS impact: AS-03, AS-09, AS-10, AS-11
- Test impact: TC-19, TC-32, TC-40, TC-64, TC-65, TC-66
- Permission impact: PM-14, PM-18, PM-20, PM-21, PM-22, PM-27, PM-29
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.REASSIGNMENT

- Parent decision: D-12
- Question: What reads/writes remain after Coach or Member assignment changes?
- Candidate options: Revoke future writes and separately decide historical reads; Explicit signed delegation exceptions; UNRESOLVED transition policy
- Selected option: UNRESOLVED
- Proposed recommendation: No permanent original-author privilege
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; S05 current ownership recheck
- Rationale: Historic authorship differs from present authority
- UC impact: UC-F4-01P, UC-F4-01V, UC-F4-04, UC-F4-04C, UC-F4-06
- AS impact: AS-01, AS-07, AS-10, AS-11
- Test impact: TC-08, TC-36, TC-37, TC-38, TC-64, TC-65, TC-66
- Permission impact: PM-15, PM-16, PM-33, PM-34
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.HISTORIC_ENTITLEMENT

- Parent decision: D-12
- Question: Who may read historical training across former assignments?
- Candidate options: Previously entitled Member history under retention policy; Only current scoped entitlement; Reviewed role/resource-specific historic grant
- Selected option: UNRESOLVED
- Proposed recommendation: Decide historical reads separately from writes
- Owner/coordinator: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; original NX-02 section 9
- Rationale: Former Coach does not inherit all member history or private notes
- UC impact: UC-F4-03, UC-F4-05, UC-F4-06
- AS impact: AS-05, AS-09, AS-10
- Test impact: TC-36, TC-37
- Permission impact: PM-14, PM-15, PM-19, PM-34
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.CORRECTION_RIGHTS

- Parent decision: D-12
- Question: Which actors can correct each resource, including after reassignment?
- Candidate options: Current authorized Coach by resource; Separately reviewed delegated correction role; UNRESOLVED escalation authority
- Selected option: UNRESOLVED
- Proposed recommendation: No Manager or former-author correction grant without explicit policy
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; S02/S05/S12/S13
- Rationale: Operational Manager/check-in privileges are not Coach correction rights
- UC impact: UC-F4-02, UC-F4-03, UC-F4-04C
- AS impact: AS-04, AS-05, AS-07
- Test impact: TC-13, TC-24, TC-38, TC-39
- Permission impact: PM-09, PM-11, PM-13, PM-16, PM-30
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.AUDIT_ACCESS

- Parent decision: D-12
- Question: Which staff may inspect correction/audit metadata and private reasons?
- Candidate options: Minimum purpose-scoped audit projection; Separately authorized full evidence review; UNRESOLVED retention/access policy
- Selected option: UNRESOLVED
- Proposed recommendation: Reuse Manager audit only within separately reviewed data boundary
- Owner/coordinator: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; S16/S17
- Rationale: Generic event storage is not an immutable private-note oversight grant
- UC impact: UC-F4-02, UC-F4-04C, UC-F4-05
- AS impact: AS-04, AS-07, AS-09
- Test impact: TC-24, TC-40, TC-53
- Permission impact: PM-09, PM-13, PM-20, PM-29
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.MEMBER_CORRECTION_VIEW

- Parent decision: D-12
- Question: What correction outcome/reason is visible to the affected Member?
- Candidate options: Current corrected value with minimal indication; Own redacted revision history; UNRESOLVED reason projection
- Selected option: UNRESOLVED
- Proposed recommendation: Keep restricted reason separate from visible correction summary
- Owner/coordinator: Dat + Truc + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; S12/S13
- Rationale: Member transparency does not authorize raw private staff narrative
- UC impact: UC-F4-04C, UC-F4-05, UC-F4-06
- AS impact: AS-07, AS-09, AS-10
- Test impact: TC-15, TC-19, TC-24, TC-32
- Permission impact: PM-13, PM-20, PM-21, PM-22
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-12.PRIVACY_OPERATIONS

- Parent decision: D-12
- Question: Who handles verified export/deletion/correction requests and retained evidence?
- Candidate options: Human-reviewed identity/purpose/scope process; UNRESOLVED authority/retention exceptions
- Selected option: UNRESOLVED
- Proposed recommendation: Name accountable privacy/legal authority before processing policy
- Owner/coordinator: Dat + Truc + Hung coordinate; privacy/legal authority OWNER_UNCONFIRMED
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-12; original NX-02 sections 8/9
- Rationale: No legal retention/export/deletion decision is provided by actor list
- UC impact: UC-F4-05, UC-F4-06
- AS impact: AS-09, AS-10
- Test impact: TC-19, TC-40, TC-59
- Permission impact: PM-19, PM-20, PM-21, PM-22, PM-29
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.STATE_SEMANTICS

- Parent decision: D-13
- Question: Which participation, missing, cancelled and outcome meanings are adopted?
- Candidate options: Explicit reviewed meanings preserving missing versus absent; UNRESOLVED status set and transitions
- Selected option: UNRESOLVED
- Proposed recommendation: No production enumeration chosen
- Owner/coordinator: Dat + Hung; Truc reviews Member labels
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S12/S14
- Rationale: Migration constraints and design statements do not sign policy
- UC impact: UC-F4-04, UC-F4-05
- AS impact: AS-06, AS-09
- Test impact: TC-20, TC-21, TC-22, TC-34
- Permission impact: PM-12, PM-20, PM-35
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.LIFECYCLE

- Parent decision: D-13
- Question: When is an observation draft/final/submitted/correctable, if those concepts are adopted?
- Candidate options: Explicit deliberate finalization with correction path; Immediate authoritative record plus correction history; UNRESOLVED lifecycle
- Selected option: UNRESOLVED
- Proposed recommendation: Approve meanings before names or state-machine design
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S12/S14
- Rationale: No implemented attendance submission-state workflow verified
- UC impact: UC-F4-04, UC-F4-04C, UC-F4-05
- AS impact: AS-06, AS-07, AS-09
- Test impact: TC-20, TC-24
- Permission impact: PM-12, PM-13, PM-20
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.ELIGIBILITY

- Parent decision: D-13
- Question: Which session/member/booking/participation evidence permits attendance and results?
- Candidate options: Current eligible confirmed roster with explicit result conditions; Reviewed alternate eligibility with signed exception; UNRESOLVED linkage and self-training rules
- Selected option: UNRESOLVED
- Proposed recommendation: Keep arrival, ACK and Coach participation distinct
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S01/S03/S05/S12/S13/S14
- Rationale: Result/attendance linkage differs across design and schema
- UC impact: UC-F4-02, UC-F4-04, UC-F4-07
- AS impact: AS-04, AS-06, AS-08
- Test impact: TC-10, TC-15, TC-26, TC-28
- Permission impact: PM-08, PM-12, PM-24, PM-35
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.TIMING_BOUNDARIES

- Parent decision: D-13
- Question: What defines on-time, late, absent/excused/no-show and entry cutoff?
- Candidate options: Human-defined thresholds and exception authority; UNRESOLVED values and edge cases
- Selected option: UNRESOLVED
- Proposed recommendation: No threshold or deadline invented
- Owner/coordinator: Dat + Hung; Truc reviews labels
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S15; original NX-02 section 7
- Rationale: Current arrival timestamps do not calculate Coach lateness
- UC impact: UC-F4-04, UC-F4-05, UC-F4-07
- AS impact: AS-06, AS-08, AS-09
- Test impact: TC-21, TC-54
- Permission impact: PM-12, PM-20, PM-24
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.UNIQUENESS

- Parent decision: D-13
- Question: Is there one current session/member outcome with history or multiple competing observations?
- Candidate options: One current outcome plus accountable corrections; Reviewed multi-observer model; UNRESOLVED retry/conflict policy
- Selected option: UNRESOLVED
- Proposed recommendation: One current outcome as a proposal consistent with S12 uniqueness
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S12
- Rationale: Schema uniqueness is not proof of retry/concurrency semantics
- UC impact: UC-F4-04, UC-F4-04C
- AS impact: AS-06, AS-07
- Test impact: TC-23, TC-25
- Permission impact: PM-12, PM-13
- Downstream blockers: NX-03, NX-04, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.CORRECTION_AUTHORITY

- Parent decision: D-13
- Question: Who may correct or supersede attendance and how do competing corrections resolve?
- Candidate options: Current authorized Coach with revision-aware history; Separate reviewed oversight/correction role; UNRESOLVED reassignment exceptions
- Selected option: UNRESOLVED
- Proposed recommendation: No lost updates or implicit former-Coach/Manager grant
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13/D-12; S12/S16
- Rationale: Correction identity/authority is not enforced by row scaffolding alone
- UC impact: UC-F4-04C
- AS impact: AS-07
- Test impact: TC-24, TC-25, TC-38
- Permission impact: PM-13, PM-16, PM-30
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.HISTORY

- Parent decision: D-13
- Question: What prior/current evidence and reason must correction retain?
- Candidate options: Attributable prior/current state, actor/time/reason; Separately reviewed correction evidence model; UNRESOLVED audit-failure consistency
- Selected option: UNRESOLVED
- Proposed recommendation: Retain accountable evidence; no immutable-storage claim
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S12/S16
- Rationale: Generic activity logging cannot certify atomic immutable correction history
- UC impact: UC-F4-04C, UC-F4-05
- AS impact: AS-07, AS-09
- Test impact: TC-24, TC-53
- Permission impact: PM-13, PM-20, PM-29
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.VISIBILITY_IMPACT

- Parent decision: D-13
- Question: How do corrections affect Member history and dependent result/evaluation review?
- Candidate options: Visible current outcome with permitted correction indicator and flagged dependencies; Own redacted history with reviewed dependency handling; UNRESOLVED notification linkage
- Selected option: UNRESOLVED
- Proposed recommendation: Review affected results without silently deleting or recalculating; notifications remain D-04
- Owner/coordinator: Dat + Hung; Truc reviews Member outcome
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S12/S13; Decision Log D-04
- Rationale: Correction must not silently imply delivery or erase observations
- UC impact: UC-F4-02, UC-F4-03, UC-F4-04C, UC-F4-05
- AS impact: AS-04, AS-05, AS-07, AS-09
- Test impact: TC-15, TC-24, TC-32
- Permission impact: PM-09, PM-13, PM-20, PM-21
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

## D-13.FINANCIAL_INVARIANTS

- Parent decision: D-13
- Question: Do training/attendance changes remain neutral to booking and package/payment/refund state?
- Candidate options: Preserve existing flows with no new financial side effect; Any alternate behavior requires separately signed scope and finance review
- Selected option: UNRESOLVED
- Proposed recommendation: Preserve booking-time consumption and controlled cancellation restoration
- Owner/coordinator: Dat + Hung
- Approval authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Classification: REQUIRED_BEFORE_APPROVAL
- Current status: PENDING_HUMAN_DECISION
- Source references: NX-00 D-13; S01/S03/S07
- Rationale: Arrival and training must not double-consume sessions or grant refunds
- UC impact: UC-F4-02, UC-F4-04, UC-F4-04C, UC-F4-07, UC-F4-01V
- AS impact: AS-04, AS-06, AS-07, AS-08, AS-11
- Test impact: TC-41, TC-42, TC-55, TC-64, TC-65, TC-66
- Permission impact: PM-08, PM-12, PM-13, PM-24
- Downstream blockers: NX-03, NX-04, NX-05, NX-06
- Missing sign-off evidence: Named authorized reviewer, dated selected policy, exact option/value or explicit signed scope exception, source record and reviewed report/version; no signature supplied
- Dated approval: None
- Signed scope exception: None

NX-03 cannot finalize contracts while relevant required dimensions are unresolved. NX-04 cannot resolve business uncertainty by choosing a schema. Truc/NX-05 and Hung/NX-06 review only the fields/flows relevant to their listed dimensions, with no invented URLs, DTOs or role privileges. No date or retention duration is supplied.
