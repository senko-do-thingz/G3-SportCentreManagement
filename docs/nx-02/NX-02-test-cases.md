# NX-02 business and remediation test specifications

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

All 66 specifications remain SPECIFIED_NOT_EXECUTED. Legacy TC-01 through TC-63 retain their meanings; TC-64/65/66 cover AS-11 publication and both negative paths. Executed documentation tests and controlled invalid fixtures are separate evidence, not business test PASS claims. Synthetic identities and record IDs describe no production data.

## Requirement coverage

| Requirement | Test IDs | Count |
| --- | --- | --- |
| F4-REQ-01 | TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-31, TC-39, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-50, TC-52, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63, TC-64, TC-65, TC-66 | 29 |
| F4-REQ-02 | TC-09, TC-10, TC-11, TC-12, TC-13, TC-14, TC-15, TC-31, TC-39, TC-40, TC-42, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-50, TC-52, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63 | 27 |
| F4-REQ-03 | TC-16, TC-17, TC-18, TC-19, TC-31, TC-39, TC-40, TC-42, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-50, TC-52, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63 | 24 |
| F4-REQ-04 | TC-15, TC-20, TC-21, TC-22, TC-23, TC-24, TC-25, TC-26, TC-30, TC-31, TC-38, TC-39, TC-41, TC-42, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-50, TC-52, TC-53, TC-54, TC-55, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63 | 33 |
| F4-REQ-05 | TC-32, TC-34, TC-35, TC-36, TC-39, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-50, TC-51, TC-52, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63 | 22 |
| F4-REQ-06 | TC-03, TC-04, TC-05, TC-18, TC-19, TC-33, TC-35, TC-36, TC-37, TC-39, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-50, TC-51, TC-52, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63 | 27 |
| F4-REQ-07 | TC-27, TC-28, TC-29, TC-30, TC-41, TC-43, TC-44, TC-45, TC-46, TC-47, TC-48, TC-49, TC-52, TC-54, TC-55, TC-56, TC-57, TC-58, TC-59, TC-60, TC-61, TC-62, TC-63 | 23 |

## ORIGINAL_BUSINESS_SPECIFICATIONS

### TC-01 - Create an individual draft

- Test ID: TC-01
- Requirement: F4-REQ-01
- Scenario: Create an individual draft
- Preconditions: C-101 currently assigned to M-201; approved personal-plan model

Steps:

1. Open M-201 authorized context.
2. enter ordered activities.
3. save draft P-301.
4. attempt Member read.

- Expected Result: Attributable personal draft is confirmed only after save; no Member draft disclosure and no financial mutation
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Business happy path
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-02 - Create a class plan without peer notes

- Test ID: TC-02
- Requirement: F4-REQ-01
- Scenario: Create a class plan without peer notes
- Preconditions: C-101 assigned K-401; M-201/M-202 eligible; class audience choice approved

Steps:

1. Create shared plan.
2. assign selected class audience.
3. publish after deliberate confirmation.
4. read as each Member.

- Expected Result: Both see permitted shared steps; neither receives the other member personal notes/results
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Business happy path and privacy
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-03 - Draft is excluded from Member access

- Test ID: TC-03
- Requirement: F4-REQ-01, F4-REQ-06
- Scenario: Draft is excluded from Member access
- Preconditions: Personal draft A and published B belong to M-201; publication policy approved

Steps:

1. Read own plan list as M-201.
2. request A directly.
3. compare response fields and counts.

- Expected Result: B may be shown; A content and draft existence details remain undisclosed under selected denial policy
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Negative authorization
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-04 - Published revision preserves past session context

- Test ID: TC-04
- Requirement: F4-REQ-01, F4-REQ-06
- Scenario: Published revision preserves past session context
- Preconditions: S-501 completed using revision A; current C-101 authorized to revise

Steps:

1. Create and publish B.
2. open S-501 history and current plan.
3. compare references.

- Expected Result: Past S-501 retains A and current assignment uses selected effective revision; no silent historical rewrite
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: History acceptance
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-05 - Withdrawn assignment revokes future class content

- Test ID: TC-05
- Requirement: F4-REQ-01, F4-REQ-06
- Scenario: Withdrawn assignment revokes future class content
- Preconditions: M-201 previously entitled A, then removed from K-401; D-12 history choice approved

Steps:

1. Publish future B for K-401.
2. request B as M-201.
3. request previously entitled A.

- Expected Result: B denied; A availability follows separately selected historical entitlement and retention policy
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Historical authorization
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-06 - Clone template without member narrative

