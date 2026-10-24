# UI-03 - Standardize media, credits and performance: test cases

Scope: shared `Photo` component and Unsplash helpers, one sport photo registry for Home and member class pages,
responsive image sizes, lighter and deferred hero video, credits built from the same helpers, unused assets removed.
No API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/media/media.test.tsx` (12 tests), `src/pages/public/HomeHeroVideo.test.tsx` (16 tests), existing `HomePage.test.tsx` and `CreditsPage.test.tsx` | Yes (`npm test`) |
| Browser, placeholder photos (Playwright, Chromium) | `scripts/ui03-media-check.py`, evidence in `docs/screenshots/ui-03` | No, run locally (moves to CI with UI-04) |
| Browser, real photos, dark theme, layout shift (Chrome DevTools Protocol, Node) | `scripts/ui03-real-media-check.mjs`, evidence in `docs/screenshots/ui-03/real` | No, run locally |
| Reviewer sign-off | Steps under "Acceptance sign-off" | No |

"Pass" below means the automated check passed on the author's machine with the recorded evidence. It is not the
reviewer's visual acceptance, which is listed separately at the end.

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Responsive srcset | Unit | Widths 480, 720, 1000 for a 1000 px photo; none for local files or other hosts | Pass |
| TC-02 | Sport photo registry | Unit | Same Swimming photo as Home; Soccer uses Football; unknown sport has none | Pass |
| TC-03 | Photo fallback | Unit | One image error swaps to the bundled file and drops srcset and sizes | Pass |
| TC-04 | Decorative default | Unit | `alt=""` by default; no srcset for a local file | Pass |
| TC-05 | No unused assets | Unit | Every file in `src/assets` is referenced | Pass |
| TC-06 | One Unsplash builder | Unit | Only `src/media/unsplash.ts` contains the Unsplash host | Pass |
| TC-07 | Credits complete | Unit | Every Unsplash photo id in source is linked on `/credits` | Pass |
| TC-08 | Alt text | Unit | Every `<img>` in `.tsx` has `alt` | Pass |
| TC-09 | Video quality and data saver | Unit (9 tests) | Full HD first only at 1600 px and up with `effectiveType` `4g`; 720p first below 1600 px, with no Network Information API, without `effectiveType`, with an unknown type, on `3g` and `2g`; no video with data saver on 4g or an unknown network | Pass |
| TC-10 | Video deferral | Unit | No video before the load event, first clip after it | Pass |
| TC-11 | Home requested photo pixel area | Browser (placeholders): sum of requested width x height, main vs branch | Never more than main | Pass: 390 px 14.67 to 2.15 MP (-85%), 768 px 14.67 to 5.31 MP, 1440 px 14.67 to 5.64 MP (-62%), 1920 px 14.67 to 8.12 MP, 390 px at 3x 14.67 to 11.41 MP. This is requested pixel area, not transferred bytes; see TC-22 |
| TC-12 | Home photos sharp | Browser: file width against rendered slot x pixel ratio | No upscaled photo at any viewport, including a 3x phone | Pass (0 upscaled at all 5 viewports) |
| TC-13 | Find Classes photos sharp | Browser: member session, Find Classes at 1440 px and 390 px 3x | No upscaled image (main: 15 of 15 upscaled, 87 px sources) | Pass (0 of 15 upscaled at both). Found and fixed during this check: session cards used 720 px, too small on a 3x phone; now 1080 |
| TC-14 | Hero video file and timing | Browser: first video request at 390, 1440 and 1920 px | None on 390; 720p first at 1440; 1080p first at 1920 on 4g; requested after the load event | Pass. Note: main also requested after load in this fast local run; deferral is proven by TC-10 and TC-24 |
| TC-15 | Offline fallbacks | Browser: Unsplash blocked, Home and Find Classes | All 15 images on each page render from bundled files | Pass |
| TC-16 | No layout change, light | Browser (placeholders): Home at 390, 768, 1440 px and Photo credits at 390, 1440 px, main vs branch | Screenshots identical pixel for pixel | Pass (5 of 5 identical) |
| TC-17 | Real photos on the real network | Browser (real Unsplash photos): Home and Find Classes with session cards, light and dark, 390, 768, 1440 px and a 390 px 3x phone | Every image renders; none upscaled; screenshots saved for crop review | Pass (16 of 16 screens: Home 14 or 15 images, Find Classes 15; 0 broken, 0 upscaled). Screens in `real/real-*.jpg` |
| TC-18 | No layout shift | Browser: cumulative layout shift on load and after scrolling through every lazy image, same 16 screens | CLS at most 0.1 (Core Web Vitals "good") and no shift caused by an image | Pass. Home 0 everywhere. Find Classes 0, except 0.0232 at 768 px (light and dark) from the level picker and filter fields, not from an image |
| TC-19 | No layout shift on a slow network or without the photo CDN | Browser: Home and Find Classes at 390 and 1440 px with 150 ms latency and 1.6 Mbit/s, and with Unsplash blocked | CLS at most 0.1; fallbacks render | Pass (8 of 8). Largest 0.0272 on Home 1440 slow, from text when the web font swaps; 0 with the CDN blocked on Home |
| TC-20 | No layout change, dark | Browser (placeholders, reduced motion): Home at 390, 768, 1440 px and Photo credits at 390, 1440 px in dark, merge base vs branch | Screenshots identical pixel for pixel; `data-theme="dark"` | Pass (5 of 5 identical; light re-run also 5 of 5). Sheets in `real/parity-*-before-after.jpg` |
| TC-21 | Photo source change on the same element | Unit: render A, fail it, rerender the same Photo with B | B is requested with its own srcset and sizes; B failing shows B's fallback; going back to A requests A again | Pass (fails on the previous Photo) |
| TC-22 | Image bytes transferred and LCP | Browser (real network, cache disabled): Home in light, merge base vs branch, median of 3 runs | Branch transfers no more image bytes than main | Pass: 390 px 2258 to 414 KiB (-82%), 768 px 2258 to 905 KiB (-60%), 1440 px 2258 to 1030 KiB (-54%), 390 px 3x 2258 to 1847 KiB (-18%). LCP on localhost without throttling stays within run-to-run noise (about 230 to 570 ms on both builds), so no LCP gain is claimed |
| TC-23 | Fallback does not loop and keeps the caller's handler | Unit: three image errors with a missing fallback and an `onError` prop | Stays on the fallback without srcset; `onError` called each time | Pass (fails on the previous Photo) |
| TC-24 | Load event before the listener | Unit: `readyState` is `interactive` on first render and `complete` when the effect runs, no further load event | Video still mounts | Pass (fails on the previous hook) |
| TC-25 | Runners reproducible from the PR revision | Unit: `media.test.tsx` "UI-03 browser check runners" | Both runners committed and not git-ignored; Python packages pinned; Node runner imports only `node:` modules; steps below build the merge base and serve with `vite preview` | Pass |

Browser environment for TC-11 to TC-16: Chromium 1243 (Playwright 1.63), light theme, fixed API fixtures, remote
photos answered with solid placeholder JPEGs of the requested size (the colored blocks in the screenshots), videos
recorded then blocked. Raw numbers in `docs/screenshots/ui-03/report.json`.

Browser environment for TC-17 to TC-20 and TC-22: Chrome 156 (headless, DevTools Protocol), fixed API fixtures,
hero video CDN blocked, real Unsplash photos over the author's network for TC-17 to TC-19 and TC-22, SVG placeholders
of the requested size for TC-20. Raw numbers in `docs/screenshots/ui-03/real/report.json`.

Builds for every browser case: merge base `d20833c` (the `main` this branch starts from) and `feat/ui-03`, both with
`VITE_API_BASE_URL=http://localhost:8080/api/v1`.

