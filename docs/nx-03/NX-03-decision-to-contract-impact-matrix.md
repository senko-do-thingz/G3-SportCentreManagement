# Decision to contract impact matrix

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

## Parent decisions

| Decision | Status | Question | Affected operations | Unsigned options |
| --- | --- | --- | --- | --- |
| D-01 | PENDING_HUMAN_DECISION | Reuse or extend existing arrival check-in for Flow 4? | ["F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05"] | A: retain arrival and add separate participation; B: extend arrival with explicitly distinct meanings; C: treat arrival as sole attendance |
| D-02 | PENDING_HUMAN_DECISION | Which personal/class/template/version/ownership lifecycle is adopted? | ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04"] | A: mutable class scaffold with explicit personal-scope exception; B: distinct personal/class assignments and preserved published revisions; C: per-session copied plans |
| D-03 | PENDING_HUMAN_DECISION | Which result metrics, edit/correction windows and retention apply? | ["F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"] | A: approved metric dictionary plus classified comments and accountable corrections; B: narrative-only outcomes and explicit privacy policy; C: Coach-defined metrics with governance to be specified |
| D-04 | PENDING_HUMAN_DECISION | Where are homework and notifications allocated? | [] | A: review in separate NX-16 backlog; B: explicitly adopt selected Flow 4 triggers after approval; C: reuse an evidenced existing module if later found |
| D-05 | PENDING_HUMAN_DECISION | Will optional Flow 5 be adopted, and who decides provider/model/budget? | [] | A: defer independent decision to NX-20; B: adopt only after NX-20 signed policy; C: not in scope by signed owner decision |
| D-06 | PENDING_HUMAN_DECISION | Coach-only or also Member-direct recommendations? | [] | A: decide Coach-only scope in NX-20; B: separately approve Member candidate via NX-20/NX-24; C: defer all AI audience work |
| D-07 | PENDING_HUMAN_DECISION | Is Coach review required before AI suggestion becomes a plan? | [] | A: require explicit human review if Flow 5 adopted; B: proposal-only suggestions never become plans; C: defer transition policy to NX-20 |
| D-08 | PENDING_HUMAN_DECISION | Will optional Flow 6 be adopted, with which sources and retention? | [] | A: defer independent policy to NX-27; B: adopt after signed NX-27 source/access policy; C: not in scope by signed owner decision |
| D-09 | PENDING_HUMAN_DECISION | Are optional flows separate milestones and what is demo acceptance? | [] | A: separate scope/release approvals for each optional flow with approved demo evidence; B: one combined optional release after all separate scope choices; C: defer optional release scheduling |
| D-10 | PENDING_HUMAN_DECISION | What exact API endpoints and DTOs are approved? | ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"] | A: defer Flow 4 contracts to NX-03 after signed scenarios; B: request signed scope exception for limited contract exploration while keeping approval pending |
| D-11 | PENDING_HUMAN_DECISION | Independent Flow 4 adoption and boundary? | ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"] | A: adopt Flow 4 with explicit included capabilities/exclusions; B: defer Flow 4; C: not in scope |
| D-12 | PENDING_HUMAN_DECISION | What record/field permissions apply to Coach Member Manager and Receptionist? | ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"] | A: current scoped writes, separately granted historical reads, explicit note visibility, deny unspecified rights; B: permanent original-author rights and broad staff oversight; C: current assignment only for both writes and all history |
| D-13 | PENDING_HUMAN_DECISION | What training states, linkage, corrections and balance effects are authoritative? | ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-RESULT-01", "F4-RESULT-02", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-PROGRESS-01", "F4-PROGRESS-02"] | A: per-session/member participation with correction history, explicit eligibility and no financial side effects; B: booking-centric participation with separately approved eligibility changes; C: preserve arrival only and defer Coach attendance by explicit scope ruling |
| D-14 | PENDING_HUMAN_DECISION | What cross-AI minimization, access, retention/cost and mutation limits apply? | [] | A: defer separately to NX-20 and NX-27; B: record only common policy principles now, with all AI transmission and business action unapproved |

## Required dimensions

### D-02.PLAN_SCOPE

