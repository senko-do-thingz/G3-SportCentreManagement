# Release 1.1 source baseline and evidence confidence

## Provenance

- Supplied assessment: `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`, dated 2026-11-05. The numbers approximately 90/95/90/90 percent are the report's snapshots, not authoritative GitHub-computed progress.
- Public repository: https://github.com/senko-do-thingz/G3-SportCentreManagement
- The observations below came from selected files on `main` inspected on 2026-11-05. They are bounded static observations, not new local runtime/test claims.
- Each agent MUST fetch `origin/main` before work, inspect its exact HEAD and refresh all claims. Do not rely on stale PR/branch snapshots.

## Confirmed observed on current main

| Area | Evidence observed | Implication |
|---|---|---|
| Reception support | `backend/src/main/java/com/sportify/catalog/controller/ReceptionSupportController.java` has GET list/detail, POST creation with mandatory `Idempotency-Key` and PUT status for enabled RECEPTIONIST; member self-view GET exists | R11-01 primarily frontend integration, no backend rewrite by default |
| Support rules | `docs/training/reception-support.md` specifies supported categories, bounded fields, max 100 newest, permitted status transitions, and creator-preserving idempotency | Reuse existing state machine; not a free-text CRM or chat |
| Reception support desktop | Code search for `SupportRequestsPage` and `/reception/support` did not find a frontend page/route; `AppRoutes.tsx` and `navigation.ts` inspect confirms no support menu in inspected content | Candidate true gap, verify again at task start |
| Operations report | `docs/payment/Operations-report.md` defines GET `/api/v1/manager/reports/operations?from=&to=` with member counts, booking counts and authoritative revenue | R11-02 should reuse existing API; preserve date semantics and count definitions |
| Manager report desktop | `frontend/src/pages/manager/ManagerRevenueReportsPage.tsx` displays revenue and date filters; no new operations stats seen in reviewed sections | Candidate true gap; inspect entire updated page before coding |
| Manager attendance | `ManagerTrainingController` serves only `/api/v1/manager/training/attendance/backfill`; overview route not located in inspected controller | R11-04 must search all APIs first and add minimal read-only endpoints only if absent |
| Coach progress | `TrainingProgressController` provides GET `/api/v1/coach/training/classes/{classId}/members/{memberId}/progress` and self Member progress; `CoachTrainingController` provides GET `.../profile` | R11-05 is frontend-first; preserve Coach-class ownership restrictions |
| Release context | Previous merged PRs #194, #203, #206 and #211 provide integrated Flow 1-4 capabilities and verification evidence | Never reimplement or copy past PR source without a precise gap |
| Existing tests | User assessment states 1,928 frontend test pass in its assessment, but local backend/typescript/lint were not verified there; some merged PRs have earlier green GitHub CI | R11-03 must capture fresh exact-SHA results, not claim tests were never performed |

## Important business definitions

- Manager operations `registeredMembers` is registration in selected date range, while `currentlyEnabledMembersRegistered` is current enabled subset, NOT historical enabled count at range end.
- `classBookings` uses `booked_at` for date range and sessions linked to a class; `upcoming` and `completed` are subsets of confirmed, not additive partitions.
- Demo/synthetic financial rows are excluded as defined in backend. Do not add front-end math that changes API semantics.
- Reception support OPEN -> IN_PROGRESS or CLOSED, IN_PROGRESS -> RESOLVED or CLOSED, RESOLVED -> CLOSED. Resolution/closure needs meaningful notes. CLOSED is terminal.
- Coach progress is scoped to an assigned class and its Member; never expose arbitrary member lookups.

## Verify before implementation

1. Check the actual Figma reference assets exist under `context/`; record exact discovered filenames, not guessed paths. `F1-16`, `F4-10`, `F4-05`, `S4-ManagerDetails`, `S4-Profile`, and `S4-ProgressSaved` are labels from the supplied assessment.
2. Search `AppRoutes.tsx`, `navigation.ts`, API clients and relevant pages for newly added implementations.
3. Inspect backend DTOs and errors from real code; the assessment gives endpoint families, not complete request/response contracts.
4. Detect open conflicting PRs; do not duplicate a teammate's pending change without CODEOWNER allocation.
5. Capture current main SHA and CI status. A passed older SHA is not proof for a newer branch.

## Source links

- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/backend/src/main/java/com/sportify/catalog/controller/ReceptionSupportController.java
- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/docs/training/reception-support.md
- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/docs/payment/Operations-report.md
- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/backend/src/main/java/com/sportify/catalog/controller/ManagerTrainingController.java
- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/backend/src/main/java/com/sportify/catalog/controller/TrainingProgressController.java
- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/frontend/src/routes/AppRoutes.tsx
- https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/main/frontend/src/layouts/navigation.ts
