# AI Audit Log: NX-14 and NX-15 Member training screens, NX-18 QA framework

## Metadata
- Date: 2026-10-29.
- Agent: Claude Code (claude-sonnet-5-5), AI-assisted. Human approval is not claimed.
- Branch: feat/member-ui-flow4-training-plans-and-history, based on main 5224d58.

## Inputs inspected
- Task rows NX-14, NX-15, NX-18 pasted by the author; other Flow 4 rows read from the shared task spreadsheet.
- docs/nx-03 operation catalogue, RBAC matrix, DTO notes (all DRAFT, routes TBD_AFTER_APPROVAL); docs/nx-04 privacy matrix.
- Existing Member screens, API client, preview mock, UI components, tests.

## Operations
- Added api/memberTrainingApi.ts, api/mock/memberTrainingPreview.ts, two Member pages, presentation helpers, CSS, menu items and routes; wired the preview fixture into the existing preview layer.
- Added 16 tests; ran lint, tsc and the full vitest suite (1613 passed in the final run).
- Captured screenshots with Chrome headless and mocked data; found and fixed an "Arrived -" display defect from review of the screenshots.
- Wrote test cases, contract-gap questions for the backend owner, and the NX-18 QA plan. A temporary dev server was started and stopped; puppeteer-core was installed outside the repo.

## Findings and decisions
- The author chose to build the UI against a provisional contract with synthetic data rather than wait for backend sign-off. Routes and fields are therefore proposals, isolated in one file and flagged in code, tests and the PR.
- Privacy by construction: parsers copy only allowlisted fields, the API has no member id parameter, and tests assert it.
- NX-18 could only be a framework: Coach screens (NX-11 to NX-13) are not in the repository.

## Limitations
- No real backend; contract compatibility unverified; contrast not measured; no screen-reader pass; Chrome only.