- Question: How are personal and class plan ownership/assignments distinguished?
- Options: ["Distinct personal/class assignments", "Per-session copies", "Reduced scope only with signed exception"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03"]
- Approval evidence: None
- Scope exception: None

### D-02.RECIPIENTS

- Question: Is a published class audience a recipient snapshot or current enrollment?
- Options: ["Snapshot when published", "Dynamic current enrollment", "Per-member assignment copies"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung; Truc reviews Member visibility
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04"]
- Approval evidence: None
- Scope exception: None

### D-02.PUBLISH_AUTHORITY

- Question: Who may expose a plan revision to its audience?
- Options: ["Current assigned authorized Coach", "Separate explicitly authorized reviewer", "UNRESOLVED delegation model"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03"]
- Approval evidence: None
- Scope exception: None

### D-02.REVISION_LIFECYCLE

- Question: How do draft/published/superseded/archived revisions preserve history?
- Options: ["Preserved published revisions", "Mutable published content", "Freeze after publication"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04"]
- Approval evidence: None
- Scope exception: None

### D-02.EDITABLE_FIELDS

- Question: Which plan fields/order may change at each chosen lifecycle stage?
- Options: ["Draft editable and published revision copied for changes", "Explicit field-by-stage allowlist", "UNRESOLVED field/order rules"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03"]
- Approval evidence: None
- Scope exception: None

### D-02.HISTORICAL_ACCESS

- Question: What prior plan revisions remain visible after assignment withdrawal?
- Options: ["Previously entitled history retained subject to retention", "Current entitlement only", "UNRESOLVED exception policy"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung; Truc reviews history
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04"]
- Approval evidence: None
- Scope exception: None

### D-02.SESSION_RELINK

- Question: What happens to a plan link on session reschedule/cancel/reassignment?
- Options: ["Keep historical link and deliberately assign future use", "Relink only after authorized review", "UNRESOLVED cancellation exceptions"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03"]
- Approval evidence: None
- Scope exception: None

### D-02.TEMPLATE_ORIGIN

- Question: Which permitted content may be cloned and how is its origin retained?
- Options: ["Clone shared permitted content with provenance", "No templates in signed initial scope", "UNRESOLVED template entitlement"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03"]
- Approval evidence: None
- Scope exception: None

### D-03.METRICS

- Question: Who defines permitted observations, units and meaning?
- Options: ["Approved sport metric dictionary", "Narrative-only outcomes", "Governed Coach-defined metrics"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung; metric steward OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-01", "F4-RESULT-02", "F4-PROGRESS-01", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

### D-03.VALIDATION

- Question: How are missing/coerced values, units and incomplete results handled?
- Options: ["Reject incomplete required metrics", "Save explicitly incomplete result", "Narrative-only result when approved"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-01", "F4-RESULT-02", "F4-PROGRESS-01", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

### D-03.CORRECTION_MECHANISM

- Question: How are result/comment corrections authorized and recorded?
- Options: ["Retained prior/current revision with reason", "Separate reviewed correction request", "UNRESOLVED amendment mechanism"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-02", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

### D-03.CORRECTION_WINDOW

- Question: Is correction time-bounded or permitted through review at any time?
- Options: ["Owner-defined bounded window plus later review", "Accountable correction without time cutoff", "UNRESOLVED duration/exception rules"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-02", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

### D-03.RETENTION

- Question: How long are results/comments and their revisions retained?
- Options: ["UNRESOLVED duration", "Differentiated policy by record purpose", "Common justified policy with explicit exceptions"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung; privacy/legal authority OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-03.COMMENT_VISIBILITY

- Question: Which Coach comments are member-visible versus restricted?
- Options: ["Intentional visible feedback only", "Visible feedback plus justified restricted notes", "UNRESOLVED classification"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-03.RESULT_HISTORY

- Question: What corrected result/evaluation history may Member view?
- Options: ["Current value plus correction indicator", "Own revision history with reason redaction", "UNRESOLVED history projection"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-12.RESOURCE_RIGHTS

- Question: What record operations may each actor perform for each owned/assigned resource?
- Options: ["Explicit actor/action/resource predicates", "UNRESOLVED oversight/delegation grants"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-12.FIELD_VISIBILITY

- Question: Which plan/result/comment/correction fields may each actor read?
- Options: ["Explicit minimum field projection per actor/context", "UNRESOLVED restricted-note and audit visibility"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-12.REASSIGNMENT

- Question: What reads/writes remain after Coach or Member assignment changes?
- Options: ["Revoke future writes and separately decide historical reads", "Explicit signed delegation exceptions", "UNRESOLVED transition policy"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-RESULT-01", "F4-RESULT-02", "F4-ATTEND-01", "F4-ATTEND-02", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03"]
- Approval evidence: None
- Scope exception: None

### D-12.HISTORIC_ENTITLEMENT

- Question: Who may read historical training across former assignments?
- Options: ["Previously entitled Member history under retention policy", "Only current scoped entitlement", "Reviewed role/resource-specific historic grant"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-05", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-12.CORRECTION_RIGHTS

- Question: Which actors can correct each resource, including after reassignment?
- Options: ["Current authorized Coach by resource", "Separately reviewed delegated correction role", "UNRESOLVED escalation authority"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-02", "F4-PLAN-03", "F4-RESULT-02", "F4-ATTEND-02", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

### D-12.AUDIT_ACCESS

- Question: Which staff may inspect correction/audit metadata and private reasons?
- Options: ["Minimum purpose-scoped audit projection", "Separately authorized full evidence review", "UNRESOLVED retention/access policy"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung; privacy authority OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-02", "F4-PLAN-03", "F4-RESULT-02", "F4-ATTEND-02", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

### D-12.MEMBER_CORRECTION_VIEW

- Question: What correction outcome/reason is visible to the affected Member?
- Options: ["Current corrected value with minimal indication", "Own redacted revision history", "UNRESOLVED reason projection"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-04", "F4-RESULT-03", "F4-ATTEND-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-12.PRIVACY_OPERATIONS

- Question: Who handles verified export/deletion/correction requests and retained evidence?
- Options: ["Human-reviewed identity/purpose/scope process", "UNRESOLVED authority/retention exceptions"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Truc + Hung coordinate; privacy/legal authority OWNER_UNCONFIRMED
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-PLAN-04", "F4-RESULT-01", "F4-RESULT-02", "F4-RESULT-03", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03", "F4-ATTEND-04", "F4-ATTEND-05", "F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03", "F4-PROGRESS-04"]
- Approval evidence: None
- Scope exception: None

### D-13.STATE_SEMANTICS

- Question: Which participation, missing, cancelled and outcome meanings are adopted?
- Options: ["Explicit reviewed meanings preserving missing versus absent", "UNRESOLVED status set and transitions"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung; Truc reviews Member labels
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03"]
- Approval evidence: None
- Scope exception: None

### D-13.LIFECYCLE

- Question: When is an observation draft/final/submitted/correctable, if those concepts are adopted?
- Options: ["Explicit deliberate finalization with correction path", "Immediate authoritative record plus correction history", "UNRESOLVED lifecycle"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02"]
- Approval evidence: None
- Scope exception: None

### D-13.ELIGIBILITY

- Question: Which session/member/booking/participation evidence permits attendance and results?
- Options: ["Current eligible confirmed roster with explicit result conditions", "Reviewed alternate eligibility with signed exception", "UNRESOLVED linkage and self-training rules"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02"]
- Approval evidence: None
- Scope exception: None

### D-13.TIMING_BOUNDARIES

- Question: What defines on-time, late, absent/excused/no-show and entry cutoff?
- Options: ["Human-defined thresholds and exception authority", "UNRESOLVED values and edge cases"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung; Truc reviews labels
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02"]
- Approval evidence: None
- Scope exception: None

### D-13.UNIQUENESS

- Question: Is there one current session/member outcome with history or multiple competing observations?
- Options: ["One current outcome plus accountable corrections", "Reviewed multi-observer model", "UNRESOLVED retry/conflict policy"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02"]
- Approval evidence: None
- Scope exception: None

### D-13.CORRECTION_AUTHORITY

- Question: Who may correct or supersede attendance and how do competing corrections resolve?
- Options: ["Current authorized Coach with revision-aware history", "Separate reviewed oversight/correction role", "UNRESOLVED reassignment exceptions"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02"]
- Approval evidence: None
- Scope exception: None

### D-13.HISTORY

- Question: What prior/current evidence and reason must correction retain?
- Options: ["Attributable prior/current state, actor/time/reason", "Separately reviewed correction evidence model", "UNRESOLVED audit-failure consistency"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03"]
- Approval evidence: None
- Scope exception: None

### D-13.VISIBILITY_IMPACT

- Question: How do corrections affect Member history and dependent result/evaluation review?
- Options: ["Visible current outcome with permitted correction indicator and flagged dependencies", "Own redacted history with reviewed dependency handling", "UNRESOLVED notification linkage"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung; Truc reviews Member outcome
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-03"]
- Approval evidence: None
- Scope exception: None

### D-13.FINANCIAL_INVARIANTS

- Question: Do training/attendance changes remain neutral to booking and package/payment/refund state?
- Options: ["Preserve existing flows with no new financial side effect", "Any alternate behavior requires separately signed scope and finance review"]
- Status: PENDING_HUMAN_DECISION
- Selected option: UNRESOLVED
- Owner: Dat + Hung
- Authority: OWNER_UNCONFIRMED: business/privacy authority must be named where relevant; CODEOWNER review still required
- Affected operations: ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03", "F4-RESULT-01", "F4-RESULT-02", "F4-ATTEND-01", "F4-ATTEND-02", "F4-ATTEND-04", "F4-PROGRESS-01", "F4-PROGRESS-02"]
- Approval evidence: None
- Scope exception: None

Full approval requires signed parent strategy AND all required dimensions approved or explicitly covered by a signed scope exception. No pending policy became approved in remediation. Read validation profiles contain selector/history policy only; participation uniqueness, transition and correction policies are not imposed on reads. D-13.STATE_SEMANTICS can affect a Member attendance projection without becoming a read transition rule.
