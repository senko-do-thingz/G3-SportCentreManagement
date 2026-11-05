# Release 1.1 task board

Latest-main follow-up: candidate cb4565d21dadada24ce15082d57d706121cce5ad includes main c9c50da94423f8540de328855f9a7efd0878b1f1. R11-03, R11-10 and R11-11 round-002 record repeat verification. All implementation tasks remain IN_REVIEW; scope decisions and R11-00 acceptance are unchanged.

Execution update: R11-00 accepted. CODEOWNER authorized one consolidated branch `feat/release-1-1-desktop`, sequential automatic continuation and one PR (EXECUTION_AUTHORIZATION.md). Implemented tasks are IN_REVIEW, not self-approved or merged. R11-06/07/08 retain decision-only scope. Final evidence and blockers are under R11-11/reports; the workbook carries per-task status and evidence links.

Status definitions: READY, PLANNED, IN_PROGRESS, IN_REVIEW, CHANGES_REQUESTED, PASSED, MERGED, ALREADY_DONE, BLOCKED, DEFERRED_DECISION_ONLY.
Do not mark a task PASSED merely because an agent says it is done. Record CODEOWNER review, exact source SHA and CI evidence.

| ID | Original | Priority | Suggested branch | Depends on | Deliverable |
|---|---|---|---|---|---|
| R11-00 | New | P0 | `chore/r11-00-baseline` | None | source inventory, exact main SHA and gap matrix |
| R11-01 | T1 | P0 | `feat/r11-01-reception-support` | R11-00 | Reception Support desktop page and API tests |
| R11-02 | T2 | P0 | `feat/r11-02-operations-report` | R11-00 | Manager operations cards and booking counts |
| R11-03 | T3 | P0 | `test/r11-03-release-verification` | R11-00, sync post-feature | exact-SHA unit/SQL/frontend/CI results |
| R11-04 | T4 | P1 | `feat/r11-04-manager-attendance` | R11-00, P0 scope review | Manager attendance overview and detail |
| R11-05 | T5 | P1 | `feat/r11-05-coach-progress` | R11-00, P0 scope review | Coach class-scoped Member progress |
| R11-06 | T6 | P3 | `docs/r11-06-rbac-decision` | R11-00 | fixed-role design decision only |
| R11-07 | T7 | P3 | `docs/r11-07-waitlist-decision` | R11-00 | explicit waitlist deferred decision only |
| R11-08 | T8 | P3 | `docs/r11-08-legacy-decision` | R11-00 | document keeping deprecated legacy path |
| R11-09 | T9 | P1 | `docs/r11-09-status-sync` | R11-00, final integration | accurate README/TODO/CHANGELOG |
| R11-10 | T10 | P1 | `docs/r11-10-trace-demo` | R11-00, all functional tasks | Requirement -> UI -> API -> test matrix and demo |
| R11-11 | New | P0 | `test/r11-11-release-gate` | R11-01/02/03/04/05/09/10 | final integration acceptance + CODEOWNER decision |

## Parallel work

- Lane A: R11-01 then (if assigned) R11-04.
- Lane B: R11-02 then (if assigned) R11-05.
- Lane C: R11-03 and documentation preparation; R11-09/10 final check after merges.
- Never run two agents in the same mutable worktree, branch or SQL fixture.
- CODEOWNER decides whether to include P1 optional Flow 4 tasks in the 1.1 release; do not silently claim 95 percent if they are excluded.

## Estimates from supplied review

- T1: 1-1.5 person-days; T2: 1; T3: 0.5; T4: 2; T5: 1.5; T9: 0.5; T10: 1.
- T6 fixed-RBAC documentation: 0.5, dynamic-role build 1.5 if separately approved; T7: 2-3 if ever approved; T8: 0.5-1 if separately approved.
- These are planning estimates, not a task-completion promise.
