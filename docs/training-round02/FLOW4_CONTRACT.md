# Flow 4 integration contract - Round 03

Status: implementation proposal, awaiting recorded CODEOWNER/backend/frontend sign-off. This is not a signed contract. User authorized reconciliation on 2026-11-03 and confirmed the demo photo migration has not run on shared databases.

## Canonical integration scope

PR #185 is the proposed integration branch. All deployed clients on this branch use the API below. Paths include /api/v1; frontend client adds that prefix. Actor identity comes from JWT, never a caller-supplied actor ID. Member responses are personal projections; another member's detail is 404.

The separate proposed clients in PR #173, #180 and #193 must not be merged unchanged. Their differing DTOs cannot be reconciled by replacing route strings alone. This branch retains its connected screens and replaces their overlapping Flow 4 functionality rather than adding a second competing client. Unrelated UI changes from those branches are not imported. Branch owners still need to acknowledge this disposition before cross-PR acceptance.

| Proposed operation elsewhere | Canonical operation here | DTO / disposition |
|---|---|---|
| #173 GET /members/me/training-plans; #180/#193 GET /members/me/training/plans | GET /member/training/plans | MemberTrainingResponse.Plan[]; id,title,description,className,coachName |
| #173 GET /members/me/coach-notes | GET /member/training/feedback | Feedback[]; plan-linked rating,comments,strengths,areasForImprovement,createdAt |
| #173 GET /members/me/attendance; #180/#193 GET /members/me/training/attendance | GET /member/training/attendance | Attendance[]; id,session,status,notes; PRESENT/LATE/ABSENT/EXCUSED |
| #173 GET /members/me/training-results; #180/#193 GET /members/me/training/results | GET /member/training/results | Result[]; id,session,notes |
| Member plan detail | GET /member/training/plans/{id} | PlanDetail: plan,steps,feedback |
| Member result detail | GET /member/training/results/{id} | ResultDetail: result,scores; metricName,description,score,maxScore |
| #180/#193 GET /coach/training/members | GET /coach/training/classes then GET /coach/training/classes/{id}/members | ClassSummary[] then BookingCandidate[]; bookingId,memberId,memberName,sessionDate |
| Coach plan list/detail | GET /coach/training/plans; GET /coach/training/plans/{id} | CoachPlan[]; CoachPlanDetail: plan,steps |
| Coach save plan | POST /coach/training/plans | Plan request: classId,memberId,bookingId,title,description,steps; Created {id}, HTTP 201 |
| #180/#193 POST /coach/training/plans/{id}/publish | No publish operation in this proposal | Immediate visibility preserves the user's original rule. User permitted publish if needed, but no approved draft/revision policy exists; do not fabricate one. |
| Coach feedback | POST /coach/training/plans/{id}/feedback | Feedback request: rating 1-5,comments,strengths,areasForImprovement; Created, HTTP 201 |
| Coach session selection | GET /coach/training/sessions; GET /coach/training/sessions/{id}/members | SessionSummary[] then SessionMember[] including nullable attendanceId,resultId |
| Coach metrics/results | GET /coach/training/metrics?sportId=; POST /coach/training/results | Metric[]; Result request: sessionId,memberId,notes,scores[{metricId,score}]; Created, HTTP 201 |
| Coach attendance/correction | POST /coach/training/attendance; PUT /coach/training/attendance/{id} | Attendance: bookingId,status,notes; Correction: status,notes,reason; Created |
| Reception session selection | GET /reception/training/sessions; GET /reception/training/sessions/{id}/members | SessionSummary[], SessionMember[] |
| Reception attendance/correction | POST /reception/training/attendance; PUT /reception/training/attendance/{id} | Same Attendance/Correction shape; Created |

Session: id,className,date,startTime,endTime. Step: id,stepOrder,title,durationMinutes,instructions. Lists are arrays, not page envelopes. Nullable narrative fields remain null; numeric zero is a valid metric value. No revision, publishedAt, free-form metric strings, class-wide plan audience or result correction endpoint is claimed. Preserve these distinctions when porting other screens.

The executable DTO definitions are MemberTrainingResponse, TrainingWriteRequest and TrainingWriteResponse in backend/src/main/java/com/sportify/catalog/dto/. Connected FE types are memberTrainingApi.ts and staffTrainingApi.ts; shared roots are trainingContract.ts. Class information and private coach questions are mapped separately in API_AND_CODE_MAP.md.

## Migration integration

Keep catalog V37__personal_member_training.sql unchanged. Import only the photo assignment from PR #193 as demo V38__assign_demo_coach_photos.sql; its SQL content is unchanged. Existing credited portrait assets are already present on this branch. PR #193 must retire its V37 photo file before a later merge; importing that old file would recreate the collision. No applied migration is renamed on a shared database per the user's confirmation. V38 does not run in the production migration locations.

## Evidence and approvals

R3_01 and R3_02 in TrainingWorkflowIntegrationTest start a real random-port HTTP server, use real JWT validation and SQL Server persistence, and verify Coach -> Member and Reception -> Member visibility, role denial, cross-member isolation and conflicting correction rejection. No training service or HTTP response is mocked. R3_03 checks fresh dev+demo context startup, V37/V38 application, portrait assignment, Flyway validation and zero pending migrations on replay.

CODEOWNER approval of endpoint/DTO decisions, attendance/result policies and V37 schema remains outstanding. Do not mark signed or request acceptance based solely on local green tests. Exact-head GitHub CI must run after the final commit is pushed.

## Approval evidence search - 2026-11-03

No recorded approval was found in docs/nx-03, docs/nx-04, or the accessible review/comment timelines of PR #173, #180, #185 and #193. NX-03 README says FE/BE G-01 signatures and implementation authorization remain pending. NX-04 14-review-checklist-and-signoff.md explicitly lists CODEOWNER and migration authorization as pending.

PR #185 review https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/185#pullrequestreview-5736133209 requests changes, not approval. PR #193 release comment https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/193#issuecomment-6584683900 excludes photo migration work from HOTFIX #194. V38 here is a feature-branch proposal only, not authorization to include it in that HOTFIX. Keep all PRs open; do not self-approve or merge.

## Required decision record

| Authority | Decision scope | Reviewed SHA / contract reference | Decision |
|---|---|---|---|
| CODEOWNER | Canonical paths/DTOs, immediate visibility, timing/correction policy | Pending | Pending |
| BE owner | DTO compatibility, lock/transaction behavior, legacy endpoint compatibility | Pending | Pending |
| FE owners | #173/#180/#193 overlapping clients replaced or ported to this mapping | Pending | Pending |
| Schema owner | V37 release approval; V38 sequence and actual target histories | Pending | Pending |

The decision can be recorded in a PR comment linking this document and the exact reviewed SHA; a separate pre-existing signed document was not found. Only the responsible human may fill an approval outcome.
