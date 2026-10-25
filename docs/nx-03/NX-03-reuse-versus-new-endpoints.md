# Reuse versus new endpoints

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

| Operation | Semantic kind | Actor | Requirements / direct scenarios | Route status |
| --- | --- | --- | --- | --- |
| F4-PLAN-01 Author personal or class draft | CREATE | Coach | F4-REQ-01, AS-01, AS-02 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PLAN-02 Edit permitted draft content | UPDATE_OR_CORRECTION | Coach | F4-REQ-01, AS-01, AS-02, AS-11 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PLAN-03 Publish or revise authorized plan | PUBLISH_OR_REVISE | Coach | F4-REQ-01, AS-11 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PLAN-04 Read own permitted plans and revisions | READ | Member | F4-REQ-06, AS-03, AS-10 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-RESULT-01 Record session outcome | CREATE | Coach | F4-REQ-02, AS-04 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-RESULT-02 Correct session outcome | UPDATE_OR_CORRECTION | Coach | F4-REQ-02, AS-04 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-RESULT-03 Read own outcome history | READ | Member | F4-REQ-05, AS-09 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-01 Record Coach participation | CREATE | Coach | F4-REQ-04, AS-06 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-02 Correct Coach participation | UPDATE_OR_CORRECTION | Coach | F4-REQ-04, AS-07 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-03 Read own participation history | READ | Member | F4-REQ-05, AS-09 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-ATTEND-04 Reuse staff arrival recording | EXISTING_WRITE | Receptionist | F4-REQ-07, AS-08 | POST /api/v1/check-ins |
| F4-ATTEND-05 Reuse staff arrival history | EXISTING_READ | Receptionist | F4-REQ-07, AS-08 | GET /api/v1/check-ins |
| F4-PROGRESS-01 Evaluate progress and write feedback | CREATE | Coach | F4-REQ-03, AS-05 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PROGRESS-02 Correct progress assessment | UPDATE_OR_CORRECTION | Coach | F4-REQ-03, AS-05 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PROGRESS-03 Review scoped training history | READ | Coach | F4-REQ-03, AS-05 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |
| F4-PROGRESS-04 Read own visible feedback | READ | Member | F4-REQ-06, AS-03, AS-10 | TBD_AFTER_APPROVAL TBD_AFTER_APPROVAL |

Existing F4-ATTEND-04 POST and F4-ATTEND-05 GET refer to `/api/v1/check-ins` and alias `/api/v1/check-in`; these are two method operations, not new routes. D-01 reuse with separate Coach participation is a non-binding recommendation. Existing schedule/roster/booking/profile behavior can supply guarded context but grants no new training access. Public membership `/api/v1/plans` and self-training acknowledgement are not training-plan/participation APIs.
