# Test Cases - Manager UI Readability and Colors (User Management)

## 1. Scope and provenance
- Branch: feat/manager-ui-readability-and-colors. Review: PR #159, code owner round 1 (Request Changes).
- Task ID(s): TO BE FILLED by the author from the task assignment spreadsheet. Not invented here.
- Screen ID(s): TO BE FILLED from the same spreadsheet. Affected screen: Manager User Management.
- Affected route: the route that renders UserManagementPage (frontend/src/pages/manager/UserManagementPage.tsx).
- Ownership: the author of PR #159 owns this change. Other Manager screens are owned by their own tasks and must not change visually.

## 2. Change summary
- UserManagementPage root element gains the modifier class manager-page--users.
- frontend/src/styles/manager.css: the readability and colour blocks that were previously scoped to .manager-page (all Manager screens) are now scoped to .manager-page--users. The original base rules above those blocks are untouched.
- No logic, API, routing or data change.

## 3. Acceptance criteria
| ID | Criterion | Verification |
|---|---|---|
| AC-1 | User Management shows the navy header band, gold primary action, tinted table and larger type | Manual, screenshots below |
| AC-2 | Other Manager screens (Classes, Class editor, Schedule, Packages, Membership cards, Activity log, Financial overview, Revenue reports, Transactions) render exactly as on main | Manual before/after on main vs branch |
| AC-3 | No horizontal page scroll at 390px, 768px, 1440px in light and dark | Manual |
| AC-4 | Keyboard focus, hover and disabled states remain visible | Manual |
| AC-5 | Lint, type check, build and unit tests pass | Automated, section 4 |

## 4. Executed automated verification
Run on the working tree on top of 65a90e8, 2026-10-29, from frontend/.

| Command | Result |
|---|---|
| npm run lint | Passed, no output |
| npx tsc -b | Passed, no output |
| npm run build | Passed (only the existing chunk size warning) |
| npm test | 87 files passed, 1589 tests passed, 0 failed |

CI run URL for the final commit: TO BE FILLED after push.

## 5. Visual evidence (captured)
Captured 2026-10-29 with Chrome headless (puppeteer-core) against `vite --mode manager-preview` (sample data). Before = origin/main d8a56c5. After = this branch. Images and the raw report are in docs/tests/evidence/mgr-ui/ (naming: `<before|after>_<screen>_<390|768|1440>_<light|dark>.png`, full page).

Screens captured: users, packages, classes, finance, transactions, each at 390px, 768px and 1440px in light and dark, for before and after.

| Screen | Result (before vs after, 6 viewport/theme combinations each) |
|---|---|
| packages | Pixel-identical in 6 of 6 |
| classes | Pixel-identical in 6 of 6 |
| finance | Pixel-identical in 6 of 6 |
| transactions | Pixel-identical in 6 of 6 |
| users | Intentionally different in 6 of 6 (new header band, gold CTA, tinted table) |

This is the regression evidence for AC-2: the other captured Manager screens are byte-identical in PNG output to main. Not captured: Class editor, Membership cards, Activity log, Revenue reports. They use the same `.manager-page` class and the same scoped selectors, so they are expected to match, but this is not pixel-verified.

Horizontal overflow check (scrollWidth > clientWidth): none in any of the 60 captures (AC-3).

Interaction states on User Management, before and after, at 1440px and 390px, light and dark: `*_users_<w>_<scheme>_hover.png` (row action hover), `*_focus.png` (after three Tab presses), `*_modal.png` (Add staff modal open). The disabled self-deactivate button is visible in the first row of every users capture.

Limitations: Chrome only (no Firefox or Safari); sample data, no live backend.

## 6. Accessibility checks
Computed from getComputedStyle on the rendered User Management page (WCAG contrast ratio, text colour vs its own background colour). Threshold 4.5:1 for normal text.

| Element | Light | Dark |
|---|---|---|
| Table header text | 5.71 | 6.13 |
| Gold primary button (Add staff) | 9.89 | 9.89 |
| Secondary row action button | 6.63 | 6.90 |

All pass. Not measured: title and description over the gradient header band (no single background colour). They were inspected visually in the screenshots (light text on dark navy, readable in both themes). Keyboard focus was exercised with Tab presses and captured; a full screen-reader pass was not done.
