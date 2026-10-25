# NX-02 actor-specific acceptance scenarios

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

The categories DENY, EMPTY, NOT_FOUND, CONFLICT, HIDDEN_PRIVATE_DATA, VALIDATION_ERROR and PENDING_POLICY are analytical outcomes, not approved transport/HTTP contracts. A PENDING_POLICY case blocks policy/acceptance completion; it does not assert an existing runtime refusal. Every business example remains SPECIFIED_NOT_EXECUTED. Existing check-in source observations require later runtime regression verification.

## Scenario specificity matrix

| AS | UC / requirement | Actor / operation | Distinct negatives | Permission references | Tests |
| --- | --- | --- | --- | --- | --- |
| AS-01 | UC-F4-01P / F4-REQ-01 | Coach / PROPOSED_MUTATION | AS-01.N1: DENY, AS-01.N2: VALIDATION_ERROR | PM-01, PM-03, PM-05, PM-15, PM-16 | TC-01, TC-07, TC-08, TC-56 |
| AS-02 | UC-F4-01C / F4-REQ-01 | Coach / PROPOSED_MUTATION | AS-02.N1: DENY, AS-02.N2: PENDING_POLICY | PM-02, PM-06, PM-07, PM-17 | TC-02, TC-05, TC-06, TC-50, TC-59 |
| AS-03 | UC-F4-06 / F4-REQ-06 | Member / READ_ONLY | AS-03.N1: HIDDEN_PRIVATE_DATA, AS-03.N2: DENY, AS-03.N3: EMPTY | PM-18, PM-19, PM-22, PM-34 | TC-03, TC-19, TC-33, TC-37, TC-50, TC-57 |
| AS-04 | UC-F4-02 / F4-REQ-02 | Coach / PROPOSED_MUTATION | AS-04.N1: NOT_FOUND, AS-04.N2: VALIDATION_ERROR, AS-04.N3: CONFLICT | PM-08, PM-09, PM-35 | TC-09, TC-10, TC-11, TC-12, TC-13, TC-14 |
| AS-05 | UC-F4-03 / F4-REQ-03 | Coach / PROPOSED_MUTATION | AS-05.N1: DENY, AS-05.N2: PENDING_POLICY | PM-10, PM-11, PM-14, PM-30 | TC-16, TC-17, TC-18, TC-19, TC-50, TC-59 |
| AS-06 | UC-F4-04 / F4-REQ-04 | Coach / PROPOSED_MUTATION | AS-06.N1: DENY, AS-06.N2: CONFLICT | PM-12, PM-17, PM-35 | TC-20, TC-22, TC-23, TC-25, TC-26, TC-38 |
| AS-07 | UC-F4-04C / F4-REQ-04 | Coach / PROPOSED_MUTATION | AS-07.N1: CONFLICT, AS-07.N2: DENY, AS-07.N3: VALIDATION_ERROR | PM-13, PM-16, PM-33 | TC-24, TC-25, TC-38, TC-53 |
| AS-08 | UC-F4-07 / F4-REQ-07 | Receptionist / EXISTING_CHECK_IN | AS-08.N1: NOT_FOUND, AS-08.N2: DENY, AS-08.N3: DENY, AS-08.N4: DENY, AS-08.N5: DENY | PM-24, PM-25, PM-28 | TC-27, TC-28, TC-29, TC-30, TC-49, TC-52, TC-54, TC-55, TC-58 |
| AS-09 | UC-F4-05 / F4-REQ-05 | Member / READ_ONLY | AS-09.N1: DENY, AS-09.N2: EMPTY, AS-09.N3: DENY | PM-20, PM-21, PM-33 | TC-32, TC-34, TC-35, TC-50, TC-51 |
| AS-10 | UC-F4-06 / F4-REQ-06 | Member / READ_ONLY | AS-10.N1: HIDDEN_PRIVATE_DATA, AS-10.N2: PENDING_POLICY, AS-10.N3: DENY | PM-18, PM-19, PM-22, PM-34 | TC-03, TC-05, TC-19, TC-33, TC-37, TC-59 |
| AS-11 | UC-F4-01V / F4-REQ-01 | Coach / PROPOSED_MUTATION | AS-11.N1: DENY, AS-11.N2: CONFLICT | PM-03, PM-04, PM-07, PM-16, PM-33 | TC-04, TC-08, TC-64, TC-65, TC-66 |