- Test ID: TC-06
- Requirement: F4-REQ-01
- Scenario: Clone template without member narrative
- Preconditions: Template use approved; source includes permitted shared content and a restricted member note

Steps:

1. Clone for another authorized class.
2. inspect copied steps/origin.
3. inspect notes and audience.

- Expected Result: Only permitted shared content copied, origin retained, restricted note excluded and new audience validated
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Data minimization
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-07 - Reject ambiguous step ordering

- Test ID: TC-07
- Requirement: F4-REQ-01
- Scenario: Reject ambiguous step ordering
- Preconditions: Current Coach editing draft with approved ordering validation

Steps:

1. Submit repeated/invalid ordering values.
2. retry after correcting order.
3. inspect stored revision.

- Expected Result: Ambiguity rejected using readable validation; valid unique ordering retained without partial corrupt steps
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Validation
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-08 - Former Coach cannot publish or edit by authorship alone

- Test ID: TC-08
- Requirement: F4-REQ-01
- Scenario: Former Coach cannot publish or edit by authorship alone
- Preconditions: C-101 authored P-301; C-102 now assigned; no delegation granted

Steps:

1. C-101 submits edit and publication from previously opened form.
2. reload plan as current Coach.

- Expected Result: Former Coach write denied without exposing new private content; no unauthorized revision or publication
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Reassignment denial
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-09 - Record valid post-session outcome

- Test ID: TC-09
- Requirement: F4-REQ-02
- Scenario: Record valid post-session outcome
- Preconditions: Approved metric Skill-A/unit and eligible completed S-501/M-201; C-101 assigned

Steps:

1. Open session result.
2. enter a dictionary-valid value and permitted note.
3. submit.
4. read own result as M-201.

- Expected Result: Result linked to exact session/member/metric meaning, actor/time attributable, own permitted outcome readable
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Business happy path
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-10 - Missing or stale session blocks result

- Test ID: TC-10
- Requirement: F4-REQ-02
- Scenario: Missing or stale session blocks result
- Preconditions: Coach form was opened for S-501; session is no longer available or valid

Steps:

1. Submit cached result after session context becomes missing.
2. reload available session context.

- Expected Result: No invented session/result; truthful context error and no balance/attendance mutation
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Missing dependency
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-11 - Missing metric never becomes zero

- Test ID: TC-11
- Requirement: F4-REQ-02
- Scenario: Missing metric never becomes zero
- Preconditions: Required metric policy selected; one value blank while another equals valid zero if allowed

Steps:

1. Submit blank metric.
2. inspect validation/incomplete state.
3. reload result.
4. compare blank and zero.

- Expected Result: Selected reject/incomplete policy applied explicitly; missing never stored/displayed as zero or failed progress
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Validation boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-12 - Reject invalid value and unit coercion

- Test ID: TC-12
- Requirement: F4-REQ-02
- Scenario: Reject invalid value and unit coercion
- Preconditions: Approved dictionary defines type/unit and allowed range; no numerical range invented by test

Steps:

1. Submit text where number expected, incompatible unit and value outside approved range.
2. inspect record.

- Expected Result: Readable validation; no silent coercion/truncation, invalid score save or partial authoritative result
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Negative validation
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-13 - Correct result with trace

- Test ID: TC-13
- Requirement: F4-REQ-02
- Scenario: Correct result with trace
- Preconditions: Existing R-701 revision A; current authorized corrector; correction policy approved

Steps:

1. Load A.
2. change permitted value with reason.
3. save.
4. inspect current and authorized history/audit.

- Expected Result: Current corrected value and prior value, actor/time/reason attributable; original observation not silently lost
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Correction acceptance
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-14 - Retry result after lost confirmation

- Test ID: TC-14
- Requirement: F4-REQ-02
- Scenario: Retry result after lost confirmation
- Preconditions: Authorized valid submission saved but client loses confirmation; retry policy selected

Steps:

1. Submit result, simulate lost confirmation, retry same intended result, inspect authoritative record count and audit.

- Expected Result: No duplicate current session/member result or extra balance change; confirmation accurately reflects the accepted operation
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Retry/idempotency
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-15 - Attendance correction does not erase result

- Test ID: TC-15
- Requirement: F4-REQ-02, F4-REQ-04
- Scenario: Attendance correction does not erase result
- Preconditions: Result exists for S-501/M-201; participation later corrected to meaning affecting eligibility

Steps:

1. Apply authorized attendance correction.
2. read result and progress.
3. inspect dependent review indication.

