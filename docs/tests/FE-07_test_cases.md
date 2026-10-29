# FE-07 test cases

## Automated specifications

| Area | Expected result | Test source |
| --- | --- | --- |
| AC4 access | All four routes redirect anonymous visitors and reject members without manager requests | `managerClasses.test.tsx` |
| Class list | Combined search/sport/status filters reach server pagination | `managerClasses.test.tsx` |
| Class editor | Invalid required fields do not submit; selected coach certification details are displayed | `managerClasses.test.tsx` |
| AC1 weekly schedule | Four inclusive Mondays produce four sessions; request uses range/weekdays, seconds, training type and no date/status | `managerSchedule.test.tsx` |
| AC2 conflict | Every exact conflict is displayed, including missing-resource fallback, and editor values survive dismissal | `managerSchedule.test.tsx`, `managerSessionsApi.test.ts` |
| AC3 cancellation | Cancelled reason is visible and cancelled cards have no management actions; reason is required | `managerSchedule.test.tsx` |
| Weekly timetable | Labels start-time grouping explicitly and shows each full interval; overlapping sessions remain independently visible; midnight-adjacent sessions stay under their calendar dates; same-time sessions render deterministically; current-day highlight survives the general table-header rule | `managerSchedule.test.tsx` |
| Dense schedule and long content | Many unique start times and long class/facility names remain in the labeled scroll region; Edit and Cancel remain present for each manageable session | `managerSchedule.test.tsx` |
| Week navigation | Previous/current/next-week controls request each matching range; a delayed superseded response cannot overwrite the last selected week | `managerSchedule.test.tsx` |
| Browser layout and theme | Real-browser preview checked at 390x844, 768x1024, 1024x768 and 1440x900 in light and dark themes; no page-wide horizontal overflow, table scroll is contained, computed today colors change by theme; native 200% zoom is not verified | frontend/docs/FE-07-verification.md |
| Responsive and keyboard access | Horizontal overflow stays inside the labeled timetable region; the region is focusable and Tab/Shift+Tab reaches session actions | `managerSchedule.test.tsx` |
| Edit safety | Capacity cannot fall below booked count; class/training type remain immutable | `managerSchedule.test.tsx` |
| Missing API | A facility 404 enables explicit known-ID entry; 403/500 never trigger fallback; existing sessions remain visible | `managerSchedule.test.tsx` |
| Coach discovery | All active coaches load before sport selection; selecting sport filters qualification; refresh reloads directory | `managerClasses.test.tsx` |
| Dates | Vietnam midnight, Monday normalization, year rollover, inclusive repetitions, batch cap and future guards | `scheduleModel.test.ts` |
| Preview atomicity | Overlapping save returns exact conflict without adding a duplicate session | `managerSchedulePreview.test.ts` |
| FE-06 preservation | Existing account, package, card and activity tests still pass | `managerScreens.test.tsx` |

Sources are under `frontend/src/pages/manager` and `frontend/src/api`. Follow `frontend/docs/FE-07-verification.md` for setup, API dependencies and pending manual acceptance steps. Automated API fixtures do not establish live backend acceptance.
