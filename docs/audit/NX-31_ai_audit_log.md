# AI Audit Log: NX-31 Member assistant entry points and route protection

## Metadata
- Date: 2026-10-30.
- Agent: Claude Code (claude-sonnet-5-5), AI-assisted. Human approval is not claimed.
- Branch: nx-31-member-assistant-entry-points, based on main e45b6d3.

## Inputs inspected
- NX-31 row (pasted by the author) and the NX-24 to NX-34 rows of the task spreadsheet (read-only).
- GitHub branch list and the repository: no NX-27, NX-29 or NX-30 work, and no assistant code (only marketing copy on Home).
- Existing role gate, Member routes, Dashboard and My Profile pages, UI components, test helpers.

## Operations
- Added a build-time feature flag, a per-account opt-in preference, an entry card, a consent page with a safe return path, an empty chat slot, one route under /member, and CSS; placed the card on the Dashboard and My Profile only.
- Added 12 tests; ran lint, tsc and the test suites.
- Browser check with Chrome headless and mocked API (puppeteer-core installed outside the repo; temporary dev server stopped).
- Wrote test cases, open contract questions, PR description and this log.

## Findings and decisions
- The predecessors and the F6 contract do not exist, so only the independent part was built, switched off by default, with no network calls. The author asked to continue with NX-31, and this scope was chosen to avoid inventing assistant APIs.
- Consent is a browser-side per-account preference, not a server record; documented as an open question.
- No menu item was added because the shared header is full; entry cards sit on pages that carry no booking or payment action.

## Limitations
- No real assistant, no backend, no accessibility measurements on the new screens, Chrome only.