- Expected Result: No automatic result deletion/rewrite; chosen policy flags dependency for review and retains provenance
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Cross-record consistency
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-16 - Assess progress using actual observations

- Test ID: TC-16
- Requirement: F4-REQ-03
- Scenario: Assess progress using actual observations
- Preconditions: C-101 authorized M-201 history; approved comparable observations and missing values

Steps:

1. Select review period.
2. enter assessment citing observations.
3. save as selected visibility.
4. read feedback.

- Expected Result: Assessment source/period explicit; missing evidence remains visible and no invented aggregate metric appears
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Business happy path
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-17 - Empty history cannot imply poor progress

- Test ID: TC-17
- Requirement: F4-REQ-03
- Scenario: Empty history cannot imply poor progress
- Preconditions: M-201 has no training results in selected period; qualitative comment policy selected

Steps:

1. Open progress review.
2. attempt numerical comparison.
3. enter permitted qualitative comment if adopted.

- Expected Result: Clear insufficient-evidence state; no fabricated zero score, ranking or attendance conclusion
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Empty data boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-18 - Member-visible feedback is isolated

- Test ID: TC-18
- Requirement: F4-REQ-03, F4-REQ-06
- Scenario: Member-visible feedback is isolated
- Preconditions: Member-visible comment approved for M-201 and separate comment for M-202

Steps:

1. Publish permitted M-201 feedback.
2. read as M-201.
3. request M-202 feedback.

- Expected Result: M-201 sees only own authorized comment; peer content and counts not leaked
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Visibility happy and denial
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-19 - Restricted note never leaks through Member projection

- Test ID: TC-19
- Requirement: F4-REQ-03, F4-REQ-06
- Scenario: Restricted note never leaks through Member projection
- Preconditions: Restricted notes adopted by signed policy; visible comment and restricted note stored for M-201

Steps:

1. Read plan/progress/history and export if later offered as M-201.
2. inspect body, hidden fields, metadata and cache.

- Expected Result: Only permitted visible feedback returned; restricted note absent from every projection and minimized routine logs
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Field-level privacy
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-20 - Record attendance from fresh roster

- Test ID: TC-20
- Requirement: F4-REQ-04
- Scenario: Record attendance from fresh roster
- Preconditions: C-101 currently assigned S-501; M-201 confirmed; chosen attendance policy approved

Steps:

1. Refresh roster.
2. choose adopted participation meaning.
3. submit.
4. reload authorized Member history.

- Expected Result: One durable current participation for S-501/M-201 with actor/time; no arrival or financial side effect
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Business happy path
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-21 - Timing and absence meanings require adopted boundaries

- Test ID: TC-21
- Requirement: F4-REQ-04
- Scenario: Timing and absence meanings require adopted boundaries
- Preconditions: Humans have adopted status meanings and late threshold without test inventing it

Steps:

1. Exercise just before/at/after adopted boundary and selected absent/excused/no-show cases.
2. compare labels.

- Expected Result: Each follows selected documented policy; missing entry not automatically absent and cancellation not automatically no-show
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Policy boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-22 - Cancelled session is not a new participation event

- Test ID: TC-22
- Requirement: F4-REQ-04
- Scenario: Cancelled session is not a new participation event
- Preconditions: S-501 cancelled after roster form opened; session policy approved

Steps:

1. Attempt attendance submission from stale form.
2. reload session and history.

- Expected Result: Submission denied or requires approved exception path; no false participation, refund or package restoration
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Cancellation boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-23 - Duplicate attendance form has one current result

- Test ID: TC-23
- Requirement: F4-REQ-04
- Scenario: Duplicate attendance form has one current result
- Preconditions: Valid S-501/M-201 submission prepared; uniqueness policy approved

Steps:

1. Submit same attendance twice including retry after timeout.
2. inspect current records and correction history.

- Expected Result: One authoritative current outcome; retry does not become second participation or unrequested correction
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Duplicate prevention
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-24 - Correction keeps accountable history

- Test ID: TC-24
- Requirement: F4-REQ-04
- Scenario: Correction keeps accountable history
- Preconditions: Existing outcome revision A; authorized current Coach; reason policy approved

Steps:

1. Request correction.
2. enter new adopted meaning and reason.
3. confirm.
4. read authorized history.

- Expected Result: Current corrected outcome and old/new value, actor/time/reason retained; member sees only permitted correction summary
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Correction acceptance
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-25 - Competing corrections cannot silently lose changes

- Test ID: TC-25
- Requirement: F4-REQ-04
- Scenario: Competing corrections cannot silently lose changes
- Preconditions: Two authorized staff opened revision A; concurrency policy approved

