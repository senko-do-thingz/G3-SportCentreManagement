# NX-18 Flow 4 frontend cross-role QA report

Task: NX-18 "Run Flow 4 frontend regression and accessibility checks" (Flow 4, F4-REQ-01..07, FE, owner Truc, with Hung and Phat, wave W3). Depends on NX-11, NX-12, NX-13 (Hung), NX-14, NX-15 (Truc). Status of the task proposal: requires team sign-off.

Date: 2026-10-30. Base: main c6358dc (includes the ui-02 shared shell) plus the Member screens of branch nx-14-nx-15-member-training-screens.

## 1. Result in one paragraph

This is a PARTIAL run. NX-11, NX-12 and NX-13 (coach plan editor, coach outcomes and feedback, attendance UI) are not in the repository yet, so those screens could not be tested. The Flow 4 screens that exist today were tested across the three roles. The run found 2 defects that are fixed in this branch (Check-in overflowed horizontally at 390px, and the table that then scrolls was not keyboard reachable; section 5) and 1 accessibility defect in the shared header logo that is reported but NOT fixed here (section 6). FE/BE contract compatibility could not be verified: the Flow 4 contract (docs/nx-03) is an unsigned draft and the backend tasks are not in the repository. NX-18 therefore cannot be closed yet.

## 2. Scope actually tested

| Role | Screen | Route | Task | Tested |
|---|---|---|---|---|
| Coach | Teaching schedule | /coach | FE-10 | yes |
| Coach | Class roster | /coach/roster | FE-10 | yes |
| Receptionist | Member search | /reception | FE-05 | yes |
| Receptionist | Check-in | /reception/check-in | FE-05 | yes |
| Member | Training plans and coach notes | /member/training | NX-14 | yes (synthetic data) |
| Member | Attendance | /member/training/history | NX-15 | yes (synthetic data) |
| Member | Training results | /member/training/history?tab=results | NX-15 | yes (synthetic data) |
| Coach | Plan editor, outcomes and feedback | not built | NX-11, NX-12 | NOT TESTED |
| Coach and Receptionist | Attendance and check-in UI | not built | NX-13 | NOT TESTED |

## 3. Method

Runner: docs/nx-18/qa-tools/flow4-qa.mjs (Chrome headless through puppeteer-core, axe-core 4 for accessibility). API answers are mocked in the browser, so no backend is involved. Setup is documented at the top of the script and uses packages installed outside the project (`npm install --no-save puppeteer-core axe-core`).

Per screen, 3 viewports (390, 768, 1440 px) and 2 themes (light, dark): 42 captures. For each capture:
- horizontal overflow (page wider than the viewport);
- exactly one h1; no redirect to /login or /403;
- axe-core run on the rendered page (all rules); serious and critical counted;
- keyboard: up to 40 Tab presses; count of focus stops and of stops with no visible focus ring;
- browser console errors.

Error paths, at 1440 and 390 px in both themes (84 checks): empty (authorized, nothing to show), error (HTTP 503) and forbidden (HTTP 403). Each is checked for the right state: empty panel; alert with a Try again button; alert for 403 (and never an empty panel for 403 or 503).

## 4. Results after the fixes in this branch

| Check | Result |
|---|---|
| Captures | 42 (coach 12, reception 12, member 18) |
| Horizontal overflow | none in 42 of 42 |
| One h1, no unexpected redirect, console errors | all pass, 0 console errors |
| Keyboard | 4 to 30 focus stops per screen; 0 stops without a visible focus ring |
| Empty state | correct empty panel in 28 of 28 checks |
| Error (503) state | alert and Try again in 28 of 28 |
| Forbidden (403) state | alert in 28 of 28, with Try again, never an empty list |
| Overflow in the state checks | none |
| axe serious or critical | 1 rule on every page: label-content-name-mismatch (header logo), section 6 |
| axe other rules | none |

Evidence: docs/nx-18/evidence/after-fix/ (`<screen>_<390|768|1440>_<light|dark>.png`, plus `_empty`, `_error`, `_forbidden` states, and report.json with every number above).

## 5. Defects found and fixed in this branch

