# UI-00 - Approved UI design system: test cases

Scope: shared design tokens frozen from the approved Home (Royal and Gold), Home reading them, and a raw color
freeze for the frontend. No screen layout, API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/styles/designTokens.test.ts` (3 tests), `src/styles/rawColors.test.ts` (11 tests), `src/styles/visualCheckRunner.test.ts` (2 tests) | Yes (`npm test`) |
| Browser (Playwright, Chromium) | `scripts/ui00-visual-check.py`, evidence in `docs/screenshots/ui-00` | No, run locally (moves to CI with UI-04); steps below |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Brand surface tokens exist with approved values | Unit: designTokens "defines the Home brand surfaces" | Hero, hero deep, footer, on-hero, on-hero muted, hero highlight (light and dark) and `--font-display` match the contract | Pass |
| TC-02 | Text on hero, footer and hero deep meets WCAG 2.2 AA | Unit: designTokens "keeps text on hero and footer surfaces" | Every pair at least 4.5:1 in light and dark | Pass |
| TC-03 | Home reads its brand values from shared tokens | Unit: designTokens "makes Home read its brand colors" | Each brand `--h-*` in the light block is a `var(--color-...)` or `var(--font-display)` | Pass |
| TC-04 | Scanner finds all color syntaxes | Unit: rawColors scanner tests (3) | Hex, rgb, rgba, hsl, oklch, lab, color(), named colors in CSS keyed by enclosing rules and property; hex and functions in script strings and HTML attributes; comments, url(), variables and plain words ignored; nested at-rule blocks scanned | Pass |
| TC-05 | Freeze rejects changed, moved, extra and new-file colors | Unit: rawColors "flags a changed value..." (in-memory mutation, no manual edit) | Changed value, swapped property, higher count and a color in a new nested file fail; removals pass | Pass |
| TC-15 | Freeze rejects colors swapped between selectors | Unit: rawColors "flags colors swapped between two selectors..." | `.hero` and `.footer` exchanging `#fff` and `#000` fails although every property and value count is unchanged; moving a rule into `@media` fails; reformatting whitespace or hex case passes | Pass |
| TC-16 | Named colors in TSX and JS inline styles are scanned | Unit: rawColors "finds named colors in inline styles...", "finds named colors in conditional style values..." and "keeps named color words..." | Named colors in `style={{ color: 'white' }}`, `backgroundColor`, quoted kebab keys, `style="color: tomato"`, SVG `fill`/`stroke`, `textColor`/`bgColor` props, `style.color =`, `setProperty('--x', 'orange')`, CSS template strings and either branch of `?:`, `&&`, `\|\|`, `??` are found; `<style>` blocks in HTML are scanned as stylesheets; the same words in labels, titles, other props or prose are not | Pass |
| TC-17 | Hex-like link targets are not colors | Unit: rawColors "ignores hex-like link targets..." | `#abc` in `href`, `to`, `htmlFor`, `id`, `querySelector`, `closest`, `navigate`, `/classes#face` and `?tab=#beef` is ignored; the same text as a value or `theme-color` is still found | Pass |
| TC-18 | Browser runner is reproducible from the PR revision | Unit: visualCheckRunner (2) | Runner and pinned `requirements-visual-check.txt` are committed and not git-ignored; runner and this file build the merge base and serve with `vite preview` (not a static server that 404s on `/login`) | Pass |
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

## Reproduce the browser evidence (TC-07 to TC-12)

PowerShell, from `frontend/` with the PR revision checked out. The base build is the merge base with `main`, so the
comparison does not pick up later `main` commits. Python packages are pinned in `scripts/requirements-visual-check.txt`.

```powershell
python -m pip install -r scripts/requirements-visual-check.txt; python -m playwright install chromium
git fetch origin; git worktree add ../../ui00-base (git merge-base HEAD origin/main)
$env:VITE_API_BASE_URL='http://localhost:8080/api/v1'
npm ci; npm run build -- --outDir ../../dist-after
Push-Location ../../ui00-base/frontend; npm ci; npm run build -- --outDir ../../dist-before; Pop-Location
# Two other windows (vite preview answers /login and /register with index.html; a plain static server returns 404):
npx vite preview --outDir ../../dist-before --port 4301 --strictPort
npx vite preview --outDir ../../dist-after --port 4302 --strictPort
python scripts/ui00-visual-check.py --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-00
git worktree remove ../../ui00-base
```

Exit code 0 and `report.json` with `"failures": []` mean every pair is identical and every rendered value matches.

## Process checks before merge

- Required CI checks green at the latest PR head (`gh pr checks 121`).
- Design approval of the frozen Royal and Gold values recorded on the PR by the design owner.
- The AI Audit Log for UI-00 is kept locally by the author and is not committed; review notes stay on the PR.
- Merge only after explicit CODEOWNER approval.
