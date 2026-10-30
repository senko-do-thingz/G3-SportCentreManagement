## Description
Adds the opt-in entry points and route protection for the member AI assistant (NX-31, Flow 6), without the assistant itself:
- a feature flag, off by default (`VITE_ASSISTANT_ENABLED`); when off there is no entry point and `/member/assistant` redirects to the member home;
- an opt-in card at the end of the Member Dashboard side column and of My Profile;
- `/member/assistant` under the existing Member role gate: signed-out visitors go to Log in, other roles see Access denied;
- a consent screen before anything opens, stored per account in the browser, with Turn off at any time;
- return navigation to the page the member came from (only member-area paths are accepted);
- an empty chat slot (`AssistantHost`) for the chat UI of NX-30. It calls no service.

Booking and payment pages are not touched and have no entry point (a test checks where the card is used).

## IMPORTANT: predecessors do not exist
NX-27 (scope and RBAC), NX-29 (service) and NX-30 (chat UI) are not in the repository, and the F6 member route/API contract is undefined. This PR is therefore only the independent part. Open questions for Dat and Phat: docs/nx-31/NX-31-open-contract-questions.md. NX-31 is not complete until the chat is connected and the end-to-end flow is tested.

## Scope
- Task ID: NX-31 (Flow 6, F6-REQ-01, owner Truc, coordinate with Phat on shared UI). Screen ids: none in the spreadsheet.
- Details and acceptance map: docs/tests/NX-31_test_cases.md. Browser evidence: docs/nx-31/evidence/.

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log (`docs/audit/NX-31_ai_audit_log.md`) and test specifications updated
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines (ASCII checked)
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none; the feature is off by default)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after the PR is opened (CI runs on pull requests)
- **Total Tests Run**: local full run 90 files, 1654 tests passed (12 new); lint and tsc pass
- **Failures / Errors / Skipped**: 0 / 0 / 0 (local)

## Related Issue / Ticket
- **Task ID**: NX-31
