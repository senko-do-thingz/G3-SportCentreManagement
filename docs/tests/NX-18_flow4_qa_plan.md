# NX-18 - Flow 4 frontend cross-role QA plan (framework)

Task: "Run Flow 4 frontend regression and accessibility checks" (F4-REQ-01..07, owner Truc, with Hung and Phat). Depends on NX-11, NX-12, NX-13 (Hung, Coach plan editor, outcomes and feedback, attendance UI), NX-14 and NX-15. Status of the proposal: requires team sign-off.

## Current status
| Area | Screens | Status |
|---|---|---|
| Member | /member/training, /member/history | Built, QA executed on synthetic data (see NX-14-NX-15_test_cases.md) |
| Coach | NX-11, NX-12, NX-13 screens | NOT in the repository yet, QA not run |
| Reception | Check-in (existing FE-05 screen) and NX-13 receptionist check-in UI | Existing check-in only; NX-13 part not in the repository |
| FE/BE contract | docs/nx-03 | Unsigned draft; no backend endpoints; contract compatibility cannot be verified |

The final NX-18 deliverable (screenshots, E2E evidence, zero blocking regressions) can only be completed after the missing screens and the backend exist. This file is the checklist and the tooling to run then.

## Checklist per screen and role
Run each item in Light and Dark, at 390px, 768px and 1440px.
1. Role access: the right role sees the screen and menu entry; other roles get the access-denied or placeholder page; a signed-out visitor goes to login.
2. States: loading, empty, error with Try again, 403, malformed answer, slow network.
3. Privacy: no other member's data in any response or on screen; members cannot see edit controls; staff-only notes never shown to members.
4. Keyboard: Tab order follows the visual order; every control reachable; visible focus; tabs use arrow keys; modals trap focus and close with Escape.
5. Screen reader: headings in order, list and table semantics, status badges readable as text, errors announced (role alert).
6. Layout: no horizontal page scroll; touch targets at least 44px on mobile; long names wrap.
7. Time: dates and times follow the same wording as My Bookings and use the center's local clock.
8. Regression: other areas unchanged (compare screenshots against main).
9. Contract: each request path, method, status code and field matches the signed contract; unknown fields are ignored.

## Tooling
docs/tests/qa-tools/member-screens.mjs captures the Member screens in manager-preview mode (Chrome headless). It needs `npm install --no-save puppeteer-core` outside the project dependencies and a running `vite --mode manager-preview`. The same pattern (request interception with mocked API answers) was used for the Reception screens. Extend `pages` and the login step for Coach screens when they exist.

## Sign-off table (to fill when all screens exist)
| Role | Screens | Light | Dark | Keyboard | Mobile | Error paths | Contract | Tester | Date |
|---|---|---|---|---|---|---|---|---|---|
| Member | training, history | done (synthetic) | done | tabs only | done | done | blocked | | |
| Coach | | | | | | | | | |
| Reception | | | | | | | | | |
