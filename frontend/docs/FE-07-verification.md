# FE-07 verification

Date: 2026-10-16. Branch: `feat/f2-manager-screens`, based on the FE-06 branch at `a6b6f0e` so its unmerged features are retained. Implementation changes are frontend-only, with documentation. No backend, migrations or database seeds were changed.

## Implemented

- F2-01: server-paged class table; debounced search; combined sport, level, coach and active filters; edit, schedule and confirmed activation/deactivation actions.
- F2-02: create/edit with age group, level, capacity and sport-qualified coach selection; optional coach qualification panel; save and schedule navigation. Sport is locked when upcoming sessions exist.
- F2-03: URL-driven Monday-based weekly calendar and filters; single/weekly creation; session edit; reason-required cancellation; booked-capacity minimum and future-session action guards.
- S2-Conflict: stacked dialog lists every valid conflict supplied by the backend, including exact session IDs, dates, times, resources and conflict types. The editor draft remains intact. Generic 409 errors remain normal errors.
- All four routes reuse the manager role guard, authenticated client, FE-06 form handling and shared modal stack. FE-06 screens and preview remain in place.

## API inventory

All paths are relative to the existing API base URL.

| Classification | Calls |
| --- | --- |
| Listed | GET/POST `/manager/classes`; GET/PUT `/manager/classes/{id}`; PATCH `/manager/classes/{id}/status` |
| Listed | GET `/age-groups`; GET `/coaches?sportId=`; GET `/manager/sessions`; POST `/manager/classes/{id}/sessions`; PUT `/manager/sessions/{id}`; PUT `/manager/sessions/{id}/cancel` |
| Implied/reused | GET `/sports`; GET `/coaches/{id}` for qualification details |
| Extra, missing on main | GET `/facilities` for the facility selector |

The missing facility lookup is an integration dependency, not implemented in this frontend task. A 404 shows an explicit notice and retry and enables manual entry of a known facility ID in create/edit. Existing session facility IDs are prefilled on edit. Positive integer validation runs locally; the scheduling API remains authoritative for existence, active state and conflicts. A 403 or server error does not trigger this fallback. No guessed facility IDs or fixture IDs are substituted. Once the backend owner exposes this contract, confirm its response shape against `facilitiesApi.ts`.

The class editor now loads all active coaches before sport selection, filters them after sport selection, explains qualification filtering and offers Refresh coaches for newly created accounts. Changing sport clears the previous coach selection.

Unlike the initial plan's separate facility mock flag, facilities are mocked only inside the existing fully isolated manager preview. This avoids combining fixture IDs with live writes. No automatic fallback to fixtures exists.

## Run for review

Live frontend: `npm run dev -- --host localhost --port 5173` (requires the backend and a real manager account).

Isolated frontend preview: `npm run dev:preview -- --host localhost --port 5174`.
Open `http://localhost:5174/login`, sign in with `manager@preview.sportify.local` / `preview`, then open Classes or Schedule and coaches. Preview mutations live only in module memory and reset on page reload. These are not backend-seeded accounts or records.

Preview-only data and simulated writes are in `src/api/mock/managerSchedulePreview.ts`, reached through the existing DEV + explicit preview mode + preview-token boundary. Production builds exclude the preview. `?previewState=empty`, `?previewState=error` and `?previewState=slow` exercise existing preview scenarios; preserve that parameter while navigating if needed.

## Automated evidence

Commands: `npm run lint`, `npm run test`, `npm run build`.

- Final follow-up regression run passed 619 tests across 34 test files, including the existing FE-06 suite. Follow-up coverage adds coach refresh and 404-only facility fallback behavior, including rejection of 403/500 fallback. Lint and production build also pass.
- Focused follow-up tests cover all four routes for anonymous/member access, weekly wire fields without draft status, edit capacity limits, missing facility integration, conflict draft preservation and atomic preview writes.
- Production build passes with the existing non-blocking bundle-size warning (main chunk above 500 kB).
- Date-model tests use explicit UTC instants and Vietnam formatting, date-only UTC calendar arithmetic, inclusive weekly range counts and a 100-session limit.

