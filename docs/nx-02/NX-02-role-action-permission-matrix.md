# NX-02 role and resource permission matrix

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

All 35 IDs are retained. New rights are PROPOSED; existing guards are bounded static facts, not inherited rights for hypothetical training APIs. Authentication alone is insufficient; derive identity from the principal, and authorize every resource, field, filter, page, export and cache projection. Historical read authority and current write/correction rights are separate.

| ID | Actor | Action | Resource | Scope/ownership predicate | Existing verified guard | Proposed requirement | Denied behavior | Audit requirement | Open decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PM-01 | Coach | create personal plan | personal plan | Current assigned member/context; principal author | S05 context only; no write guard verified | ALLOW after scoped policy | DENY foreign member | Author/context/outcome | D-02/D-12 |
| PM-02 | Coach | create class plan | class plan | Current assigned class/session | S05/S06 read scope only | ALLOW scoped authoring | DENY other class | Author/class/audience | D-02/D-12 |
| PM-03 | Coach | edit draft | plan revision | Current editor entitlement, expected revision | No training edit guard verified | ALLOW own authorized draft | DENY guessed planId/stale revision | Actor/revision/outcome | D-02/D-12 |
| PM-04 | Coach | publish plan | plan audience | Current publisher and validated audience | No publication guard verified | ALLOW intentional publication | DENY unauthorized publication | Revision/audience/change | D-02/D-12 |
| PM-05 | Coach | assign personal plan | member assignment | Authorized member and compatible context | S05 prerequisite only | ALLOW authorized target | DENY tampered memberId | Actor/target/scope | D-02/D-12 |
| PM-06 | Coach | assign class plan | class audience | Authorized class; chosen snapshot/current policy | S05/S06 prerequisite only | ALLOW validated audience | DENY wrong classId | Actor/audience/revision | D-02/D-12 |
| PM-07 | Coach | supersede/archive/clone | plan/template | Current authority; preserve permitted history/origin | S11 scaffold only | ALLOW under chosen lifecycle | DENY cloning private peer notes | Origin/revision/actor | D-02/D-12 |
| PM-08 | Coach | record result | session/member outcome | Current session assignment and approved eligibility | S05/S06 context only | ALLOW approved session target | DENY missing/stale/foreign target | Metric version/actor/outcome | D-03/D-13 |
| PM-09 | Coach | correct result | result revision | Current correction entitlement and reason | No runtime guard verified | ALLOW accountable correction | DENY silent historical overwrite | Prior/current metadata/reason | D-03/D-12 |
| PM-10 | Coach | assess progress | member assessment | Permitted current member history and evidence | S05 roster only, not broad history | ALLOW scoped assessment | DENY unrelated member history | Evidence references/actor | D-03/D-12 |
| PM-11 | Coach | write feedback/private note | classified comment | Approved audience classification and current authority | S13 scaffold only | ALLOW separately authorized classes | DENY implicit member publication | Classification/change/actor | D-03/D-12 |
| PM-12 | Coach | submit attendance | session roster | Current session assignment and eligible member | S05/S06 roster guard only | ALLOW per-session participation | DENY cancelled/foreign/ineligible | Session/member/actor/outcome | D-01/D-13 |
| PM-13 | Coach | correct attendance | current attendance | Current correction entitlement; revision and reason | S12 scaffold only | ALLOW retained correction history | DENY lost update/unauthorized change | Prior/current/actor/time/reason | D-12/D-13 |
| PM-14 | Coach | read member training history | results/plans/feedback | Minimum member data needed for assigned scope | S05 roster does not grant all history | ALLOW explicit needed subset | DENY unrelated class/notes | Access metadata minimized | D-12 |
| PM-15 | Former Coach | read historical training | prior member records | Separate historical entitlement, no authorship shortcut | S05 current assignment only | DENY by default pending policy | DENY automatic permanent access | Denied/access metadata | D-12 |
| PM-16 | Former Coach | edit/correct previous work | historic plan/result/attendance | Current delegated authority needed | No historic training grant verified | DENY absent explicit delegation | DENY original-author-only bypass | Denied/correction request | D-12/D-13 |
| PM-17 | Other-class Coach | read/write changed identifiers | member/session/class/plan | Must pass current resource assignment | S05 denies foreign roster | DENY foreign resources | DENY all private existence details | Denied scope/outcome | D-12 |
| PM-18 | Member | read active plan | assigned published revision | Principal member plus assignment/publication | No training read guard verified | ALLOW own published assignment | DENY drafts/foreign planId | Read metadata if justified | D-02/D-12 |
| PM-19 | Member | read historical plan | permitted old revisions | Own historical entitlement separately evaluated | No training history guard verified | ALLOW chosen retained history | DENY future class revisions after withdrawal | Read metadata minimized | D-02/D-12 |
| PM-20 | Member | read attendance | session history | Member id derived from principal for every row | S07 own booking read pattern only | ALLOW own permitted participation | DENY changed memberId/sessionId | Read/denial metadata | D-01/D-12 |
| PM-21 | Member | read results | session outcomes | Own permitted result, current access | S07/S08 principal binding only | ALLOW own permitted outcomes | DENY peer data/counts | Read/denial metadata | D-03/D-12 |
| PM-22 | Member | read feedback | member-visible comments | Own subject and approved visibility | S13 has no visibility workflow | ALLOW permitted feedback | DENY private notes/peer feedback | Read metadata minimized | D-03/D-12 |
| PM-23 | Member | write Coach records | plan/result/attendance/comment | No source capability grants Coach editing | No training route verified | DENY writes; review request separate | DENY self-granted Coach role | Denied/outcome metadata | D-12 |
| PM-24 | Receptionist | record arrival | check-in attempt | Existing staff role and service eligibility | S02 guard; S01 checks | ALLOW existing bounded path | DENY ineligible attempt as recorded outcome | Existing S01 activity event | D-01 |
| PM-25 | Receptionist | read arrival confirmation/history | arrival records | Existing staff role and operational purpose | S02 existing history guard | ALLOW existing arrival data | DENY inference of training privileges | History-access policy to review | D-01/D-12 |
| PM-26 | Receptionist | edit plan/result/Coach attendance | training records | No inherited Coach authority | No training grant verified | DENY absent separately approved role | DENY desk role bypass | Denied metadata | D-12 |
| PM-27 | Receptionist | read private Coach notes | training narratives | No established business need/grant | No training grant verified | DENY | DENY fields including error/cache payload | Denied metadata | D-12 |
| PM-28 | Manager | read Coach roster and arrival | existing operational records | Existing manager-specific scope | S02/S05 verified guard exceptions | ALLOW bounded existing reads | DENY implied training-note grant | Existing audit/access policy | D-12 |
| PM-29 | Manager | read activity audit | activity metadata | Existing Manager role | S17 class-level guard | ALLOW existing audit scope | DENY using audit to expose private narrative | Audit-access review | D-12 |
| PM-30 | Manager | write Coach training or read restricted notes | Flow 4 records | Separate explicitly approved oversight scope needed | No training right verified | DENY default until decision | DENY blanket inheritance | Delegation/access provenance | D-12 |
| PM-31 | Admin/unknown role | access training | Flow 4 private records | Role existence and privileges not verified | No Admin training grant verified | DENY default | DENY treating label as superuser | Denied metadata | D-12 |
| PM-32 | Guest | read/write protected data | all private Flow 4 records | Authentication required plus resource checks | S09/S10 authenticated fallback | DENY | DENY data and metadata | Minimal denial event | D-12 |
| PM-33 | Stale JWT/deactivated user | read/write | all private records | Current account role/enabled and resource authority | S09 loads current user; S05 fresh reader | DENY stale/deactivated grant | DENY cached token-role authority | Denial without token logging | D-12 |
| PM-34 | Removed class Member | read future shared plan | class plan | Current future assignment revoked; historic separate | No training grant verified | DENY future; historical choice pending | DENY whole-class permanent access | Access/revocation metadata | D-02/D-12 |
| PM-35 | Cancelled-booking Member/Coach | create new attendance/result | cancelled context | Revalidate booking/session eligibility | S05/S06 confirmed roster; S07 cancellation | DENY stale eligibility under proposal | DENY cached roster authority | Denial/context/revision | D-13 |