Steps:

1. Staff one saves B.
2. staff two submits C against A.
3. refresh second view.
4. inspect audit.

- Expected Result: C cannot silently overwrite B; conflict/review outcome exposes current authorized revision without lost accepted evidence
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Concurrency
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-26 - Missing roster and unauthorized walk-in do not manufacture eligibility

- Test ID: TC-26
- Requirement: F4-REQ-04
- Scenario: Missing roster and unauthorized walk-in do not manufacture eligibility
- Preconditions: S-501 roster unavailable or M-203 has no eligible booking; no exception approved

Steps:

1. Open roster.
2. submit cached/non-roster member attendance.
3. inspect bookings and training records.

- Expected Result: Clear unavailable/denied outcome; no synthetic roster member, attendance or automatic booking
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Missing dependency and denial
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-27 - Reuse existing eligible reception arrival

- Test ID: TC-27
- Requirement: F4-REQ-07
- Scenario: Reuse existing eligible reception arrival
- Preconditions: Receptionist R-601; M-201 confirmed today booking; active date-valid package

Steps:

1. Select member and intended booking.
2. record check-in.
3. inspect authorized confirmation and history.

- Expected Result: Arrival ALLOWED and activity persisted through existing flow; no claim of Coach attendance and no extra package debit
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Existing behavior regression specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-28 - Walk-in without booking remains denied

- Test ID: TC-28
- Requirement: F4-REQ-07
- Scenario: Walk-in without booking remains denied
- Preconditions: M-203 has active package but no confirmed booking today

Steps:

1. Receptionist attempts check-in.
2. inspect denial and records.
3. inspect training participation.

- Expected Result: Existing no-booking denial recorded; no implicit booking or Coach attendance
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Negative eligibility
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-29 - Repeat explicit arrival booking is denied

- Test ID: TC-29
- Requirement: F4-REQ-07
- Scenario: Repeat explicit arrival booking is denied
- Preconditions: Booking B-801 already has ALLOWED arrival today

Steps:

1. Repeat arrival explicitly for B-801.
2. inspect arrival attempts/current participation/balance.

- Expected Result: Duplicate is an existing DENIED attempt; original ALLOWED retained, no second session consumption or inferred participation
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Duplicate regression
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-30 - Coach-only identity has no reception check-in grant

- Test ID: TC-30
- Requirement: F4-REQ-04, F4-REQ-07
- Scenario: Coach-only identity has no reception check-in grant
- Preconditions: C-101 has Coach role only; valid member/booking exists

Steps:

1. Attempt existing arrival write as Coach.
2. separately exercise authorized Coach attendance only after policy approval.

- Expected Result: Reception arrival denied for Coach-only role; approved scoped participation rights do not become desk rights
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Role boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-31 - Receptionist cannot perform Coach writes

- Test ID: TC-31
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04
- Scenario: Receptionist cannot perform Coach writes
- Preconditions: R-601 has Receptionist only; plan/result/feedback/participation target exists

Steps:

1. Attempt create/edit/publish plan, result, assessment and Coach participation as R-601.

- Expected Result: All Coach-only writes denied absent explicit separate approved grant; no record changes or narrative disclosure
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Role boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-32 - Member reads only own history

- Test ID: TC-32
- Requirement: F4-REQ-05
- Scenario: Member reads only own history
- Preconditions: M-201 has own permitted attendance/results; M-202 also has records

Steps:

1. Read as M-201.
2. apply own date filter.
3. change memberId/sessionId selector to M-202 record.

- Expected Result: Own permitted rows shown; foreign rows, totals and existence details denied or excluded consistently
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Member isolation
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-33 - Guessed personal plan does not reveal peer data

- Test ID: TC-33
- Requirement: F4-REQ-06
- Scenario: Guessed personal plan does not reveal peer data
- Preconditions: P-302 belongs only to M-202; M-201 authenticated

Steps:

1. Request P-302 by planId as M-201.
2. compare response/error metadata to inaccessible unknown plan.

- Expected Result: No P-302 content, publication metadata or existence leak; exact denial contract deferred to NX-03
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: IDOR denial
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-34 - Filter empty history is honest

- Test ID: TC-34
- Requirement: F4-REQ-05
- Scenario: Filter empty history is honest
- Preconditions: M-201 has no records in selected valid range; another range contains records

Steps:

1. Request empty range.
2. use reversed/invalid dates.
3. request valid range again.

- Expected Result: Empty state distinguishes no data from unavailable; invalid filters rejected without widening scope
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Filters and empty/error
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-35 - Stale token after deactivation grants no history

