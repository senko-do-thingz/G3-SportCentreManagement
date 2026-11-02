# FE-07 - Sunday Boundary CI Maintenance - Acceptance Test Cases

**Workstream:** FE-07 (Manager Weekly Schedule Management)
**Maintenance Scope:** Sunday Boundary Vitest Fixture Stabilization
**Branch:** `fix/fe-07-sunday-boundary-ci`
**Base Commit:** `6689165da6c09e2426f3677803a12d562342a32b` (origin/main)
**Target Pull Request Base:** `main`

---

## 1. Overview and Root Cause Context

On Sundays (such as 2026-11-01), the weekly manager schedule test `shows multiple time slots and sorts same-time sessions regardless of API order` in `frontend/src/pages/manager/managerSchedule.test.tsx` failed in CI.

The root cause was top-level fixture date calculation:
`const date = addDays(centerNow().date, 7)`

When evaluated on a Sunday, `date` became the following Sunday. In the test, `Next day class` was set to `addDays(date, 1)`, which rolled over into Monday of the *subsequent* week. Because the timetable view displays only the 7 days of the selected week (`monday(date)` through Sunday), `Next day class` was filtered out from the rendered weekly table, causing the title array assertion to receive 3 sessions instead of 4.

The fixture was stabilized by anchoring to the upcoming Monday:
`const date = addDays(monday(centerNow().date), 7)`

This guarantees that `date` is always a Monday and `addDays(date, 1)` is always a Tuesday of the exact same 7-day week, regardless of which day of the week CI runs.

---

## 2. Test Cases and Execution Matrix

| Test ID | Test Category | Scenario Description | Expected Outcome | Execution Result |
| :--- | :--- | :--- | :--- | :--- |
| **TC-FE07-MAINT-01** | Boundary Stabilization | Top-level fixture anchor evaluates to Monday across all calendar days | `addDays(monday(centerNow().date), 7)` is always a Monday; `addDays(date, 1)` is always Tuesday | **PASS** |
| **TC-FE07-MAINT-02** | Sunday Boundary Regression | Evaluated when reference date is Sunday (2026-10-18T12:00:00+07:00) | `displayedWeekStart` matches fixture Monday; Tuesday is contained within the displayed week | **PASS** |
| **TC-FE07-MAINT-03** | Weekday Invariance | Evaluated when reference dates are Monday, Wednesday, Saturday | Monday, Wednesday, Saturday all anchor correctly to the subsequent Monday without day drift | **PASS** |
| **TC-FE07-MAINT-04** | Timetable Sort & Slot Rendering | Manager schedule renders 4 sessions across Monday and Tuesday | `Alpha class` and `Zulu class` on Monday 09:00, `Next day class` on Tuesday 09:00, `Later class` on Monday 10:00; rowheaders `['09:00', '10:00']` | **PASS** |
| **TC-FE07-MAINT-05** | Component Suite Integrity | Run complete manager schedule Vitest suite (`managerSchedule.test.tsx`) | 16/16 tests PASS | **PASS** |
| **TC-FE07-MAINT-06** | Date Model Suite Integrity | Run complete schedule model Vitest suite (`scheduleModel.test.ts`) | 4/4 tests PASS | **PASS** |
| **TC-FE07-MAINT-07** | Full Frontend Test Suite | Full frontend Vitest regression (`npm test`) | 91/91 test files PASS, 1688/1688 tests PASS | **PASS** |
| **TC-FE07-MAINT-08** | Lint and Build Verification | ESLint and TypeScript production build (`npm run lint && npm run build`) | Zero lint errors; client build succeeds | **PASS** |

---

## 3. Detailed Verification Evidence

### TC-FE07-MAINT-04: Multi-Time Slot & Title Order Verification
- **Test File:** `frontend/src/pages/manager/managerSchedule.test.tsx`
- **Test Name:** `shows multiple time slots and sorts same-time sessions regardless of API order`
- **Assertions:**
  - Rowheaders: `expect(rows.map((row) => within(row).queryByRole('rowheader')?.textContent).filter(Boolean)).toEqual(['09:00', '10:00'])` -> PASS.
  - Titles: `expect([...table.querySelectorAll('.f2-session__title')].map((title) => title.textContent)).toEqual(['Alpha class', 'Zulu class', 'Next day class', 'Later class'])` -> PASS.

### TC-FE07-MAINT-02 & TC-FE07-MAINT-03: Pure Date Model Boundary Coverage
- **Test File:** `frontend/src/pages/manager/scheduleModel.test.ts`
- **Test Name:** `anchors weekly session fixtures to Monday across all weekdays including Sunday`
- **Reference Dates Checked:**
  - Monday (`2026-10-12T12:00:00+07:00`): Anchors to `2026-10-19` (Monday). Next day is `2026-10-20` (Tuesday). Both inside displayed week `2026-10-19` to `2026-10-25`.
  - Wednesday (`2026-10-14T12:00:00+07:00`): Anchors to `2026-10-19` (Monday). Next day is `2026-10-20` (Tuesday). Both inside displayed week.
  - Saturday (`2026-10-17T12:00:00+07:00`): Anchors to `2026-10-19` (Monday). Next day is `2026-10-20` (Tuesday). Both inside displayed week.
  - Sunday (`2026-10-18T12:00:00+07:00`): Anchors to `2026-10-19` (Monday). Next day is `2026-10-20` (Tuesday). Both inside displayed week.
