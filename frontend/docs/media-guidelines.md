# Media, credits and performance rules (UI-03)

Owner: UI-03 (Phat). Applies to every screen task that shows a photo, video or icon image.

## Where media lives

| Kind | Location | Notes |
|------|----------|-------|
| Remote photos | Unsplash CDN, built only by `src/media/unsplash.ts` | Responsive sizes, credited on `/credits` |
| Sport photos | `src/media/sportPhotos.ts` (one photo per sport) | Shared by Home and the member class pages |
| Offline fallbacks | `src/assets/home/*.jpg` | Low resolution copies shown only when a remote photo fails |
| Log in and Sign up background | Home hero photo (`HOME_PHOTOS.hero`) through `Photo` in `AuthShell` | Since UI-06 and UI-07; credited on `/credits` |
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
   or data saver, and tries the 720p file first unless the screen is at least 1600 px wide on a fast connection.

## Known gaps (tracked, not blocking)

- The bundled fallbacks were cut from the team design screenshots; the original photo source is not recorded, so
  they are not listed on `/credits`. They show only when the CDN fails. The unsourced `auth-hero.jpg` was removed in
  UI-07 when Sign up moved to the credited Home hero photo.
- Videos stream from the Mixkit CDN; there is no bundled video fallback, the hero photo stays visible instead.

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