## Reproduce the browser evidence

PowerShell, from `frontend/` with the PR revision checked out. Python packages are pinned in
`scripts/requirements-visual-check.txt`; the Node runner needs only Node 22 and Chrome or Edge (`CHROME_PATH` when
not installed in the default place).

```powershell
git fetch origin; git worktree add ../../ui03-base (git merge-base HEAD origin/main)
$env:VITE_API_BASE_URL='http://localhost:8080/api/v1'
npm ci; npm run build -- --outDir ../../dist-after
Push-Location ../../ui03-base/frontend; npm ci; npm run build -- --outDir ../../dist-before; Pop-Location
# Two other windows (vite preview answers /member/classes and /credits with index.html; a static server returns 404):
npx vite preview --outDir ../../dist-before --port 4301 --strictPort
npx vite preview --outDir ../../dist-after --port 4302 --strictPort
# Placeholder photos, light (TC-11 to TC-16):
python -m pip install -r scripts/requirements-visual-check.txt; python -m playwright install chromium
python scripts/ui03-media-check.py --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-03
# Real photos, dark theme, layout shift, bytes (TC-17 to TC-20, TC-22):
node scripts/ui03-real-media-check.mjs --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-03/real
git worktree remove ../../ui03-base
```

Each runner exits with 0 and writes `report.json` with `"failures": []` when every check passes. Real-network numbers
(bytes, LCP) vary slightly with the network and the photo CDN.

## Acceptance sign-off (reviewer)

| Item | Evidence to look at | Status |
|------|---------------------|--------|
| Real photo crops and sharpness on Home and session cards | `real/real-home-*.jpg`, `real/real-find-classes-*.jpg` | Waiting for reviewer |
| Dark theme parity of Home and Photo credits | `real/parity-*-dark-*-before-after.jpg` | Waiting for reviewer |
| Media provenance of bundled files (see `media-guidelines.md`, "Provenance") | Owner decision | Waiting for owner decision |