## AS-01 - Coach

- Use case: UC-F4-01P
- Requirement: F4-REQ-01
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-02, D-11, D-12, D-13
- Permission rows: PM-01, PM-03, PM-05, PM-15, PM-16
- Related tests: TC-01, TC-07, TC-08, TC-56
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given C-101 currently assigned to M-201 and the personal-plan policy is fully approved, when C-101 saves ordered private draft P-301 for M-201, then A saved personal draft is attributable to C-101/M-201 and remains hidden from Member until the approved publication condition. Every policy choice in the Given requires full approval-completeness evidence.

### AS-01.N1 - DENY

- Preconditions: C-101 assignment to M-201 revoked after opening draft
- When: C-101 saves the stale personal draft
- Reason: No current member-scope authoring authority
- Observable expected result: No new personal-plan revision or member data disclosure; existing balances unchanged
- Justified boundary: S05 current scope pattern; D-12 proposed plan entitlement
- Unresolved dimension: D-12.REASSIGNMENT
- Permission rows: PM-01, PM-16
- Test references: TC-08, TC-56
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-01.N2 - VALIDATION_ERROR

- Preconditions: Draft contains duplicate or ambiguous step order under the chosen validation policy
- When: C-101 submits the ambiguous steps
- Reason: The selected ordering rule is not satisfied
- Observable expected result: Readable validation and no partially saved authoritative plan
- Justified boundary: D-02 editable/order policy; S11 scaffold is not a complete validator
- Unresolved dimension: D-02.EDITABLE_FIELDS
- Permission rows: PM-03
- Test references: TC-07
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-02 - Coach

- Use case: UC-F4-01C
- Requirement: F4-REQ-01
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-02, D-12, D-13
- Permission rows: PM-02, PM-06, PM-07, PM-17
- Related tests: TC-02, TC-05, TC-06, TC-50, TC-59
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given C-101 assigned to K-401 and fully approved class-audience/publication policy exists, when Coach saves shared steps and intentionally publishes to the validated audience, then Only entitled recipients see shared steps; personal outcomes stay isolated. Every policy choice in the Given requires full approval-completeness evidence.

### AS-02.N1 - DENY

- Preconditions: C-101 assigned to K-401 only
- When: C-101 switches classId to another class and assigns its plan
- Reason: Class target is outside current authoring scope
- Observable expected result: No foreign class plan assignment or recipient disclosure
- Justified boundary: S05/S06 own assigned scope; D-12 extension pending
- Unresolved dimension: D-12.RESOURCE_RIGHTS
- Permission rows: PM-02, PM-06, PM-17
- Test references: TC-02, TC-50
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-02.N2 - PENDING_POLICY

- Preconditions: No signed choice between recipient snapshot and dynamic enrollment
- When: Coach requests publication after membership changes
- Reason: Intended recipient set has no approved definition
- Observable expected result: No publication success can be accepted or release gate closed until audience policy is approved; no audience is guessed
- Justified boundary: D-02 recipient choice unresolved; this is an acceptance gate, not an implemented runtime denial
- Unresolved dimension: D-02.RECIPIENTS
- Permission rows: PM-06
- Test references: TC-05, TC-59
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-03 - Member

- Use case: UC-F4-06
- Requirement: F4-REQ-06
- Actor: Member
- Operation kind: READ_ONLY
- Blocking decisions: D-02, D-03, D-12
- Permission rows: PM-18, PM-19, PM-22, PM-34
- Related tests: TC-03, TC-19, TC-33, TC-37, TC-50, TC-57
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given M-201 has an entitled published plan B and visibility policy is fully approved, when M-201 reads own active plan, then Permitted B and only member-visible feedback are returned. Every policy choice in the Given requires full approval-completeness evidence.