| ID | Severity | Finding | Fix | Evidence |
|---|---|---|---|---|
| NX18-D1 | Blocking on mobile | Check-in (reception) overflowed horizontally at 390px in light and dark: the card was 433px wide in a 358px column, because `.reception-checkin` used a bare `1fr` column that grows to its widest content (frontend/src/styles/reception.css) | `grid-template-columns: minmax(0, 1fr)` | evidence/before-fix/ (report.json, reception-check-in_390_light.png) versus after-fix |
| NX18-D2 | Serious (axe scrollable-region-focusable) | After D1 the Today's check-ins table scrolls inside its card at 390px, but a scrolling region must be reachable by keyboard | The existing Table prop `scrollLabel` gives the region tabindex 0 and an accessible name (frontend/src/pages/reception/CheckInPage.tsx) | axe after-fix: rule gone |

A regression test covers both (receptionScreens.test.tsx, "NX-18 Check-in narrow screens").

## 6. Defect found and NOT fixed

| ID | Severity | Finding | Why not fixed here | Suggested fix |
|---|---|---|---|---|
| NX18-D3 | Serious per axe, WCAG 2.5.3 Label in Name | The logo link in the shared header has aria-label "Sportify Center home" but its visible text is different, so the accessible name does not contain the visible text. Present on all 7 screens in every theme and size (frontend/src/components/Brand.tsx) | It belongs to the shared shell (ui-02) and the public Home page, and 3 existing tests look the link up by that exact name (PaymentWorkspaceHeader.test.tsx, HomePage.test.tsx twice). Changing it is a shared UI decision | Make the aria-label start with the visible words, or drop the aria-label so the visible text is the name; update the 3 tests together. Owner: Phat (shared UI) |

## 7. Not verified (open)

- NX-11, NX-12, NX-13 screens do not exist; coach plan editing, outcome entry, feedback and the attendance UI have no QA.
- FE/BE contract compatibility: not verifiable. docs/nx-03 is an unsigned draft with routes marked TBD_AFTER_APPROVAL, and NX-07 to NX-10 are not in the repository. The Member screens use a proposed contract (see the NX-14 and NX-15 branch, docs/pr/NX-14-NX-15_contract_gaps_for_BE.md).
- Member data is synthetic; Coach and Reception answers are mocked in the browser.
- Real end-to-end flow (a coach records an outcome, a member sees it) cannot run without the backend and the NX-11 to NX-13 screens.
- Screen reader testing, Firefox and Safari, real devices, slow network: not done.
- axe-core finds about a third of accessibility issues automatically; the keyboard check only measures focus visibility, not logical order.

## 8. Checklist for the full run (when NX-11 to NX-13 and the backend exist)

Run each item in light and dark, at 390, 768 and 1440 px.
1. Role access: right role sees the screen; other roles get the access denied page; signed-out visitors go to login.
2. States: loading, empty, error with Try again, 403, malformed answer, slow network.
3. Privacy: no other member's data in a response or on screen; members see no edit control; staff-only notes never shown to members.
4. Keyboard: Tab order follows the visual order; visible focus; tabs use arrow keys; dialogs trap focus and close with Escape.
5. Screen reader: headings in order, list and table semantics, errors announced.
6. Layout: no horizontal page scroll; touch targets at least 44px on mobile.
7. Time: dates and times use the My Bookings wording and the center local clock.
8. Regression: other areas unchanged against main.
9. Contract: each request path, method, status and field matches the signed contract.

Extend `SCREENS` and `answer()` in the runner for the new screens, then record the result in the table below.

## 9. Sign-off table

| Role | Screens | Light | Dark | Keyboard | Mobile | Error paths | Contract | Tester | Date |
|---|---|---|---|---|---|---|---|---|---|
| Member | training, attendance, results | pass | pass | pass (focus visible; order not judged) | pass | pass | blocked | automated run | 2026-10-30 |
| Coach | schedule, roster | pass | pass | pass | pass | pass | blocked | automated run | 2026-10-30 |
| Coach | NX-11, NX-12, NX-13 screens | not built | | | | | | | |
| Reception | member search, check-in | pass (after D1, D2) | pass | pass | pass (after D1) | pass | blocked | automated run | 2026-10-30 |
| Reception | NX-13 check-in UI | not built | | | | | | | |
| All | header logo (D3) | open | | | | | | | |

Human sign-off from Hung and Phat is still required (task: coordinate Truc, Hung and Phat). It is not claimed here.
