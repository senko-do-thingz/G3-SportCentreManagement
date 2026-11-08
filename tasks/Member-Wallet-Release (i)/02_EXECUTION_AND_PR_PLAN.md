# Execution Plan - 8 Tasks / 8 PRs / 1 Final Review

## Linear stack (default)

| Order | Task | PR title (exact conventional pattern) | Scope | Base |
|---|---|---|---|---|
| 1 | MW-01 | `fix(flow-1): complete member profile and password updates` | Profile validation and current password flow verification | `main` |
| 2 | MW-02 | `fix(flow-1): add optional member health assessment` | Questionnaire and limited Coach summary | MW-01 branch |
| 3 | MW-03 | `fix(flow-3): implement internal wallet ledger and overview` | Internal wallet, exact balances, ledger, overview UI | MW-02 branch |
| 4 | MW-04 | `fix(flow-3): implement QR wallet top-up verification` | QR topup, receptionist confirmation | MW-03 branch |
| 5 | MW-05 | `fix(flow-2): integrate wallet settlement with safe booking` | Wallet purchase integration, atomic booking compensation | MW-04 branch |
| 6 | MW-06 | `fix(flow-3): implement member withdrawal requests` | Payout bank details, approval/completion | MW-05 branch |
| 7 | MW-07 | `fix(flow-1): add receptionist action inbox` | Operational notifications | MW-06 branch |
| 8 | MW-08 | `fix(flow-3): expose member payment and wallet history` | Transaction history/filters/detail/receipts | MW-07 branch |

A strict stack permits 8 PRs to be opened without any merge. PR 1 targets `main`; PR 2 targets PR 1's branch, etc. Review diffs and checks for each PR using *their respective base*, not a giant accumulated diff to `main`. CODEOWNER merges in order only when ready, then retargets/rebases remaining PRs as appropriate. If branch protection or GitHub tooling cannot support stacked PR bases, propose an alternative with a correct dependency graph in the final handoff; do not secretly collapse to a single PR. Keep each branch remote and PR open for review; no auto-merge.

## Branch naming

`fix/flow-1-mw-01-member-profile`, `fix/flow-1-mw-02-health`, `fix/flow-3-mw-03-wallet-core`, `fix/flow-3-mw-04-topup`, `fix/flow-2-mw-05-booking-wallet`, `fix/flow-3-mw-06-withdrawal`, `fix/flow-1-mw-07-reception-inbox`, `fix/flow-3-mw-08-member-history`.

Names can be adjusted for branch collisions only after confirming no accidental overwrite. For each task, put execution evidence under `tasks/Member-Wallet-Release (i)/evidence/MW-XX/`; documentation baseline and final review under `tasks/Member-Wallet-Release (i)/reports/`. Put the instruction files in this folder in the repo as well, but avoid copying the entire bundle as unrelated unrelated code every PR; initial task may add the assignment bundle if useful and approved.

## PR review gate

`READY_FOR_CODEOWNER_REVIEW` requires working implementation, role access checks, actual desktop screenshots, test commands/results, and no unresolved severe defect in that PR's incremental behavior. `CHANGES_REQUIRED` means known fixable defects; `BLOCKED` means external gate (missing environment, database signoff, GitHub permissions, no browser). Never mark `READY` based on screenshots alone.

## Migration coordination

Do not let multiple pending Flyway scripts collide. Assign unique versions after inspecting fresh main. Every new migration must be additive, have rollback/backup plan, tests and SQL safety review. It may be committed but not applied to shared `sportify` without explicit owner signoff; if app boot would auto-migrate it, don't boot against shared DB. If no authorized DB test, label corresponding integration E2E blocked.

## End-of-run review

Perform **one** comparative review across all eight PRs, documenting dependency chain, CI, migration order, financial invariants, regression results and screenshot evidence in one handoff. Codeowner alone decides approvals/merge. Do not comment empty reviews just to make PRs look approved.
