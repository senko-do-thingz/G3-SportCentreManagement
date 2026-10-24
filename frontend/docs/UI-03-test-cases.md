# UI-03 - Standardize media, credits and performance: test cases

Scope: shared `Photo` component and Unsplash helpers, one sport photo registry for Home and member class pages,
responsive image sizes, lighter and deferred hero video, credits built from the same helpers, unused assets removed.
No API or backend change.

Automated: `frontend/src/media/media.test.tsx` (TC-01 to TC-08), `frontend/src/pages/public/HomeHeroVideo.test.tsx`
(TC-09 to TC-12), existing `HomePage.test.tsx` and `CreditsPage.test.tsx` (TC-13, TC-14).

| ID | Case | Steps | Expected | Type |
|----|------|-------|----------|------|
| TC-01 | Responsive srcset | Build srcset for a 1000 px Unsplash URL | Widths 480, 720, 1000; none for local or other hosts | Automated |
| TC-02 | Sport photo registry | Look up Swimming, Soccer, Yoga | Same Swimming photo as Home; Soccer uses Football; Yoga has none | Automated |
| TC-03 | Photo fallback | Render a remote Photo, fire an image error | Swaps to the bundled file once and drops srcset and sizes | Automated |
| TC-04 | Decorative default | Render Photo without alt | `alt=""`, no srcset for a local file | Automated |
| TC-05 | No unused assets | Scan `src/assets` | Every file is referenced by source code | Automated |
| TC-06 | One Unsplash builder | Scan source for the Unsplash host | Only `src/media/unsplash.ts` contains it | Automated |
| TC-07 | Credits complete | Collect every Unsplash photo id in source | Each id is linked on `/credits` | Automated |
| TC-08 | Alt text | Scan every `<img>` in `.tsx` | Each has an `alt` | Automated |
| TC-09 | Video quality below 1600 px | Wide screen under 1600 px | 720p file is tried first | Automated |
| TC-10 | Video on slow connection | effectiveType 3g on a large screen | 720p file is tried first | Automated |
| TC-11 | Data saver | saveData on | No video, hero photo only | Automated |
| TC-12 | Deferred video | Render before the load event, then fire load | No video until load, then the first clip | Automated |
| TC-13 | Home photos | Open Home, fail one sport photo | Remote photo first, bundled fallback after the error | Automated |
| TC-14 | Credits page | Open `/credits` from the Home footer | Every photo, portrait, video and font listed, links open in a new tab | Automated |
| TC-15 | Mobile download size | DevTools, 375 px wide, Network tab, open Home | Hero and sport photos load the 480 or 720 width, not 2200 or 1000 | Manual |
| TC-16 | Desktop quality | 1920 px wide, open Home | Hero photo loads 2200 or 1600 width, sharp; video plays after the page loads | Manual |
| TC-17 | Member class pages | Sign in as a member, open Find Classes and session cards | Sport photos are sharp (no 87 px stretched images); offline shows the bundled fallback | Manual |
| TC-18 | No layout shift | Reload Home and Find Classes with cache disabled | Images do not push content when they load | Manual |
