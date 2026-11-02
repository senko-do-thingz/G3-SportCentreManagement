# Class learning API and code map

All paths below use /api/v1. Read/write actor IDs come from the authenticated principal.

| Flow | API | Code |
|---|---|---|
| Member class information and own participation | GET /member/training/classes; GET /member/training/classes/{id} | ClassLearningController, ClassLearningService, ClassLearningPages.tsx |
| Ask coach | GET/POST /member/training/questions; GET /member/training/questions/{id}; POST /member/training/questions/{id}/messages | ClassLearningController/Service, ClassQuestionsPage |
| Coach questions/replies | GET /coach/training/questions; GET /coach/training/questions/{id}; POST /coach/training/questions/{id}/messages | Current assigned coach scope in SQL; ClassQuestionsPage |
| Coach attendance | POST /coach/training/attendance; PUT /coach/training/attendance/{id} | CoachTrainingController, TrainingWriteService, CoachTrainingAttendancePage |
| Coach session results | Existing POST /coach/training/results; roster GET /coach/training/sessions/{id}/members now includes nullable resultId | TrainingWriteService, CoachTrainingResultsPage |
| Personal plans and supplementary steps | Existing /coach/training/plans and /member/training/plans routes | Existing plan flow retained; transactional creation audit added |
| Plan feedback | Existing POST /coach/training/plans/{id}/feedback and Member reads | Creation audit added, no extra publish step |
| Catalog guidance without AI | GET /member/training/guidance?query=Badminton | ClassLearningService search; MemberAssistantPage explicitly labels no AI |
| Existing reception/self-training | Existing endpoints retained | Shared locking/audit, corrected success message; no package debit |

Example question: POST /member/training/questions with {"classId":1,"subject":"Footwork practice","body":"Which supplementary steps should I practise?"}.

Example result: POST /coach/training/results with {"sessionId":1,"memberId":2,"notes":"Session assessment and practice guidance","scores":[]} after session end and PRESENT/LATE attendance.

No new production dependency, provider SDK or migration is required in this round. The new service uses the existing support and audit tables. Historical NX-04 conceptual proposals remain distinguishable from deployed schema and must not be treated as signed approval.
