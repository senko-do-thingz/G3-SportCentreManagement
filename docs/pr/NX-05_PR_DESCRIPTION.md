## Description
Adds the Member Flow 4 screen map for NX-05: traceability to UC-F4-05 and UC-F4-06, four screens (training plans, coach notes, attendance history, training results), ASCII wireframes for desktop and mobile, loading, empty, error and denied states, responsive and accessibility rules, data map, mock flows, open decisions and a human review checklist. Documentation only; no source code changes.

## Scope
- Task ID: NX-05 (Flow 4, F4-REQ-05 and F4-REQ-06, owner Truc, wave W1, predecessor NX-02)
- Document: docs/nx-05/NX-05-member-f4-screen-map.md
- Status: DRAFT FOR REVIEW. NX-02 sign-off is still pending, so "approved use cases" and "mock flows approved" are not claimed; section 9 of the document is the checklist for the reviewer.
- Related: NX-14 and NX-15 implement these screens on feat/member-ui-flow4-training-plans-and-history.

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log (`docs/audit/NX-05_ai_audit_log.md`) and test specifications updated (no tests apply to a documentation-only change)
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines (ASCII checked)
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after the PR is opened (CI runs on pull requests)
- **Total Tests Run**: not applicable, documentation only
- **Failures / Errors / Skipped**: not applicable

## Related Issue / Ticket
- **Task ID**: NX-05
