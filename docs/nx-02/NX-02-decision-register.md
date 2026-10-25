# NX-02 decision register and composite approval rule

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

All D-01 through D-14 are retained and PENDING_HUMAN_DECISION. D-01..10 follow the official Decision Log; D-11..14 follow the sourced NX-00 supplemental register. No new approval evidence is supplied.

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. A strategic recommendation or unsigned selected letter is only direction for discussion. It cannot complete a composite policy.

| Decision | Required | Approved | Explicit signed deferral | Unresolved | Overall status |
| --- | --- | --- | --- | --- | --- |
| D-02 | 8 | 0 | 0 | 8 | PENDING_HUMAN_DECISION |
| D-03 | 7 | 0 | 0 | 7 | PENDING_HUMAN_DECISION |
| D-12 | 8 | 0 | 0 | 8 | PENDING_HUMAN_DECISION |
| D-13 | 9 | 0 | 0 | 9 | PENDING_HUMAN_DECISION |

## D-01 - Reuse or extend existing arrival check-in for Flow 4?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A6:H6; workbook/source hashes in current evidence.
- Owner/coordinator: Dat
- Additional reviewers: Hung, Truc, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: retain arrival and add separate participation; B: extend arrival with explicitly distinct meanings; C: treat arrival as sole attendance
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: S01/S03/S05/S12 show distinct arrival, ACK and roster concepts. Separate evidence avoids granting Coach rights to Receptionist.
- Implications: A adds explicit labels/authorization and later reconciliation; B expands compatibility/race testing; C loses participation evidence and conflicts with status/correction needs.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-03/04/05/06, NX-09
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-02 - Which personal/class/template/version/ownership lifecycle is adopted?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A7:H7; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Hung
- Additional reviewers: Truc, business approver, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: mutable class scaffold with explicit personal-scope exception; B: distinct personal/class assignments and preserved published revisions; C: per-session copied plans
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): B
- Rationale: Original capability includes personal/class plans; S11/S14 disagree on linkage. Preserved revisions make member history attributable.
- Implications: A reduces implementation but needs scope ruling; B needs publication/audience/reassignment design and FE revision states; C simplifies snapshots but duplicates editing and QA.
- Required subdecisions: D-02.PLAN_SCOPE, D-02.RECIPIENTS, D-02.PUBLISH_AUTHORITY, D-02.REVISION_LIFECYCLE, D-02.EDITABLE_FIELDS, D-02.HISTORICAL_ACCESS, D-02.SESSION_RELINK, D-02.TEMPLATE_ORIGIN
- Approval rule: Verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by signed scope exception. A strategic letter alone cannot approve this composite decision.
- Full decision approval: false
- Downstream blockers: NX-03/04/05/06
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-03 - Which result metrics, edit/correction windows and retention apply?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A8:H8; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Truc + Hung
- Additional reviewers: Named metric steward, privacy/legal reviewer, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: approved metric dictionary plus classified comments and accountable corrections; B: narrative-only outcomes and explicit privacy policy; C: Coach-defined metrics with governance to be specified
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: S13 supplies constraints but no complete metric/visibility/correction policy. Missing values must remain distinct from zero.
- Implications: A needs units/range owner and member-visible history UX; B limits comparisons; C adds validation and comparability risks. All need explicit edit-window and retention selections, with no durations inferred.
- Required subdecisions: D-03.METRICS, D-03.VALIDATION, D-03.CORRECTION_MECHANISM, D-03.CORRECTION_WINDOW, D-03.RETENTION, D-03.COMMENT_VISIBILITY, D-03.RESULT_HISTORY
- Approval rule: Verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by signed scope exception. A strategic letter alone cannot approve this composite decision.
- Full decision approval: false
- Downstream blockers: NX-03/04/05/06, NX-08
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-04 - Where are homework and notifications allocated?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A9:H9; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Phat
- Additional reviewers: Hung, Truc, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: review in separate NX-16 backlog; B: explicitly adopt selected Flow 4 triggers after approval; C: reuse an evidenced existing module if later found
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: Requirement Map row 13 marks placement unconfirmed; schema alone proves no delivery.
- Implications: A avoids promising delivery; B adds recipient/content/error/privacy and FE/QA work; C needs runtime reuse verification before contract commitments.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-16; dependent notification UI only
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-05 - Will optional Flow 5 be adopted, and who decides provider/model/budget?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A10:H10; workbook/source hashes in current evidence.
- Owner/coordinator: Dat
- Additional reviewers: Business/budget/privacy approvers, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: defer independent decision to NX-20; B: adopt only after NX-20 signed policy; C: not in scope by signed owner decision
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: Project!G5 optional. NX-02 has no AI provider selection authority.
- Implications: Provider/model/budget remain unselected. Adoption requires independent evaluation, privacy and fallback; deferral leaves Flow 1-3 intact.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-20..26
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-06 - Coach-only or also Member-direct recommendations?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A11:H11; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Truc + Hung
- Additional reviewers: Business/privacy approver, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: decide Coach-only scope in NX-20; B: separately approve Member candidate via NX-20/NX-24; C: defer all AI audience work
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A, as review routing only
- Rationale: Original Coach capability does not require a Member-direct recommender.
- Implications: B adds Member UX, principal data scoping and separate QA; A does not itself adopt Flow 5. No member capability approved here.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-20/NX-24
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-07 - Is Coach review required before AI suggestion becomes a plan?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A12:H12; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Hung
- Additional reviewers: Business/privacy approver, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: require explicit human review if Flow 5 adopted; B: proposal-only suggestions never become plans; C: defer transition policy to NX-20
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): C, with A a later proposed guardrail
- Rationale: Workbook calls review a recommended guardrail, not an official lifecycle.
- Implications: A needs later review/rejection/provenance cases; B avoids plan mutations; C blocks AI-to-plan integration until signed. No transition is designed here.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-20 and dependent AI plan integration
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-08 - Will optional Flow 6 be adopted, with which sources and retention?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A13:H13; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Phat
- Additional reviewers: Truc, business/privacy approver, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: defer independent policy to NX-27; B: adopt after signed NX-27 source/access policy; C: not in scope by signed owner decision
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: Project!G5 independently optional; no runtime assistant or approved data policy evidenced.
- Implications: B needs source freshness, isolation, retention and fallback decisions; no provider, chat API or corpus selected in NX-02.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-27..33
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-09 - Are optional flows separate milestones and what is demo acceptance?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A14:H14; workbook/source hashes in current evidence.
- Owner/coordinator: Dat
- Additional reviewers: Business approver, FE owners, CODEOWNER @senko-do-thingz
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: separate scope/release approvals for each optional flow with approved demo evidence; B: one combined optional release after all separate scope choices; C: defer optional release scheduling
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: QA Gates separates G-04/G-05/G-06 and G-00 requires signed scope. Owner identity alone is not evidence.
- Implications: A allows independent defer/Go decisions; B couples readiness; C blocks release planning. FE/QA need named reviewers, exact evidence and acceptance version.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: G-00, NX-02 formal acceptance, NX-19 and optional release gates
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-10 - What exact API endpoints and DTOs are approved?