### AS-03.N1 - HIDDEN_PRIVATE_DATA

- Preconditions: Plan A remains Coach draft and is not published to M-201
- When: Member requests A directly or through the plan list
- Reason: Draft state does not grant Member visibility under the proposed policy
- Observable expected result: No A content or private draft metadata returned
- Justified boundary: D-02 publication and D-12 field visibility; not a mutation test
- Unresolved dimension: D-02.PUBLISH_AUTHORITY
- Permission rows: PM-18
- Test references: TC-03, TC-57
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-03.N2 - DENY

- Preconditions: Personal P-302 is assigned only to M-202
- When: M-201 requests P-302 by planId
- Reason: Subject does not own or hold an entitlement to the personal plan
- Observable expected result: No peer plan content, counts or existence disclosure under the selected denial policy
- Justified boundary: D-12 personal record scope; S08 principal binding pattern
- Unresolved dimension: D-12.RESOURCE_RIGHTS
- Permission rows: PM-18
- Test references: TC-33, TC-50
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-03.N3 - EMPTY

- Preconditions: M-201 has no permitted published plan in selected period
- When: Member opens own plan list
- Reason: There are no visible records rather than an authorization failure
- Observable expected result: Honest empty view; no inferred draft, attendance or plan outcome
- Justified boundary: Original Member capability and D-02 publication option; UI behavior proposed
- Unresolved dimension: D-02.PUBLISH_AUTHORITY
- Permission rows: PM-18
- Test references: TC-57
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-04 - Coach

- Use case: UC-F4-02
- Requirement: F4-REQ-02
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-03, D-12, D-13
- Permission rows: PM-08, PM-09, PM-35
- Related tests: TC-09, TC-10, TC-11, TC-12, TC-13, TC-14
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given C-101 assigned to eligible S-501/M-201 and metric/eligibility/correction policy fully approved, when Coach submits dictionary-valid observations after the session, then Result is bound to S-501/M-201, approved metric meaning and attributable author/time. Every policy choice in the Given requires full approval-completeness evidence.

### AS-04.N1 - NOT_FOUND

- Preconditions: Cached S-501 context is no longer available to the authorized actor
- When: Coach submits the result from the cached form
- Reason: Required session context cannot be verified
- Observable expected result: No invented session or result; readable unavailable outcome without private fallback
- Justified boundary: S05/S06 context and D-13 eligibility; analytical category does not prescribe HTTP
- Unresolved dimension: D-13.ELIGIBILITY
- Permission rows: PM-08
- Test references: TC-10
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-04.N2 - VALIDATION_ERROR

- Preconditions: Approved dictionary requires a unit/type not met by the entered observation
- When: Coach submits incompatible unit, coerced text or value outside approved validation
- Reason: Observation violates the approved metric definition
- Observable expected result: No authoritative invalid result or silent coercion; missing remains distinct from zero
- Justified boundary: D-03 metric/validation choice; S13 defaults do not supply approved values
- Unresolved dimension: D-03.VALIDATION
- Permission rows: PM-08
- Test references: TC-11, TC-12
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-04.N3 - CONFLICT

- Preconditions: Result revision A was concurrently corrected to B
- When: Coach submits stale correction against A
- Reason: A newer accepted observation must not be lost
- Observable expected result: No silent overwrite; refresh current authorized revision under proposed correction policy
- Justified boundary: D-03 accountable correction; no runtime implementation claimed
- Unresolved dimension: D-03.CORRECTION_MECHANISM
- Permission rows: PM-09
- Test references: TC-13, TC-14
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-05 - Coach

- Use case: UC-F4-03
- Requirement: F4-REQ-03
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-03, D-12
- Permission rows: PM-10, PM-11, PM-14, PM-30
- Related tests: TC-16, TC-17, TC-18, TC-19, TC-50, TC-59
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given C-101 has approved scoped history access and feedback visibility rules, when Coach records an evidence-linked assessment for M-201, then Assessment cites available observations and classifies comments by the approved audience rule. Every policy choice in the Given requires full approval-completeness evidence.

