# NX-02 corrected detailed use cases

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

Runtime participants are distinct from affected subjects and policy approvers. None is intentional where no additional participant acts in the software interaction. A subject/recipient is not automatically a supporting actor. CODEOWNER reviews policy and this package; CODEOWNER is never a runtime actor. Any adopted policy mentioned in a precondition requires full subdecision completeness, not a strategic letter alone.

## UC-F4-01P - Author a personal training plan

- Use Case ID: UC-F4-01P
- Requirement: F4-REQ-01
- Name and bounded goal: Author a personal training plan. Prepare a plan for one assigned member.
- Primary actor: Coach
- Affected subject: M-201 personal plan and its assignment
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Hung coordinate D-02; runtime author/edit rights remain PENDING_HUMAN_DECISION under D-12.
- Authorization boundary: Author identity comes from principal. Draft visible only to authorized current editors; another member and Receptionist cannot read it. Historical author rights after reassignment require D-12.
- Business decision dependencies: D-02, D-11, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 6; S05/S06 current scope; S11/S14 conflicting plan links; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-01, PM-03, PM-05, PM-15, PM-16
- Acceptance references: AS-01; tests TC-01, TC-08
- Trigger: Coach selects a member from an authorized teaching context.
- Preconditions: Active Coach C-101; current assignment to the relevant member/session; synthetic Member M-201. Existing roster reads establish only S05/S06 prerequisites, not a personal assignment model. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Coach chooses M-201 and identifies the intended training context.
2. System revalidates current Coach/member authority and the selected context.
3. Coach enters ordered training activities and reviews the intended audience.
4. Under proposed D-02 option B, saving retains a private draft associated with M-201.
5. System confirms the saved revision only after authorized persistence is available in a future implementation.

### Alternate and denial paths

A1: A class plan is requested -> UC-F4-01C. A2: Template cloning, if adopted, copies permitted content with origin provenance and no other member notes.

F1: Session missing/rescheduled or assignment stale -> deny save, preserve input for authorized review and require refreshed context. F2: Another member identifier or guessed plan identifier -> deny without revealing private existence. F3: Invalid ordering or concurrent draft edit -> validation/conflict, no silent overwrite.

### Postconditions privacy and invariants

PROPOSED private draft and origin provenance; no booking, attendance or balance change. Author identity comes from principal. Draft visible only to authorized current editors; another member and Receptionist cannot read it. Historical author rights after reassignment require D-12. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Required context, activities, ordering and assignment checked server-side. No assumed metric ranges. Retry must not create uncontrolled duplicate plans; exact mechanism deferred.

PROPOSED observable example: Given C-101 is assigned to M-201 and D-02 strategic option B and every required applicable subdecision have signed approval or signed scope exception, when C-101 saves draft P-301, then M-201 cannot yet read it and M-202 cannot read any part of it. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: class-only first, personal scope deferred (reduces work but needs explicit scope exception); B: distinct personal/class assignment with controlled versions (PROPOSED, covers both original capabilities); C: per-session copies (clear snapshots, greater duplication).

Human routing: Dat + Hung; business approver and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-02/D-11/D-12/D-13. Required dimensions: D-02.PLAN_SCOPE, D-02.EDITABLE_FIELDS, D-02.TEMPLATE_ORIGIN, D-12.RESOURCE_RIGHTS, D-12.REASSIGNMENT. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S05/S06 current scope; S11/S14 conflicting plan links. Downstream: NX-03/NX-04/NX-06, with NX-05 visibility review. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-01C - Author and assign a class plan

- Use Case ID: UC-F4-01C
- Requirement: F4-REQ-01
- Name and bounded goal: Author and assign a class plan. Prepare shared activities for the authorized class without sharing private member data.
- Primary actor: Coach
- Affected subject: K-401 shared plan, recipient set and linked sessions
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Hung coordinate class/audience policy; correction and publishing authority remain PENDING_HUMAN_DECISION.
- Authorization boundary: Coach needs class/session assignment; members see only permitted shared content. Class membership never grants peer results or notes.
- Business decision dependencies: D-02, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 6; S05/S06 roster; S11 class link; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-02, PM-06, PM-07, PM-17
- Acceptance references: AS-02; tests TC-02, TC-05, TC-06
- Trigger: Coach opens an assigned class teaching context.
- Preconditions: Active assigned Coach; class/session exists and roster context is current under S05/S06. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Coach selects the assigned class and intended sessions.
2. System rechecks class/session assignment and lists the proposed audience.
3. Coach prepares shared ordered steps without member-specific comments.
4. Coach reviews whether assignment is a membership snapshot or a dynamic class link.
5. If D-02 choice is approved, system saves the chosen shared draft and confirms its scope.

