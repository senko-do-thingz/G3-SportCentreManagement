# Test Cases - Task FE-01 (Flow 1): Log In (F1-05) and Sign Up (F1-06)

## 1. Overview
- Task ID: FE-01
- Flow: Flow 1 - User and membership management (Member)
- Branch: feat/f1-auth-screens
- Priority: P1
- Depends on: FE-00 (frontend foundation, merged)
- Figma: `context/Flow 1 - User and membership management/Flow 1 - Member/F1-05 - Log In.png` and `F1-06 - Sign Up.png`
- Scope:
  - Log in: email, password with Show or Hide, Remember me, Forgot password, messages for wrong credentials (401) and for a locked or inactive account (403).
  - Sign up: full name, phone, email, password with live rules (same rules as the backend `@StrongPassword`), confirm password, accept terms. Calls `POST /auth/register`, signs the new member in with the returned tokens and opens the member dashboard.
  - Backend field errors shown under each field (400 `details`), duplicate email (409) shown under the email field.
  - Enter submits both forms; every control is disabled while the request is running.
  - Review round 3 (N1, N3 to N8): a switch to another account is never silent (redirect to the new role's home page with a notice in every layout, answers of requests from the previous account dropped), sign up form in one functional state update, focus after a server error on Sign up, sample account box only in the development server, empty password meets no rule, history cleanup keeps the query string and hash, `sqlcmd -C`.
  - Review round 2 (R2 to R11): one account per browser with a notice in the tab that was signed out, sample account box from env variables (off by default), Confirm password error follows both fields, backend errors without an input shown above the form, only "Email is already registered" under Email, full height hero photo, focus through a state counter, login prefill read once, "Meets requirements" label, accessible ids.

### Backend contract used (checked in the code on `main`, commit 81b9e51)
| Call | Result | Shown as |
|---|---|---|
| `POST /api/v1/auth/login` wrong email or password | 401 `Invalid email or password` | "Invalid email or password. Please try again." above the form |
| `POST /api/v1/auth/login` status not ACTIVE (`CustomUserDetails.isEnabled` and `isAccountNonLocked`) | 403 `Account is disabled` | "This account is locked or inactive. Please contact the front desk to reactivate it." |
| `POST /api/v1/auth/register` email exists | 409 `Email is already registered` | Under the Email address field |
| `POST /api/v1/auth/register` invalid body | 400 `Validation Error`, `details` per field | Under each field (fullName, email, password, phone) |
| `POST /api/v1/auth/register` success | 200 `{accessToken, refreshToken}`, role MEMBER | Tokens stored, `GET /users/me`, then `/member` |

Password rules (`StrongPasswordValidator`): 8 to 64 characters, at most 72 bytes in UTF-8, at least one letter and one digit. Phone (`RegisterRequest`): `^\+?\d{8,15}$`.

### How to run (PowerShell)
```powershell
cd frontend
npm install
npm test                         # automated cases TC-FE01-001 to TC-FE01-089 (plus the FE-00 tests)
npm run lint -- --max-warnings 0
npm run build
```

Manual cases (section 4) need the real backend and SQL Server; section 4.1 gives the exact steps without Docker.

### Result summary (after review round 3, 2026-10-12)
| Group | Cases | Result |
|---|---|---|
| Automated, screens (`src/pages/public/authScreens.test.tsx`) | 55 (24 round 1, 20 round 2, 11 round 3) | 55 PASSED |
| Automated, password rules and sign up checks (`src/auth/passwordRules.test.ts`) | 24 (17 round 1, 4 round 2, 3 round 3) | 24 PASSED |
| Automated, token storage and Remember me (`src/api/tokenStorage.test.ts`) | 8 (6 round 1, 2 round 2) | 8 PASSED |
| Automated, API client (`src/api/client.test.ts`) | 17 (15 FE-00, 2 round 3; 1 FE-00 expectation updated) | 17 PASSED |
| Automated, other FE-00 files (5 files; 1 test in `auth.flow.test.tsx` updated) | 53 | 53 PASSED |
| Build, lint and types (`npm run build`, `npm run lint -- --max-warnings 0`, `tsc -b`) | 3 | 3 PASSED, 0 warnings |
| Browser checks in Chromium against a mock of the auth API | 8 | 8 PASSED |
| Manual checks against the real backend and SQL Server | 20 | NOT RUN YET (run before merge) |

Total automated: 157 Vitest tests in 9 files (47 from round 1, 26 from round 2, 16 from round 3, 68 from FE-00). Deliberate regressions: round 1 broke 4 guards, round 2 broke 15, round 3 broke 13. Each time at least one test failed before the fix was restored. In round 3 this exposed one weak test: the first N3 test could not fail, because React 19 renders after every DOM event. It was rewritten so that both edits fall in one batch.

Note: the real Spring Boot backend could not be started in the AI workspace (no SQL Server, Maven Central blocked), so section 4 must be run on a team machine before the PR is merged.

---

## 2. Automated Test Cases

### 2.1 Log in screen (F1-05): `frontend/src/pages/public/authScreens.test.tsx`

#### TC-FE01-001: Show or hide the password
- Test: `shows and hides the password with the Show button`
- Steps: type a password, click Show, then Hide.
- Expected Result: input type changes password -> text -> password; the button label changes to "Hide password" and `aria-pressed` is true while visible.
- Status: PASSED

#### TC-FE01-002: Enter key submits the log in form
- Test: `submits with the Enter key`
- Steps: type email and password, press Enter in the password field.
- Expected Result: `POST /auth/login` with `{email, password}`; the Member Dashboard opens.
- Status: PASSED

#### TC-FE01-003: Form disabled while sending, one request only
- Test: `disables the form while sending and sends only one request`
- Preconditions: the login response is held back.
- Expected Result: Log in button disabled with `aria-busy`, email field, Show button and Remember me disabled; clicking Log in again sends no second request.
- Status: PASSED

#### TC-FE01-004: Remember me off
- Test: `without Remember me keeps the session for this tab only`
- Expected Result: tokens in sessionStorage only, nothing in localStorage. (Round 2: the `isPersistent()` check was removed with the method; the storage checks stay.)
- Status: PASSED

#### TC-FE01-005: Remember me on
- Test: `with Remember me keeps the session on this device`
- Expected Result: tokens in localStorage only. (Round 2: `isPersistent()` check removed with the method.)
- Status: PASSED

#### TC-FE01-006: Wrong credentials
- Test: `shows a clear message for wrong credentials and keeps the email`
- Expected Result: alert "Invalid email or password. Please try again."; the email stays filled; the button is enabled again; no session stored.
- Status: PASSED

#### TC-FE01-007: Locked or inactive account
- Test: `shows the locked or inactive account message for HTTP 403`
- Preconditions: account with status LOCKED (backend answers 403 "Account is disabled").
- Expected Result: alert "This account is locked or inactive. Please contact the front desk to reactivate it."; no session stored.
- Status: PASSED

#### TC-FE01-008: Server unreachable
- Test: `shows a network message when the server cannot be reached`
- Expected Result: alert "Cannot reach the server. Check your connection and try again."
- Status: PASSED

#### TC-FE01-009: Forgot password
- Test: `Forgot password explains how to reset without leaving the page`
- Expected Result: info notice asking the user to contact the front desk; no request sent (the backend has no reset endpoint).
- Status: PASSED

#### TC-FE01-010: Focus on the first invalid field
- Test: `moves the focus to the first invalid field`
- Steps: type only the email and press Enter.
- Expected Result: "Password is required" shown and the password field gets the focus.
- Status: PASSED

#### TC-FE01-011: Link to sign up
- Test: `links to the sign up page`
- Expected Result: "Create an account" opens the "Create your account" screen.
- Status: PASSED

### 2.2 Sign up screen (F1-06): `frontend/src/pages/public/authScreens.test.tsx`

#### TC-FE01-012: Successful sign up logs in and opens the dashboard
- Test: `creates the account, logs in and opens the member dashboard`
- Steps: name "  Jamie Tran ", phone "0901 234 599", email " jamie@sportify.demo ", password and confirm "Train2026", accept terms, click Create account.
- Expected Result: `POST /auth/register` without Authorization, body `{fullName: 'Jamie Tran', email: 'jamie@sportify.demo', password: 'Train2026', phone: '0901234599'}`; then `GET /users/me` with `Bearer access-1`; Member Dashboard shown with the new name; tokens in localStorage (remembered).
- Status: PASSED

#### TC-FE01-013: Enter submits, form disabled while sending
- Test: `submits with the Enter key and disables the form while sending`
- Expected Result: Enter in Confirm password sends the form; Create account button disabled with `aria-busy`, fields and the terms checkbox disabled; a second click sends nothing.
- Status: PASSED

#### TC-FE01-014: Duplicate email
- Test: `shows "Email is already registered" under the email field for a duplicate email`
- Expected Result: message under Email address, field marked invalid and linked with `aria-describedby`, focus moved to it, no session, button enabled again.
- Status: PASSED

#### TC-FE01-015: Weak password blocked before the request
- Test: `blocks a weak password with the backend StrongPassword message and sends nothing`
- Steps: password "onlyletters".
- Expected Result: the exact backend message "Password must be 8 to 64 characters long, contain at least one letter and one digit, and not exceed 72 bytes" under Password; no request.
- Status: PASSED

#### TC-FE01-016: Backend field errors under each field
- Test: `shows backend validation details under each field`
- Preconditions: backend answers 400 with `details.password` and `details.phone`.
- Expected Result: each message under its field; the generic "Validation Error" text is not shown.
- Status: PASSED

#### TC-FE01-017: Live password rules and strength
- Test: `updates the password rules and the strength label while typing`
- Expected Result: after "abc": letter rule met, digit and length not met, "Password strength: Weak"; after "abc12345": all rules met, "Meets requirements" (round 2, R10: was "Password strength: Strong"); screen reader text "- met".
- Status: PASSED

#### TC-FE01-018: Required, mismatch and terms messages
- Test: `shows required messages, mismatch and terms errors without calling the backend`
- Expected Result: six required messages, focus on Full name; then "Passwords do not match" for different passwords; no request.
- Status: PASSED

#### TC-FE01-019: Passwords match hint
- Test: `confirms when the two passwords match`
- Expected Result: "Passwords match" under Confirm password.
- Status: PASSED

#### TC-FE01-020: Invalid phone
- Test: `rejects an invalid phone number with the backend message`
- Expected Result: "Phone must be 8-15 digits with optional leading +"; no request.
- Status: PASSED

#### TC-FE01-021: Account created but sign in failed
- Test: `sends the user to log in when the account was created but the sign in failed`
- Preconditions: register succeeds, `GET /users/me` answers 500.
- Expected Result: the Log in screen opens with the notice "Your account was created, but we could not sign you in." and the email prefilled; register was called once; no session kept.
- Status: PASSED

#### TC-FE01-022: Server unreachable
- Test: `shows a network message above the form when the server cannot be reached`
- Expected Result: network message shown; the typed values stay in the form.
- Status: PASSED

#### TC-FE01-023: Signed in member opens /register
- Test: `sends a signed in member away from the sign up page`
- Expected Result: redirected to the Member Dashboard.
- Status: PASSED

#### TC-FE01-024: Link to log in
- Test: `links back to the log in page`
- Expected Result: "Log in" opens the "Welcome back" screen.
- Status: PASSED

### 2.3 Password rules and sign up checks: `frontend/src/auth/passwordRules.test.ts`

| ID | Test | Input | Expected | Status |
|---|---|---|---|---|
| TC-FE01-025 | exactly 8 characters | `Train202` | valid | PASSED |
| TC-FE01-026 | 7 characters | `Train20` | invalid | PASSED |
| TC-FE01-027 | exactly 64 characters | `a1` + 62 x `b` | valid | PASSED |
| TC-FE01-028 | 65 characters | `a1` + 63 x `b` | invalid | PASSED |
| TC-FE01-029 | letters only | `abcdefgh` | invalid | PASSED |
| TC-FE01-030 | digits only | `12345678` | invalid | PASSED |
| TC-FE01-031 | punctuation allowed | `!!!!!!!a1` | valid | PASSED |
| TC-FE01-032 | accented letter counts | `matkhau` + `é` + `1` | valid | PASSED |
| TC-FE01-033 | any Unicode decimal digit counts (Java `Character.isDigit`) | Arabic-Indic digits + `abcde` | valid | PASSED |
| TC-FE01-034 | over 72 UTF-8 bytes | 36 x `é` + `1` (37 chars, 73 bytes) | invalid; 35 x `é` + `1` (71 bytes) valid | PASSED |
| TC-FE01-035 | length in UTF-16 code units like Java | 3 characters outside the BMP + `a1` = 8 units | valid | PASSED |
| TC-FE01-036 | letter outside the BMP not counted (Java checks each `char`) | 4 x `\u{1D400}` + `1234` | invalid | PASSED |
| TC-FE01-037 | progress label (round 2, R10: `passwordProgress`) | empty, `abc`, `abcdefgh`, `abcdefg1` | empty, weak, fair, valid ("Meets requirements") | PASSED |
| TC-FE01-038 | complete form | valid values | no errors | PASSED |
| TC-FE01-039 | backend messages for limits | name 101 chars, phone 7 digits, phone 16 digits, email without dot | backend texts; `+` and 15 digits accepted | PASSED |
| TC-FE01-040 | phone separators removed | `(+84) 901-234.567` | `+84901234567`; name and email trimmed | PASSED |
| TC-FE01-041 | password not trimmed | ` Train2026 ` | sent as typed | PASSED |

### 2.4 Token storage and Remember me: `frontend/src/api/tokenStorage.test.ts`

| ID | Test | Expected | Status |
|---|---|---|---|
| TC-FE01-042 | remembered session (default) | localStorage only, same as FE-00 | PASSED |
| TC-FE01-043 | session not remembered | sessionStorage only, readable through `getAccessToken` | PASSED |
| TC-FE01-044 | refresh rotation | rotated pair written to the area of the current session | PASSED |
| TC-FE01-045 | tab session wins | sessionStorage token used even if another tab writes localStorage | PASSED |
| TC-FE01-046 | new login | the other area is cleared, so an old remembered user cannot come back | PASSED |
| TC-FE01-047 | logout | both areas cleared, epoch increased | PASSED |

### 2.5 Review round 2: `authScreens.test.tsx`, `passwordRules.test.ts`, `tokenStorage.test.ts`

| ID | Finding | Test | Expected Result | Status |
|---|---|---|---|---|
| TC-FE01-048 | R2 | `a login without Remember me removes a remembered session written by another tab (R2)` (tokenStorage) | Session only login in this tab clears the access, refresh and session keys another tab wrote to localStorage | PASSED |
| TC-FE01-049 | R2 | `endTabSession drops only the session of this tab` (tokenStorage) | sessionStorage cleared, localStorage untouched | PASSED |
| TC-FE01-050 | R2 | `a login without Remember me in another tab signs this tab out with a notice` | Remembered member tab receives the storage event, shows Log in with "You were signed out because another account signed in or out in this browser." | PASSED |
| TC-FE01-051 | R2 | `a login or logout in another tab also ends the session this tab keeps for itself` | Tab with a session only login drops its sessionStorage tokens and shows the notice | PASSED |
| TC-FE01-052 | R2 | `a logout in this tab does not show the notice` | Log out in the same tab: Log in screen, no notice | PASSED |
| TC-FE01-053 | R2 | `the notice disappears after the next successful login` | After logging in again and out again, no notice | PASSED |
| TC-FE01-054 | R3 | `is hidden by default` | No sample box, no manager email on the page | PASSED |
| TC-FE01-055 | R3 | `stays hidden when values are set but the flag is not "true"` | Flag `yes` with both values: hidden | PASSED |
| TC-FE01-056 | R3 | `shows the configured account when VITE_SHOW_SAMPLE_ACCOUNT is "true"` | "Sample account: demo.member@sportify.demo - Demo2026" | PASSED |
| TC-FE01-057 | R3 | `stays hidden when the flag is on but a value is empty` | Hidden | PASSED |
| TC-FE01-058 | R4 | `clears the error when the Password field is changed to match` | Exact review steps: Password Train2026, Confirm Train2027, submit, change Password to Train2027: error gone, "Passwords match" shown | PASSED |
| TC-FE01-059 | R4 | `clears the error when the Confirm password field is fixed instead` | Reverse case: Confirm changed to Train2026: error gone, "Passwords match" shown | PASSED |
| TC-FE01-060 | R4 | `keeps the error while the two values still differ` | Error stays | PASSED |
| TC-FE01-061 | R4 | `adds nothing while no error is shown` (unit) | No error while typing for the first time | PASSED |
| TC-FE01-062 | R4 | `clears a shown mismatch as soon as the two values match` (unit) | undefined | PASSED |
| TC-FE01-063 | R4 | `keeps the mismatch while the values still differ` (unit) | "Passwords do not match" | PASSED |
| TC-FE01-064 | R4 | `asks for the confirmation again when the field is emptied` (unit) | "Please confirm your password" | PASSED |
| TC-FE01-065 | R5 | `Sign up shows them above the form and keeps the matched ones under their field` | 400 with `role` and `phone`: "Role is not allowed" above the form, phone message under Phone, button enabled | PASSED |
| TC-FE01-066 | R5 | `Log in shows them above the form` | 400 with only `role`: message above the form, button enabled | PASSED |
| TC-FE01-067 | R6 | `shows another 409 above the form, not under Email` | "The request conflicts with existing data" above the form, Email not invalid | PASSED |
| TC-FE01-068 | R6 | `keeps "Email is already registered" under Email` | Message is a field error under Email | PASSED |
| TC-FE01-069 | R8 | `Log in focuses the field named by a backend validation error` | Server error path: focus on Email after a 400 with `details.email` | PASSED |
| TC-FE01-070 | R9 | `removes the email and notice from history so a reload does not show them again` | History state becomes null; after a simulated reload no notice and empty email | PASSED |
| TC-FE01-071 | R9 | `keeps the requested page in history when it removes the prefill` | History state becomes `{from: '/member/packages'}` | PASSED |
| TC-FE01-072 | R11 | `links the password field to the rules list through a generated id` | `aria-describedby` points to the rules list, id is not the old fixed `password-rules` | PASSED |
| TC-FE01-073 | R11 | `Checkbox keeps the caller describedby together with its error id` | Both ids in `aria-describedby`; description reads the error and the note | PASSED |

Existing focus tests TC-FE01-010, TC-FE01-014 and TC-FE01-018 still pass with the new focus hook (R8). R7 has no automated test; it is covered by browser check B07.

### 2.6 Review round 3

| ID | Finding | Test (file) | Expected Result | Status |
|---|---|---|---|---|
| TC-FE01-074 | N1 | `session only tab of member X follows a remembered login of member Y to the member home with a notice` (authScreens) | Tab A on /member/packages with a session only login of Taylor Nguyen; another tab logs in with Remember me as Jordan Pham: tab A opens Member Dashboard, notice "You are now signed in as Jordan Pham because another account signed in in this browser.", Taylor Nguyen no longer on the page | PASSED |
| TC-FE01-075 | N1 | `remembered tab of member X lands on the manager home when a manager logs in elsewhere` (authScreens) | Tab A opens User Management with the notice for Morgan Lee, not the 403 page | PASSED |
| TC-FE01-076 | N1 | `the same user logging in again in another tab keeps the page and shows nothing` (authScreens) | Profile reloaded, Sport Packages still shown, no notice | PASSED |
| TC-FE01-077 | N1 | `an anonymous tab shows no notice when another tab logs in` (authScreens) | Tab on /login follows to the dashboard without a notice | PASSED |
| TC-FE01-078 | N1 | `a profile request of the old account that answers late does not overwrite the new user` (authScreens) | A reloadUser of account X hangs, the browser switches to Y, X answers: the page keeps Y and the notice for Y | PASSED |
| TC-FE01-079 | N1 | `the notice can be dismissed and is cleared by a logout` (authScreens) | Dismiss hides it; after a new switch, Log out clears it | PASSED |
| TC-FE01-080 | N1 | `drops the answer of a request started before another tab switched the account (N1)` (client) | The request fails with StaleSessionError; the old account's body is never returned | PASSED |
| TC-FE01-081 | N1 | `never retries an old 401 with the token of the new account (N1)` (client) | StaleSessionError, no request sent with the new token | PASSED |
| TC-FE01-082 | N3 | `keeps the Password edit when Confirm password changes in the same event batch` (authScreens) | Both onChange handlers run in one batch: both fields keep Train2026 and "Passwords match" is shown | PASSED |
| TC-FE01-083 | N3 | `chains two edits without losing the first one` (passwordRules) | applyFieldChange keeps both edits | PASSED |
| TC-FE01-084 | N3 | `computes the Confirm password error from the same next values` (passwordRules) | Mismatch error and the edited field error are both cleared | PASSED |
| TC-FE01-085 | N4 | `moves the focus to Phone number for a 400 with details.phone` (authScreens) | Phone number enabled again and focused | PASSED |
| TC-FE01-086 | N5 | `stays hidden when DEV is false even with the flag and both values set` (authScreens) | No sample box | PASSED |
| TC-FE01-087 | N6 | `shows every rule as not met and no progress label while the field is empty` (authScreens) | All four rules `data-met="false"`, no label, also after typing and clearing | PASSED |
| TC-FE01-088 | N6 | `an empty password meets no rule, not even the byte limit (N6)` (passwordRules) | All rules not met for an empty value; the byte rule is met for "a" | PASSED |
| TC-FE01-089 | N7 | `removes the prefill but stays on /login?x=1#top` (authScreens) | History state null, pathname /login, search ?x=1, hash #top | PASSED |

Updated in round 3 (behaviour changed on purpose by N1):
- FE-00 `auth.flow.test.tsx`, `follows a login as another user in another tab`: a manager tab followed by a member login now opens the Member Dashboard with the notice instead of the 403 page.
- FE-00 `client.test.ts`, `does not clear a newer session when an old request fails late`: the old request must still fail with an ApiError, which is now either a 401 or StaleSessionError depending on timing; the checks that the new session is kept and no logout event fires are unchanged.
- R3 tests: the box needs `import.meta.env.DEV`, which is true under Vitest, so no stub was needed for the existing cases. TC-FE01-086 sets DEV to false.

---

## 3. Browser Checks (Chromium, production build, mocked auth API)

Run with `npm run build`, `npx vite preview --port 4173`, and Playwright routing `http://localhost:8080/api/v1/**` to a mock that copies the contract in section 1. Viewport 1880 x 760 and 390 x 900.

| ID | Check | Result |
|---|---|---|
| B01 | Locked account (403) shows the locked or inactive message | PASSED |
| B02 | Wrong password (401) shows the invalid credentials message | PASSED |
| B03 | Duplicate email (409) shown under Email address | PASSED |
| B04 | Weak password shows the StrongPassword message under Password | PASSED |
| B05 | Successful sign up opens `/member` (Member Dashboard) | PASSED |
| B06 | Sign up on a 390 px phone has no horizontal scroll | PASSED |
| B07 | Round 2, R7: the photo covers the full height of the hero column on both screens and the slogan stays readable (screenshot `FE-01_round2_screens.png`) | PASSED |

| B08 | Round 3, N1: two pages in one browser context (they share localStorage and receive each other's storage events). Page A logs in without Remember me as member Taylor Nguyen and opens Sport Packages; page B logs in with Remember me as Jordan Pham: page A goes to /member with the notice for Jordan Pham and Taylor Nguyen is gone. A third page then writes a manager session to localStorage: page A goes to /manager (no 403) with the notice for Morgan Lee; Dismiss hides it (screenshot `FE-01_round3_account_switch.png`) | PASSED |

B01 to B06 were run again after review rounds 2 and 3 on each new production build: all passed. B07 was checked again on the round 3 build (`FE-01_round3_screens.png`).

N5 bundle check: `npm run build` with `VITE_SHOW_SAMPLE_ACCOUNT=true` and sample values set in the environment produced a bundle with 0 occurrences of the email and password. Only the static label `Sample account: ` remains, inside markup that cannot render because `sampleAccount()` returns null in production.

Screens were compared with the Figma exports: same 519 / 619 split of the 1140 px card, photo hero with slogan, brand, headings, field order and footer links.

---

## 4. Manual Test Cases (real backend and SQL Server)

### 4.1 Start everything without Docker (PowerShell)

Preconditions: SQL Server running locally with a `sportify` database (see the root README, section on SQL Server without Docker), JDK 25, Maven 3.9+, Node.js 24. `backend/.env` filled from `backend/.env.example`.

Terminal 1, backend:
```powershell
cd backend
mvn spring-boot:run
```
Wait for `Started` in the log. The `dev` profile is active by default and Flyway applies the migrations.

Terminal 2, frontend:
```powershell
cd frontend
npm install
npm run dev
```
Open http://localhost:5173/login.

Change the status of a test user for M03 and M04 (in SSMS, or with `sqlcmd` in PowerShell; replace the email with the account you created in M07):
```powershell
sqlcmd -S localhost -d sportify -U sa -P "<DB_PASSWORD from backend/.env>" -C -Q "UPDATE user_account SET status = 'LOCKED' WHERE email = 'jamie@sportify.demo'; SELECT email, status FROM user_account WHERE email = 'jamie@sportify.demo';"
```
`-C` trusts the server certificate. sqlcmd 18 (ODBC Driver 18) encrypts the connection by default and otherwise fails with the self signed certificate of a local SQL Server. The same SQL can also be run in SSMS: connect to `localhost`, open a New Query on the `sportify` database, paste the statements below and press F5.
```sql
-- M03: lock the account
UPDATE user_account SET status = 'LOCKED' WHERE email = 'jamie@sportify.demo';
-- M04: make it inactive
UPDATE user_account SET status = 'INACTIVE' WHERE email = 'jamie@sportify.demo';
-- Restore after the test
UPDATE user_account SET status = 'ACTIVE' WHERE email = 'jamie@sportify.demo';
```
The backend accepts any status other than `ACTIVE` as disabled (`CustomUserDetails.isEnabled` and `isAccountNonLocked`), so both return 403.

Sample account box for M17 and M18: edit `frontend/.env`, then stop `npm run dev` (Ctrl+C) and start it again, because Vite reads `.env` only at start up.

### 4.2 Cases

| ID | Steps | Expected Result | Status |
|---|---|---|---|
| M01 | Open /login, log in as the seeded manager (`manager@sportify.com`, password from migration V4) | Manager workspace opens | NOT RUN |
| M02 | Log in with a wrong password | "Invalid email or password. Please try again." | NOT RUN |
| M03 | Run the LOCKED SQL from 4.1 for the M07 account, then log in with it | "This account is locked or inactive. Please contact the front desk to reactivate it.", no session | NOT RUN |
| M04 | Run the INACTIVE SQL from 4.1, log in again; restore ACTIVE afterwards | Same message | NOT RUN |
| M05 | Log in without Remember me, then close the TAB (not the whole browser), open a new tab on http://localhost:5173/member | Login page. Note: Chrome and Edge with "Continue where you left off" (Settings, On start up) restore sessionStorage when the browser is reopened, so closing the browser is not a valid test of this case | NOT RUN |
| M06 | Log in with Remember me, close the browser, open http://localhost:5173/member | Member Dashboard without logging in again | NOT RUN |
| M07 | Open /register, sign up `jamie@sportify.demo` with valid values | Member Dashboard opens with the new name; row in `user_account` (role MEMBER, status ACTIVE) and in `member_profile` (new member code) | NOT RUN |
| M08 | Sign up again with `JAMIE@sportify.demo` | "Email is already registered" under Email address | NOT RUN |
| M09 | Sign up with password `password` | Weak password message, no request in the DevTools Network tab | NOT RUN |
| M10 | Sign up with phone `0901 234 567` | Saved as `0901234567` | NOT RUN |
| M11 | Press Enter in any field of both forms | Form submits | NOT RUN |
| M12 | DevTools, Network, throttling Slow 3G, submit either form | Spinner on the button, all fields disabled, one request only | NOT RUN |
| M13 | Stop the backend (Ctrl+C in terminal 1) and submit either form | "Cannot reach the server" message | NOT RUN |
| M14 | Compare both screens with the Figma PNGs at 1880 px wide | Layout, copy, colors and full height photo match | NOT RUN |
| M15 | R2: tab A logs in with Remember me as the M07 member. Tab B (same browser) logs in without Remember me as the manager. Switch back to tab A | Tab A shows the Log in screen with "You were signed out because another account signed in or out in this browser." | NOT RUN |
| M16 | R2: tabs A and B both on the same remembered session. Click Log out in tab A | Tab A: Log in screen without the notice. Tab B: Log in screen with the notice | NOT RUN |
| M17 | R3: no `frontend/.env` (or `VITE_SHOW_SAMPLE_ACCOUNT=false`), restart `npm run dev`, open /login | No sample account box | NOT RUN |
| M18 | R3: in `frontend/.env` set `VITE_SHOW_SAMPLE_ACCOUNT=true`, `VITE_SAMPLE_ACCOUNT_EMAIL` and `VITE_SAMPLE_ACCOUNT_PASSWORD` to a demo member, restart `npm run dev`, open /login. Then run `npm run build` and `npm run preview` and open the preview's /login | Dev server: box shows "Sample account: <email> - <password>". Preview of the production build: no box (N5). Remove the values afterwards and do not commit `.env` (`git status` must not list it) | NOT RUN |
| M19 | N1: in Chrome, tab A logs in without Remember me as member A (for example the M07 account) and opens Sport Packages. Tab B (same window) logs in with Remember me as member B. Switch back to tab A and click Dismiss | Tab A shows the Member Dashboard (not Sport Packages) of member B with "You are now signed in as <member B> because another account signed in in this browser."; member A's name is nowhere on the page; Dismiss hides the notice. Note: if tab B logs out first, tab A shows the signed out notice instead (M15, M16), and a later login only redirects tab A without a switch notice, because tab A was anonymous | NOT RUN |
| M20 | N1: tab A logs in without Remember me as member A and opens Sport Packages. Tab B (same window, still anonymous because A's session lives only in tab A) logs in with Remember me as the seeded manager. Switch back to tab A | Tab A opens the manager home (User Management) with "You are now signed in as <manager name> because another account signed in in this browser.", never the 403 page of /member/packages | NOT RUN |
