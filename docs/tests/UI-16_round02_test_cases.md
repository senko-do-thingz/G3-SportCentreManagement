# UI-16 PR #186 - Round 02 Test Cases

**Task:** UI-16 Manager Activity Log
**Pull request:** https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/186
**Test baseline:** local frontend validation on 2026-11-01, Asia/Saigon
**Test sources:** ActivityLogPage.test.tsx, managerScreens.test.tsx, responsive Chromium evidence in docs/reviews/PR-186-Round-02/

This matrix preserves the 22 cases requested by the CODEOWNER Round 01 review. Existing FE-06 coverage is identified separately from new UI-16 regressions. PASS means the behavior was exercised by an automated test or the browser evidence described. PARTIAL identifies the one manual screen-reader check that is not available in this environment.

| ID | Scenario | Result and evidence |
| --- | --- | --- |
| UI16-TC-01 | Manager opens Activity Log and loads authorized rows | PASS - existing FE-06 Activity Log API test and local manager preview. |
| UI16-TC-02 | Anonymous visitor or member attempts manager access | PASS - existing manager access tests assert redirect/403 and no unauthorized manager data request. |
| UI16-TC-03 | Desktop filter panel starts expanded and toggles | PASS - UI-16 regression test; 1440x900 open/closed screenshots in both themes. |
| UI16-TC-04 | Filter panel at 390px; toggle state is exposed accurately | PASS - UI-16 regression test asserts aria-controls and aria-expanded; light/dark viewport screenshots. |
| UI16-TC-05 | Resize across the 900/901px breakpoint | PASS - UI-16 regression test updates the mocked media query and verifies predictable panel state. |
| UI16-TC-06 | Combine search, actor, action, and date filters | PASS - existing FE-06 test verifies the serialized query values and valid local calendar dates. It also verifies invalid date ranges do not issue another request. |
| UI16-TC-07 | Clear filters while the panel is collapsed | PASS - UI-16 regression test verifies hidden fields reset and the active-filter badge disappears. |
| UI16-TC-08 | Reject an invalid date range accessibly | PASS - existing FE-06 test verifies the error and that no invalid request is sent. |
| UI16-TC-09 | Rapid search changes do not show stale results or errors | PASS - UI-16 tests cover pending new activity search and picker search after an error. |
| UI16-TC-10 | Actor picker with more than 20 results can paginate | PASS - actor preview has 26 entries; automated paging test moves between pages and retains controls. |
| UI16-TC-11 | Empty actor page while totalElements remains positive | PASS - UI-16 regression test verifies pagination remains available on an empty valid page and that an out-of-range page can return to the last valid page. |
| UI16-TC-12 | Truly empty actor search result | PASS - UI-16 regression test verifies the empty state and no unnecessary pager when totalElements is zero. |
| UI16-TC-13 | Actor API loading, error, retry, and recovery | PASS - UI-16 regression test verifies loading, retry action, and recovered results. |
| UI16-TC-14 | Long actor name and role remain usable; contact data is omitted | PASS - UI-16 regression test verifies wrapping, visible role, correct selection, and no email/phone shown. |
| UI16-TC-15 | System or unavailable actor event | PASS - existing FE-06 test verifies the neutral fallback without inventing an actor identity. |
| UI16-TC-16 | Details dialog values, read-only behavior, close, and focus return | PASS - UI-16 test and existing FE-06 test; no second list request is made. |
| UI16-TC-17 | Long target and detail content | PASS - UI-16 test asserts long target and detail text remains present; 1101px light/dark browser stress screenshots show wrapping without overlap. |
| UI16-TC-18 | Details action around 1101px | PASS - Chromium geometry check at 1101px confirms the 97px Details action is fully visible; also checked at 1200, 1280, and 1440px in both themes. |
| UI16-TC-19 | 390x844, 768x1024, and 1440x900 in light/dark | PASS - exact viewport screenshots provided; no page-level horizontal overflow. Narrow table overflow stays inside its labelled scroll region. |
| UI16-TC-20 | NVDA/Chrome or VoiceOver/Safari at mobile width | PARTIAL - NVDA is not installed in the available Windows environment. Chromium accessibility snapshot retains a native table and four column headers; keyboard focus and Tab navigation to Details were verified at 390px. A manual screen-reader pass remains outstanding. |
| UI16-TC-21 | Refresh when the activity total changes | PASS - new UI-16 regression test changes the mocked total from 21 events to zero after Refresh and verifies the updated state. |
| UI16-TC-22 | Existing manager schedule regression | PASS - full frontend suite passed on local head cc54acc after current main was merged. The UI-16 diff still overlaps PR #184 in managerSchedule.test.tsx and managerScreens.test.tsx; reconcile after PR #184 merges. |

## Final local verification

- Full frontend suite: 92 test files, 1,699 tests passed, zero failed.
- Focused Activity Log and manager-screen suite: 35 tests passed.
- ESLint: passed.
- Production TypeScript/Vite build: passed; Vite emitted the existing advisory for a bundle chunk above 500 kB.
- The DOCX AI Audit Log is stored locally under docs/audit/ and is excluded from Git by the repository's private-audit ignore rules.

## Round 03 local validation

- After fetching and merging current origin/main 646bd143c40d0e3721210433997e1316ae05cc1e into feat/ui-16 at cc54acc40ff6dc93446cd7b543bbd4c3d598ee63, the frontend suite passed: 94 test files and 1,764 tests, zero failures.
- npm run lint passed.
- npm run build passed with the existing Vite advisory for a bundle chunk above 500 kB.
- GitHub Actions run 41378482529 passed on the earlier remote head 55e49b259134341511c7804421dd3e25b53a7ea4. It does not validate local head cc54acc; push and rerun CI before requesting approval.
- The local audit DOCX remains excluded from Git and has not been delivered privately. No destination is authorized.
- No production backend request or write was performed during the browser review or local merge.