### AS-05.N1 - DENY

- Preconditions: M-202 is outside C-101 authorized teaching/history scope
- When: Coach opens M-202 progress to write an assessment
- Reason: Roster access does not grant unrestricted member history
- Observable expected result: No foreign history or new assessment disclosure/save
- Justified boundary: S05/S06 limited roster scope; D-12 history entitlement pending
- Unresolved dimension: D-12.HISTORIC_ENTITLEMENT
- Permission rows: PM-10, PM-14, PM-17
- Test references: TC-16, TC-50
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-05.N2 - PENDING_POLICY

- Preconditions: No signed feedback privacy classification or retention policy
- When: Coach asks to publish a narrative assessment
- Reason: Audience and retention obligations remain unsigned
- Observable expected result: Feedback release cannot be accepted and no private/public classification is guessed
- Justified boundary: D-03 comments/retention and D-12 field policy; acceptance gate only
- Unresolved dimension: D-03.COMMENT_VISIBILITY
- Permission rows: PM-11
- Test references: TC-19, TC-59
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-06 - Coach

- Use case: UC-F4-04
- Requirement: F4-REQ-04
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-01, D-12, D-13
- Permission rows: PM-12, PM-17, PM-35
- Related tests: TC-20, TC-22, TC-23, TC-25, TC-26, TC-38
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given C-101 currently assigned S-501, roster M-201 confirmed and attendance policy fully approved, when Coach submits participation for S-501/M-201, then One current per-session outcome is confirmed with provenance and no financial side effect. Every policy choice in the Given requires full approval-completeness evidence.

### AS-06.N1 - DENY

- Preconditions: Booking cancelled or member removed from eligible roster after form load
- When: Coach submits participation for stale member entry
- Reason: Current eligibility differs from the cached roster
- Observable expected result: No new participation, booking or package debit/refund; refresh required
- Justified boundary: S05/S06 confirmed roster context; D-13 eligibility proposal
- Unresolved dimension: D-13.ELIGIBILITY
- Permission rows: PM-12, PM-35
- Test references: TC-22, TC-26
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-06.N2 - CONFLICT

- Preconditions: An attendance submission for S-501/M-201 already accepted and retry conflicts with current version
- When: Coach resubmits inconsistent current outcome after timeout
- Reason: One current record must not be duplicated or silently changed
- Observable expected result: No second authoritative record; current outcome remains until an authorized correction
- Justified boundary: S12 uniqueness scaffold; D-13 duplicate/correction policy pending
- Unresolved dimension: D-13.UNIQUENESS
- Permission rows: PM-12, PM-13
- Test references: TC-23, TC-25
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-07 - Coach

- Use case: UC-F4-04C
- Requirement: F4-REQ-04
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-01, D-12, D-13
- Permission rows: PM-13, PM-16, PM-33
- Related tests: TC-24, TC-25, TC-38, TC-53
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given Current authorized corrector opens S-501/M-201 revision A and correction policy fully approved, when Coach saves a reasoned correction against A, then Old/current outcome, actor/time and approved correction summary remain attributable. Every policy choice in the Given requires full approval-completeness evidence.

### AS-07.N1 - CONFLICT

- Preconditions: Two authorized correctors loaded A; first accepted B
- When: Second submits C still based on A
- Reason: Latest accepted outcome must not be lost
- Observable expected result: C does not silently replace B; second reviewer refreshes current authorized record
- Justified boundary: D-13 correction history and concurrency proposal
- Unresolved dimension: D-13.CORRECTION_AUTHORITY
- Permission rows: PM-13
- Test references: TC-25
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-07.N2 - DENY

- Preconditions: Original author is now former Coach with no current delegated correction grant
- When: Former Coach edits old attendance
- Reason: Authorship alone is not present correction authority
- Observable expected result: No corrected record or new private history access
- Justified boundary: S05 current scope; D-12 reassignment and D-13 correction rights pending
- Unresolved dimension: D-12.CORRECTION_RIGHTS
- Permission rows: PM-13, PM-16
- Test references: TC-24, TC-38
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-07.N3 - VALIDATION_ERROR