### Alternate and denial paths

A1: A personal adjustment is needed -> separate personal plan UC-F4-01P. A2: Removed class member -> future access depends on D-12 historical rights, not a shared link.

F1: Wrong class or missing/stale session -> deny or reload. F2: Roster changes during save -> refresh intended audience before confirmation. F3: Cancelled/rescheduled session -> retain historical plan but do not create attendance.

### Postconditions privacy and invariants

PROPOSED shared plan assignment only. Individual results/comments remain separately private. Coach needs class/session assignment; members see only permitted shared content. Class membership never grants peer results or notes. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Audience eligibility rechecked; ordered steps have unambiguous order. Duplicate retries and simultaneous class assignment changes require conflict handling.

PROPOSED observable example: Given class K-401 has M-201 and M-202, when a permitted shared plan is published after approval, then both may see shared steps while M-202 cannot see M-201 feedback. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: snapshot eligible assignees on publication (PROPOSED, stable historical audience); B: resolve current membership on every read (simple current view, historical access changes); C: separate per-member copies (isolation, more reconciliation).

Human routing: Dat + Hung with Truc visibility review; CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-02/D-12/D-13. Required dimensions: D-02.PLAN_SCOPE, D-02.RECIPIENTS, D-02.EDITABLE_FIELDS, D-02.SESSION_RELINK, D-02.TEMPLATE_ORIGIN, D-12.RESOURCE_RIGHTS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S05/S06 roster; S11 class link. Downstream: NX-03/NX-04/NX-05/NX-06. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-01V - Publish and revise a plan

- Use Case ID: UC-F4-01V
- Requirement: F4-REQ-01
- Name and bounded goal: Publish and revise a plan. Make an intentional plan revision available to the approved audience.
- Primary actor: Coach
- Affected subject: P-301 published revision, entitled audience and historical session references
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Hung coordinate lifecycle/publish policy; Truc reviews visibility; runtime publishers/correctors remain PENDING_HUMAN_DECISION.
- Authorization boundary: Member-visible publication and Coach editing are separate checks. Authorship does not grant permanent edit rights after reassignment.
- Business decision dependencies: D-02, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 6; S11 lacks this lifecycle; S14 is unapproved design; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-03, PM-04, PM-07, PM-16
- Acceptance references: AS-11; tests TC-04, TC-08, TC-64, TC-65, TC-66
- Trigger: Coach requests publication or a correction to a published plan.
- Preconditions: Synthetic current Coach C-101, permitted Member M-201 or class K-401 context, existing draft/published P-301 and known revision A. Publication, revision history, visibility, assignment lifespan and edit timing are conditional fixture assumptions under D-02/D-12/D-13, never declared signed by this document.

### Primary path

1. Coach opens P-301 in the permitted member/class context and identifies expected current revision A.
2. System rechecks current Coach authority and the revision relevant to the proposed publication or revision.
3. Coach confirms the intended permitted audience and submits publication or revised content against A.
4. If the explicitly assumed policy permits the action and A is still current, the proposed accepted outcome records Coach attribution and the policy-selected traceable revision relationship.
5. System confirms only the accepted authorized outcome, exposes content according to the eventual signed visibility policy and preserves existing booking, payment and Receptionist arrival state.

### Alternate and denial paths

A1: Supersede/archive -> proposed future-assignment visibility changes while permitted history remains. A2: Template -> clone with provenance rather than sharing mutable personal data.

F1: Former Coach or stale revision -> deny/conflict. F2: Missing session or cancellation -> require deliberate relink decision, no silent historical rewrite. F3: Failure during publication -> no partial audience exposure.

### Postconditions privacy and invariants

