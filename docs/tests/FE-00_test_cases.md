# Test Cases - Task FE-00 (Shared): Frontend Foundation - Routing, API Client, Auth, Layouts

## 1. Overview
- Task ID: FE-00
- Flow: Shared (used by every frontend task of Flow 1, 2, 3)
- Branch: feat/fe-foundation
- Priority: P1
- Depends on: BE-00 (CORS for http://localhost:5173, merged)
- Scope:
  - API client (`frontend/src/api/client.ts`) on top of fetch: base URL from `VITE_API_BASE_URL`, JSON handling, Authorization header, automatic `POST /auth/refresh` on 401 (one shared refresh, one retry, then logout), typed `ApiError` (status, message, fieldErrors).
  - Auth context (`AuthProvider`, `useAuth`): login, logout, current user from `GET /users/me`, role, token storage, session restore after reload, logout in other tabs.
  - Route guards `RequireAuth` and `RequireRole(roles)`, 403 and 404 pages.
  - Layouts: public, member (top nav), staff (side nav) for RECEPTIONIST, MANAGER, COACH with a menu per role and placeholder pages for screens built later.
  - Shared UI: Button, Input, Select, Modal, Table with Pagination, Badge and StatusBadge, Toast, Spinner, EmptyState, ErrorState, Card, StatCard, PageHeader; money and date formatters.
  - Design tokens sampled from the Figma screens (blue and white, light) and a demo page at `/ui-kit`.

### How to run
```powershell
cd frontend
npm install
npm test          # automated cases TC-FE00-001 to TC-FE00-043
npm run lint
npm run build
```

Manual cases (section 3) need the real backend: `cd backend` then `mvn spring-boot:run`, and in a second terminal `cd frontend` then `npm run dev`, then open http://localhost:5173.

### Result summary
| Group | Cases | Result |
|---|---|---|
| Automated, API client (`src/api/client.test.ts`) | 15 | 15 PASSED |
| Automated, page helper (`src/api/page.test.ts`) | 2 | 2 PASSED |
| Automated, auth flow (`src/auth/auth.flow.test.tsx`) | 12 | 12 PASSED |
| Automated, shared UI (`src/components/ui/components.test.tsx`) | 9 | 9 PASSED |
| Automated, formatters (`src/utils/format.test.ts`) | 5 | 5 PASSED |
| Build and lint (`npm run build`, `npm run lint`) | 2 | 2 PASSED |
| Browser checks in Chromium against a mock of the auth API | 12 | 12 PASSED |
| Manual checks against the real backend and SQL Server | 12 | NOT RUN YET (run before merge) |

Note: the browser checks used a local mock server that copies the backend auth contract (login, refresh with token rotation, `/users/me`, error body `{timestamp, status, message, details}`) with a 4 second access token. The real Spring Boot backend could not be started in the AI workspace (no SQL Server image), so section 3 must be run on a team machine before the PR is merged.

---

## 2. Automated Test Cases

### 2.1 API client: `frontend/src/api/client.test.ts`

#### TC-FE00-001: Build URL from VITE_API_BASE_URL
- Test: `builds URLs from VITE_API_BASE_URL and skips empty query values`
- Steps: call `buildUrl('/members', { q: 'taylor', page: 0, role: '', status: undefined, ids: [1, 2] })`.
- Expected Result: `http://api.test/api/v1/members?q=taylor&page=0&ids=1&ids=2` (empty and undefined values are dropped, arrays repeat the key).
- Status: PASSED

#### TC-FE00-002: Bearer header and JSON parsing
- Test: `sends the access token as a Bearer header and parses JSON`
- Preconditions: access token `access-1` is stored.
- Expected Result: request has `Authorization: Bearer access-1`; the parsed body is returned.
- Status: PASSED

#### TC-FE00-003: No token on /auth endpoints
- Test: `does not send the access token to /auth endpoints`
- Expected Result: `POST /auth/login` is sent without an Authorization header.
- Status: PASSED

#### TC-FE00-004: Silent refresh on 401 and token rotation
- Test: `refreshes once on 401, retries the request and stores the rotated tokens`
- Steps: `GET /users/me` returns 401 for the old token; `POST /auth/refresh` returns a new pair.
- Expected Result: calls are `/users/me`, `/auth/refresh` (body `{refreshToken: 'refresh-1'}`), `/users/me`; the second call succeeds; both new tokens are stored.
- Status: PASSED

#### TC-FE00-005: One refresh for parallel 401 responses
- Test: `uses a single refresh call for parallel requests that fail with 401`
- Steps: three requests fail with 401 at the same time.
- Expected Result: exactly one `POST /auth/refresh`; all three requests succeed after the retry. Needed because the backend rotates the refresh token on every refresh.
- Status: PASSED

#### TC-FE00-006: Rejected refresh token ends the session
- Test: `logs out when the refresh token is rejected`
- Steps: `/auth/refresh` returns 401 "Invalid or expired token".
- Expected Result: `ApiError` 401 "Your session has expired. Please log in again."; both tokens are removed; session expired listeners run once.
- Status: PASSED

#### TC-FE00-007: Only one retry
- Test: `retries only once: a second 401 after refresh also ends the session`
- Expected Result: 3 calls in total (request, refresh, retry); no loop; session cleared.
- Status: PASSED

#### TC-FE00-008: No refresh on 403
- Test: `does not refresh for a 403 response`
- Expected Result: `ApiError` 403 "Access Denied"; one call only; tokens are kept.
- Status: PASSED

#### TC-FE00-009: Validation details become fieldErrors
- Test: `maps validation details to fieldErrors`
- Steps: backend returns 400 `{message: 'Validation Error', details: {email: 'Email is invalid', password: 'Password is required'}}`.
- Expected Result: `error.status = 400`, `error.message = 'Validation Error'`, `error.fieldErrors` equals the details map.
- Status: PASSED

#### TC-FE00-010: Network failure
- Test: `turns a network failure into an ApiError with status 0 and keeps the tokens`
- Expected Result: `ApiError` with status 0 and `isNetworkError = true`; the user is not logged out.
- Status: PASSED

#### TC-FE00-011: 204 No Content
- Test: `returns undefined for 204 No Content`
- Expected Result: the promise resolves to `undefined` without a JSON parse error.
- Status: PASSED

### 2.2 Page helper: `frontend/src/api/page.test.ts`

#### TC-FE00-012: PageResponse is kept as is
- Test: `keeps PageResponse as is`
- Expected Result: `{content, page, size, totalElements, totalPages}` is returned unchanged.
- Status: PASSED

#### TC-FE00-013: Spring Page is normalized
- Test: `maps Spring Page number to page`
- Expected Result: the `number` field of a raw Spring page (returned by `GET /members`) becomes `page`.
- Status: PASSED

### 2.3 Auth flow with the full router: `frontend/src/auth/auth.flow.test.tsx`

#### TC-FE00-014: Manager login lands on the staff layout (acceptance criterion 1)
- Test: `manager login lands on the staff layout`
- Steps: open `/login`, log in with `manager@sportify.com` / `password`.
- Expected Result: URL `/manager`, heading "User Management", side navigation "Manager workspace" with "Users" active and the finance items listed, name "Morgan Lee" in the header, access token stored.
- Status: PASSED

#### TC-FE00-015: Member login lands on the member layout (acceptance criterion 1)
- Test: `member login lands on the member layout`
- Expected Result: heading "Member Dashboard", top navigation "Member navigation" with "Sport Packages", no side navigation.
- Status: PASSED

#### TC-FE00-016: Wrong password
- Test: `shows the backend message when the password is wrong`
- Expected Result: alert "Invalid email or password"; no token stored.
- Status: PASSED

#### TC-FE00-017: Member opens a manager page (acceptance criterion 3)
- Test: `a member opening a manager page sees the 403 page`
- Expected Result: heading "Access denied" and "This page is not available for the Member role."
- Status: PASSED

#### TC-FE00-018: Manager opens a member page (acceptance criterion 3)
- Test: `a manager opening a member page sees the 403 page`
- Expected Result: heading "Access denied".
- Status: PASSED

#### TC-FE00-019: Return to the requested page after login
- Test: `sends anonymous users to login and back to the requested page after login`
- Steps: anonymous user opens `/member/packages`, logs in as a member.
- Expected Result: redirected to the login page first, then to `/member/packages` (heading "Sport Packages").
- Status: PASSED

#### TC-FE00-020: Requested page of another role is ignored
- Test: `ignores a requested page that belongs to another role`
- Steps: anonymous user opens `/manager/finance`, logs in as a member.
- Expected Result: member lands on "Member Dashboard" instead of a 403 page.
- Status: PASSED

#### TC-FE00-021: Session restore with silent refresh (acceptance criterion 2)
- Test: `restores the session after reload and refreshes an expired access token silently`
- Preconditions: stored access token is expired, refresh token is valid.
- Expected Result: calls `/users/me` (401), `/auth/refresh`, `/users/me` (200); "User Management" is shown without the login page; the rotated refresh token is stored.
- Status: PASSED

#### TC-FE00-022: Expired refresh token logs out (acceptance criterion 2)
- Test: `logs out when the refresh token is expired`
- Expected Result: login page "Welcome back"; no tokens left in storage.
- Status: PASSED

#### TC-FE00-023: Log out
- Test: `Log out clears the tokens and returns to the login page`
- Expected Result: login page shown; storage has no tokens.
- Status: PASSED

#### TC-FE00-024: 404 page
- Test: `shows the 404 page for an unknown address`
- Expected Result: heading "Page not found".
- Status: PASSED

#### TC-FE00-025: Root redirect
- Test: `redirects / to the login page for anonymous users`
- Expected Result: login page.
- Status: PASSED

### 2.4 Shared UI: `frontend/src/components/ui/components.test.tsx`

#### TC-FE00-026: Demo page renders every component (acceptance criterion 4)
- Test: `renders every component on the /ui-kit demo page`
- Expected Result: headings Design tokens, Buttons, Inputs, Select, Badges, Table with pagination, Toast, Spinner, Empty state, Error state; table "Demo center accounts"; formatter output "1.350.000 VND" and "04 Jul 2027".
- Status: PASSED

#### TC-FE00-027: Modal and Toast
- Test: `opens the modal, closes it with Escape and shows a toast`
- Expected Result: dialog "Add staff account" opens, Escape closes it, "Success toast" shows "Your changes were saved."
- Status: PASSED

#### TC-FE00-028: Table pagination
- Test: `pages through the demo table`
- Expected Result: after Next, "Demo User 06" is shown and the summary reads "Showing 6-10 of 23".
- Status: PASSED

#### TC-FE00-029: Status badges
- Test: `maps status codes to badge tones and labels`
- Expected Result: ACTIVE is green "Active", PENDING_PAYMENT is orange "Pending payment", EXPIRED is red "Expired", an unknown code is neutral.
- Status: PASSED

#### TC-FE00-030: Page window
- Test: `builds a compact page window with gaps`
- Expected Result: page 6 of 10 gives `1 ... 5 6 7 ... 10` (zero based `[0, null, 4, 5, 6, null, 9]`).
- Status: PASSED

#### TC-FE00-031: Pagination controls
- Test: `disables Previous on the first page and reports the selected page`
- Expected Result: Previous disabled on page 1; page 1 has `aria-current="page"`; clicking "Page 5" calls `onPageChange(4)`.
- Status: PASSED

#### TC-FE00-032: Non dismissible modal
- Test: `keeps a non dismissible modal open on Escape`
- Expected Result: dialog stays open; focus moved inside the dialog.
- Status: PASSED

### 2.5 Formatters: `frontend/src/utils/format.test.ts`

#### TC-FE00-033: Money
- Expected Result: 80000 gives "80.000 VND", 1350000 gives "1.350.000 VND", "2400000.00" gives "2.400.000 VND", null gives "-".
- Status: PASSED

#### TC-FE00-034: LocalDate without time zone shift
- Expected Result: "2027-07-04" gives "04 Jul 2027"; "2026-01-01" stays on day 1.
- Status: PASSED

#### TC-FE00-035: LocalDateTime
- Expected Result: "2026-10-05T17:00:00" gives "05 Oct 2026, 17:00"; time "18:30".
- Status: PASSED

#### TC-FE00-036: Code labels
- Expected Result: "PENDING_PAYMENT" gives "Pending payment".
- Status: PASSED

#### TC-FE00-037: Initials
- Expected Result: "Taylor Nguyen" gives "TN"; empty gives "?".
- Status: PASSED

### 2.6 Session safety and modal edge cases (added after code review)

#### TC-FE00-038: Logout during a running refresh does not restore the tokens
- Test: `does not bring tokens back when the user logs out while a refresh is running` (`client.test.ts`)
- Steps: a request gets 401 and starts the refresh; the user logs out; the refresh then answers 200 with a new pair.
- Expected Result: the new pair is not stored; the request fails with 401; storage stays empty.
- Status: PASSED

#### TC-FE00-039: A late failure of an old request does not log out a new session
- Test: `does not clear a newer session when an old request fails late` (`client.test.ts`)
- Steps: an old request is refreshing; a new login stores new tokens; the old refresh is rejected.
- Expected Result: the new tokens stay in storage and the session expired listener is not called.
- Status: PASSED

#### TC-FE00-040: Server error on refresh keeps the session
- Test: `keeps the session when the refresh endpoint fails with a server error` (`client.test.ts`)
- Expected Result: `ApiError` 500 is thrown; the refresh token is kept; no logout. Only 400, 401 and 403 from `/auth/refresh` end the session.
- Status: PASSED

#### TC-FE00-041: Cancelled request
- Test: `rethrows the abort reason when the caller cancels the request` (`client.test.ts`)
- Expected Result: the abort reason is rethrown as is, not turned into "Cannot reach the server".
- Status: PASSED

#### TC-FE00-042: Stacked modals
- Test: `closes only the top modal on Escape when modals are stacked` (`components.test.tsx`)
- Expected Result: Escape closes the inner modal only; the outer modal stays open.
- Status: PASSED

#### TC-FE00-043: Focus restore after a busy state
- Test: `restores focus to the opener after dismissible changed while open` (`components.test.tsx`)
- Expected Result: after the modal switches to non dismissible (saving) and then closes, focus returns to the button that opened it.
- Status: PASSED

### 2.7 Build and lint

#### TC-FE00-044: Production build (acceptance criterion 5)
- Command: `npm run build`
- Expected Result: `tsc -b` has no errors and Vite writes `dist/`.
- Status: PASSED

#### TC-FE00-045: Lint
- Command: `npm run lint`
- Expected Result: no ESLint errors or warnings.
- Status: PASSED

---

## 3. Manual Test Cases (real backend, run before merge)

Preparation (PowerShell, backend running). Create one member, one receptionist and one coach:

```powershell
$api = "http://localhost:8080/api/v1"
Invoke-RestMethod -Method Post "$api/auth/register" -ContentType "application/json" -Body '{"fullName":"Taylor Nguyen","email":"member@sportify.demo","password":"Demo1234","phone":"0901234567"}'
$token = (Invoke-RestMethod -Method Post "$api/auth/login" -ContentType "application/json" -Body '{"email":"manager@sportify.com","password":"password"}').accessToken
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Post "$api/manager/users" -Headers $h -ContentType "application/json" -Body '{"fullName":"Jamie Tran","email":"reception@sportify.demo","phone":"0901234570","temporaryPassword":"Demo1234","role":"RECEPTIONIST"}'
Invoke-RestMethod -Method Post "$api/manager/users" -Headers $h -ContentType "application/json" -Body '{"fullName":"Daniel Smith","email":"coach@sportify.demo","phone":"0901234568","temporaryPassword":"Demo1234","role":"COACH","sportIds":[1]}'
```

If an account already exists the command returns 409; that is fine.

| ID | Steps | Expected result | Result |
|---|---|---|---|
| TC-FE00-M01 | Open http://localhost:5173, log in `manager@sportify.com` / `password` | URL `/manager`, side menu "Manager workspace" with 9 items, "System Manager" (seeded by V4) and initials "SM" top right | |
| TC-FE00-M02 | Log out, log in `member@sportify.demo` / `Demo1234` | URL `/member`, top tabs Dashboard to My Payments, no side menu | |
| TC-FE00-M03 | Log in `reception@sportify.demo` / `Demo1234` | URL `/reception`, side menu "Receptionist workspace" (Front desk, Payments) | |
| TC-FE00-M04 | Log in `coach@sportify.demo` / `Demo1234` | URL `/coach`, side menu "Coach workspace" with Teaching schedule and Class roster | |
| TC-FE00-M05 | As member, type http://localhost:5173/manager/users in the address bar | 403 "Access denied", URL stays `/manager/users` | |
| TC-FE00-M06 | Log in with a wrong password | Red message "Invalid email or password" | |
| TC-FE00-M07 | Click Log in with empty fields | Messages "Email is required" and "Password is required" under the fields | |
| TC-FE00-M08 | Logged in, open DevTools, Application, Local Storage, change `sportify.accessToken` to `abc`, reload the page | Page reloads without going to login; Network tab shows `/users/me` 401, `/auth/refresh` 200, `/users/me` 200; both tokens in storage have new values | |
| TC-FE00-M09 | Change both `sportify.accessToken` and `sportify.refreshToken` to `abc`, reload | Login page; both keys removed from storage | |
| TC-FE00-M10 | Open http://localhost:5173/ui-kit | All sections render; Open modal, Escape closes it; toasts appear bottom right; table pages with Next | |
| TC-FE00-M11 | Stop the backend, try to log in | Message "Cannot reach the server. Check your connection and try again." | |
| TC-FE00-M12 | Resize the browser to phone width on a staff page | Side menu collapses behind a Menu button, no horizontal scroll | |

---

## 4. Browser Checks Already Run (Chromium, mock auth API)

Run with Playwright against `npm run dev` and a local mock of the auth API (4 second access token).

| Check | Result |
|---|---|
| Manager login lands on `/manager` with the side navigation | PASSED |
| Access token expired after 4 seconds, page reload refreshes silently and stays on `/manager/reports` | PASSED |
| Manager opening `/member` sees 403 | PASSED |
| "Log in with another account" on the 403 page clears the tokens | PASSED |
| Wrong password shows "Invalid email or password" | PASSED |
| Empty form shows field errors from `details` | PASSED |
| Member login lands on `/member` without side navigation | PASSED |
| Rejected refresh token after reload goes to `/login` and clears storage | PASSED |
| `/ui-kit` renders, modal opens and closes with Escape | PASSED |
| Unknown address shows 404 | PASSED |
| No console errors other than the expected 400 and 401 responses | PASSED |
| Receptionist and member layouts at 390 px width: no horizontal scroll, Menu button opens the staff menu | PASSED |
