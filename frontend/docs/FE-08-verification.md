# FE-08 - Member browsing verification

Date: 2026-10-17. Working project: `E:\SWP391_SS2\Project\V1\G3-SportCentreManagement-main`.
Branch: `feat/f2-member-browse`. Latest `origin/main` (`91c1f29`) is an ancestor through the FE-06 integration merge `26a2895`; updated FE-06 and FE-07 work is then merged through `6094342`. Backend files match `origin/main`; all follow-up changes are frontend-only. No SQL writes or new live data were performed.

## Delivered screens

| Route / UI | Delivered behavior |
| --- | --- |
| `/member/schedule` | F2-04, complete Monday-based week or selected day, sport/format filters, date navigation, all-page loading and availability cards |
| `/member/classes` | F2-05, server-side sport/date/format/level/coach filters, 20-row pagination, URL state and contextual coach options |
| `/member/sessions/:sessionId` | F2-06, coach-led/self-training detail, all eligibility reason views, safe return navigation, suitable-package links |
| Session-local coach modal | S2-CoachProfile, lazy public-profile request, real certificate verification/expiry, experience/null handling, upcoming sessions and close/focus behavior |

All routes use the existing MEMBER role guard and member layout. Anonymous users reach login; manager, receptionist and coach accounts are denied before browse data is requested. FE-06/FE-07 routes and behavior remain intact.

## Explicit scope boundaries

- FE-08 does not create or review bookings. For an eligible member only, `Book session` navigates to the FE-09 Booking Options route `/member/bookings/new?sessionId={id}&returnTo={path}`. The validated return context is preserved. FE-09 owns that route and all booking requests; FE-08 never calls `POST /bookings`.
- Package recovery links use the existing catalog query keys, for example `/member/packages?sport=3&format=COACH_LED`. They do not create registrations or payments. Returning through a later purchase flow is not implemented here.
- Owner waiver recorded in this task thread on 2026-10-17: the user explicitly said to skip the mobile layout. No 375px visual acceptance check is claimed. A pre-waiver viewport check identified and fixed a shared header overflow, but that was not full mobile QA.

## API use and contracts

Listed task endpoints: GET `/sessions`, GET `/sessions/{id}`, GET `/coaches/{id}`. Reused lookup: GET `/sports`. No new backend endpoints.

Member session DTOs use flat `className`, `coachName`, `facilityName`, `sessionDate` and `trainingFormat`; manager DTOs are deliberately not reused.

GET `/coaches` is staff-only and is never used by these member screens. The coach selector deduplicates coaches from all pages of member-visible sessions for the selected sport/date/format/level context, omitting coachId from the options query. The default search range is center today through today plus 30 days. A selected deep-linked coach ID is retained even when no matching option exists. Option lookup errors do not hide the primary results.

Calendar and coach-option queries fetch every page with size 100. Later-page errors or duplicate/short changed-page results fail visibly instead of returning a misleading partial calendar. Search result pagination stays server-side with size 20. Abort signals and the shared resource hook prevent stale data from replacing newer filters.

Date-only values use UTC calendar arithmetic, while today is derived explicitly in Asia/Ho_Chi_Minh. UI dates use dd/MM/yyyy; times use HH:mm. UI-only view/date parameters are not sent as API filters. Invalid/reversed search date ranges prevent session requests.

## Eligibility mapping

| Backend reason | UI behavior |
| --- | --- |
| FULL | Session Fully Booked; find another class |
| NO_PACKAGE | No Eligible Sport Package; suitable packages |
| NO_SESSIONS_LEFT | Same recovery view, explicit exhausted-credit explanation |
| PACKAGE_EXPIRED_BY_THEN | Package does not cover the session date |
| PACKAGE_NOT_STARTED_BY_THEN | Package starts after this session |
| ALREADY_BOOKED | Already booked, no duplicate booking action |
| CAPACITY_UNAVAILABLE | Availability cannot be confirmed, not a false full badge |
| Eligible member snapshot | `Book session` handoff to FE-09 with session ID and safe return path; no booking is submitted |
| Unknown/incomplete eligibility or staff context | Cannot confirm eligibility; no Book |
| HTTP 404 | Session unavailable, not an invented package reason |

Already booked takes precedence over full. List availability is not presented as package eligibility. A valid suitable registration is required for the eligible state. Detail snapshots reserve no seat or credit. Refresh availability is explicit; visibility-return refresh is suppressed while a profile is open, and ordinary window refocusing does not dismiss the dialog. Returning to a detail route mounts a fresh request.

Self-training hides the coach block and makes no coach-profile request even if an unexpected coach ID arrives. Private/unavailable coach profiles fail inside the modal without breaking session detail. No private account contacts are displayed.

## Figma comparison