- Test ID: TC-35
- Requirement: F4-REQ-05, F4-REQ-06
- Scenario: Stale token after deactivation grants no history
- Preconditions: M-201 had valid token then account deactivated; protected training records exist

Steps:

1. Use old token for plan/history reads and repeat through cached UI if later supported.

- Expected Result: No private data returned; current account status governs access and no token/narrative is logged
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Stale authorization
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-36 - Former Coach history right is independently checked

- Test ID: TC-36
- Requirement: F4-REQ-05, F4-REQ-06
- Scenario: Former Coach history right is independently checked
- Preconditions: C-101 formerly assigned M-201; C-102 current; historical policy selected

Steps:

1. As C-101 request old result/plan and current history.
2. attempt correction.

- Expected Result: Reads follow explicitly chosen historical entitlement; writes denied without current approved authority, no authorship shortcut
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Historical ownership
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-37 - Former class member sees no future revisions

- Test ID: TC-37
- Requirement: F4-REQ-06
- Scenario: Former class member sees no future revisions
- Preconditions: M-201 removed after entitled published A; current K-401 receives B

Steps:

1. Request B via classId and planId.
2. request A through history.
3. inspect feedback of current members.

- Expected Result: Future B denied; A follows selected retention/entitlement; no current-member private data disclosed
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Withdrawn assignment
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-38 - Assignment changes during attendance submission

- Test ID: TC-38
- Requirement: F4-REQ-04
- Scenario: Assignment changes during attendance submission
- Preconditions: C-101 loaded S-501; session reassigned to C-102 before submit

Steps:

1. Submit participation as C-101.
2. refresh roster as C-102.
3. inspect accepted record/audit.

- Expected Result: Current assignment revalidated; former Coach denied unless explicit delegated correction authority approved
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Race and assignment
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-39 - Manager has no implicit training superuser privilege

- Test ID: TC-39
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06
- Scenario: Manager has no implicit training superuser privilege
- Preconditions: Manager has existing roster/check-in/audit rights; no new training oversight grant signed

Steps:

1. Read existing roster.
2. attempt restricted note access and Coach training writes.

- Expected Result: Existing bounded reads retain behavior; new training privilege denied until explicit resource/field policy adopted
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Manager oversight boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-40 - Logs use synthetic minimized metadata

- Test ID: TC-40
- Requirement: F4-REQ-02, F4-REQ-03
- Scenario: Logs use synthetic minimized metadata
- Preconditions: Training event includes synthetic narrative marker PRIVATE-NOTE-TEST; logging policy approved

Steps:

1. Exercise authorized save/denial/correction.
2. inspect approved test logs and activity metadata.

- Expected Result: Routine logs omit raw narrative/token/credentials; only justified metadata retained with restricted access
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Privacy and logging
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-41 - Attendance submission preserves balances

- Test ID: TC-41
- Requirement: F4-REQ-04, F4-REQ-07
- Scenario: Attendance submission preserves balances
- Preconditions: Record pre-state: package sessions, seat count, bookings, payment/refund/cash event counts

Steps:

1. Create authorized attendance.
2. retry.
3. compare pre/post financial and booking data.

- Expected Result: No extra consume/restore, booking/seat mutation or payment/refund/cash event from attendance
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Compatibility invariant
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-42 - Training corrections remain financially neutral

- Test ID: TC-42
- Requirement: F4-REQ-02, F4-REQ-03, F4-REQ-04
- Scenario: Training corrections remain financially neutral
- Preconditions: Pre-existing result/feedback/attendance and known package/payment/booking state

Steps:

1. Correct result, feedback and participation separately.
2. inspect balance and finance history after each.

- Expected Result: Only approved training revisions change; no debit, credit, invoice, refund, cash or booking side effects
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Compatibility invariant
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-43 - Missing prerequisite signatures block formal acceptance

- Test ID: TC-43
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Missing prerequisite signatures block formal acceptance
- Preconditions: No dated NX-00/NX-01 signatures or approved exceptions; technical reports exist

Steps:

1. Evaluate acceptance sheet using exact source versions and current signature evidence.

- Expected Result: Technical report may be complete; formal acceptance and G-00 remain BLOCKED, no fabricated approver/date
- Evidence Needed: Actual reviewed source versions, unsigned/signed decision records, generated package checks and reviewer evidence. This specification is not itself an executed gate check.
- Type: Approval gate specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-09/D-11
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-44 - Open decision stays pending despite recommendation

