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

- The reported curly quotation marks in browser-evidence.md were replaced with straight ASCII quotes.
- Latest origin/main, 646bd143c40d0e3721210433997e1316ae05cc1e, was fetched and merged into feat/ui-16 at cc54acc40ff6dc93446cd7b543bbd4c3d598ee63 with no conflicts. This sync was pushed to PR #186.
- PR #184 remains open at 0288be8d4d03e120285083e45a005f1e6877efd3. A changed-file comparison confirms overlap in frontend/src/pages/manager/managerSchedule.test.tsx and frontend/src/pages/manager/managerScreens.test.tsx. Keep the shared changes until PR #184 merges; then remove only what is redundant and rerun checks.
- On local merge head cc54acc, the frontend suite passed: 94 files and 1,764 tests; lint and build passed. The build has the existing Vite chunk-size advisory.
- GitHub Actions run 41388216476 passed on pushed head f07f06cc4bddbf10b808654bcba437ddab3e085e, including the latest main sync. The following report-only correction is covered by the exact workflow linked in the current PR description.
- UI16-TC-20 remains PARTIAL. No NVDA/Chrome or VoiceOver/Safari pass is claimed; an explicit CODEOWNER-approved scope exception remains necessary if manual testing cannot be provided.
- The local-only AI Audit Log was opened read-only in Word and reported as 2 pages. It remains excluded from Git. Private delivery to the CODEOWNER has not happened because an approved destination and authorization are still missing.
- The updated ASCII scan command and result are recorded in docs/reviews/PR-186-Round-03/README.md.
- No production backend request or write was performed during the Round 02/03 browser review or this branch reconciliation.