Inspected all seven PNGs under `context/Flow 2 - Class booking and schedule management/Flow 2 - Member`: F2-04, F2-05, F2-06, F2-06-Self, S2-CoachProfile, S2-Full and S2-NoPackage.

Preserved blue/white cards, class results, availability badges, detail/sidebar organization and modal presentation. Intentional differences:

- Reuse the existing member shell rather than adding duplicate Figma headers.
- Display all seven days; desktop day cards wrap at readable widths rather than squeezing text into seven narrow columns.
- Replace unsupported Age group/Goal filters with the task's supported date/format/coach filters. No fabricated age, goals, description, coach photo or teaching-style data.
- A Find Classes result is a session occurrence, not an aggregated class; repeated class names keep their own dates and seat counts.
- Coach avatar uses initials. Certifications show actual verification and expiry, not a fabricated verified badge.
- NoPackage links to Sport Packages rather than starting Figma's out-of-scope single-visit payment action.

A cross-feature CSS collision was found in browser review: receptionist `.member-header` flex styles also affected the member shell. Those two selectors now apply only within `.staff-layout`, preserving receptionist card layout while restoring the member header.

## Run and review

From `frontend`:

```powershell
npm run dev:preview -- --host localhost --port 5174
```

Open `http://localhost:5174/login` and choose `Open member browse preview`. The existing manager preview and member-access check shortcuts are preserved. Synthetic login, if needed: `member@preview.sportify.local` / `preview`. These are explicitly isolated preview credentials, not a live backend account.

The preview uses the existing DEV flag and synthetic MEMBER-token boundary. Fixtures are in `src/api/mock/memberBrowsePreview.ts`; FE-06/FE-07 preview data is unchanged. Member fixtures are read-only, excluded from production and have a visible sample-data notice. The synthetic member dashboard/package screens outside the browse feature are not fully mocked; use the member browse shortcut rather than treating the preview as a complete application demo.

Preview session IDs:

| ID | Scenario |
| --- | --- |
| 801 | Eligible coach-led session |
| 802 | Eligible self-training, no coach |
| 803 | Full |
| 804 | No package |
| 805 | No sessions left |
| 806 | Package expired by session |
| 807 | Package not started by session |
| 808 | Already booked |
| 809 | Capacity unavailable |

Dates are relative to Vietnam today, not fixed Figma dates. Append `previewState=empty`, `previewState=error` or `previewState=slow` to exercise those states. Slow timing uses the existing preview boundary. Normal real tokens are never silently replaced with fixture data.

Live mode remains `npm run dev -- --host localhost --port 5173`, with the existing backend and an actual MEMBER account. The local backend was unreachable during this task's final verification, so no live member API pass or real booking eligibility is claimed.

## Verification evidence

- Baseline: 619 tests across 34 files passed before FE-08.
- Full regression before the FE-09 handoff refinement: 670 tests across 41 files passed, including existing manager and receptionist suites.
- The final visibility/focus refinement also passed all 24 detail tests, production build and lint.
- Final combined branch verification on 2026-10-17: 677 tests across 42 files passed; lint and production build passed. Build output has a non-blocking 559.83 kB JavaScript chunk warning.
- A final browser re-check was attempted, but the computer-use safety layer stopped before interacting because it could not confidently determine Chrome's current URL. The earlier desktop preview observations above are not a re-check of the final combined branch. The local preview server is listening on port 5174.
- Production build passes with the pre-existing non-blocking main-chunk warning above 500 kB. No package or lockfile changes.
- Desktop Chrome preview: schedule, complete search results, self-training filtering, source-query return, coach-led detail and coach profile inspected. No new console errors after a clean reload; a transient dependency-array warning during hot reload was resolved by reload and was not reproduced on the clean run.
- Automated coverage includes each eligibility reason, unknown states, member/staff guards, complete paging, query/date safety, no coach request for self-training, independent profile failure, modal Escape/focus restoration, and no unintended booking writes.
- A preview screenshot is saved outside the repository at `C:\Users\ADMIN\AppData\Local\Temp\FE-08-desktop-review.png`.

Manual follow-up for a running backend:

1. Sign in as MEMBER, not manager. Browse real published sessions, change filters and refresh, then use Back/Forward.
2. Confirm a real member's eligible/no-package/full/already-booked responses select the expected view; do not alter migrations or fabricate eligibility in live mode.
3. Open/close a public coach profile with keyboard. Check unavailable profile handling and that self-training has no profile request.
4. Change the browser timezone to a zone ahead of Vietnam and confirm date-only labels remain unchanged. Automated date tests already cover center midnight and year rollover; full browser timezone testing is still pending.
5. When FE-09 is integrated, confirm it registers `/member/bookings/new`, reads `sessionId` and `returnTo`, and revalidates eligibility at submit. Booking writes remain exclusively in FE-09.

Plans and the local audit row remain ignored. No force-add, push or PR creation is part of this implementation turn.
