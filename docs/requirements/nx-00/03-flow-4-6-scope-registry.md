# Flow 4-6 scope registry

Baseline: `c00a3c1a29677a4f069000fa6ff38fe3c970e492`. Repository source paths below refer to the isolated audit worktree, never the older original checkout.

Labels: **Confirmed** = directly supported official capability or named flow; **Partially confirmed** = related baseline artifact exists without the full workflow; **Proposed** = internal design or mapping awaiting approval; **Unknown** = no approved detail located; **Conflicting** = reviewed sources disagree. Existing implementation evidence is static, not runtime certification. Official E5 statements below are English paraphrases, never literal quotations.

Flows 1-3 are Required (`Project!F5`): User and membership management; Class booking and schedule management; Payment and report managment. Their current rules remain the baseline. Optional adoption cannot silently expand or alter that baseline.

## Flow 4

Source-exact title: **Training and attendence management**. Classification: **Optional** (`Project!G5`). Normalized display title: Training and attendance management. Adoption: **Pending Decision**. The owner must explicitly record Go, Deferred or Not in Scope; this audit chooses none.

### Confirmed capability content and proposed allocation

Official `Project!E5`, Coach bullets 3-7 (lines 14-18): create individual or class training plans; record training results after each session; evaluate progress and add comments; take attendance for each session; send notices or homework to members. Member bullets 6-7 (27-28): view attendance history and training outcomes; view training plans and coach comments. Receptionist bullet 5 (37): check in members on arrival. The flow title is explicit, but allocating these bullets to Flow 4 is a proposed interpretation. Notices/homework in particular are candidate cross-flow scope, not an approved Flow 4 boundary.

Business goal, as an interpretation of those capabilities: support coached training and let members see their own training history. Source-named action actors are Coach, Member and Receptionist; Center Manager is an administrative context actor. No explicit official manager training dashboard, manager correction permission or full note visibility is specified. Assigned-coach/self-member restrictions are a reasonable proposal and partly match existing reads, but the complete future RBAC matrix needs approval.

Major actions and source-supported outcomes: a Coach creates a plan for an individual or class, records session outcomes, attendance and progress comments; a Member can view the stated history/plan/comments; a Receptionist can check in arrivals. Required fields, workflows, persistence linkage and release conditions are not defined by the course text.

### Partially confirmed baseline and reuse

- S22-S23: staff `POST /api/v1/check-ins` (alias `/api/v1/check-in`) persists a check-in, with confirmed-today booking, valid coverage, duplicate and locking checks. GET history is staff-scoped. S34-S35 show the existing Receptionist UI/API. This is arrival/check-in evidence, not proof of Coach session attendance. Preserve current checks and balances.
- S24-S25: `POST /api/v1/bookings/self-training/attendance` exists for Receptionist/Manager, but only returns acknowledgement. S25:327-353 says `Self-training attendance acknowledged (persistence pending Flow 4 implementation)`. S30:728-770 checks no save. It must not be described as a completed attendance store, debit operation or newly needed duplicate endpoint.
- S26-S29: assigned Coach schedules/rosters and Member booking history provide reusable session/booking identity. They expose neither full training outcomes nor a new training mutation. S41 stores goal/level fields, but does not establish permitted use, quality, full training history or note privacy.
- S12 defines training schema artifacts; no implemented training JPA/service/controller workflow was identified in the bounded searches, consistent with S01:60. This is not a completed NX-01 audit or a claim about a deployed database.

Flow 1 dependencies: identities, roles, member/coach profiles, packages and arrival check-in. Flow 2: class/session assignment, booking identities and existing booking/cancellation balance rules. Flow 3: payment/report rules remain separate; no new fee, refund or reporting requirement is inferred. Do not deduct twice for booking and attendance. Final attendance/check-in semantics and correction effects require D-01/D-13 approval.

### Proposed, unknown and conflicting

