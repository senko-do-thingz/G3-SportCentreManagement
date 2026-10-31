## Description
Aligns the Reception front desk pages (Member Search, Register and Renew, Check-in) with the Manager workspace look: navy header band, gold primary action, accent card border, larger inputs and tinted table header. CSS is scoped to `.reception-page`; payment, refund and booking pages are unchanged. No logic or API change.

## Scope
- Task ID: none in the assignment spreadsheet for this change. Author tasks NX-14 and NX-15 are Member screens and are not touched. Owner decision requested.
- Screens: F1-13, F1-14, F1-15 (FE-05). Routes: /reception, /reception/register, /reception/check-in.
- Details and acceptance criteria: docs/tests/RECEPTION-UI_test_cases.md

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log (`docs/audit/RECEPTION-UI-01_ai_audit_log.md`) and test specifications updated
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after push
- **Total Tests Run**: local targeted run 226 tests in 11 files
- **Failures / Errors / Skipped**: 0 / 0 / 0 (local); lint and tsc pass

## Visual evidence
Before (main c6358dc) and after screenshots at 390px, 768px, 1440px in light and dark: docs/tests/evidence/reception-ui/. Check-in has horizontal overflow at 390px both before and after (pre-existing).

## Related Issue / Ticket
- **Task ID**: not assigned (see Scope)
