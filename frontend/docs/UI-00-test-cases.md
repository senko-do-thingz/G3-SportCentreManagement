# UI-00 - Approved UI design system: test cases

Scope: shared design tokens frozen from the approved Home (Royal and Gold), Home reading them, and a raw color
freeze for the frontend. No screen layout, API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/styles/designTokens.test.ts` (3 tests), `src/styles/rawColors.test.ts` (6 tests) | Yes (`npm test`) |
| Browser (Playwright, Chromium) | `scripts/ui00-visual-check.py`, evidence in `docs/screenshots/ui-00` | No, run locally (moves to CI with UI-04) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Brand surface tokens exist with approved values | Unit: designTokens "defines the Home brand surfaces" | Hero, hero deep, footer, on-hero, on-hero muted, hero highlight (light and dark) and `--font-display` match the contract | Pass |
| TC-02 | Text on hero, footer and hero deep meets WCAG 2.2 AA | Unit: designTokens "keeps text on hero and footer surfaces" | Every pair at least 4.5:1 in light and dark | Pass |
| TC-03 | Home reads its brand values from shared tokens | Unit: designTokens "makes Home read its brand colors" | Each brand `--h-*` in the light block is a `var(--color-...)` or `var(--font-display)` | Pass |
| TC-04 | Scanner finds all color syntaxes | Unit: rawColors scanner tests (3) | Hex, rgb, rgba, hsl, oklch, lab, color(), named colors in CSS; hex and functions in script strings and HTML attributes; comments, url(), anchors, variables and plain words ignored; nested at-rule blocks scanned | Pass |
| TC-05 | Freeze rejects changed, moved, extra and new-file colors | Unit: rawColors "flags a changed value..." (in-memory mutation, no manual edit) | Changed value, swapped property, higher count and a color in a new nested file fail; removals pass | Pass |
| TC-06 | Freeze covers the real project | Unit: rawColors freeze tests (2) | Scope includes nested `src` stylesheets, component `.tsx`, `public/appearance.js`, `index.html`, no test files; no color outside `rawColorBaseline.json` | Pass |
| TC-07 | Home unchanged in light mode | Browser: Home at 390, 768, 1440 px, main vs branch | Screenshots identical pixel for pixel | Pass (3 of 3 identical, 0 changed pixels) |
| TC-08 | Home unchanged in dark mode | Browser: same in dark | Screenshots identical pixel for pixel | Pass (3 of 3 identical) |
| TC-09 | Rendered tokens in the browser | Browser: computed values at each width and theme | Resolved `--h-*` variables, hero and footer backgrounds, hero highlight, Membership offers button, CTA gradient (dark override `#2a50c8`) and heading font match the contract and main | Pass (see `report.json`, `rendered`) |
| TC-10 | Guest auth dialog on Home | Browser: click Membership offers as a guest, light and dark, 390 and 1440 px | Dialog identical to main; buttons keep the royal blue fill with white text | Pass (4 of 4 identical) |
| TC-11 | Log in and Sign up unchanged | Browser: `/login`, `/register`, light and dark, 390 and 1440 px | Identical to main | Pass (8 of 8 identical) |
| TC-12 | Browser check detects a real change | Browser negative control: build with dark `--color-hero` changed by one step (`#060b18` to `#070b18`) | Dark Home screenshots differ and the rendered hero color is reported | Pass (3 dark screens flagged, 18 failures reported) |
| TC-13 | Signed-in screens unchanged | Manual: open Member dashboard, Find Classes, Manager users, Reception payments in light and dark | No visual change. UI-00 adds tokens and changes no existing value (baseline diff of `tokens.css` is additions only) | To run by reviewer |
| TC-14 | Print receipt stays light | Manual: print a payment receipt in dark mode | Receipt prints in light colors; new dark tokens are inside `@media screen` | To run by reviewer |

Browser environment for TC-07 to TC-12: Chromium 1243 (Playwright 1.63), device scale 1, reduced motion on, fixed
API fixtures, remote photos and videos blocked so both builds show the same bundled images. Builds: `main`
`d20833c` and `feat/ui-00`, both with `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