- Status: PENDING_HUMAN_DECISION
- Source: Decision Log!A15:H15; API Contracts!A6:L11; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + FE team
- Additional reviewers: Hung, Truc; Phat for later related scope; CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: defer Flow 4 contracts to NX-03 after signed scenarios; B: request signed scope exception for limited contract exploration while keeping approval pending
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: Workbook explicitly says no speculative URLs and pending contracts. NX-02 cannot approve DTOs.
- Implications: A supplies business/field visibility inputs only; B needs dated exception and cannot authorize implementation. Both defer persistence to NX-04.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-03; NX-27 for independent assistant contract
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-11 - Independent Flow 4 adoption and boundary?

- Status: PENDING_HUMAN_DECISION
- Source: NX-00 docs/requirements/nx-00/05-conflicts-decisions-and-open-questions.md, D-11; workbook/source hashes in current evidence.
- Owner/coordinator: Dat coordinator
- Additional reviewers: Named business approver and CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: adopt Flow 4 with explicit included capabilities/exclusions; B: defer Flow 4; C: not in scope
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A only as review option, not adoption
- Rationale: Project!G5 says optional; current task authorizes analysis without approving adoption.
- Implications: A requires signed capability mapping and scenario review before design; B/C preserve required Flow 1-3 and must record disposition of planning tasks.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: G-00 and NX-03..19
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-12 - What record/field permissions apply to Coach Member Manager and Receptionist?