PROPOSED publication/supersession/archive lifecycle only. These names are candidate concepts, not approved enumeration. Member-visible publication and Coach editing are separate checks. Authorship does not grant permanent edit rights after reassignment. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Version consistency, audience consistency, origin attribution and atomic publication required if chosen. Correction windows remain pending.

PROPOSED observable example: Given synthetic current Coach C-101 has permitted P-301 context and expected revision A, when C-101 conditionally publishes or revises against A, then the accepted outcome is attributed to C-101 and preserves the revision trace selected by pending policy, with no lost update or booking/payment/arrival side effect. Policy assumptions are fixture conditions only; no approval is supplied.

### Ambiguities and handoff

A: mutable published plan (less storage, historical ambiguity); B: preserved published revisions with new revisions (PROPOSED, traceable); C: freeze forever after publication (simple history, corrections awkward).

Human routing: Dat + Hung; Truc and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-02/D-12/D-13. Required dimensions: D-02.PLAN_SCOPE, D-02.RECIPIENTS, D-02.PUBLISH_AUTHORITY, D-02.REVISION_LIFECYCLE, D-02.EDITABLE_FIELDS, D-02.HISTORICAL_ACCESS, D-02.SESSION_RELINK, D-02.TEMPLATE_ORIGIN, D-12.RESOURCE_RIGHTS, D-12.FIELD_VISIBILITY, D-12.REASSIGNMENT, D-13.FINANCIAL_INVARIANTS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S11 lacks this lifecycle; S14 is unapproved design. Downstream: NX-03/NX-04/NX-05/NX-06. NX-03 designs contracts; NX-04 designs persistence.

Related cross-workflow examples only, not direct coverage: AS-01, AS-02, AS-03, AS-10.

## UC-F4-02 - Record and correct a session result

- Use Case ID: UC-F4-02
- Requirement: F4-REQ-02
- Name and bounded goal: Record and correct a session result. Capture an authorized member outcome for an identified completed session.
- Primary actor: Coach
- Affected subject: S-501/M-201 result, metric observations and correction history
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Truc + Hung coordinate D-03; metric/privacy authority is OWNER_UNCONFIRMED; current corrector rights remain PENDING_HUMAN_DECISION.
- Authorization boundary: Only authorized Coach writes. Member sees own permitted outcomes; private notes require explicit D-12 classification.
- Business decision dependencies: D-03, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 7; S05/S06 eligibility context; S13 result scaffold; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-08, PM-09, PM-35
- Acceptance references: AS-04; tests TC-09, TC-10, TC-12, TC-13, TC-14
- Trigger: Coach records results after a session.
- Preconditions: Active assigned Coach; session/member link verified; metric dictionary, result eligibility and late-entry policy require D-03/D-13. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Coach chooses S-501 and M-201 from authorized context.
2. System revalidates assignment, session status and member eligibility.
3. Coach records only approved metric values/units and optional permitted notes.
4. System validates values without coercion and records provenance.
5. System confirms saved result and its proposed visibility; a correction retains prior value, actor and reason if approved.

### Alternate and denial paths

A1: Missing metric -> explicit incomplete state or reject according to selected D-03 option; never infer zero. A2: Correct prior result -> accountable revision, with downstream evaluation marked for review under proposal.

F1: Session missing/cancelled or roster stale -> do not create result. F2: Invalid value/unit, string coercion or wrong member -> reject. F3: Concurrent correction -> conflict with current revision; do not silently overwrite.

### Postconditions privacy and invariants

PROPOSED result/history; no attendance auto-creation, booking mutation or package debit. Only authorized Coach writes. Member sees own permitted outcomes; private notes require explicit D-12 classification. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Metric membership, unit compatibility, missing versus zero, range only from future approved dictionary; idempotent retry and edit provenance.

PROPOSED observable example: Given approved synthetic metric Skill-A and eligible S-501/M-201, when an authorized result is saved then M-201 can later see its permitted value; a swapped M-202 identifier is denied. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: approved sport metric dictionary plus optional commentary (PROPOSED, comparable); B: narrative results only (less measurement); C: unrestricted Coach-defined metrics (flexible, inconsistent history). Edit window: bounded owner-defined period versus reviewed corrections at any time; no duration selected.