- Test ID: TC-44
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Open decision stays pending despite recommendation
- Preconditions: D-02/D-03/D-12/D-13 strategic letters may be proposed or selected without signed required subdecisions

Steps:

1. Inspect strategic evidence independently of recommendations.
2. Count every required dimension and its approval/exception evidence.
3. Evaluate formal completeness.

- Expected Result: No composite decision is fully approved by a strategic letter; unsigned required dimensions keep PENDING_HUMAN_DECISION and block formal acceptance
- Evidence Needed: Actual reviewed source versions, unsigned/signed decision records, generated package checks and reviewer evidence. This specification is not itself an executed gate check.
- Type: Decision process specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-03/D-12/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified. Round 2 update preserves TC-44 while strengthening approval completeness.

### TC-45 - Optional capabilities never auto-promote

- Test ID: TC-45
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Optional capabilities never auto-promote
- Preconditions: Original G5 optional; F4-CAND-08 and F5-CAND-02 unapproved

Steps:

1. Review scope handoff with Flow 4 Go still unsigned.
2. inspect optional AI/homework acceptance claims.

- Expected Result: Flow 4 adoption remains pending, candidates not mandatory, Flow 5/6 independently deferred and no provider chosen
- Evidence Needed: Actual reviewed source versions, unsigned/signed decision records, generated package checks and reviewer evidence. This specification is not itself an executed gate check.
- Type: Scope gate specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-04/D-05/D-08/D-11
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-46 - D-10 contract work deferred to NX-03

- Test ID: TC-46
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: D-10 contract work deferred to NX-03
- Preconditions: NX-02 scenario package exists but no approved contract

Steps:

1. Inspect NX-02 report for new method/path, DTO shapes or transport status claims.
2. review handoff.

- Expected Result: No new API/DTO represented as approved; D-10 routes to NX-03 and FE agreement before implementation
- Evidence Needed: Actual reviewed source versions, unsigned/signed decision records, generated package checks and reviewer evidence. This specification is not itself an executed gate check.
- Type: Handoff specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-10
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-47 - Persistence work deferred to NX-04

- Test ID: TC-47
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Persistence work deferred to NX-04
- Preconditions: V16 and design conflict; requirements choice pending

Steps:

1. Review generated files and handoff.
2. inspect repository diff and database activity evidence.

- Expected Result: No new SQL, migration/schema/API/production changes; NX-04 reconciliation awaits policy/contracts
- Evidence Needed: Actual reviewed source versions, unsigned/signed decision records, generated package checks and reviewer evidence. This specification is not itself an executed gate check.
- Type: Handoff and scope specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-48 - Hung and Truc review field and visibility inputs

- Test ID: TC-48
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Hung and Truc review field and visibility inputs
- Preconditions: NX-05/NX-06 fields/screens planned; no dated FE review

Steps:

1. Check Coach/Reception and Member scenario maps.
2. locate required FE review signatures.

- Expected Result: Hung/Truc reviews stay unsigned; future screens need approved fields, visibility, empty/loading/denied/error and responsive/accessibility states
- Evidence Needed: Actual reviewed source versions, unsigned/signed decision records, generated package checks and reviewer evidence. This specification is not itself an executed gate check.
- Type: FE handoff specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-03/D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-49 - Multiple sessions and omitted booking selection

- Test ID: TC-49
- Requirement: F4-REQ-07
- Scenario: Multiple sessions and omitted booking selection
- Preconditions: M-201 has two confirmed today bookings B-801/B-802; neither checked in

Steps:

1. Check in explicit B-801.
2. retry without booking selection.
3. compare resolved booking and arrival labels.

- Expected Result: Existing unchecked-selection behavior may resolve B-802; no one-per-day assumption, no fabricated idempotent same-response claim, UX choice returned to humans
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Multi-session boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-50 - All resource selectors enforce record scope

- Test ID: TC-50
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06
- Scenario: All resource selectors enforce record scope
- Preconditions: Two coaches/classes/members and private plan/result/attendance records exist

Steps:

1. Change memberId, sessionId, classId and planId one at a time in authorized requests.
2. inspect errors, totals, fields and cache.

- Expected Result: Every selector is reauthorized on server; no foreign data, hidden note or resource-existence disclosure
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: IDOR matrix
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-51 - Read cache does not outlive revoked entitlement

- Test ID: TC-51
- Requirement: F4-REQ-05, F4-REQ-06
- Scenario: Read cache does not outlive revoked entitlement
- Preconditions: M-201 authorized and cached history; entitlement revoked before next page/read

Steps:

1. Request subsequent page and cached detail after revocation.
2. switch authenticated member.

