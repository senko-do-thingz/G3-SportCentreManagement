## Description
Improves readability and colour of the Manager User Management screen (wider content area, larger type, navy header band, gold primary action, tinted table).

Addresses code owner round 1 on #159: the styling is now scoped to `.manager-page--users`, so no other Manager screen, shared component or responsive layout is affected. UserManagementPage gets the modifier class; no logic or API changes.

## Scope
- Task ID(s): TO BE FILLED (task assignment spreadsheet)
- Screen ID(s): TO BE FILLED
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
Before/after screenshots at 390px, 768px, 1440px in light and dark, plus unchanged-screen comparisons: TO BE ATTACHED (matrix in the test document).

## Related Issue / Ticket
- **Task ID**: TO BE FILLED
