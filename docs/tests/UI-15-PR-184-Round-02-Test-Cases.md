# UI-15 PR #184 Round 02 Test Cases

## Run metadata

| Item | Value |
| --- | --- |
| Task | UI-15 - Manager sport packages and membership cards |
| Pull request | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/184 |
| Branch | `feat/ui-15` |
| Main base | `6689165da6c09e2426f3677803a12d562342a32b` |
| Final local source head | `112c5432e9af73cb1fc528bd5243ccce55defc99` |
| Test file | `frontend/src/pages/manager/managerScreens.test.tsx` |
| Browser | HeadlessChrome 156.0.0.0 on Windows 10 x64 |
| Browser preview | `http://127.0.0.1:5176`, opt-in FE-06 manager preview with sample data |
| Capture date | 2026-11-01, Asia/Ho_Chi_Minh (UTC+07:00) |

The browser preview uses local fixtures only. Its UI states that changes stay in the browser and are never sent to the backend. Browser screenshots and browser-flow results do not establish live API or database behavior.

## Test matrix

| ID | Type | Steps and expected result | Result and evidence |
| --- | --- | --- | --- |
| UI15-TC-01 | Normal | Search by mixed-case package name, code, and sport. Each search returns the matching package and updates the count. | PASS. `searches package name, code and sport case-insensitively`. |
| UI15-TC-02 | Normal | Combine sport, training format, status, and a case-insensitive search. The displayed row and count match all selected criteria. | PASS. `uses the complete manager catalog, filters it, and validates package edits`; the test asserts the backend filter query and the combined result count. |
| UI15-TC-03 | Normal | Go to page 2, then change a catalog filter. The parent page resets to the first valid page. | PASS. `resets package pagination when a filter changes on page two`. Search reset is also exercised in TC-01/02. |
| UI15-TC-04 | Boundary | Load 21 filtered packages, deactivate the only item on page 2, and reload 20 results. The remaining page is shown instead of a false empty state. | PASS. `returns to a valid package page after a mutation shrinks the catalog`. |
| UI15-TC-05 | Boundary | Create a package with a 500-character description. The input accepts it and the request includes all 500 characters. | PASS. `accepts a 500-character package description and rejects overlong input before create`. Frontend mock only. |
| UI15-TC-06 | Abnormal | Attempt a 501-character package description. The form shows a field error and sends no create request. | PASS. Same test as TC-05. Backend direct-request validation remains a separate BE follow-up. |
| UI15-TC-07 | Abnormal | Set a paid tier validity to 0 months. The client rejects it and sends no update request. | PASS. `enforces paid-tier validity boundaries at 1 and 120 months`. |
| UI15-TC-08 | Boundary | Set a paid tier validity to 1 month and 120 months. The client permits both boundary values in the update payload. | PASS. Same test as TC-07. Backend remains authoritative; no live service was called. |
| UI15-TC-09 | Abnormal | Set paid tier validity to 121 months. The client rejects it and sends no update request. | PASS. Same test as TC-07. |
| UI15-TC-10 | Normal | Return null status for a package and for a tier. The UI displays Unknown and requires an explicit status choice before saving. | PASS. `keeps a missing package status unknown and requires an explicit choice before update`; `keeps a missing tier status unknown and requires an explicit choice before update`. |
| UI15-TC-11 | Domain boundary | Return a one-day package with 3 sessions. It is described as `1 day`, not `Single visit`. | PASS. `labels a one-day package by duration without assuming its session count`. |
| UI15-TC-12 | Domain normal | Return a one-day package with 1 session. Do not claim `Single visit` unless the product has approved that domain definition. | PARTIAL. The UI consistently says `1 day` for both one-session and multi-session fixtures. The UI-15 sources reviewed do not contain an approved single-visit rule, so the more specific label remains withheld pending product/CODEOWNER decision. |
| UI15-TC-13 | Normal | Edit a tier and inspect the visible policy. It distinguishes the current tier discount for future eligible registrations from the paid card fee and saved registration snapshots. | PASS. `explains current tier discounts separately from paid card fees and saved registrations`; wording aligned to the reviewed `MembershipCardServiceImpl` and registration snapshot fields on main. |
| UI15-TC-14 | Responsive | Inspect tier Actions at 1101, 1280, and 1440 px in light and dark themes. Full labels and button borders remain visible. | PASS. Browser captures: `cards-1101x900-*`, `cards-1280x900-*`, and `cards-1440x900-*` in the screenshot manifest. |
| UI15-TC-15 | Responsive | Inspect package and tier pages at 390x844 and 768x1024. Page width remains at the viewport width; table overflow is contained in the labeled table scroll region. | PASS. Screenshots cover both routes and themes. Browser measurements at 390 and 768 px reported `document.scrollWidth == viewport`; table scroll widths were 900 px for packages and 960 px for tiers. |
| UI15-TC-16 | Accessibility | Use keyboard focus and ArrowRight on the mobile tier table. Headers remain semantic and actions remain in the table. Also run a screen-reader/browser combination. | PARTIAL. Unit test confirms the table caption, column headers, named region, and `tabIndex=0`. Browser keyboard check moved `scrollLeft` to 40 and showed a 2 px focus outline. No screen reader was available for a real assistive-technology run. |
| UI15-TC-17 | Error state | Fail the package catalog GET, then choose Retry. The error is shown first and returned rows load after retry. | PASS. `retries a failed package catalog request and renders the returned data`. Mocked API only. |
| UI15-TC-18 | Error state | Return a field validation error from package update. The error maps to its field, values stay in the editor, and no success is shown. | PASS. `rejects BE-03 package limits and maps price and active server errors to the displayed fields`. Mocked API only. |
| UI15-TC-19 | Authorization | Directly open package and tier routes while anonymous and as a member. The app redirects/denies access and does not request manager catalog data. | PASS. `blocks direct package and tier catalog access for anonymous and member sessions`; existing general Manager guard tests also pass. |
| UI15-TC-20 | Regression | Run the existing Manager schedule suite after the separate schedule fixture date correction. | PASS. Included in the final 91-file frontend suite. No schedule implementation was changed by this remediation. |
| UI15-TC-21 | Regression | Check whether catalog styles can affect Manager Users, Schedule, Classes, or Finance outside the two UI-15 routes. | PARTIAL. `managerCatalog.css` selectors are scoped to `.manager-catalog`, and the full frontend suite passes. A separate visual comparison of those four unrelated routes was not captured. |
| UI15-TC-22 | Theme | Inspect populated package and tier catalogs in light and dark themes for legibility and styling consistency. | PASS. Both routes have 390, 768, and 1440 px light/dark captures in the screenshot manifest. |