Proposed only: plan templates/versioning/publication, draft/submitted attendance, draft/published feedback, result scoring, manager overview, correction history policy and notification links. Source support for a capability is not approval of a particular state machine or screen.

Unknown: personal versus class/session plan association; assignment/reassignment ownership; historical record access; author/editor/deleter roles; correction window; cancellation/no-show/late/absent/excused treatment; self-training semantics; metrics/units/ranges; who can view raw notes versus published comments; member correction requests; retention; audit content; notification/homework placement; APIs/errors/pagination/timezone. No numerical metric or edit window is mandated by Topic 4.

Conflicts requiring human/design reconciliation (details C-01..C-04 in report 05): S06 specifies session-linked plans and booking-linked attendance/results, while S12 defines class-linked plans, nullable attendance booking and session/member result linkage. S06 describes publication states absent from those V16 tables. S06 self-training wording about consuming a session is unsafe to equate with another debit, given existing booking-time deduction and non-debiting check-in.

### Source-supported acceptance candidates

These capability observations are testable only after Flow 4 adoption and allocation are approved; they are not a signed API/UI acceptance specification:

- SAC4-01: demonstrate Coach creation of a training plan for an individual or class, matching E5 Coach 3. The official OR wording does not mandate templates or two separate UI products.
- SAC4-02: demonstrate post-session results and progress comments, matching Coach 4-5.
- SAC4-03: demonstrate Coach session attendance, matching Coach 6; demonstrate Receptionist arrival check-in separately, matching Receptionist 5, with integration semantics still TBD.
- SAC4-04: demonstrate Member viewing attendance history/outcomes and plans/Coach comments, matching Member 6-7. Visibility rules remain TBD.

Draft scenario proposals, NOT APPROVED: an assigned Coach records a confirmed roster member; a different Coach is denied; a Member sees only approved own results; correction retains author/time/reason; duplicate arrival does not consume another session. These require roles/state/data approval, not automatic implementation. Notices/homework acceptance is withheld pending placement D-04.

## Flow 5

Source-exact title: **AI workout recommendation**. Classification: **Optional** (`Project!G5`). Adoption: **Pending Decision**, independently from Flow 4 and Flow 6.

### Confirmed and proposed mapping

Official English paraphrase, E5 Coach bullet 8 (line 19): use AI workout suggestions based on goals, proficiency/level and training history. Proposed allocation: F5-REQ-01 to Flow 5. Business goal interpretation: assist a Coach in choosing suitable exercises using those inputs. The source-named user is Coach; the Member is a possible data subject, not an explicitly mandated direct recommendation UI actor. Center Manager administrative access and Receptionist use are not specified for this feature.

Major source-supported action/outcome: Coach requests/uses AI suggestions informed by the three named input types. No official algorithm, provider, model, generation format, score/ranking, latency target, cost, automatic plan conversion or automatic approval is stated.

### Partially confirmed and baseline dependencies

S14 contains workout/rule/candidate/review migration artifacts, and S41 has goal and level fields. S01:62 calls the module schema-only; no running AI provider or service is verified. Existing Flow 1 identity/roles/profile and Flow 2 Coach assignment, class/session and package eligibility may be reused after NX-01 data/access verification. Usable training history depends on whichever Flow 4 results become approved and available. No new booking, payment or debit behavior is authorized.

### Proposed, unknown and conflicting

S08 and S16 propose Member goals, ranked sports/classes, suitable sessions and saved recommendations. This is **F5-CAND-02**, proposal only, explicitly approval-gated by Tasks EN NX-24 (row 30), not official Member functionality. Coach review before applying a suggestion and a fallback to manual planning are project design/QA proposals; the lecturer does not prescribe that lifecycle. S04's AI-never-commits-actions assumption is a project guardrail, not a course quote.