- Preconditions: Selected correction policy requires reason but input reason is missing
- When: Authorized Coach submits reasonless correction
- Reason: Mandatory accountability input is absent under that proposed option
- Observable expected result: No accepted unaccountable correction; readable reason validation
- Justified boundary: D-13 correction proposal; S12 reason column scaffold
- Unresolved dimension: D-13.HISTORY
- Permission rows: PM-13
- Test references: TC-24, TC-53
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-08 - Receptionist

- Use case: UC-F4-07
- Requirement: F4-REQ-07
- Actor: Receptionist
- Operation kind: EXISTING_CHECK_IN
- Blocking decisions: D-01, D-12, D-13
- Permission rows: PM-24, PM-25, PM-28
- Related tests: TC-27, TC-28, TC-29, TC-30, TC-49, TC-52, TC-54, TC-55, TC-58
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given Receptionist R-601 authenticated; identified M-201 has selected confirmed booking today and active date-valid package, when R-601 records arrival for that booking, then Existing path saves an ALLOWED arrival and activity event without extra package consumption; Coach assignment is not an eligibility condition. Every policy choice in the Given requires full approval-completeness evidence.

### AS-08.N1 - NOT_FOUND

- Preconditions: Provided identifier has no MemberProfile
- When: Receptionist requests arrival for the unknown identifier
- Reason: Current service cannot resolve member
- Observable expected result: Existing resource error; no invented member, arrival or participation
- Justified boundary: S01 recordCheckIn lines 49-58
- Unresolved dimension: No additional dimension chosen; cited existing rule or parent decision applies
- Permission rows: PM-24
- Test references: TC-28, TC-58
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-08.N2 - DENY

- Preconditions: Member has active package but no confirmed booking for centre-local today, including cancelled booking
- When: Receptionist requests arrival
- Reason: Current-day confirmed booking required independently of any Coach assignment
- Observable expected result: Existing DENIED attempt with no-booking/invalid-booking explanation; no automatic booking or participation
- Justified boundary: S01 lines 60-89,100-103
- Unresolved dimension: No additional dimension chosen; cited existing rule or parent decision applies
- Permission rows: PM-24
- Test references: TC-28, TC-54
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-08.N3 - DENY

- Preconditions: Explicit selected booking already has ALLOWED arrival
- When: Receptionist repeats check-in for that same booking
- Reason: Existing duplicate ALLOWED check prevents another allowed arrival
- Observable expected result: Existing duplicate DENIED attempt; original allowed arrival retained and no extra debit
- Justified boundary: S01 lines 93-104,122-142
- Unresolved dimension: No additional dimension chosen; cited existing rule or parent decision applies
- Permission rows: PM-24
- Test references: TC-29, TC-55
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-08.N4 - DENY

- Preconditions: Confirmed today booking links package expired or not yet valid
- When: Receptionist requests arrival
- Reason: Package is outside its valid date range
- Observable expected result: Existing DENIED arrival attempt; no financial change or participation inference
- Justified boundary: S01 lines 105-117
- Unresolved dimension: No additional dimension chosen; cited existing rule or parent decision applies
- Permission rows: PM-24
- Test references: TC-27, TC-58
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-08.N5 - DENY

- Preconditions: Signed-in principal is Coach-only or Member rather than existing Receptionist/Manager role
- When: Principal calls protected arrival write
- Reason: Role guard denies that principal
- Observable expected result: No arrival write or private member confirmation returned by the protected operation
- Justified boundary: S02 CheckInController lines 26-32
- Unresolved dimension: No additional dimension chosen; cited existing rule or parent decision applies
- Permission rows: PM-24, PM-28
- Test references: TC-30, TC-52
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-09 - Member

