# UI-07 - Redesign Sign up to Home theme: test cases

Scope: visual only. Sign up moves to the same layout B as Log in (UI-06), so the two screens are a matching pair:
- Home hero photo under the Home overlay, compact Home header (brand, Back to Home, sun and moon switch).
- Slogan on the left, form card on the right; the Sign up card is 560 px wide for its two-field rows and password rules.
- Below 960 px the photo becomes a band with the slogan above the card.

Structure:
- `AuthShell` now has a single layout, and loads `src/styles/auth.css` (renamed from `login.css`) and the display fonts itself, so Log in and Sign up cannot drift apart.
- The old split card is removed: `.auth-shell`, `.auth-hero` and `.auth-panel__toolbar` rules in `layouts.css` and `appearance.css`, and the unsourced `src/assets/auth-hero.jpg`.

Not changed: POST /auth/register, automatic sign in after sign up, validation, password rules, terms, messages,
return navigation.

Branch: based on `feat/ui-06` (which contains UI-00 and UI-03). Merge order: UI-00, UI-03, UI-06, then UI-07.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/styles/auth.test.ts` (6 tests, Log in and Sign up), color freeze `src/styles/rawColors.test.ts`, media rules `src/media/media.test.tsx`, existing `authScreens.test.tsx`, `homeAuthCtas.test.tsx`, `homeGuestAuthFlow.test.tsx` | Yes (`npm test`) |
| Browser (Playwright, Chromium) | `scripts/ui07-signup-check.py` (Sign up), `scripts/ui06-login-check.py` (Log in), evidence in `docs/screenshots/ui-07` | No, run locally (moves to CI with UI-04) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | One layout for both screens | Unit | `AuthShell` imports `auth.css` and the fonts; Log in and Sign up pass their screen class | Pass |
| TC-02 | Home photo, header and switch | Unit | Home hero photo with fallback, Back to Home, shared `ThemeToggle`, no Appearance select | Pass |
| TC-03 | Old split card retired | Unit | No `.auth-shell`, `.auth-hero` or toolbar rules; `auth-hero.jpg` deleted; no unused asset left | Pass |
| TC-04 | No local palette, shared tokens | Unit | No `--login-*` or dark block; hero, navy, highlight, display font, focus ring and primary tokens used | Pass |
| TC-05 | Shapes and widths | Unit | Square fields, button and password rules; Sign up card 560 px; photo band at 960 px | Pass |
| TC-06 | No raw colors | Unit (color freeze) | `auth.css` has none; baseline tightened after the split card colors left `layouts.css` | Pass |
| TC-07 | Existing Sign up and Log in behavior | Unit (existing suites) | Registration, validation, duplicate email, automatic sign in, theme persistence and return navigation unchanged | Pass (1400 tests) |
| TC-08 | Text contrast as rendered | Browser: 2 themes x 3 widths x 3 states (empty, field errors, email already registered) | Card text, rules, links, button, errors and alerts against their real background; header and slogan against the overlay navy; at least 4.5:1 (3:1 large) | Pass (lowest 4.96:1, password rules) |
| TC-09 | No horizontal overflow, card below header | Browser: 390, 768, 1440 px | Page never wider than the window; card starts below the header | Pass |
| TC-10 | Visible keyboard focus | Browser: theme switch, full name, Show password, terms checkbox, Create account | Each shows a focus ring | Pass |
| TC-11 | Sign up still works | Browser: valid form, mocked API | Leaves `/register` for `/member` | Pass |
| TC-12 | Log in unchanged by the shared refactor | Browser: `scripts/ui06-login-check.py` on this build | Same results as UI-06 | Pass (0 failures, lowest 4.85:1, login reaches /member) |
| TC-13 | Visual review | Before/after sheets in `docs/screenshots/ui-07` | Sign up matches Log in and the Home look in light and dark | For reviewer |
| TC-14 | Real photo and backend | Manual: open `/register` online and create an account against the dev backend | Sharp Home hero photo; account created and signed in; duplicate email shows the error | To run by reviewer |

Notes for review:
- The browser run had no access to the Unsplash CDN, so the screenshots show the bundled low resolution fallback photo; online the page loads the sharp Home hero photo.
- On a 1440 x 900 screen the Sign up page is 940 px tall (the form is long), so it scrolls slightly; Log in still fits one screen.

Browser environment: Chromium 1243 (Playwright 1.63), device scale 1, reduced motion, fixed API answers. Builds:
`feat/ui-06` (before) and `feat/ui-07` (after), both with `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