Unknown: Go/Deferred/Not in Scope; Coach-only versus any Member-direct extension; input quality/consent/minimization; which notes/history may leave the system; provider/model, credentials and budget authority; request/cost limits; retention/logging/deletion; prompt/content safety; evaluation dataset/threshold; timeout/provider-outage fallback; reviewer and final plan authority; ownership/versions; contractual outputs/errors. No choices are made by this audit.

Conflicts: S08 defines richer provider/revision/ranking fields and review lifecycle than S14. S08's review status PENDING/DECIDED/CANCELLED differs from S14:123-129 PENDING/APPROVED/MODIFIED/REJECTED. S16's PARTIAL labels cannot prove an AI endpoint exists. See C-05/C-06.

Source-supported conditional acceptance SAC5-01: after adoption/mapping approval, demonstrate a Coach obtaining workout suggestions informed by goals, level and history. Draft proposals only: Coach reviews a suggestion before saving a plan; unavailable provider yields a clear fallback; sensitive fields are excluded; a Member cannot obtain another member's suggestions. Exact policy/threshold tests await NX-20 decisions. A Member recommendation screen has no official acceptance statement.

## Flow 6

Source-exact title: **AI assistant**. Classification: **Optional** (`Project!G5`). Adoption: **Pending Decision**, independently from Flow 5.

### Confirmed and proposed mapping

Official English paraphrase, E5 Member bullet 8 (line 29): ask the AI system about schedules, exercises and center services. Proposed allocation to Flow 6, F6-REQ-01. Business goal interpretation: help Members find relevant center/training information. The explicit question actor is Member. Receptionist support-request intake is separately official E5 Receptionist bullet 8 (line 40), but attaching it to chat or Flow 6 is a proposal; Manager configuration and support assignment/reply screens are not explicitly mandated here.

Major action/outcome: Member asks questions in the named subject areas and receives relevant assistance. Official text does not authorize booking, cancellation, payment, package purchase, attendance edits, plan application, unrestricted database access or automatic business actions through chat.

### Partially confirmed and baseline dependencies

S13 defines support/assistant/conversation/message migration artifacts. S01:61 describes schema-only AI & Support, not a functioning assistant. Existing Home AI availability copy (S39-S40) is not an implemented chat route/API. Identity and existing catalog/schedule/service data are potential sources; personal package, booking or training data must pass approved identity-scoped access rules. No catalog truth, eligibility, payment or cancellation rules are changed by an AI answer.

### Proposed, unknown and conflicting

Proposed only: topic configuration, stored conversations, support handoff, staff inbox, source references, identity-scoped retrieval, uncertainty response, provider outage state and read-only tool boundaries. S04's no-AI-business-actions design assumption is a useful project guardrail to ratify, not lecturer text. S17's mockup description mentions booking inside chat; that conflicts with assuming a strictly informational assistant and has no explicit official action mandate. The distinction between a navigation link and executing a booking needs an owner decision.

Unknown: independent adoption choice; approved knowledge corpus/source freshness; public versus personal data and permissions; access after role/membership changes; model/provider and costs; prompts/system instructions; prompt-injection and exfiltration boundaries; response grounding/uncertainty/refusal policy; history retention/consent/deletion; rate/token/budget limits; human support scope, access and lifecycle; evaluation/acceptance thresholds; contracts and availability fallback.

Conflicts: S09 proposes a singleton settings row, usage/latency/entity metadata and conversation-linked tickets, whereas S13 defines key/value settings and lacks those proposed linkage/telemetry fields. S16-S17 schema/mockup PARTIAL statuses do not prove a chat or support workflow. See C-07/C-08.

Source-supported conditional acceptance SAC6-01: after adoption/mapping approval, demonstrate Member AI questions covering schedules, exercises and center services. It does not specify model quality thresholds or permit transactions. Draft proposals only: answer from approved sources; say when uncertain; keep Member A's private information inaccessible to Member B; fail safely during provider outage; offer support handoff only if approved. These require NX-27 approval and later evaluation, not NX-00 implementation.
