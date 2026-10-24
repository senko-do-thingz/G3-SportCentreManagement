# Media, credits and performance rules (UI-03)

Owner: UI-03 (Phat). Applies to every screen task that shows a photo, video or icon image.

## Where media lives

| Kind | Location | Notes |
|------|----------|-------|
| Remote photos | Unsplash CDN, built only by `src/media/unsplash.ts` | Responsive sizes, credited on `/credits` |
| Sport photos | `src/media/sportPhotos.ts` (one photo per sport) | Shared by Home and the member class pages |
| Offline fallbacks | `src/assets/home/*.jpg` | Small copies of the credited Unsplash photos (sports 240 x 160, hero 640 x 427), shown only when a remote photo fails |
| Log in and Sign up background | Unsplash dunk photo by August Phlieger (`AUTH_PHOTO_ID` in `src/media/sportPhotos.ts`) through `Photo` in `AuthShell`, with a small copy in `src/assets/auth/auth-dunk.jpg` as the offline fallback | Player on the left; credited on `/credits` |
| Demo coach portraits | `public/images/coaches/*.jpg` (640 x 640) | Committed files, credited on `/credits` |
| Hero background video | Mixkit CDN (`HOME_HERO_VIDEOS` in `homeContent.ts`) | Credited on `/credits` |
| Display font | `@fontsource/be-vietnam-pro` | Imported only by the pages that use it |

## Rules

1. Show photos with `Photo` from `src/media/Photo.tsx`, not a bare `<img>`. It adds a responsive `srcset` for
   Unsplash photos, decodes off the main thread and swaps once to the bundled fallback (dropping the `srcset`)
   when the CDN fails, so the page still works offline.
2. Always pass `sizes` that match the rendered slot, plus `width` and `height` for the aspect ratio
   (no layout shift). Use `loading="lazy"` below the fold and `fetchPriority="high"` only for the hero photo.
3. Build Unsplash URLs only with `unsplashUrl`, `unsplashThumb` or `unsplashOriginal`. A new remote photo must
   also appear in `buildCredits` (`src/pages/public/creditsContent.ts`); a test fails otherwise.
4. Every `<img>` has an `alt`: a description for meaningful photos, `alt=""` for decoration.
5. A file in `src/assets` that no source file references is removed; a test fails otherwise.
6. The hero video mounts after the page load event, only on screens at least 768 px wide without reduced motion
   or data saver, and tries the 720p file first unless the screen is at least 1600 px wide and the browser reports
   `effectiveType` `4g`. A missing Network Information API (Safari, Firefox) or an unknown type counts as not fast.
7. A `Photo` whose `src` changes requests the new photo even if the previous one failed; the fallback is used only
   for the `src` that failed, never retried in a loop, and an `onError` passed by the caller still runs.

## Provenance

| Files | Source | Licence | Owner decision (UI-03) |
|-------|--------|---------|------------------------|
| Remote photos (`SPORT_PHOTO_IDS`, Home content) | Unsplash, ids in source | Unsplash License, credited on `/credits` (a test enforces it) | Keep |
| Hero videos (`HOME_HERO_VIDEOS`) | Mixkit CDN | Mixkit License (free video), credited on `/credits` | Keep |
| `public/images/coaches/*.jpg` | Committed demo portraits | Credited on `/credits` | Keep |
| `src/assets/home/*.jpg` (offline fallbacks) | Replaced with small copies of the credited Unsplash photos they stand in for: each sport file is its `SPORT_PHOTO_IDS` photo, `hero.jpg` is the `HOME_PHOTOS.hero` photo | Unsplash License, the same photos credited on `/credits` | Replaced; the old copies cut from the design screenshots are gone |
| `src/assets/auth-hero.jpg` | Cut from the team design screenshots; original photo not recorded | Unknown | Deleted in UI-07 |
| Log in and Sign up background (`AUTH_PHOTO_ID`, fallback `src/assets/auth/auth-dunk.jpg`) | Unsplash, photo by August Phlieger (unsplash.com/photos/CREqtqgBFcU) | Unsplash License, credited on `/credits` | Keep |

Videos have no bundled fallback; the hero photo stays visible instead.

## Performance numbers

Report image savings by name: "requested pixel area" (sum of requested width x height, from
`ui03-media-check.py`) is a proxy, while "image bytes transferred" (`encodedDataLength`, cache disabled, from
`ui03-real-media-check.mjs`) is the data actually downloaded. LCP measured on localhost without throttling is
within run-to-run noise and is not used as a claim.

## Pick the largest width for the biggest slot

The width passed to `unsplashUrl` or `sportPhotoFor` is the largest file the browser may choose, so it must cover
the slot on a 3x phone: slot width in CSS px x 3, capped where the photo is already sharp enough (about 75%).
Session cards (`SessionCard`, full width on phones) use 1080; sport tiles and small art use 480.

## Checks

- Unit: `src/media/media.test.tsx` (helpers, fallback, assets, credits, alt) and
  `src/pages/public/HomeHeroVideo.test.tsx` (video quality, data saver, deferral).
- Browser: `scripts/ui03-media-check.py` (Playwright, Chromium) runs main and the branch with placeholder photos
  of the requested size and records requested widths, upscaled or broken images, the first video file and its
  timing, offline fallbacks, and before/after screenshots. Evidence for UI-03 is in `docs/screenshots/ui-03`;
  the solid colored blocks in the screenshots are the placeholders that stand in for photos.
- Browser: `scripts/ui03-real-media-check.mjs` (Node and Chrome, no packages) adds the real photos over the real
  network in light and dark, layout shift on load, after scrolling, on a slow network and without the photo CDN,
  dark-theme before/after parity, and the image bytes transferred. Evidence in `docs/screenshots/ui-03/real`.
- Steps to reproduce both from the PR revision: `docs/UI-03-test-cases.md`.
