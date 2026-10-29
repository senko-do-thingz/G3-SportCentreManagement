# Test Cases - Reception Front Desk UI aligned with the Manager workspace

## 1. Scope and provenance
- Branch: feat/reception-ui-front-desk-manager-style, based on main 5224d58.
- Task ID: no row in the task assignment spreadsheet covers this change. The author's assigned tasks NX-14 (Member training plans UI) and NX-15 (Member attendance and results UI) are Member screens and are not changed here. This is a UI consistency improvement outside those deliverables. Owner decision on a Task ID is pending.
- Screen ID(s): none assigned in the spreadsheet. Existing in-code references: F1-13 (Member Search and Profile), F1-14 (Register and Renew), F1-15 (Check-in), task FE-05.
- Routes: /reception, /reception/register, /reception/check-in.
- Change: modifier class `reception-page` on the three pages and a CSS block in frontend/src/styles/reception.css, scoped to `.reception-page`. It reuses existing design tokens only. No logic or API change.
- Not changed: Book for member, Payment requests, Refund requests, payment and receipt pages.

## 2. Acceptance criteria
| ID | Criterion | Verification |
|---|---|---|
| AC-1 | The three pages show the navy header band, gold primary action, accent card border and larger inputs, like the Manager workspace | Screenshots |
| AC-2 | Pages outside the scope are unaffected | Class only exists on the three pages; scoped selectors |
| AC-3 | No new horizontal overflow at 390, 768, 1440px, light and dark | Overflow check |
| AC-4 | Lint, tsc and tests pass | Section 3 |

## 3. Executed automated verification
Run from frontend/ on this branch, 2026-10-29.

| Command | Result |
|---|---|
| npm run lint | Passed |
| npx tsc -b | Passed |
| npx vitest run src/pages/reception src/styles src/layouts | 11 files, 226 tests passed |

Full-suite run and CI URL for the latest commit: TO BE FILLED after the pipeline runs.

## 4. Visual evidence
Chrome headless (puppeteer-core), Vite dev server, API responses mocked in the browser (receptionist profile and a 3-member search result; no backend). Before = main 5224d58. Images: docs/tests/evidence/reception-ui/, named `<before|after>_<member-search|register|check-in>_<390|768|1440>_<light|dark>.png`. 36 images plus report.json.

| Screen | Viewports and themes captured | Observed |
|---|---|---|
| Member Search (with 3 results) | 390/768/1440, light and dark | Header band and gold CTA applied |
| Register and Renew | same | Header band applied |
| Check-in | same | Header band applied |

Overflow: the only horizontal overflow is Check-in at 390px, in both light and dark. It also occurs on main (before), so it is pre-existing and not introduced here. It is not fixed in this change.

Not captured: hover, focus and modal states; Create member modal. Not measured: contrast ratios. The page reuses the same tokens as the Manager header band and gold button, which measured 9.89:1 for the button and were checked visually for the title.

## 5. Limitations
Chrome only; mocked API data; no receptionist login against a real backend.