## Cross-check with runtime actors

| UC | Initiating actor | Relevant permission rows |
| --- | --- | --- |
| UC-F4-01P | Coach | PM-01, PM-03, PM-05, PM-15, PM-16 |
| UC-F4-01C | Coach | PM-02, PM-06, PM-07, PM-17 |
| UC-F4-01V | Coach | PM-03, PM-04, PM-07, PM-16 |
| UC-F4-02 | Coach | PM-08, PM-09, PM-35 |
| UC-F4-03 | Coach | PM-10, PM-11, PM-14, PM-30 |
| UC-F4-04 | Coach | PM-12, PM-17, PM-35 |
| UC-F4-04C | Coach | PM-13, PM-16, PM-33 |
| UC-F4-05 | Member | PM-20, PM-21, PM-33 |
| UC-F4-06 | Member | PM-18, PM-19, PM-22, PM-34 |
| UC-F4-07 | Receptionist | PM-24, PM-25, PM-28 |

The matrix does not add supporting actors or grant policy approvers runtime rights. Receptionist arrival uses PM-24/25, not Coach assignment. Manager existing arrival/roster/audit exceptions PM-28/29 do not grant PM-30 training rights. Former Coach, stale JWT, removed Member and cancelled booking boundaries are explicit in PM-15/16/33/34/35.

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. All D-12 dimensions and related D-02/D-03/D-13 policies remain unresolved.
