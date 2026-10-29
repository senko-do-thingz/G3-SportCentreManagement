## Description
Adds two read-only Member screens for Flow 4 and their menu entries:
- NX-14 My Training Plans (/member/training): published plans with steps and the coach notes shared with the member.
- NX-15 Attendance and Results (/member/history): own attendance history and per-session training results, with paging.

Also adds the NX-18 QA plan and a screenshot helper (documentation only).

## IMPORTANT: provisional API contract
The Flow 4 contract in docs/nx-03 is an unsigned draft ("IMPLEMENTATION_NOT_AUTHORIZED"), the Member read routes are TBD_AFTER_APPROVAL, and the backend tasks NX-07 to NX-10 are not in the repository. The screens use a proposed contract isolated in `frontend/src/api/memberTrainingApi.ts` and read synthetic data in manager-preview mode. Real backend compatibility is NOT verified. Questions for the backend owner: docs/pr/NX-14-NX-15_contract_gaps_for_BE.md. Please do not merge before the contract is signed, or merge knowing the routes are placeholders.

## Scope
- Tasks: NX-14 (F4-REQ-06), NX-15 (F4-REQ-05), NX-18 framework only (the Coach and Reception screens it needs do not exist yet).
- Screen IDs: none in the spreadsheet.
- Details: docs/tests/NX-14-NX-15_test_cases.md, docs/tests/NX-18_flow4_qa_plan.md

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log (`docs/audit/NX-14-NX-15_ai_audit_log.md`) and test specifications updated
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after push
- **Total Tests Run**: local full run 89 files, 1613 tests passed; 16 new tests in 2 files
- **Failures / Errors / Skipped**: 0 / 0 / 0 (local); lint and tsc pass

## Visual evidence
docs/tests/evidence/member-training-ui/: both screens at 390/768/1440px, light and dark, plus empty, error and keyboard states. Contrast measured on both screens in both themes: all text at or above 4.5:1 (lowest 4.54, idle tab in light mode); table in the test document.

## Related Issue / Ticket
- **Task ID**: NX-14, NX-15
