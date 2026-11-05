# Sportify Center - Flow 1-4 Release 1.1

Latest integration: main c9c50da94423f8540de328855f9a7efd0878b1f1 was incorporated as cb4565d21dadada24ce15082d57d706121cce5ad. Current handoff and repeated checks are in tasks/R11-11/reports/round-002. Earlier rounds retain their original SHAs.

Execution status: Release 1.1 candidate prepared for CODEOWNER review in https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/217. R11-00 was accepted; R11-01/02/04/05 are implemented and R11-03/09/10/11 have verification, documentation and acceptance packets. Per-task reports and the tracker record actual source SHAs and evidence. No merge, self-approval or deployment was performed.

EXECUTION_AUTHORIZATION.md records the CODEOWNER override for continuous sequential execution and one consolidated PR. The initial planning statuses and original execution order below are historical package context; current review status is in the tracker and reports. R11-06/07/08 remain scope decisions only.

The original Topic 4 workbook and unmodified assessment remain available in the owner's local inputs. Their exact provenance is recorded by R11-00 and the English requirement traceability. Raw execution logs and full runner XML artifacts remain local or in linked CI artifacts; the PR contains the task documents, summaries and synthetic desktop evidence.
Package created on 2026-11-05 from the user-supplied `KE_HOACH_HOAN_THIEN_FLOW_1-4.md` and a limited live `main` code/contract inspection.

## Goal

Complete the required Topic 4 Flow 1-3 business-facing desktop pages and validate Flow 4 to committee-demo MVP quality. Preserve the existing backend/SQL semantics, security boundaries, and all merged Flow 1-4 behavior.

## Fixed scope

- Required: Flow 1, Flow 2, Flow 3 per Topic 4. Flow 4 is optional, selected for the Release 1.1 demonstration.
- Desktop only. Do not request or build mobile or responsive-specific acceptance work.
- No AI Audit Log deliverable, no Flow 5/6, no LLM provider.
- No new payment network, live bank transfer, external messaging/CRM, waitlist or dynamic-role implementation.
- Reuse existing Spring REST APIs and React shared components before changing backend behavior.
- Work in isolated git worktrees/branches, one task per PR, CODEOWNER controls merges.
- Do not manipulate the user's `sportify` database, Flyway history or any production/shared dataset to create test fixtures.
- Avoid creating new data seeds or migrations unless strictly necessary, approved, additive, isolated and SQL-tested.

## Task mapping

| New ID | Original review task | Priority | Scope | Initial status |
|---|---|---|---|---|
| R11-00 | Baseline verification (release gate) | P0 | Inventory source/main, establish truth | READY |
| R11-01 | T1 | P0 | Reception support requests desktop | PLANNED |
| R11-02 | T2 | P0 | Manager operations report desktop | PLANNED |
| R11-03 | T3 | P0 | Build, test, SQL, CI evidence | PLANNED |
| R11-04 | T4 | P1 | Manager attendance overview | PLANNED |
| R11-05 | T5 | P1 | Coach member progress | PLANNED |
| R11-06 | T6 | P3 | Fixed RBAC decision, defer dynamic roles | DEFERRED_DECISION_ONLY |
| R11-07 | T7 | P3 | Waitlist decision, no implementation | DEFERRED_DECISION_ONLY |
| R11-08 | T8 | P3 | Legacy memberships decision, no deletion | DEFERRED_DECISION_ONLY |
| R11-09 | T9 | P1 | README/TODO/CHANGELOG synchronization | PLANNED |
| R11-10 | T10 | P1 | Traceability and four-role demo | PLANNED |
| R11-11 | New | P0 | Final release acceptance gate | PLANNED |

The source document estimates T1-T5 and T9-T10 at 7-8 person-days and 3-4 elapsed days with multiple contributors. These are historical estimates, not a promise or a deadline.

## Package contents

- `reference/` - original source review copied without modification.
- `BASELINE_VERIFICATION.md` - evidence observed in default-branch source and unresolved checks.
- `GOVERNANCE.md` - agent requirements, isolation, safety and report format.
- `TASK_BOARD.md` - schedule, task/branch/PR ownership controls and dependencies.
- `ACCEPTANCE_MATRIX.md` - requirements, actor/route/API coverage and status.
- `RELEASE_GATES.md` - required CODEOWNER acceptance gates, no false 100 percent claims.
- `DEMO_12_MIN_SCRIPT.md` - practical four-role desktop demo with fallback.
- `TEST_RUNBOOK.md` - local commands and exact-HEAD CI evidence requirements.
- `START_CODEX_MESSAGE.md` - a short English startup message.
- `tasks/R11-xx/PROMPT.md` - one executable task prompt in English for the assigned agent.
- `tasks/R11-xx/TEST_CASES.md` - task test specification and negative cases.
- `tasks/R11-xx/reports/` - report destination, empty until an agent runs.
- `Flow1-4_Release_1.1_Task_Tracker.xlsx` - owner/priority/status/dependency/evidence tracker.

## Execution order

1. Run R11-00, confirm actual `origin/main`, branch conflicts and existing implementations. If a task is already complete, mark `ALREADY_DONE` with proof rather than rewrite.
2. Parallel: R11-01 and R11-02 in separate worktrees; R11-03 baseline test pass can run early.
3. R11-04 and R11-05 when P0 review gates are green (may run in parallel with isolated worktrees).
4. R11-09 and R11-10 can prepare evidence in parallel, but final reports must match the integrated main SHA.
5. R11-11 only after every required task is reviewed/merged by CODEOWNER and fresh main CI and desktop rehearsal pass.
6. R11-06/07/08 are decisions, not permission to implement optional work.

## Installation

The ZIP is intended to be extracted directly into:

`D:\G3-SportCentreManagement\SWP\tasks\Flow1-4 - Release 1.1 (i)`

This packaging tool cannot write to your Windows D: drive. Use the PowerShell command supplied with the delivery. Do not overwrite existing files without review.