- Use case: UC-F4-05
- Requirement: F4-REQ-05
- Actor: Member
- Operation kind: READ_ONLY
- Blocking decisions: D-01, D-03, D-12, D-13
- Permission rows: PM-20, PM-21, PM-33
- Related tests: TC-32, TC-34, TC-35, TC-50, TC-51
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given M-201 authenticated and own attendance/outcomes visible under approved policy, when Member filters own history by an approved date range, then Only own permitted session participation/results and allowed correction indicators returned. Every policy choice in the Given requires full approval-completeness evidence.

### AS-09.N1 - DENY

- Preconditions: M-202 has a private result/attendance record
- When: M-201 swaps memberId or sessionId to retrieve the peer record
- Reason: Principal is not the subject and has no entitlement
- Observable expected result: No foreign row, total, comment or existence detail disclosed
- Justified boundary: D-12 own record scope; S07/S08 principal patterns
- Unresolved dimension: D-12.RESOURCE_RIGHTS
- Permission rows: PM-20, PM-21
- Test references: TC-32, TC-50
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-09.N2 - EMPTY

- Preconditions: Valid selected range contains no own training records
- When: Member reads own range
- Reason: No eligible data exists; this is not an absence determination
- Observable expected result: Honest empty history, no invented zero result or no-show
- Justified boundary: D-03 result meaning and D-13 attendance semantics
- Unresolved dimension: No additional dimension chosen; cited existing rule or parent decision applies
- Permission rows: PM-20, PM-21
- Test references: TC-34
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-09.N3 - DENY

- Preconditions: M-201 account deactivated since old token/cache was created
- When: Member requests own history with stale token or cache
- Reason: Current enabled account required; old token is insufficient
- Observable expected result: No private history returned, including cache/page projections
- Justified boundary: S09 enabled account check; D-12 cache/entitlement proposal
- Unresolved dimension: D-12.RESOURCE_RIGHTS
- Permission rows: PM-33
- Test references: TC-35, TC-51
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-10 - Member

- Use case: UC-F4-06
- Requirement: F4-REQ-06
- Actor: Member
- Operation kind: READ_ONLY
- Blocking decisions: D-02, D-03, D-12
- Permission rows: PM-18, PM-19, PM-22, PM-34
- Related tests: TC-03, TC-05, TC-19, TC-33, TC-37, TC-59
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given M-201 previously entitled to revision A and allowed feedback; historical policy fully approved, when Member opens permitted historical plans/comments after class withdrawal, then Only history permitted by the chosen entitlement/retention rule is returned; no future revisions or restricted notes. Every policy choice in the Given requires full approval-completeness evidence.

### AS-10.N1 - HIDDEN_PRIVATE_DATA

- Preconditions: Coach retained a restricted narrative beside member-visible comment
- When: Member reads feedback/history
- Reason: Restricted field is not member-visible under the selected policy
- Observable expected result: Restricted text absent from body, hidden fields, totals and cache metadata
- Justified boundary: D-03 visibility plus D-12 field rules
- Unresolved dimension: D-12.FIELD_VISIBILITY
- Permission rows: PM-22
- Test references: TC-19
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-10.N2 - PENDING_POLICY

- Preconditions: Class withdrawal occurred; no signed historical entitlement/retention choice exists
- When: Member historical access behavior is reviewed for acceptance
- Reason: Past membership alone does not decide ongoing entitlement
- Observable expected result: No acceptance claim or permanent entitlement inferred; humans resolve before implementation
- Justified boundary: D-02/D-12 historical choices and D-03 retention unsigned
- Unresolved dimension: D-12.HISTORIC_ENTITLEMENT
- Permission rows: PM-19, PM-34
- Test references: TC-37, TC-59
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-10.N3 - DENY

- Preconditions: M-201 entitlement to future class revisions revoked by the selected policy; K-401 has new B
- When: Member requests future B by classId/planId
- Reason: Former membership does not grant new class content
- Observable expected result: Future B and other current-member data not disclosed; A separately evaluated
- Justified boundary: D-02 recipients/history and D-12 reassignment boundary
- Unresolved dimension: D-02.HISTORICAL_ACCESS
- Permission rows: PM-19, PM-34
- Test references: TC-05, TC-37
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.

## AS-11 - Coach

