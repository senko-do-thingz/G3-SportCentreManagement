# UI-03 - Standardize media, credits and performance: test cases

Scope: shared `Photo` component and Unsplash helpers, one sport photo registry for Home and member class pages,
responsive image sizes, lighter and deferred hero video, credits built from the same helpers, unused assets removed.
No API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/media/media.test.tsx` (8 tests), `src/pages/public/HomeHeroVideo.test.tsx` (4 new tests), existing `HomePage.test.tsx` and `CreditsPage.test.tsx` | Yes (`npm test`) |
| Browser (Playwright, Chromium) | `scripts/ui03-media-check.py`, evidence in `docs/screenshots/ui-03` | No, run locally (moves to CI with UI-04) |
| Manual | Steps below | No |

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
| TC-09 | Video quality and data saver | Unit (3 tests) | 720p first below 1600 px or on a slow connection; Full HD first at 1600 px and up on 4g; no video with data saver | Pass |
| TC-10 | Video deferral | Unit | No video before the load event, first clip after it | Pass |
| TC-11 | Home photo download size | Browser: total requested photo area, main vs branch | Never more than main | Pass: 390 px 14.67 to 2.15 MP (-85%), 768 px 14.67 to 5.31 MP, 1440 px 14.67 to 5.64 MP (-62%), 1920 px 14.67 to 8.12 MP, 390 px at 3x 14.67 to 11.41 MP |
| TC-12 | Home photos sharp | Browser: file width against rendered slot x pixel ratio | No upscaled photo at any viewport, including a 3x phone | Pass (0 upscaled at all 5 viewports) |
| TC-13 | Find Classes photos sharp | Browser: member session, Find Classes at 1440 px and 390 px 3x | No upscaled image (main: 15 of 15 upscaled, 87 px sources) | Pass (0 of 15 upscaled at both). Found and fixed during this check: session cards used 720 px, too small on a 3x phone; now 1080 |
| TC-14 | Hero video file and timing | Browser: first video request at 390, 1440 and 1920 px | None on 390; 720p first at 1440; 1080p first at 1920; requested after the load event | Pass. Note: main also requested after load in this fast local run; deferral is proven by TC-10 |
| TC-15 | Offline fallbacks | Browser: Unsplash blocked, Home and Find Classes | All 15 images on each page render from bundled files | Pass |
| TC-16 | No layout change | Browser: Home at 390, 768, 1440 px and Photo credits at 390, 1440 px, main vs branch | Screenshots identical pixel for pixel | Pass (5 of 5 identical) |
| TC-17 | Real photos on a real network | Manual: DevTools Network, 375 px and 1920 px, open Home and Find Classes | Photos look sharp; phone requests 480 or 720 widths | To run by reviewer |

Browser environment for TC-11 to TC-16: Chromium 1243 (Playwright 1.63), light theme, fixed API fixtures, remote
photos answered with solid placeholder JPEGs of the requested size (the colored blocks in the screenshots), videos
recorded then blocked. Builds: `main` `d20833c` and `feat/ui-03`, both with
`VITE_API_BASE_URL=http://localhost:8080/api/v1`. Raw numbers in `docs/screenshots/ui-03/report.json`.
