# NX-02 downstream handoff and dependencies

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

| Downstream task | Workbook source | Coordinator | Required NX-02 decision/input | Current handoff boundary |
| --- | --- | --- | --- | --- |
| NX-03 API/error contract | Tasks EN row 9; depends NX-02 | Dat + FE | D-01/02/03/09/11/12/13, approved use cases and PM matrix | Business inputs prepared; contract approval BLOCKED. D-10 deferred here, no new URLs/DTOs |
| NX-04 persistence/authorization design | Row 10; depends NX-02/NX-03 | Dat | Approved identity, lifecycle, assignment/history, correction/uniqueness and contracts | Reconcile S11/S12/S13/S14; forward-only plan only after approval; no SQL/database work in NX-02 |
| NX-05 Member wireframes | Row 11; depends NX-02 | Truc | Own published plans/history/outcomes/feedback, correction visibility, withdrawn assignment | Review fields, empty/loading/denied/error/history, accessibility/mobile/light/dark; no invented API |
| NX-06 Coach/Reception UX | Row 12; depends NX-02 | Hung | Personal/class/version editor, roster/result validation, arrival versus participation, correction races | Review actor journeys and PM; preserve Flow 2 rules and truthful ACK wording |
| NX-16 homework/notifications | Row 22; depends NX-02 | Phat + Dat | D-04 placement/triggers/audience/content policy | Candidate only; no automatic correction notifications or delivery promise |
| NX-20 Flow 5 scope | Row 26; depends NX-02/NX-19 | Dat + Hung/Truc | D-05/06/07/14 separate adoption, privacy and human review | Optional. No provider/model/budget or Member candidate selected |
| NX-27 Flow 6 scope | Row 33; depends NX-02 | Dat + Phat/Truc | D-08/10/14 independent adoption/data boundaries | Optional independently of Flow 5. No chat contract, source corpus or AI action chosen |

Approval order: dated NX-00 acceptance or exception -> dated NX-01 formal disposition -> Flow 4 adoption and NX-02 scenario/RBAC decisions -> G-00/CODEOWNER review of exact version -> NX-03 contract review with FE -> NX-04 persistence review -> separately authorized implementation and verification. Analysis can be complete before these approval steps. No next backend implementation is authorized by this package.

Hung/Truc should record whether each displayed field is shared/personal, current/historical, published/restricted and editable/read-only. They should review stale context, no roster, validation, duplicate retry, correction conflict and unavailable history states. They must not infer URLs from screens. Dat coordinates the business authority and metrics/privacy owners. No calendar deadlines are provided; decisions are due before the dependent activity rather than on an invented date.

## Required policy dimensions in this round

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. Strategic A/B/C recommendations are not adopted policy. See [approval completeness](NX-02-approval-completeness-matrix.md) for all 32 required dimensions, selected options UNRESOLVED, owners, evidence and downstream blockers. No duration, threshold, permission or signed exception is invented.

| Subdecision | Unresolved selection | Actual downstream dependencies |
| --- | --- | --- |
| D-02.PLAN_SCOPE | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-02.RECIPIENTS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-02.PUBLISH_AUTHORITY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-02.REVISION_LIFECYCLE | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-02.EDITABLE_FIELDS | UNRESOLVED | NX-03, NX-04, NX-06 |
| D-02.HISTORICAL_ACCESS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-02.SESSION_RELINK | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-02.TEMPLATE_ORIGIN | UNRESOLVED | NX-03, NX-04, NX-06 |
| D-03.METRICS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-03.VALIDATION | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-03.CORRECTION_MECHANISM | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-03.CORRECTION_WINDOW | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-03.RETENTION | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-03.COMMENT_VISIBILITY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-03.RESULT_HISTORY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.RESOURCE_RIGHTS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.FIELD_VISIBILITY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.REASSIGNMENT | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.HISTORIC_ENTITLEMENT | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.CORRECTION_RIGHTS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.AUDIT_ACCESS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.MEMBER_CORRECTION_VIEW | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-12.PRIVACY_OPERATIONS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.STATE_SEMANTICS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.LIFECYCLE | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.ELIGIBILITY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.TIMING_BOUNDARIES | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.UNIQUENESS | UNRESOLVED | NX-03, NX-04, NX-06 |
| D-13.CORRECTION_AUTHORITY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.HISTORY | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.VISIBILITY_IMPACT | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |
| D-13.FINANCIAL_INVARIANTS | UNRESOLVED | NX-03, NX-04, NX-05, NX-06 |

## Direct acceptance ownership

| UC | Owned positive AS |
| --- | --- |
| UC-F4-01P | AS-01 |
| UC-F4-01C | AS-02 |
| UC-F4-01V | AS-11 |
| UC-F4-02 | AS-04 |
| UC-F4-03 | AS-05 |
| UC-F4-04 | AS-06 |
| UC-F4-04C | AS-07 |
| UC-F4-05 | AS-09 |
| UC-F4-06 | AS-03, AS-10 |
| UC-F4-07 | AS-08 |

UC-F4-01V directly owns AS-11 with revoked-authority DENY and stale-revision CONFLICT. Related Member reads do not count as Coach mutation acceptance. Full required policy approval remains pending. DT-22 normalizes only UTF-8 BOM/newline encoding; all other content remains exact.
