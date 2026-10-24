# UI-06 - Redesign Log in to Home theme: test cases

Scope: visual only. The Log in screen takes the approved Home theme from the shared tokens (UI-00): navy hero with
gold highlight, uppercase Be Vietnam Pro display type, italic brand, square card, fields and button, gold rule, royal
blue primary action. Styles move from `layouts.css` to `login.css`, loaded by the Log in page only. The local
`--login-*` palette and its dark block are removed; dark mode now follows the shared dark tokens.
Not changed: POST /auth/login, token refresh, validation, messages, Remember me, return navigation, Sign up (UI-07).
Based on `feat/ui-00` (needs its tokens); merge after PR #121.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/styles/login.test.ts` (5 tests), color freeze `src/styles/rawColors.test.ts`, existing `authScreens.test.tsx`, `homeAuthCtas.test.tsx`, `guestReturnMatrix.test.tsx` | Yes (`npm test`) |
| Browser (Playwright, Chromium) | `scripts/ui06-login-check.py`, evidence in `docs/screenshots/ui-06` | No, run locally (moves to CI with UI-04) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Styles isolated to Log in | Unit | No Log in rules left in `layouts.css`; `login.css` imported by `LoginPage` | Pass |
| TC-02 | No local palette | Unit | No `--login-*` variables, no dark override block | Pass |
| TC-03 | Shared tokens used | Unit | Hero, hero deep, hero highlight, on-hero muted, highlight, display font, focus ring, primary and on-primary come from tokens | Pass |
| TC-04 | No raw colors | Unit (color freeze) | `login.css` has no color literal; baseline tightened so the 32 color uses removed from `layouts.css` cannot return | Pass |
| TC-05 | Display font loaded | Unit and browser | Page imports Be Vietnam Pro 800 and 800 italic; browser reports the font loaded and used by the heading | Pass |
| TC-06 | Shape and alignment rules | Unit | Square card, fields and button; options row top-aligned | Pass |
| TC-07 | Existing Log in behavior | Unit (existing suites) | Login, errors, Remember me, return navigation and role guards unchanged | Pass (1386 tests) |
| TC-08 | Text contrast as rendered | Browser: 2 themes x 3 widths x 3 states | Heading, subtitle, labels, Remember me, Forgot password, footer, link, button text, brand, field errors and alerts at least 4.5:1 (3:1 large) against their real background | Pass (lowest 4.85:1, server error alert, light) |
| TC-09 | Remember me and Forgot password aligned | Browser: text baseline of both | Same baseline | Pass (0 px; base branch was 13.25 px apart) |
| TC-10 | No horizontal overflow | Browser: 390, 768, 1440 px | Page never wider than the window | Pass |
| TC-11 | Fixed desktop card | Browser: 1440 px empty, field errors, server error | Card stays 820 px high in every state | Pass |
| TC-12 | Visible keyboard focus | Browser: email field, Show password, Log in button, light and dark, every width | Each shows a focus ring | Pass |
| TC-13 | Login still works | Browser: valid credentials, mocked API | Leaves `/login` for `/member` | Pass |
| TC-14 | Visual review | Before/after sheets in `docs/screenshots/ui-06` | Matches the Home look in light and dark | For reviewer |
| TC-15 | Real backend | Manual: log in as member, staff and manager against the dev backend | Each lands on its home; wrong password shows the error | To run by reviewer |

Design decision to confirm in review: in dark mode the Log in button uses the shared dark primary (`#8aa8ff` with
navy text, 7.7:1) like every other app screen, not Home's brighter dark button (`#3d6cf0`), which the UI-00
contract keeps Home-only. The side photo is still `auth-hero.jpg` (source not recorded); switching it to a credited
photo needs the UI-03 `Photo` component and is done after UI-03 merges.

Browser environment: Chromium 1243 (Playwright 1.63), device scale 1, reduced motion, fixed API answers. Builds:
`feat/ui-00` (`66f92fa`, before) and `feat/ui-06` (after), both with `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