- Status: PENDING_HUMAN_DECISION
- Source: NX-00 decision register D-12; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Truc + Hung coordinators
- Additional reviewers: Business/privacy approver and CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: current scoped writes, separately granted historical reads, explicit note visibility, deny unspecified rights; B: permanent original-author rights and broad staff oversight; C: current assignment only for both writes and all history
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: S05 current Coach scope and Manager roster exception do not justify new training privileges.
- Implications: A needs entitlement and privacy design with negative tests; B risks former-Coach/staff exposure; C may remove legitimate Member history. Retention/export/deletion authority must be named.
- Required subdecisions: D-12.RESOURCE_RIGHTS, D-12.FIELD_VISIBILITY, D-12.REASSIGNMENT, D-12.HISTORIC_ENTITLEMENT, D-12.CORRECTION_RIGHTS, D-12.AUDIT_ACCESS, D-12.MEMBER_CORRECTION_VIEW, D-12.PRIVACY_OPERATIONS
- Approval rule: Verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by signed scope exception. A strategic letter alone cannot approve this composite decision.
- Full decision approval: false
- Downstream blockers: NX-03/04/05/06 and all training authorization
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-13 - What training states, linkage, corrections and balance effects are authoritative?

- Status: PENDING_HUMAN_DECISION
- Source: NX-00 decision register D-13; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Hung coordinators
- Additional reviewers: Truc, business approver, CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: per-session/member participation with correction history, explicit eligibility and no financial side effects; B: booking-centric participation with separately approved eligibility changes; C: preserve arrival only and defer Coach attendance by explicit scope ruling
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: S12 uniqueness supports one current record; S07 already consumes at booking. S14/S12/S13 differ on identity and result preconditions.
- Implications: A/B need state meanings, late/absence timing, reassignment, cancelled-session and correction race acceptance; C defers source capability and needs explicit boundary. No approved enum/window/SQL here.
- Required subdecisions: D-13.STATE_SEMANTICS, D-13.LIFECYCLE, D-13.ELIGIBILITY, D-13.TIMING_BOUNDARIES, D-13.UNIQUENESS, D-13.CORRECTION_AUTHORITY, D-13.HISTORY, D-13.VISIBILITY_IMPACT, D-13.FINANCIAL_INVARIANTS
- Approval rule: Verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by signed scope exception. A strategic letter alone cannot approve this composite decision.
- Full decision approval: false
- Downstream blockers: NX-03/04/06, NX-08/NX-09
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

## D-14 - What cross-AI minimization, access, retention/cost and mutation limits apply?

- Status: PENDING_HUMAN_DECISION
- Source: NX-00 decision register D-14; workbook/source hashes in current evidence.
- Owner/coordinator: Dat + Hung for F5; Phat/Truc for F6 coordinators
- Additional reviewers: Business/privacy/budget approvers and CODEOWNER
- Strategic question: choose a direction for discussion and record exact scope/conditions; this alone does not approve required policy dimensions.
- Candidate strategic options: A: defer separately to NX-20 and NX-27; B: record only common policy principles now, with all AI transmission and business action unapproved
- Current strategic selected option: UNRESOLVED
- Strategic evidence: None. Reviewer [unsigned]; authority [unconfirmed]; date [blank]; source reference [blank]; exact selected option/scope/version [blank].
- Recommendation (PROPOSED): A
- Rationale: No approved AI policy or integration in current source; training data is private.
- Implications: Later decisions need scoped data, retention/cost/fallback/access tests and read versus transaction limits. NX-02 chooses no AI provider, budget or capability.
- Required subdecisions: Not decomposed in this targeted remediation; original decision remains pending and requires verifiable scope/approval evidence.
- Approval rule: Verifiable named authority, dated selected option, source reference, exact scope and version required. No approval supplied.
- Full decision approval: false
- Downstream blockers: NX-20/NX-27 and dependent AI work
- Deadline dependency: No calendar deadline supplied; before dependent task/gate

For each required dimension use the approval-completeness matrix fields. A proposed recommendation, tracker Done cell or generated checklist is not APPROVED_WITH_EVIDENCE. An explicit signed scope exception must identify the exact deferred dimension and conditions; no such exception exists. D-10 remains deferred to NX-03, persistence to NX-04, and independent AI policy to NX-20/NX-27.
