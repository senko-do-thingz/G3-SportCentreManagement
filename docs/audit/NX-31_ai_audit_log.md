# AI Audit Log: NX-31 Member assistant opt-in and route protection (on PR #185)

## Metadata
- Date: 2026-11-02.
- Agent: Claude Code (claude-sonnet-5-5), AI-assisted. Human approval is not claimed.
- Branch: nx-31-assistant-optin-on-pr185, based on PR #185 head 7c42630 (feat/plan-comment-attend-result).

## Inputs inspected
- NX-31 row and the NX-24 to NX-34 rows of the task spreadsheet (read-only).
- PR #185 (frontend and backend diff, tests, CI runs). Its frontend already contains the header link, the route, the safe return path and the chat slot for the assistant.
- Earlier work of this author on branches nx-31-member-assistant-entry-points and nx-14-nx-15-member-training-screens, which duplicated parts of #185 and conflicted with it.

## Operations
- Rebuilt the NX-31 work on top of #185 instead of adding a second implementation: feature switch, consent gate with per-account choice and Turn off, header link hidden when the switch is off, route element changed to the gate.
- Added 10 tests and adapted 1 existing test; ran lint, tsc and the full vitest suite.
- Browser check with Chrome headless and mocked API (puppeteer-core outside the repo; temporary dev server and worktree removed afterwards).
- Wrote test cases, open questions, PR description and this log.

## Findings and decisions
- The author chose to stack the NX-14, NX-15 and NX-31 work on #185 rather than keep parallel branches.
- The switch is off by default, so the header link that #185 currently shows is hidden in the default build. This is a deliberate behavior change recorded in the test cases.
- Consent is a per-account browser setting; whether it must be a server record is an open question for Dat.
- Predecessors NX-27, NX-29 and NX-30 and the F6 API contract still do not exist, so nothing here calls an assistant service.

## Limitations
- No real assistant or backend, no accessibility measurement on the two new screens, Chrome only.
