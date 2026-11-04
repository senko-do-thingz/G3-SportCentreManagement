## Description
Improves readability and colour of the Manager User Management screen (wider content area, larger type, navy header band, gold primary action, tinted table).

Addresses code owner round 1 on #159: the styling is now scoped to `.manager-page--users`, so no other Manager screen, shared component or responsive layout is affected. UserManagementPage gets the modifier class; no logic or API changes.

## Scope
- Task ID(s): NX-14 (Flow 4, F4-REQ-06, owner Truc, assigned to the PR author). Note: NX-14 deliverable is the Member training plans UI. This PR is a Manager UI polish that arose outside that deliverable; it does not change any Member training plan screen.
- Screen ID(s): Not assigned in the spreadsheet (the sheet has no Screen ID column; NX-14 lists Member training plans). Affected screen: Manager User Management.
- Route: the User Management route (UserManagementPage)
- Ownership: author of this PR
- Acceptance criteria: see docs/tests/MGR-UI_test_cases.md section 3

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log (`docs/audit/MGR-UI-01_ai_audit_log.md`) and test specifications (`docs/tests/MGR-UI_test_cases.md`) updated
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after push
- **Total Tests Run**: local frontend run: 1589 tests in 87 files
- **Failures / Errors / Skipped**: 0 / 0 / 0 (local); lint, tsc and build also pass

## Visual evidence
Screenshots at 390px, 768px, 1440px in light and dark, before (main d8a56c5) and after, are in `docs/tests/evidence/mgr-ui/`. Packages, classes, finance and transactions are pixel-identical to main in all 6 combinations each; only User Management changes. No horizontal overflow in any capture. Contrast ratios (all above 4.5:1) and details: `docs/tests/MGR-UI_test_cases.md` sections 5 and 6.

## Related Issue / Ticket
- **Task ID**: NX-14 (see Scope; Manager UI polish outside the NX-14 deliverable)