Human routing: Dat + Truc + Hung; CODEOWNER and designated privacy reviewer; exact business authority remains to be confirmed where unsigned. Dependencies: D-03/D-12/D-13. Required dimensions: D-03.METRICS, D-03.VALIDATION, D-03.CORRECTION_MECHANISM, D-03.CORRECTION_WINDOW, D-03.RETENTION, D-03.RESULT_HISTORY, D-12.RESOURCE_RIGHTS, D-12.CORRECTION_RIGHTS, D-12.AUDIT_ACCESS, D-13.ELIGIBILITY, D-13.VISIBILITY_IMPACT, D-13.FINANCIAL_INVARIANTS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S05/S06 eligibility context; S13 result scaffold. Downstream: NX-03/NX-04/NX-05/NX-06. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-03 - Evaluate progress and record feedback

- Use Case ID: UC-F4-03
- Requirement: F4-REQ-03
- Name and bounded goal: Evaluate progress and record feedback. Describe progress using identified results and deliberate comment visibility.
- Primary actor: Coach
- Affected subject: M-201 progress assessment and classified Coach comments
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Truc + Hung coordinate feedback/evaluation choices; privacy authority is OWNER_UNCONFIRMED; no publication or oversight privilege is granted.
- Authorization boundary: Member access to feedback is distinct from Coach-internal note creation. Receptionist and Manager do not receive private narrative rights by default.
- Business decision dependencies: D-03, D-12
- Source reference: Original Project!E5; revised Requirement Map row 8; S05 authorized context; S13 feedback scaffold lacks publication state; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-10, PM-11, PM-14, PM-30
- Acceptance references: AS-05; tests TC-16, TC-17, TC-18, TC-19
- Trigger: Coach reviews authorized member history.
- Preconditions: Current access to M-201 history approved; comparable approved metrics or documented qualitative evidence exist. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Coach opens M-201 authorized history for a selected period.
2. System shows available results and missing-data context without inventing scores.
3. Coach writes an assessment citing the observations used.
4. Coach explicitly classifies feedback as member-visible or restricted if D-12 permits that split.
5. System confirms saved assessment and its visibility; publication/correction follows the approved choice.

### Alternate and denial paths

A1: No results -> explicit insufficient evidence; qualitative comment may be allowed by D-03. A2: Corrected source result -> flag affected assessment for review rather than silently rewriting it under proposal.

F1: Former Coach, foreign member or stale assignment -> deny. F2: Missing/deleted source session -> show provenance limitation; do not fabricate history. F3: Concurrent feedback edit -> preserve current revision and report conflict.

### Postconditions privacy and invariants

PROPOSED assessment/comment record, with source references and correction provenance; no automated plan or financial mutation. Member access to feedback is distinct from Coach-internal note creation. Receptionist and Manager do not receive private narrative rights by default. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Separate observation, result and assessment; no unapproved progress formula, medical threshold or retention duration.

PROPOSED observable example: Given a member-visible comment and a restricted note are both allowed by future policy, when M-201 opens feedback then only the permitted comment appears; M-202 receives neither. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: every comment member-visible (simple but changes note expectations); B: explicit visible feedback plus narrowly scoped restricted notes (PROPOSED subject to privacy approval); C: all notes staff-only (conflicts with member comment capability unless separate visible feedback added).

Human routing: Dat + Truc + Hung; business/privacy approver and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-03/D-12. Required dimensions: D-03.METRICS, D-03.VALIDATION, D-03.CORRECTION_MECHANISM, D-03.CORRECTION_WINDOW, D-03.RETENTION, D-03.COMMENT_VISIBILITY, D-03.RESULT_HISTORY, D-12.RESOURCE_RIGHTS, D-12.FIELD_VISIBILITY, D-12.HISTORIC_ENTITLEMENT, D-12.CORRECTION_RIGHTS, D-13.VISIBILITY_IMPACT. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S05 authorized context; S13 feedback scaffold lacks publication state. Downstream: NX-03/NX-04/NX-05/NX-06. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-04 - Record session attendance

