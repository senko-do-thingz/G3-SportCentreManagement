# UI-06 - Redesign Log in to Home theme: test cases

Scope: visual only. Log in uses layout B, chosen by the team after comparing four layouts and live sports sites:
- The Home hero photo (credited, responsive through the UI-03 `Photo` component) fills the page under the same navy overlay as Home.
- A compact Home header sits on top: brand, Back to Home, and the same sun and moon switch as Home.
- The slogan is on the left and the form card floats on the right.
- Below 960 px the photo becomes a band with the slogan and the card follows.
- Every color, font and focus style comes from the UI-00 tokens; there is no local palette.

Styles live in `login.css`, loaded by the Log in page only. `AuthShell` gains `layout="stage"`; Sign up keeps the
split card until UI-07. Not changed: POST /auth/login, token refresh, validation, messages, Remember me, return
navigation. Branch contains UI-00 and UI-03; merge order UI-00 (#121), UI-03, then UI-06.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/styles/login.test.ts` (6 tests), `src/theme/ThemeToggle.test.tsx`, color freeze `src/styles/rawColors.test.ts`, existing `authScreens.test.tsx`, `homeAuthCtas.test.tsx`, `guestReturnMatrix.test.tsx` | Yes (`npm test`) |
| Browser (Playwright, Chromium) | `scripts/ui06-login-check.py`, evidence in `docs/screenshots/ui-06` | No, run locally (moves to CI with UI-04) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Styles isolated to Log in | Unit | No Log in rules in `layouts.css`; `login.css` imported by `LoginPage` | Pass |
| TC-02 | Layout B structure | Unit | `layout="stage"`; Home hero photo with bundled fallback; Back to Home link; shared `ThemeToggle` | Pass |
| TC-03 | No local palette | Unit | No `--login-*` variables, no dark override block | Pass |
| TC-04 | Shared tokens used | Unit | Hero, navy, hero highlight, on-hero, on-hero muted, highlight, display font, focus ring, primary, on-primary | Pass |
| TC-05 | No raw colors | Unit (color freeze) | `login.css` has no color literal; baseline tightened so the 32 color uses removed from `layouts.css` cannot return | Pass |
| TC-06 | Theme switch | Unit | Same sun and moon switch as Home; names the next mode and switches the theme | Pass |
| TC-07 | Shape, alignment, narrow band | Unit | Square fields and button; Remember me row top-aligned; photo becomes a 360 px band at 960 px and below | Pass |
| TC-08 | Existing Log in behavior | Unit (existing suites) | Login, errors, Remember me, return navigation, role guards and theme persistence unchanged | Pass (1400 tests) |
| TC-09 | Text contrast as rendered | Browser: 2 themes x 3 widths x 3 states | Card text, links, button, errors and alerts against their real background; header and slogan against the overlay navy; at least 4.5:1 (3:1 large) | Pass (lowest 4.85:1, server error alert) |
| TC-10 | Remember me and Forgot password aligned | Browser: text baselines | Same baseline | Pass (0 px; base was 13.25 px apart) |
| TC-11 | No horizontal overflow | Browser: 390, 768, 1440 px | Page never wider than the window | Pass |
| TC-12 | Desktop fits one screen | Browser: 1440 x 900, empty and field errors | No vertical scroll | Pass (900 px; card 531 px, 593 px with a server error) |
| TC-13 | Visible keyboard focus | Browser: theme switch, email, Show password, Log in, every width and theme | Each shows a focus ring | Pass |
| TC-14 | Login still works | Browser: valid credentials, mocked API | Leaves `/login` for `/member` | Pass |
| TC-15 | Visual review | Before/after sheets in `docs/screenshots/ui-06` | Matches the Home look in light and dark | For reviewer |
| TC-16 | Real photo and backend | Manual: open `/login` online, log in as member, staff and manager against the dev backend | Sharp Home hero photo; each role lands on its home; wrong password shows the error | To run by reviewer |

Notes for review:
- The browser run had no access to the Unsplash CDN, so the screenshots show the bundled low resolution fallback
  photo (the offline behavior); online the page loads the sharp Home hero photo in responsive sizes.
- Dark mode uses the shared dark primary (`#8aa8ff`, navy text, 7.7:1) for the brand mark and Log in button, like
  every other app screen; Home's brighter dark button (`#3d6cf0`) stays Home-only per the UI-00 contract.

Browser environment: Chromium 1243 (Playwright 1.63), device scale 1, reduced motion, fixed API answers. Builds:
`feat/ui-00` (before) and `feat/ui-06` (after), both with `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
