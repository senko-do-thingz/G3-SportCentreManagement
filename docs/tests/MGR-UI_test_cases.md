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

## 5. Manual visual matrix (to be completed with real screenshots)
Capture with `npm run dev:preview`. Store images under docs/tests/evidence/mgr-ui/ and link them here.

| Screen | 390px light | 390px dark | 768px light | 768px dark | 1440px light | 1440px dark |
|---|---|---|---|---|---|---|
| User Management, before (main) | | | | | | |
| User Management, after | | | | | | |
| One unchanged screen per Manager page, main vs branch | | | | | | |

Interaction states to capture on User Management: default, row hover, button hover, keyboard focus, disabled self-deactivate button, add-staff modal open.

## 6. Accessibility checks (to be completed)
- Contrast of header title and description on the gradient band in light and dark.
- Contrast of gold primary button text (on-accent over accent).
- Contrast of table header text over primary-soft background in dark mode.
- Keyboard-only walk through filters, table actions and pagination.
Record tool used and measured ratios here.