Dependencies were already installed; no package or lockfile changes were needed.

## Manual acceptance checklist (pending visual/browser verification)

Browser automation could not initialize because the local process-security launcher failed. Do not treat the following as completed browser checks.

1. Login in preview; create a Basketball class with Daniel Smith, then Save & schedule. Choose a facility, Monday repetition over four inclusive Mondays, and future times. Save and navigate across the four weeks.
2. Create an overlapping session with the same coach/facility. Confirm the dialog identifies the exact existing session; close only the conflict dialog, change time and save.
3. Edit a future session; attempt capacity below booked count, then a valid capacity. Confirm the notice that booked members are not notified. Cancel with a reason and verify red/muted styling, struck-through title, displayed reason and no edit/cancel actions.
4. Check loading, empty and error/retry scenarios for list/calendar; fail lookup and qualification requests independently. Confirm qualification failure does not prevent otherwise valid class saves.
5. At 375px, confirm filters/forms stack and the calendar becomes a day list without page-level horizontal overflow. Only the table may scroll horizontally.
6. Keyboard: tab order, visible focus, topmost-only Escape handling, focus containment/restoration, and conflict return to start time.
7. Change browser timezone to Tokyo and confirm dates/weekdays do not shift. Verify future-action hints near Vietnam midnight; backend validation remains authoritative.
8. Live mode: verify `/facilities` returns 404 and the editor offers Facility ID entry. Create with a verified local facility ID, and confirm invalid/unavailable IDs preserve the draft and display backend errors. Do not enable a demo database profile or modify migrations.

## Live local data verification (2026-10-16 follow-up)

At the user's request, created two clearly named demo classes and eight published sessions through the existing manager APIs, not SQL writes or backend code. Both use the user's existing active coach, something Nguyen (ID 3), verified by GET `/coaches` and GET `/coaches?sportId=3`.

| Class ID | Name | Weekly sessions | Facility |
| --- | --- | --- | --- |
| 1 | FE-07 Demo - Basketball Basics | Fridays 16:00-17:00, 16/10/2026 through 06/11/2026 | ID 1, Basketball Court A |
| 2 | FE-07 Demo - Swimming Technique | Saturdays 10:00-11:00, 17/10/2026 through 07/11/2026 | ID 3, Indoor Pool |

Facility IDs were verified with a read-only query against the local development database. ID 2 is Basketball Court B. These are local instance values, not production constants. No new accounts, migrations or demo profiles were added. A duplicate Basketball request returned HTTP 409 with the exact conflicting session; no duplicate session was created. The live frontend at `http://localhost:5173/manager/schedule` returned HTTP 200. Browser automation remains unavailable, so this is API and server evidence, not visual verification.

## Intentional Figma adaptations

All four PNGs in `context/Flow 2 - Class booking and schedule management/Flow 2 - Manager` were inspected. Existing blue/white staff layout, table/form components and coach qualification sidebar are reused.

- The task requires a week calendar; the F2-03 form is therefore presented as a session editor dialog over that calendar.
- The conflict screen is a stacked dialog, not a new full-page route.
- Unsupported class description, goals, training-format fields and aggregate stat cards are omitted. Class code is server-generated and status is active/inactive, not an invented class draft state.
- Training type is supplied on session creation because the backend requires it. No session `status` is sent: the backend publishes new sessions, and there is no publish-later endpoint.
- No speculative availability endpoint or advisory availability button is added. Conflict decisions come from the save response.
- The schedule selector loads all class pages at 100 rows per request, forwards the screen abort signal and stops between pages when the view unmounts or reloads. It supports up to 100 pages (10,000 classes); a larger result shows an error instead of silently omitting classes. A failed page request reaches the existing retry state. A lightweight filtered lookup remains a backend improvement.

## PR #150 Round 1 review follow-up (2026-10-29)

### Frontend decisions and regression coverage

The timetable is a weekly session list indexed by start time, not a duration occupancy grid. This matches the FE-07 weekly calendar scope and available Figma flow; the schedule creation flow does not specify a row-span calendar. Each card displays its full start/end range. A visible and screen-reader description states that separate start-time rows can overlap, so a row must not be interpreted as availability. No booking or backend conflict behavior was changed.

