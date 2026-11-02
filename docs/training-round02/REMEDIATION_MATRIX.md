# CODEOWNER Round 02 remediation matrix

Submission status: implementation for independent review, NOT approval to merge.

| Finding | Disposition | Source / evidence |
|---|---|---|
| F-01 | Implemented rejection policy with common session lock; user-selected policy still needs CODEOWNER confirmation. R2-03/09/10/11. | [backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java:188](../../backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java#L188) |
| F-02 | End-time gate for results; attendance remains flexible within allowed dates. Policy confirmation pending. R2-04/05/07/17/18. | [backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java:252](../../backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java#L252) |
| F-03 | Real SQL Server service, ownership, interleaving, rollback, cancellation and audit tests added. R2-01 through R2-18. | [backend/src/test/java/com/sportify/catalog/service/TrainingWorkflowIntegrationTest.java:33](../../backend/src/test/java/com/sportify/catalog/service/TrainingWorkflowIntegrationTest.java#L33) |
| F-04 | Added training source/tests use ASCII separators. Whole added-lines scan is attached to the submission handoff; no narrowed convention check. | [frontend/src/pages/member/TrainingPages.tsx:18](../../frontend/src/pages/member/TrainingPages.tsx#L18) |
| F-05 | Transactional activity_log attribution for plans/results/feedback/attendance/corrections; correction reasons retained. R2-01/03/05/06/08. | [backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java:225](../../backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java#L225) |
| F-08 | Success message now reflects persisted attendance; response shape and Reception/Manager authorization unchanged. R2-07. | [backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java:357](../../backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L357) |
| F-09 | Roster exposes resultId; form excludes saved members and remembers a successful save. R2-FE-03 and existing result-flow test. | [frontend/src/pages/training/CoachTrainingPages.tsx:181](../../frontend/src/pages/training/CoachTrainingPages.tsx#L181) |
| F-06 | Latest origin/main and remote PR branch merged locally without conflict; final SHA and CI evidence belong in submission handoff. | Main baseline 646bd14; inspect final commit ancestry. |
| F-07 | Scope/decision register, API map, test cases, private audit handoff and this matrix provided. V37 schema approval and explicit CODEOWNER exceptions are NOT fabricated; still pending. | SCOPE_AND_DECISIONS.md; TEST_CASES.md; API_AND_CODE_MAP.md. |

## Approval and submission gates

- Required: CODEOWNER confirmation of F-01/F-02 rejection/timing policy and V37 schema decision reference.
- Required: full required GitHub CI on the final source SHA. Local tests are not a substitute for that link.
- The previous PR 183 is closed; current review target is PR 185. Do not approve or merge it automatically.
- No AI provider/key is available. This submission does not claim AI generation. Catalog guidance and direct coach consultation are explicitly labeled alternatives.
- Private audit material is excluded by .gitignore and must never be staged or uploaded in public PR text.