- Expected Result: New response/cache projection revalidates scope; revoked/other-principal private data not returned
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Cache and stale access
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-52 - Client role and unknown Admin label grant nothing

- Test ID: TC-52
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Client role and unknown Admin label grant nothing
- Preconditions: Member/unknown Admin principal; client supplies Coach role or ownerId

Steps:

1. Attempt protected read/write with forged role/ownership fields and swapped subject.

- Expected Result: Trusted current server role/entitlement controls outcome; no Manager/Admin/Coach privilege inferred from input label
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Privilege escalation
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-53 - Audit failure cannot produce false correction confirmation

- Test ID: TC-53
- Requirement: F4-REQ-04
- Scenario: Audit failure cannot produce false correction confirmation
- Preconditions: Authorized correction prepared; adopted atomicity policy explicitly defined later

Steps:

1. Inject approved test audit persistence failure during correction.
2. reload attendance and evidence.

- Expected Result: No confirmed success with missing mandatory evidence; rollback or approved reconciliation behavior matches chosen policy and is observable
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Failure consistency
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-54 - Centre-local midnight governs today eligibility

- Test ID: TC-54
- Requirement: F4-REQ-04, F4-REQ-07
- Scenario: Centre-local midnight governs today eligibility
- Preconditions: Clock fixed around Asia/Ho_Chi_Minh midnight; browser date differs; bookings on adjacent dates

Steps:

1. Attempt check-in before/after centre midnight.
2. open same sessions in Member history.
3. inspect displayed dates.

- Expected Result: Existing today rule follows application Clock; history and arrival labels preserve session date, no browser-date eligibility bypass; future zone/DST variants need separate policy
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Time boundary
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

### TC-55 - Arrival and cancellation race preserves existing invariants

- Test ID: TC-55
- Requirement: F4-REQ-04, F4-REQ-07
- Scenario: Arrival and cancellation race preserves existing invariants
- Preconditions: Confirmed eligible booking; concurrent arrival and allowed cancellation requests in approved test environment

Steps:

1. Run competing requests under existing lock order.
2. inspect booking, arrival, seat/package balance and audit.

- Expected Result: Serialized outcome respects existing ALLOWED cancellation guard and controlled restoration, with no training-triggered extra debit/refund or fabricated participation
- Evidence Needed: Dated signed policy/version plus authorized synthetic UI/service observations, stored record/revision and minimized audit evidence; compare balances where relevant. No runtime evidence yet.
- Type: Concurrency and regression
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01/D-13
- Notes: PROPOSED acceptance example. Policy-dependent expectations require approval. No endpoint, DTO, numeric threshold or retention duration is specified.

## ROUND2_REMediation

### TC-56 - Use-case actors separate subject from runtime participation

- Test ID: TC-56
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Use-case actors separate subject from runtime participation
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Read all ten UC actor fields.
2. compare read-only and reception paths.

- Expected Result: Member is subject for Coach work, not mandatory supporting actor; Member reads have no runtime Coach reviewer; Receptionist has Member support without Coach assignment
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-12
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-57 - Member scenario negatives describe read outcomes

- Test ID: TC-57
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Member scenario negatives describe read outcomes
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Inspect AS-03/AS-09/AS-10 foreign/draft/private/empty contexts.

- Expected Result: Read-only outcomes use denial/hidden/empty/pending policy without unauthorized-mutation wording or invented records
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-03/D-12
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-58 - Reception arrival negatives follow actual source guards

- Test ID: TC-58
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Reception arrival negatives follow actual source guards
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Inspect unknown member, no booking, duplicate, date-invalid package and wrong-role cases.

- Expected Result: Source-based outcomes require no Coach assignment and never imply Coach participation or extra debit
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-01
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-59 - Strategic direction cannot close composite policy

- Test ID: TC-59
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Strategic direction cannot close composite policy
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Select a strategic letter without completing required dimensions.

- Expected Result: All four composite decisions remain pending; formal approval needs signed strategy and all 32 required dimensions or signed exceptions
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-03/D-12/D-13
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-60 - Required metric and correction dimensions cannot disappear

- Test ID: TC-60
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Required metric and correction dimensions cannot disappear
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Review D-03 dimension inventory including METRICS and CORRECTION_WINDOW.

- Expected Result: Missing dimension is incomplete documentation rather than an approved unspecified policy
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-03
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-61 - Unsigned dimensions prevent approved status

- Test ID: TC-61
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Unsigned dimensions prevent approved status
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Review a composite record marked approved without required subdecision signatures.