- Use case: UC-F4-01V
- Requirement: F4-REQ-01
- Actor: Coach
- Operation kind: PROPOSED_MUTATION
- Blocking decisions: D-02, D-12, D-13
- Permission rows: PM-03, PM-04, PM-07, PM-16, PM-33
- Related tests: TC-04, TC-08, TC-64, TC-65, TC-66
- Execution status: SPECIFIED_NOT_EXECUTED

PROPOSED success: Given Synthetic current Coach C-101 is assigned to the permitted M-201/K-401 context, P-301 exists as draft or published plan with known revision A, and the linked unresolved policy dimensions are assumed solely as explicit fixture preconditions rather than signed decisions, when C-101 publishes or revises P-301 within that authorized context using expected current revision A, then The proposed accepted outcome attributes the action to C-101, preserves immutable or otherwise traceable revision history according to the eventual policy, loses no intervening update, applies only the eventual permitted visibility and leaves bookings, balances, payment/refund amounts and Receptionist arrival records unchanged. Every policy choice in the Given requires full approval-completeness evidence.

Conditional policy assumptions, all unsigned:

| Dimension | Fixture assumption |
| --- | --- |
| D-02.PUBLISH_AUTHORITY | For this fixture only, current C-101 is permitted to publish in the selected context. No runtime entitlement is adopted. |
| D-02.REVISION_LIFECYCLE | The eventual policy supplies immutable or otherwise traceable revision history. No retention duration, locking mechanism or revision algorithm is chosen. |
| D-02.EDITABLE_FIELDS | Proposed fields/steps and any editing-time restriction satisfy the eventual signed policy; no correction deadline is invented. |
| D-02.RECIPIENTS | The eventual recipient/visibility policy determines who can see the accepted publication and when. No timing or assignment lifespan is selected. |
| D-12.REASSIGNMENT | Current authority is checked again at mutation time; expired or revoked rights do not become permanent by authorship. |
| D-13.FINANCIAL_INVARIANTS | The fixture expects no unintended booking, balance, payment/refund or arrival changes; existing behavior is preserved. |

Source: Project!E5 Coach 3; Requirement Map row 6; S05 current scope pattern, S11 plan scaffold, S14 unapproved lifecycle design; claims pinned to current source SHA.

### AS-11.N1 - DENY

- Preconditions: C-101 had plan access when opening revision A but authority expired or assignment was revoked before submission
- When: C-101 attempts publication or revision after revocation
- Reason: Current mutation authority is absent; former authorship is insufficient
- Observable expected result: No publication, new revision or protected data disclosure; no booking, payment or arrival side effect
- Justified boundary: S05 current account/assignment checks as reuse pattern; D-02.PUBLISH_AUTHORITY and D-12.REASSIGNMENT remain pending
- Unresolved dimension: D-12.REASSIGNMENT
- Permission rows: PM-04, PM-16, PM-33
- Test references: TC-08, TC-65
- Execution status: SPECIFIED_NOT_EXECUTED

### AS-11.N2 - CONFLICT

- Preconditions: C-101 loaded expected revision A; an intervening authorized edit made B authoritative before the write
- When: C-101 submits publication or revision still based on A
- Reason: The expected current revision is stale
- Observable expected result: No overwrite of B, lost update or partial authoritative publication/revision; reload the authorized current context without prescribing a locking/API mechanism
- Justified boundary: D-02.REVISION_LIFECYCLE and D-02.EDITABLE_FIELDS are conditional policy choices; S11 does not establish concurrency behavior
- Unresolved dimension: D-02.REVISION_LIFECYCLE
- Permission rows: PM-03, PM-04, PM-07
- Test references: TC-04, TC-66
- Execution status: SPECIFIED_NOT_EXECUTED

Read-only Member examples describe returned/hidden/empty data and never an unauthorized mutation. Rejected proposed Coach actions have no booking, balance, payment or refund side effects. Existing reception may save a DENIED attempt under S01; this is distinct from a training mutation, arrival is distinct from participation, and S03 self-training ACK is not durable attendance. No Coach assignment predicate is imposed on reception.
