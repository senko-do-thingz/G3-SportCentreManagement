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

### Backend contract used (checked in the code on `main`, commit 81b9e51)
| Call | Result | Shown as |
|---|---|---|
| `POST /api/v1/auth/login` wrong email or password | 401 `Invalid email or password` | "Invalid email or password. Please try again." above the form |
| `POST /api/v1/auth/login` status not ACTIVE (`CustomUserDetails.isEnabled` and `isAccountNonLocked`) | 403 `Account is disabled` | "This account is locked or inactive. Please contact the front desk to reactivate it." |
| `POST /api/v1/auth/register` email exists | 409 `Email is already registered` | Under the Email address field |
| `POST /api/v1/auth/register` invalid body | 400 `Validation Error`, `details` per field | Under each field (fullName, email, password, phone) |
| `POST /api/v1/auth/register` success | 200 `{accessToken, refreshToken}`, role MEMBER | Tokens stored, `GET /users/me`, then `/member` |

Password rules (`StrongPasswordValidator`): 8 to 64 characters, at most 72 bytes in UTF-8, at least one letter and one digit. Phone (`RegisterRequest`): `^\+?\d{8,15}$`.

### How to run
```powershell
cd frontend
npm install
npm test          # automated cases TC-FE01-001 to TC-FE01-047 (plus the 68 FE-00 tests)
npm run lint
npm run build
```

Manual cases (section 4) need the real backend: `cd backend` then `mvn spring-boot:run`, and in a second terminal `cd frontend` then `npm run dev`, then open http://localhost:5173/login.

### Result summary
| Group | Cases | Result |
|---|---|---|
| Automated, screens (`src/pages/public/authScreens.test.tsx`) | 24 | 24 PASSED |
| Automated, password rules and sign up checks (`src/auth/passwordRules.test.ts`) | 17 | 17 PASSED |
| Automated, token storage and Remember me (`src/api/tokenStorage.test.ts`) | 6 | 6 PASSED |
| Automated, FE-00 regression (6 existing files) | 68 | 68 PASSED |
| Build and lint (`npm run build`, `npm run lint`, `tsc -b`) | 3 | 3 PASSED |
| Browser checks in Chromium against a mock of the auth API | 6 | 6 PASSED |
| Manual checks against the real backend and SQL Server | 14 | NOT RUN YET (run before merge) |

Total automated: 115 Vitest tests in 9 files (47 new). Four guards were broken on purpose (409 mapping, disabled fieldset, 64 character limit, Remember me option) and each made a test fail before it was restored.

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
- Expected Result: tokens in sessionStorage only, nothing in localStorage; `tokenStorage.isPersistent()` is false.
- Status: PASSED

#### TC-FE01-005: Remember me on
- Test: `with Remember me keeps the session on this device`
- Expected Result: tokens in localStorage only; `isPersistent()` is true.
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
- Expected Result: `POST /auth/register` without Authorization, body `{fullName: 'Jamie Tran', email: 'jamie@sportify.demo', password: 'Train2026', phone: '0901234599'}`; then `GET /users/me` with `Bearer access-1`; Member Dashboard shown with the new name; session remembered.
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
- Expected Result: after "abc": letter rule met, digit and length not met, "Password strength: Weak"; after "abc12345": all rules met, "Password strength: Strong"; screen reader text "- met".
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
| TC-FE01-037 | strength label | empty, `abc`, `abcdefgh`, `abcdefg1` | empty, weak, fair, strong | PASSED |
| TC-FE01-038 | complete form | valid values | no errors | PASSED |
| TC-FE01-039 | backend messages for limits | name 101 chars, phone 7 digits, phone 16 digits, email without dot | backend texts; `+` and 15 digits accepted | PASSED |
| TC-FE01-040 | phone separators removed | `(+84) 901-234.567` | `+84901234567`; name and email trimmed | PASSED |
| TC-FE01-041 | password not trimmed | ` Train2026 ` | sent as typed | PASSED |

### 2.4 Token storage and Remember me: `frontend/src/api/tokenStorage.test.ts`

| ID | Test | Expected | Status |
|---|---|---|---|
| TC-FE01-042 | remembered session (default) | localStorage only, `isPersistent` true, same as FE-00 | PASSED |
| TC-FE01-043 | session not remembered | sessionStorage only, readable through `getAccessToken` | PASSED |
| TC-FE01-044 | refresh rotation | rotated pair written to the area of the current session | PASSED |
| TC-FE01-045 | tab session wins | sessionStorage token used even if another tab writes localStorage | PASSED |
| TC-FE01-046 | new login | the other area is cleared, so an old remembered user cannot come back | PASSED |
| TC-FE01-047 | logout | both areas cleared, epoch increased | PASSED |

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

Screens were compared with the Figma exports: same 519 / 619 split of the 1140 px card, photo hero with slogan, brand, headings, field order and footer links.

---

## 4. Manual Test Cases (real backend and SQL Server)

Preconditions: backend on http://localhost:8080 with the `dev` profile, frontend on http://localhost:5173, database migrated (V1 to V20).

| ID | Steps | Expected Result | Status |
|---|---|---|---|
| M01 | Open /login, log in as `manager@sportify.com` / `password` | Manager workspace opens | NOT RUN |
| M02 | Log in with a wrong password | "Invalid email or password. Please try again." | NOT RUN |
| M03 | In SQL set `user_account.status = 'LOCKED'` for a test member, log in | Locked or inactive message, no session | NOT RUN |
| M04 | Same with status `INACTIVE` | Same message | NOT RUN |
| M05 | Log in without Remember me, close the browser, open /member | Login page | NOT RUN |
| M06 | Log in with Remember me, close the browser, open /member | Member Dashboard without logging in again | NOT RUN |
| M07 | Open /register, sign up a new email with valid values | Member Dashboard opens with the new name; row in `user_account` (role MEMBER, status ACTIVE) and `member_profile` (new member code) | NOT RUN |
| M08 | Sign up again with the same email in upper case | "Email is already registered" under Email address | NOT RUN |
| M09 | Sign up with password `password` | Weak password message, no request in the Network tab | NOT RUN |
| M10 | Sign up with phone `0901 234 567` | Saved as `0901234567` | NOT RUN |
| M11 | Press Enter in any field of both forms | Form submits | NOT RUN |
| M12 | Throttle the network (DevTools, Slow 3G) and submit | Button shows the spinner, all fields disabled, only one request | NOT RUN |
| M13 | Stop the backend and submit either form | "Cannot reach the server" message | NOT RUN |
| M14 | Compare both screens side by side with the Figma PNGs at 1880 px wide | Layout, copy and colors match | NOT RUN |