- Use Case ID: UC-F4-04
- Requirement: F4-REQ-04
- Name and bounded goal: Record session attendance. Record participation in a specific training session independently of facility arrival.
- Primary actor: Coach
- Affected subject: Eligible roster members participation in S-501, not facility arrival
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Hung coordinate attendance policy; proposed current Coach write authority requires D-12/D-13 approval.
- Authorization boundary: Assigned Coach sees required roster fields only. Attendance is member-private history; not a public class list.
- Business decision dependencies: D-01, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 9; S05/S06 roster and S12 uniqueness; S01 arrival remains separate; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-12, PM-17, PM-35
- Acceptance references: AS-06; tests TC-20, TC-22, TC-23, TC-26, TC-38
- Trigger: Coach opens an assigned session roster.
- Preconditions: Active currently assigned Coach; S-501 exists; confirmed booking roster available through S05/S06; status and timing policy pending D-13. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Coach selects S-501 and refreshes its eligible roster.
2. System revalidates session availability, assignment and roster membership.
3. Coach selects an approved attendance meaning for each eligible member.
4. System validates the current session/member record and handles duplicate submissions.
5. System confirms authoritative attendance only after future durable save; no reception check-in is inferred or created.

### Alternate and denial paths

A1: Late/absent/excused/no-show -> use only the adopted vocabulary and timing rule. A2: Same member attends S-502 on the same day -> independent session scope.

F1: Cancelled/missing session or no roster -> no submission; require reload. F2: Withdrawn booking, unauthorized walk-in or foreign Coach -> deny. F3: Retry/timeout or concurrent roster change -> no duplicate authoritative record, return reviewable outcome.

### Postconditions privacy and invariants

PROPOSED attendance plus provenance; arrival history, bookings and financial balances unchanged. Assigned Coach sees required roster fields only. Attendance is member-private history; not a public class list. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

One current session/member record plus correction history is PROPOSED, consistent with S12 uniqueness. Unknown status is rejected; thresholds and bulk-save semantics unapproved.

PROPOSED observable example: Given approved attendance policy and confirmed roster M-201, when Coach submits participation for S-501 twice, then there is one current authoritative outcome, with no extra package consumption. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: independent attendance linked to existing arrival when available (PROPOSED); B: extend reception event with distinct attendance semantics; C: use arrival as attendance (weak participation evidence, requires explicit ruling).

Human routing: Dat + Hung; Truc member-history review and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-01/D-12/D-13. Required dimensions: D-12.RESOURCE_RIGHTS, D-12.REASSIGNMENT, D-13.STATE_SEMANTICS, D-13.LIFECYCLE, D-13.ELIGIBILITY, D-13.TIMING_BOUNDARIES, D-13.UNIQUENESS, D-13.FINANCIAL_INVARIANTS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S05/S06 roster and S12 uniqueness; S01 arrival remains separate. Downstream: NX-03/NX-04/NX-05/NX-06. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-04C - Correct attendance with accountability

- Use Case ID: UC-F4-04C
- Requirement: F4-REQ-04
- Name and bounded goal: Correct attendance with accountability. Correct an erroneous session outcome while retaining reviewable history.
- Primary actor: Coach
- Affected subject: Existing S-501/M-201 attendance revision and correction evidence
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Dat + Hung coordinate D-13; actor allowed to correct/supersede after reassignment remains PENDING_HUMAN_DECISION in D-12/D-13.
- Authorization boundary: Correction detail visibility and staff-only reason content require D-12. Member summary must not expose another member or private staff notes.
- Business decision dependencies: D-01, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 9; S12 correction scaffold; S16 audit save infrastructure is not immutable history proof; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: PROPOSED_MUTATION
- Permission rows: PM-13, PM-16, PM-33
- Acceptance references: AS-07; tests TC-24, TC-25, TC-38, TC-53
- Trigger: Authorized Coach identifies an erroneous attendance entry.
- Preconditions: Current attendance exists; current correction authority and historical Coach policy approved under D-12/D-13. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Coach opens S-501/M-201 current attendance and its revision.
2. System revalidates current correction authority and session/member context.
3. Coach states a reason and proposed corrected meaning.
4. System preserves prior/current meaning, responsible actor and time with the correction under proposal.
5. System confirms the current outcome and the member-visible correction summary allowed by policy.

### Alternate and denial paths

