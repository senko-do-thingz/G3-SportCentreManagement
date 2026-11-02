## Description
Adds the opt-in gate and the missing route-protection tests for the member AI assistant (NX-31, Flow 6) on top of PR #185, which already has the header link, the route and the chat slot:
- feature switch `VITE_ASSISTANT_ENABLED`, off by default: the header link is hidden and `/member/assistant` redirects to the member home;
- an explicit consent screen before the assistant opens, remembered per account in the browser, with Turn off at any time;
- return navigation reuses #185 (`returnTo` and `safeMemberReturnTo`); Not now and Turn off go back to the page the member came from;
- tests: signed-out visitor goes to Log in with no request; Coach, Receptionist and Manager see Access denied; switch off; per-account opt-in; unsafe return target.

Stacked on #185: open this PR against `feat/plan-comment-attend-result` (or against main after #185 is merged).

## Behavior change
In the default build the "Open AI Assistant" link added by #185 is now hidden. Set `VITE_ASSISTANT_ENABLED=true` to show it (see `frontend/.env.example`).

## Not complete
NX-27 (scope and RBAC), NX-29 (service) and NX-30 (chat UI) do not exist and the F6 API contract is undefined, so no assistant service is called. Open questions for Dat and Phat: `docs/nx-31/NX-31-open-questions.md`.

## Scope
- Task ID: NX-31 (Flow 6, F6-REQ-01, owner Truc). Screen ids: none in the spreadsheet.
- Test cases and evidence: `docs/tests/NX-31_test_cases.md`, `docs/nx-31/evidence/`.

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log kept locally as the repository rules require (not tracked); test specifications updated
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines (ASCII checked)
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (header link hidden by default; see above)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after the PR is opened (CI runs on pull requests)
- **Total Tests Run**: local full run 95 files, 1722 tests passed (baseline 94 files, 1712 tests); lint and tsc pass
- **Failures / Errors / Skipped**: 0 / 0 / 0 (local)

## Related Issue / Ticket
- **Task ID**: NX-31