- Expected Result: Unsupported approval claim rejected; no signer/date/value inferred
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-03/D-12/D-13
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-62 - Scope exception must be explicit and signed per affected dimension

- Test ID: TC-62
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Scope exception must be explicit and signed per affected dimension
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Review an attempted deferred required dimension with no dated scope reference.

- Expected Result: Unverifiable deferral remains unresolved; exception must identify authority, scope, date and exact dimension
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-03/D-12/D-13
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

### TC-63 - Prerequisite and CODEOWNER gates remain independent

- Test ID: TC-63
- Requirement: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07
- Scenario: Prerequisite and CODEOWNER gates remain independent
- Preconditions: Current proposed report package; no business signature or runtime implementation assumed

Steps:

1. Review complete technical remediation with absent NX-00/NX-01/G-00 signatures.

- Expected Result: Technical closure does not authorize implementation or formal acceptance; all human gate fields stay unsigned
- Evidence Needed: Exact reviewed report version and named dated human policy/exception evidence when supplied; executable documentation checks are recorded separately
- Type: Round 2 review and approval specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-09/D-11
- Notes: PROPOSED review acceptance specification; no API, policy value, role privilege or signature granted

## ROUND3_REMEDIATION

### TC-64 - Coach publishes or revises the expected plan revision

- Test ID: TC-64
- Requirement: F4-REQ-01
- Scenario: Coach publishes or revises the expected plan revision
- Preconditions: AS-11 conditional policy assumptions; current C-101 permitted P-301 revision A

Steps:

1. Open the permitted plan and identify A.
2. Submit publication or revision against A as C-101.
3. Inspect accepted attribution, revision trace and permitted visibility.
4. Compare booking, balance, payment and arrival pre/post state.

- Expected Result: Accepted outcome follows only the assumed policy, is attributable and traceable without lost updates or unrelated side effects
- Evidence Needed: Future approved synthetic runtime observations, attributable plan revision/history, permitted projection and unchanged booking/payment/arrival evidence; no execution yet
- Type: Plan publication/revision business acceptance specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12/D-13
- Notes: ROUND3_REMEDIATION; AS-11. Conditional policy assumptions only; no retention, deadline, locking mechanism, visibility timing or entitlement is adopted. Permission references: PM-03, PM-04, PM-07

### TC-65 - Revoked Coach cannot publish or create a revision

- Test ID: TC-65
- Requirement: F4-REQ-01
- Scenario: Revoked Coach cannot publish or create a revision
- Preconditions: AS-11.N1; C-101 rights revoked after form load

Steps:

1. Load P-301 revision A while entitled.
2. Revoke or expire authority in an approved future test environment.
3. Submit publication/revision from the stale form.
4. Inspect private output and stored plan state.

- Expected Result: Conditional DENY with no publication/new revision, protected disclosure or unrelated financial/arrival effect
- Evidence Needed: Future approved synthetic runtime observations, attributable plan revision/history, permitted projection and unchanged booking/payment/arrival evidence; no execution yet
- Type: Plan publication/revision business acceptance specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12/D-13
- Notes: ROUND3_REMEDIATION; AS-11. Conditional policy assumptions only; no retention, deadline, locking mechanism, visibility timing or entitlement is adopted. Permission references: PM-04, PM-16, PM-33

### TC-66 - Stale plan revision cannot overwrite a newer publication

- Test ID: TC-66
- Requirement: F4-REQ-01
- Scenario: Stale plan revision cannot overwrite a newer publication
- Preconditions: AS-11.N2; C-101 expects A and another authorized edit accepts B

Steps:

1. Open P-301 at A.
2. Accept intervening B through the chosen future policy.
3. Submit stale publication/revision against A.
4. Inspect current revision and authoritative visibility.

- Expected Result: Conditional CONFLICT; B retained and no partial publication or lost update; exact locking and transport remain NX-03/NX-04 design
- Evidence Needed: Future approved synthetic runtime observations, attributable plan revision/history, permitted projection and unchanged booking/payment/arrival evidence; no execution yet
- Type: Plan publication/revision business acceptance specification
- Execution Status: SPECIFIED_NOT_EXECUTED
- Blocking Decision: D-02/D-12/D-13
- Notes: ROUND3_REMEDIATION; AS-11. Conditional policy assumptions only; no retention, deadline, locking mechanism, visibility timing or entitlement is adopted. Permission references: PM-03, PM-04, PM-07

All 11 canonical AS positive/negative paths reference the specifications above. Business/runtime cases: 66 specified, 0 executed, 0 PASS and 0 FAIL.