A1: Former assigned Coach -> request review by the current authorized owner; no automatic historical write right. A2: Correction affects result eligibility -> flag dependent results for review without deleting them.

F1: Missing attendance/session or missing reason -> no overwrite. F2: Two correctors -> first accepted revision requires second reviewer to refresh; no lost update. F3: Audit persistence failure -> consistency behavior must be approved before implementation.

### Postconditions privacy and invariants

PROPOSED corrected attendance and retained provenance; no cancellation, refund or automatic notification. Correction detail visibility and staff-only reason content require D-12. Member summary must not expose another member or private staff notes. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Expected-current-revision check, reason and actor required under proposal. Retry recognizes prior applied correction. No edit deadline invented.

PROPOSED observable example: Given two authorized editors loaded revision A, when one saves B and the second attempts C against A, then C cannot silently replace B; the accepted change remains attributable. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: current Coach corrections with reviewable history (PROPOSED); B: dedicated Manager review authority (new right needs approval); C: original author indefinitely (assignment/privacy risks).

Human routing: Dat + Hung; Truc/privacy reviewer and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-01/D-12/D-13. Required dimensions: D-12.REASSIGNMENT, D-12.CORRECTION_RIGHTS, D-12.AUDIT_ACCESS, D-12.MEMBER_CORRECTION_VIEW, D-13.LIFECYCLE, D-13.UNIQUENESS, D-13.CORRECTION_AUTHORITY, D-13.HISTORY, D-13.VISIBILITY_IMPACT, D-13.FINANCIAL_INVARIANTS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S12 correction scaffold; S16 audit save infrastructure is not immutable history proof. Downstream: NX-03/NX-04/NX-05/NX-06. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-05 - View own attendance and outcomes

- Use Case ID: UC-F4-05
- Requirement: F4-REQ-05
- Name and bounded goal: View own attendance and outcomes. Understand own session participation and training outcomes without seeing another member.
- Primary actor: Member
- Affected subject: Signed-in Member own attendance and outcome history
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Read-only interaction has no runtime approver or corrector. Dat + Truc coordinate visibility policy; correction requests are outside this read and require separately defined authority.
- Authorization boundary: Own records only; filters are not authorization. Client-side hiding cannot protect foreign records. Historical retention after withdrawal requires D-12.
- Business decision dependencies: D-01, D-03, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 10; S07 own bookings and S08 principal member binding; no training-history service established; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: READ_ONLY
- Permission rows: PM-20, PM-21, PM-33
- Acceptance references: AS-09; tests TC-32, TC-34, TC-35, TC-51
- Trigger: Member opens attendance/results history.
- Preconditions: Active authenticated M-201; future records and publication/history policy approved. Existing S07 booking history is only a reuse context. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Member requests own history and selects a date/session filter.
2. System derives member identity from the principal and checks each record.
3. System reads permitted attendance/results and distinguishes missing records from absence.
4. System shows current approved outcomes and permitted correction information.
5. Member reviews the permitted history. This read does not submit corrections; any correction request is a separate pending workflow.

### Alternate and denial paths

A1: Empty history -> clear empty state without inferring no-shows. A2: Cancelled session -> show its approved historical context, not an attendance conclusion. A3: Corrected result -> show approved current value and permitted history.

F1: Foreign member/session identifier -> deny without record-existence leak. F2: Stale JWT/deactivated principal -> no private data. F3: Missing/stale session link -> truthful unavailable state, not invented outcome.

### Postconditions privacy and invariants

Read only; no balance, plan or training record change. Own records only; filters are not authorization. Client-side hiding cannot protect foreign records. Historical retention after withdrawal requires D-12. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Date range/order, pagination and timezone display are acceptance inputs for NX-03. Recheck authorization per page/read, including cached data.

PROPOSED observable example: Given M-201 and M-202 each have results, when M-201 changes a member filter to M-202 then no M-202 result, count, comment or existence detail is disclosed. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: own current corrected value plus minimal correction indication (PROPOSED); B: own complete correction history (greater transparency, needs reason redaction); C: current value only (less traceability).