## Final local verification

| Command | Result |
| --- | --- |
| `npm run test -- src/pages/manager/managerScreens.test.tsx` | 1 file passed, 36 tests passed. |
| `npm run test` | 91 files passed, 1,700 tests passed. |
| `npm run lint` | Passed. |
| `npm run build` | Passed. Vite emitted the existing advisory that the main minified JavaScript chunk exceeds 500 kB. |
| Browser viewport checks | Passed for 390 and 768 px document-width checks on both catalog routes; table scroll remains local. |
| Browser keyboard check | Passed for focusable tier scroll region, visible 2 px focus outline, and ArrowRight scrolling. |

The browser preview is not a production backend integration environment. Screen-reader testing, live backend validation, and the single-visit domain decision remain unverified.

## Backend follow-up

The frontend create form limits package descriptions to 500 characters and shows a counter. The reviewed backend `SportPackageCreateRequest` does not have a matching max-length request guard, although the entity description column is limited to 500 characters. A backend owner should add and test request validation separately. No backend files were changed in UI-15.

## Evidence

- Browser screenshots and exact routes/viewports: `frontend/output/playwright/PR-184-round-02/README.md`.
- Round 02 finding-to-fix report: `docs/reviews/PR-184-UI-15-Round-02-Remediation.md`.
- AI audit log: `docs/audit/PR-184-UI-15-AI-Audit-Log-Round-02.docx`.