Added frontend coverage for simultaneous overlapping intervals at 09:00-11:00, 09:30-10:30 and 10:00-10:30; adjacent dates across midnight; 12 unique starts with lengthy class/facility text; delayed stale week responses; 44px narrow-screen Edit/Cancel targets; and the existing edit/cancel controls. The non-ASCII separator is now ASCII.

### Real-browser verification

- Environment: isolated Vite manager-preview fixture at http://127.0.0.1:5195/manager/schedule; the page explicitly identifies sample data and says changes never reach the backend. No live authenticated backend was used.
- Browser: Chromium through the Codex in-app browser.
- Viewport/theme matrix: 390x844, 768x1024, 1024x768 and 1440x900, each in light and dark mode. document.documentElement.scrollWidth did not exceed clientWidth in any checked case. At 390 and 768 CSS px the 900px timetable remains inside its labeled overflow-x:auto region; desktop content fits the region.
- Actual computed current-day header colors: light rgb(233, 238, 252), dark rgb(28, 42, 77).
- Narrow action target measurement: Edit and Cancel were 44x44px at 390 and 768 viewports, and remained inside each session card. At 1024 and 1440 desktop viewports the measured heights were 28px.
- Keyboard check: Tab from the focusable timetable region reached Edit; the 3px visible focus ring appeared and the horizontal scroller moved to the target session (scrollLeft 593 of 593). Cancel remained adjacent and in-card. No page-wide horizontal overflow was introduced.
- Functional browser checks: Edit opened the existing session editor; Cancel opened the existing reason-confirmation dialog. Both dialogs were closed without submitting. Three rapid Next-week clicks ended at week=2026-11-16, displayed 16/11/2026 - 22/11/2026, and rendered the preview empty state.
- Screenshot status: saved viewport PNGs are in the local review folder E:\SWP391_SS2\RC; see PR150-FE-07-Round-02-Report.md for their filenames, capture metadata, and the SHA-256-verified test-case copy. The report regenerates filenames for the final pushed HEAD. Screenshots remain local and are not committed or attached to the PR.
- Native browser zoom: NOT RUN. The in-app browser exposes viewport sizing but not browser zoom; Ctrl+Plus did not change its CSS viewport or device scale. A native 200% zoom check remains required before claiming full review acceptance.

Live manager-role behavior, backend conflicts, 403/404/500 handling and production CI are not asserted by this fixture check. Existing automated tests cover their relevant FE-07 authorization, API-error and action states. No backend files, endpoints or authorization rules were changed.

## PR #150 Round 2 responsive-layout verification (2026-10-29)

The removed narrow `.f2-editor-layout` breakpoint is restored in `src/styles/managerSchedule.css`: at widths up to 900px, Class details and Coach qualification stack in one column. The class-management regression suite asserts that breakpoint, and the actual computed grid was verified in the manager preview.

- Isolated preview: `npm run dev:preview -- --host 127.0.0.1 --port 5195`, using only the built-in synthetic manager account. The visible fixture notice states that changes stay in the browser and are never sent to the backend.
- 390x844 viewport: CSS client/scroll width 375/375; editor grid one 343px column; both cards stacked. Schedule page width 375/375; the 900px timetable remains inside its 307px `overflow-x: auto` region.
- 768x1024 viewport: CSS client/scroll width 753/753; editor grid one 736px column; both cards stacked. Schedule page width 753/753; timetable remains inside its 669px scroll region.
- Native Edge browser zoom: applied actual Ctrl+Shift+Equals browser zoom steps to 200% (devicePixelRatio 2, visualViewport scale 1, CSS viewport 460px). Class editor grid is one 421px column; page width is 453/453. Schedule page width is 453/453; the 900px table remains contained in a 385px scroll region.
- The local Round 2 handoff package at `E:\SWP391_SS2\RC\reports\round-02` contains screenshots and measured viewport metadata. Synthetic fixtures only; no backend or production data was used.

The 900px table's horizontal scroll is intentional for its seven day columns. No backend files or authorization checks changed.