Human routing: Dat + Truc; Hung review of attendance meanings and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-01/D-03/D-12/D-13. Required dimensions: D-03.RETENTION, D-03.RESULT_HISTORY, D-12.RESOURCE_RIGHTS, D-12.FIELD_VISIBILITY, D-12.HISTORIC_ENTITLEMENT, D-12.AUDIT_ACCESS, D-12.MEMBER_CORRECTION_VIEW, D-12.PRIVACY_OPERATIONS, D-13.STATE_SEMANTICS, D-13.LIFECYCLE, D-13.TIMING_BOUNDARIES, D-13.HISTORY, D-13.VISIBILITY_IMPACT. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S07 own bookings and S08 principal member binding; no training-history service established. Downstream: NX-03/NX-04/NX-05. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-06 - View permitted plans and Coach feedback

- Use Case ID: UC-F4-06
- Requirement: F4-REQ-06
- Name and bounded goal: View permitted plans and Coach feedback. Read assigned plans and comments intended for this member.
- Primary actor: Member
- Affected subject: Signed-in Member permitted personal/shared plan revisions and visible feedback
- Supporting actors: None
- Supporting interaction: None. The subject or recipient does not need to participate in this software interaction. Policy reviewers and correction authorities are separate from runtime actors.
- Approval or correction authority: Read-only interaction has no runtime approver or corrector. Dat + Truc + Hung coordinate publication/history/feedback policy; no former Coach access is implied.
- Authorization boundary: Personal plan, shared class content and individual feedback require separate checks. Knowing planId/classId does not grant access.
- Business decision dependencies: D-02, D-03, D-12
- Source reference: Original Project!E5; revised Requirement Map row 11; S11/S13 scaffolds and S14 conflicting publication design; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: READ_ONLY
- Permission rows: PM-18, PM-19, PM-22, PM-34
- Acceptance references: AS-03, AS-10; tests TC-03, TC-19, TC-33, TC-37
- Trigger: Member opens own plan or feedback history.
- Preconditions: Authenticated M-201 and approved assignment/publication visibility. No training-plan read implementation established by S11/S13. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Member requests own plan/feedback view.
2. System derives identity and validates assignment, publication and historical rights.
3. System returns permitted shared or personal steps and member-visible comments.
4. System identifies the effective revision and allowed historical context.
5. Member opens a permitted historical revision using the same authorization checks.

### Alternate and denial paths

A1: No published plan -> show empty state, no draft inference. A2: Member leaves class -> retained historical access versus revoked future access follows D-12. A3: Superseded plan -> label permitted historical revision.

F1: Draft/foreign plan/private note -> deny without leaking it through metadata or cache. F2: Stale class assignment or missing session -> recheck; no fallback to broad class access. F3: Removed access while paging -> deny subsequent unauthorized data.

### Postconditions privacy and invariants

Read only; draft/publication and comments unchanged. Personal plan, shared class content and individual feedback require separate checks. Knowing planId/classId does not grant access. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Publication boundary and effective audience evaluated together. Member never updates Coach feedback through read workflow.

PROPOSED observable example: Given draft A and published revision B assigned to M-201, when M-201 reads plans then B is visible and A is excluded under proposed policy; M-202 cannot read personal B. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: preserve previously entitled historical revisions while revoking future assignments (PROPOSED, needs retention decision); B: current membership only (simpler, removes useful history); C: perpetual access to all later class revisions (excessive disclosure risk).

Human routing: Dat + Truc + Hung; privacy approver and CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-02/D-03/D-12. Required dimensions: D-02.RECIPIENTS, D-02.PUBLISH_AUTHORITY, D-02.REVISION_LIFECYCLE, D-02.HISTORICAL_ACCESS, D-03.RETENTION, D-03.COMMENT_VISIBILITY, D-12.RESOURCE_RIGHTS, D-12.FIELD_VISIBILITY, D-12.REASSIGNMENT, D-12.HISTORIC_ENTITLEMENT, D-12.MEMBER_CORRECTION_VIEW, D-12.PRIVACY_OPERATIONS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S11/S13 scaffolds and S14 conflicting publication design. Downstream: NX-03/NX-04/NX-05. NX-03 designs contracts; NX-04 designs persistence.

## UC-F4-07 - Check in an arriving member

