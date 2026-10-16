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

The missing facility lookup is an integration dependency, not implemented in this frontend task. Live lookup failure shows an explicit notice and retry, disables create/edit, and still renders existing sessions and permits valid cancellation. No guessed facility IDs are sent to the real backend. Once the backend owner exposes this contract, confirm its response shape against `facilitiesApi.ts`.

Unlike the initial plan's separate facility mock flag, facilities are mocked only inside the existing fully isolated manager preview. This avoids combining fixture IDs with live writes. No automatic fallback to fixtures exists.

## Run for review

Live frontend: `npm run dev -- --host localhost --port 5173` (requires the backend and a real manager account).

Isolated frontend preview: `npm run dev:preview -- --host localhost --port 5174`.
Open `http://localhost:5174/login`, sign in with `manager@preview.sportify.local` / `preview`, then open Classes or Schedule and coaches. Preview mutations live only in module memory and reset on page reload. These are not backend-seeded accounts or records.

Preview-only data and simulated writes are in `src/api/mock/managerSchedulePreview.ts`, reached through the existing DEV + explicit preview mode + preview-token boundary. Production builds exclude the preview. `?previewState=empty`, `?previewState=error` and `?previewState=slow` exercise existing preview scenarios; preserve that parameter while navigating if needed.

## Automated evidence

Commands: `npm run lint`, `npm run test`, `npm run build`.

- Final full regression run passed 616 tests across 34 test files, including the existing FE-06 suite.
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
8. Live mode: verify `/facilities` dependency state. Real create/edit and end-to-end backend scheduling remain unverified until that lookup is available. Do not enable a demo database profile or seed data as part of frontend verification.

## Intentional Figma adaptations

All four PNGs in `context/Flow 2 - Class booking and schedule management/Flow 2 - Manager` were inspected. Existing blue/white staff layout, table/form components and coach qualification sidebar are reused.

- The task requires a week calendar; the F2-03 form is therefore presented as a session editor dialog over that calendar.
- The conflict screen is a stacked dialog, not a new full-page route.
- Unsupported class description, goals, training-format fields and aggregate stat cards are omitted. Class code is server-generated and status is active/inactive, not an invented class draft state.
- Training type is supplied on session creation because the backend requires it. No session `status` is sent: the backend publishes new sessions, and there is no publish-later endpoint.
- No speculative availability endpoint or advisory availability button is added. Conflict decisions come from the save response.
- The calendar loads every page of class options rather than silently limiting selection to the first 100 classes.
