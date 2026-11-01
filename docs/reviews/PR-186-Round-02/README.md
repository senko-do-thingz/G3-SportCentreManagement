# PR #186 UI-16 - Round 02 Remediation

**Task:** UI-16 Manager Activity Log
**PR:** https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/186
**Branch:** feat/ui-16
**Round 01 reviewed head:** bbe5b868b01feec136f652bafe01e54a659ecc28
**Main baseline checked:** 6689165da6c09e2426f3677803a12d562342a32b
**Remediation date:** 2026-11-01 (Asia/Saigon)

## Summary

Addressed the confirmed Activity Log defects in the frontend, added focused regression coverage, and verified the responsive presentation in a real Chromium browser using the repository's local-only preview fixtures. No backend source, API contract, database schema, or migration was changed.

## Findings

| Finding | Resolution |
| --- | --- |
| F-01 Actor picker empty-page pagination | Kept recovery pagination available when a page is empty but totalElements is positive. If the current page becomes out of range, the user gets a return-to-last-valid-page action. A genuinely empty set shows the empty state without a pager. |
| F-02 Actor picker role-filter copy | Updated the description to say actors are searched by name and roles are shown for context. No unsupported role filter is implied. |
| F-03 Missing UI-16 regression tests | Added 11 focused Activity Log tests covering filter state/ARIA, active count and collapsed clear, breakpoint behavior, stale error handling, actor pagination/empty/loading/error/retry, long names/privacy, semantic table/focus, long target/details, and refresh total changes. Updated the existing FE-06 details assertion to use the improved accessible name. |
| F-04 Responsive table accessibility | Removed the narrow-screen CSS that changed native table, row, and cell display types and clipped the header. Kept the semantic table headers. The scroll area has an accessible name, tabindex, local horizontal overflow, and visible focus styling. Chromium accessibility-tree and keyboard checks passed. NVDA was not installed, so a manual screen-reader pass remains outstanding. |
| F-05 Details action near minimum desktop width | Allocated 21% to Details, set table minimum width to 780px, and kept horizontal overflow local to the labelled region. The action is fully visible at 1101, 1200, 1280, and 1440px in both themes; 1101px long-text stress screenshots show wrapping without overlap. |
| F-06 Stale error during search debounce | Activity and actor lookup errors now render only when the visible raw search matches the settled query. Added regressions for entering a new search after an error. |
| F-07 Schedule fixture overlaps PR #184 | Retained the already-existing schedule fixture correction in this branch. PR #184 is still open and main remains at the reviewed baseline, so the correction is required for the full frontend suite against current main. After PR #184 merges, refresh this branch from main and remove any duplicate patch that is then present. No schedule production code was changed in this remediation. |
| F-08 Delivery artifacts | Added this report, the 22-case test matrix, browser screenshots and capture metadata. Updated PR #186 description after pushing the final code and reading the corresponding GitHub Actions result. The AI Audit Log is stored locally under docs/audit/ and remains excluded from Git by project policy. |

## Changed frontend files

- frontend/src/pages/manager/ActivityLogPage.tsx
- frontend/src/pages/manager/ActorPickerModal.tsx
- frontend/src/styles/managerActivity.css
- frontend/src/pages/manager/ActivityLogPage.test.tsx (new)
- frontend/src/pages/manager/managerScreens.test.tsx

## Verification

- npm run test -- --reporter=dot src/pages/manager/ActivityLogPage.test.tsx src/pages/manager/managerScreens.test.tsx - 35 passed.
- npm test -- --reporter=dot - 92 files, 1,699 tests passed, zero failed.
- npm run lint - passed.
- npm run build - passed; Vite emitted only the existing >500 kB chunk advisory.
- git diff --check - passed.
- Browser: Chromium 156, Windows, deviceScaleFactor 1, local Vite preview at 127.0.0.1:5176 with VITE_MANAGER_PREVIEW=true.
- At 1101, 1200, 1280, and 1440px, document width did not exceed the browser layout width. At 1101px, the 96.67px Details action was fully within the table scroll region. At 390px, Tab from the named table region focused the first Details action and scrolled the table locally; the document remained 390px wide.
- Browser evidence uses safe synthetic preview data. Long-content stress screenshots replace the first visible action and target text in the browser DOM only to exercise wrapping; no backend request or source fixture was changed.
- Manual NVDA/VoiceOver validation was not possible in this environment. The native table/accessibility snapshot and keyboard route were verified in Chromium.

## Review artifacts

- Browser screenshots and metadata: [browser-evidence.md](browser-evidence.md)
- Test matrix: [UI-16 Round 02 test cases](../../tests/UI-16_round02_test_cases.md)
- Local-only AI Audit Log: docs/audit/PR-186_UI16_Round02_AI_Audit_Log.docx

## Remaining review notes

- Request a manual NVDA/Chrome or equivalent screen-reader pass when an environment with a screen reader is available.
- GitHub Actions status for the final pushed SHA is recorded in the PR description after the run completes.
- Do not merge this PR. CODEOWNER retains final approval and manual merge.

## Round 03 CODEOWNER follow-up (2026-11-02)

- Fixed the reported curly quotation marks in `browser-evidence.md` with straight ASCII quotes.
- The diff-wide ASCII scan is documented in `../PR-186-Round-03/README.md`; UI16-TC-20 remains PARTIAL and a screen-reader scope exception is requested, not presumed approved.
- The local-only AI Audit Log was opened read-only in Word (2 pages). No private handoff is claimed until the requester supplies an authorized private destination.
- PR #184 remains open. The review's requested post-merge update from `main` and reconciliation of the shared schedule test are deferred until that PR merges. Current `main` is `97abe0feebd0448cb87d9f89347ab8af2a320991`; this branch has not yet been updated to it.
- No production backend request or write was performed during this review.
- Round 02 source head `35b95a8b64830f2f34cdfac6a6fc438d93a9114a` passed GitHub Actions run `41315606992` (15/15 jobs). The final SHA and CI result for the Round 03 follow-up are recorded in PR #186 after the run completes.