- Use Case ID: UC-F4-07
- Requirement: F4-REQ-07
- Name and bounded goal: Check in an arriving member. Record facility arrival through the existing authorized workflow.
- Primary actor: Receptionist
- Affected subject: Identified arriving Member and selected current-day booking arrival attempt
- Supporting actors: Member
- Supporting interaction: Member provides identifying information on arrival; no Coach, Manager or CODEOWNER joins this runtime interaction.
- Approval or correction authority: No Coach approval or assignment is required by the inspected arrival path. Existing Manager role is an alternative initiating role under S02, not a supporting actor in this Receptionist case. Dat owns D-01 integration policy.
- Authorization boundary: Existing Receptionist/Manager guard permits arrival history, not training narratives. Coach-only credentials do not grant reception rights.
- Business decision dependencies: D-01, D-12, D-13
- Source reference: Original Project!E5; revised Requirement Map row 12; S01/S02/S22, S03/S04 ACK, S07 balance/cancellation; exact SHA f8959749bd2e887d7038da2843faeede5e8e0ab1. Claim ledger resolves S references to path/symbol/lines.
- Operation kind: EXISTING_CHECK_IN
- Permission rows: PM-24, PM-25, PM-28
- Acceptance references: AS-08; tests TC-27, TC-28, TC-29, TC-30, TC-49, TC-55
- Trigger: Member arrives and identifies themselves to desk staff.
- Preconditions: Authenticated authorized Receptionist R-601; member identifiable; current-day confirmed booking and active date-valid package required by S01. Presence of a valid package alone is insufficient. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Primary path

1. Receptionist identifies the member and, when relevant, the intended booking.
2. Existing service loads confirmed bookings for today using the application Clock.
3. Service resolves the requested booking or first unchecked booking, locks session and rechecks mutable state.
4. Service checks duplicate ALLOWED check-in and linked active date-valid package.
5. Service saves ALLOWED or DENIED attempt and activity event; staff reads its confirmation.

### Alternate and denial paths

A1: Multiple bookings today -> each booking has separate arrival eligibility; explicit selection avoids selecting unintended session. A2: No requested booking -> existing service chooses first unchecked confirmed booking. A3: Self-training ACK -> explain pending attendance persistence under S03, not completed Coach attendance.

F1: No booking/walk-in, invalid requested booking, duplicate or invalid package -> existing denial is retained. F2: Member missing -> resource error; no invented member. F3: Session missing or cancellation wins lock -> revalidated denial/error, never manufacture attendance.

### Postconditions privacy and invariants

EXISTING_VERIFIED persisted arrival attempt and activity event in source. No extra debit. Proposed Flow 4 integration remains unsigned. Existing Receptionist/Manager guard permits arrival history, not training narratives. Coach-only credentials do not grant reception rights. Preserve Flow 1-3 booking, balances, cancellation and payments unchanged. No production implementation is authorized.

### Validation concurrency and acceptance

Member identifier and booking association validated by service. Repeated explicit booking attempt produces denial, not guaranteed identical idempotent success. Runtime race behavior remains NOT_VERIFIED.

PROPOSED observable example: Given one eligible confirmed booking, when an authorized Receptionist checks it in then source saves an ALLOWED arrival without consumeSession; repeating the same booking is denied. Neither outcome proves Coach session attendance. Any policy adoption mentioned here requires the approval-completeness rule; a strategic letter alone is insufficient.

### Ambiguities and handoff

A: reuse existing arrival and add independent Coach attendance later (PROPOSED); B: extend existing arrival with explicit separate meanings (contract/migration review needed); C: reuse as sole attendance event (fails to prove session participation).

Human routing: Dat with Hung workflow review; CODEOWNER; exact business authority remains to be confirmed where unsigned. Dependencies: D-01/D-12/D-13. Required dimensions: D-12.RESOURCE_RIGHTS, D-13.ELIGIBILITY, D-13.TIMING_BOUNDARIES, D-13.FINANCIAL_INVARIANTS. Deadline: no calendar deadline supplied; before affected downstream task. Reuse: S01/S02/S22, S03/S04 ACK, S07 balance/cancellation. Downstream: NX-03/NX-04/NX-06; NX-05 correct history labeling. NX-03 designs contracts; NX-04 designs persistence.
