## Description
Partial Flow 4 cross-role QA run for NX-18 on the screens that exist today (Coach schedule and roster, Reception member search and check-in, Member training plans, attendance and results): 42 captures (3 viewports, light and dark), 84 empty, error and forbidden checks, axe-core and keyboard checks. Adds the reusable runner, the report and the evidence.

Two defects found by the run are fixed here:
- Check-in overflowed horizontally at 390px (bare `1fr` grid column): now `minmax(0, 1fr)`.
- The Today's check-ins table is now a keyboard-reachable named scroll region (existing `scrollLabel` prop).

One defect is reported but not fixed: the shared header logo link has an aria-label that does not contain its visible text (axe label-content-name-mismatch on every page). It belongs to the shared shell and Home and 3 tests depend on it; suggested fix and owner are in the report.

## Scope and status
- Task ID: NX-18 (Flow 4, F4-REQ-01..07, owner Truc with Hung and Phat).
- NX-18 is NOT complete: NX-11, NX-12, NX-13 screens and the backend are not in the repository, and the FE/BE contract (docs/nx-03) is an unsigned draft, so contract compatibility is reported as blocked. See section 7 of the report.
- Report: docs/nx-18/NX-18_flow4_cross_role_QA_report.md. Runner: docs/nx-18/qa-tools/flow4-qa.mjs.
- The Member screens were tested from branch nx-14-nx-15-member-training-screens; the runner needs those screens to be present to cover them.

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log (`docs/audit/NX-18_ai_audit_log.md`) and test specifications updated (report and regression test)
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines (ASCII checked)
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after the PR is opened (CI runs on pull requests)
- **Total Tests Run**: local full run 87 files, 1599 tests passed (includes 1 new regression test); lint and tsc pass
- **Failures / Errors / Skipped**: 0 / 0 / 0 (local)

## Related Issue / Ticket
- **Task ID**: NX-18
