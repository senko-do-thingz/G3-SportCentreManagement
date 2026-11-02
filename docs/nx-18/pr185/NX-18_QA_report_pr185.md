# NX-18 Flow 4 cross-role QA report on the integrated screens of PR #185

Task: NX-18 "Run Flow 4 frontend regression and accessibility checks" (Flow 4, F4-REQ-01..07, owner Truc, with Hung and Phat). This run covers the Member, Coach and Receptionist training screens that PR #185 (feat/plan-comment-attend-result, head 7c42630) brings in. It also records how those screens meet the acceptance criteria of NX-14 (member plans and coach notes) and NX-15 (member attendance and results).

Date: 2026-11-02. Base: PR #185 head 7c42630, which includes main up to the QR-06 and FE-07 merges.

## 1. Result in one paragraph

54 captures (9 screens, 3 viewports, light and dark) and 108 state checks (empty, error, forbidden) found no layout, keyboard, console or error-state defect on these screens. The only automated accessibility finding is the shared header logo link (axe label-content-name-mismatch), the same issue reported on the earlier NX-18 branch; it is not caused by #185. The frontend response types match the backend records field by field for all Member endpoints. What is not verified: real end-to-end flow with a database, the Coach and Reception write flows (create plan, feedback, results, attendance) beyond their loading and states, and sign-off by Hung and Phat. NX-18 is therefore reported as passing for read screens and open for the rest.

## 2. Scope tested

| Role | Screen | Route |
|---|---|---|
| Member | Training plans | /member/training/plans |
| Member | Plan detail | /member/training/plans/1 |
| Member | Coach feedback | /member/training/feedback |
| Member | Attendance history | /member/training/attendance |
| Member | Training results | /member/training/results |
| Member | Result detail | /member/training/results/5 |
| Coach | Plans and feedback | /coach/training/plans |
| Coach | Record training results | /coach/training/results |
| Receptionist | Session attendance | /reception/training/attendance |

Not tested here: the pending AI recommendations page (/member/training/ai, a static notice), submitting any form, and the assistant (NX-31 branch).

## 3. Method

Runner: docs/nx-18/pr185/flow4-qa-pr185.mjs (Chrome headless through puppeteer-core, axe-core 4). API answers are mocked in the browser with the response shapes of the TypeScript types, so no backend is needed. Setup: `npm install --no-save puppeteer-core axe-core`, start `npx vite --port 5175` on the branch under test, then `node flow4-qa-pr185.mjs <outDir>`.

Per screen: 3 viewports (390, 768, 1440 px) and 2 themes: overflow, one h1, no redirect, axe-core on the rendered page, up to 40 Tab presses (focus stops and stops without a visible focus ring), console errors. Then at 1440 and 390 px in both themes: empty answer, HTTP 503 and HTTP 403.

## 4. Results

| Check | Result |
|---|---|
| Captures | 54 (coach 12, reception 6, member 36) |
| Horizontal overflow | none in 54 of 54 |
| One h1, no unexpected redirect, console errors | all pass, 0 console errors |
| Keyboard | 6 to 22 focus stops per screen; 0 stops without a visible focus ring |
| Error (503) | alert and Retry button in 36 of 36 |
| Forbidden (403) | alert and Retry button in 36 of 36, never shown as an empty list |
| Empty | list screens show an empty panel (24 of 24 checks). Three screens are flagged by the script and are fine: Coach results and Reception attendance show the sentence "You have no assigned sessions." or "No sessions available." under the session picker (checked in the screenshots), and Result detail has no empty case because it always opens one record |
| axe serious or critical | 1 rule, label-content-name-mismatch, on the header logo (all pages) |
| axe other rules | none |

Evidence: docs/nx-18/pr185/evidence/ (screens `<name>_<390|768|1440>_<light|dark>.png`, state images `_empty`, `_error`, `_forbidden`, and report.json).

## 5. FE and BE contract check (code level)

Compared the Java records in MemberTrainingResponse.java with the TypeScript interfaces in memberTrainingApi.ts. Names and types match for Plan, Step, PlanDetail, Session, Attendance, Result, Score, Feedback and ResultDetail (BigDecimal score maps to number, LocalDate and LocalTime to strings). The routes match too: /member/training/plans, /plans/{id}, /attendance, /results, /results/{id}, /feedback. The backend takes the member from the signed-in principal ("no member id is accepted from the browser"), so a member cannot ask for another member's rows by id.

Not checked: the staff DTOs (CreatePlan, CreateFeedback, CreateResult, attendance input), validation limits, and the allowed attendance status values against the database constraint; no request was sent to a running backend.

## 6. NX-14 and NX-15 acceptance against the integrated screens

| Task criterion | Evidence |
|---|---|
| NX-14 show assigned plans and coach notes, no edit controls | Plans, plan detail and feedback screens are read only; existing tests training.flow.test.tsx ("opens a read-only assigned plan", only GET requests, no textbox) |
| NX-14 responsive cards, empty, loading, error states | This report: no overflow at 390, 768, 1440; empty, 503 and 403 states checked |
| NX-14 member views only own approved plan and comments | Backend resolves the member from the principal; frontend never sends a member id |
| NX-15 own attendance history and per-session results | Attendance, results and result detail screens; tests for date, time and status |
| NX-15 reuse session labels and local time rules | Not independently checked; the screens format the session date and time themselves |
| NX-15 empty and error cases | This report, sections 4 and 5 |
| NX-15 no leakage from another member | Principal-based queries on the backend; no member id in any request path |

Differences from the earlier provisional branches: the integrated screens do not distinguish "not recorded yet" from "absent" (the status values are PRESENT, LATE, ABSENT, EXCUSED) and show results as notes plus scores, with no "missing versus zero" case. These were requirements of the draft contract and are not in the backend model.

## 7. Defects and open items

| ID | Finding | Status |
|---|---|---|
| NX18-D3 | Header logo link: aria-label "Sportify Center home" does not contain its visible text (axe label-content-name-mismatch). Shared component Brand.tsx used by the Home page and the shared shell; three existing tests look it up by that name | Open, owner Phat |
| NX18-O1 | Write flows (create plan, feedback, record results, record and correct attendance) were not exercised | Open |
| NX18-O2 | No end-to-end run against a database, no screen reader pass, Firefox and Safari not tested | Open |
| NX18-O3 | Hung and Phat sign-off | Open |

The earlier NX-18 branch (nx-18-flow4-cross-role-qa) also fixed the Check-in overflow at 390px and its table keyboard access; that fix is independent of #185.
