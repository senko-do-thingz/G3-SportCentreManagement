# Test Cases - Task FE-02 (Flow 1): Member Dashboard (F1-07) and My Profile (F1-08)

## 1. Overview
- Task ID: FE-02
- Flow: Flow 1 - User and membership management (Member)
- Branch: feat/f1-member-dashboard
- Owner: Phat
- Priority: P1
- Depends on: FE-01 (merged), BE-02 (not merged yet, contract assumed in section 1.1)
- Figma: `context/Flow 1 - User and membership management/Flow 1 - Member/F1-07 - Member Dashboard.png`, `F1-07 State - New Standard Member.png`, `F1-07 State - After Purchase.png`, `F1-08 - My Profile.png`
- Acceptance criteria:
  - AC1: a new member sees the "New Standard Member" state; after a purchase the "After Purchase" state.
  - AC2: pending items link to the payment summary.
  - AC3: profile edit and password change show success and error messages.

### 1.1 Backend contract used
| Call | Status on main (commit 02f7615) | Used for |
|---|---|---|
| `GET /api/v1/users/me` | Exists. Returns id, email, fullName, phone, role, status. BE-02 adds dateOfBirth, address, mainGoal, currentLevel, memberCode (all optional on the frontend) | Name in the hero, profile form |
| `GET /api/v1/membership-cards/my` | Exists (MEMBER) | Tier stat, pending card requests, state |
| `GET /api/v1/packages/registrations/my` | Exists (MEMBER) | Active packages, pending packages, state |
| `GET /api/v1/bookings/my?when=UPCOMING` | Exists without the filter; `when` comes with BE-12. The frontend filters again (CONFIRMED and start time from now) | Upcoming sessions |
| `PUT /api/v1/users/me` | Planned in BE-02. Assumed body `{fullName, phone, dateOfBirth, address, mainGoal, currentLevel}` (phone, dateOfBirth, address, mainGoal, currentLevel may be null), answer `UserProfileResponse` or 204, 400 `Validation Error` with `details` | Save changes |
| `PUT /api/v1/users/me/password` | Planned in BE-02. Assumed body `{currentPassword, newPassword}`, answer 200 or 204. Preferred: wrong current password as 400 with `details.currentPassword`. Since review round 2 the request uses `authFailure: 'refresh-then-throw'`: a 401 refreshes the token once and retries once; a 401 on the retry is shown under Current password without signing out; a rejected refresh token still signs out | Change password |

State rules: New Standard Member = no paid card (tier other than STANDARD, not CANCELLED; a missing tier code counts as paid) and no package registration (not CANCELLED). After Purchase = anything else, including a request in PENDING_PAYMENT. The tier shown is the ACTIVE card whose dates contain today (highest discount wins), otherwise "Standard".

Hero (review rounds 1 and 2): welcome (New Standard Member), book (at least one package can be booked: ACTIVE, not ended, sessions left or no limit), pay (nothing to book, at least one pending item: Pay now to the first item's summary, or "View pending payments" to the block when it has no payment id), first-package (round 2: a paid card but never a paid package, "Choose your first sport package."), renew (the member had packages that are now used up or ended).

Active packages stat (round 2): the value counts the same packages as the list (ACTIVE, not ended); when some have no sessions left the hint says "<n> can be booked - <m> with no sessions left".

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm install
npm test                         # automated cases below plus FE-00 and FE-01 tests
npm run lint -- --max-warnings 0
npm run build
```

### 1.3 Result summary (2026-10-13, after review round 2)
| Group | Cases | Result |
|---|---|---|
| Automated, dashboard rules (`src/pages/member/dashboardModel.test.ts`) | 21 (2 new in round 2, 1 changed) | 21 PASSED |
| Automated, dashboard screen (`src/pages/member/memberDashboard.test.tsx`) | 28 (4 new in round 2) | 28 PASSED |
| Automated, profile screen (`src/pages/member/myProfile.test.tsx`) | 26 (2 new in round 2, 1 changed) | 26 PASSED |
| Automated, profile validation (`src/pages/member/profileValidation.test.ts`) | 10 | 10 PASSED |
| Automated, load hook (`src/pages/member/useMemberResource.test.tsx`) | 5 | 5 PASSED |
| Automated, menu lookup (`src/layouts/navigation.test.ts`) | 2 | 2 PASSED |
| Automated, API client (`src/api/client.test.ts`) | 23 (the round 1 FE-02 test replaced by 6 tests in round 2) | 23 PASSED |
| Automated, other FE-00 and FE-01 files | 140 | 140 PASSED |
| Lint, type check and build | 3 | 3 PASSED, 0 warnings |
| Deliberate regressions (section 3) | 10 (first round) + 14 (round 1) + 6 (round 2) | 30 caught |
| Browser checks in Chromium against a mock API (section 4) | 10 (rounds 0 and 1; B10 superseded, see section 4) | 10 PASSED when run |
| Manual checks against the real backend (section 5) | 14 | NOT RUN YET (needs BE-02 merged and SQL Server) |

Total: 255 Vitest tests in 15 files. 13 added in review round 2 (242 before): 14 new tests, 1 round 1 client test removed with the option it tested.

---

## 2. Automated Test Cases

### 2.1 Dashboard rules - `frontend/src/pages/member/dashboardModel.test.ts`
Today is fixed at 2026-10-13, now at 2026-10-13 09:30.

| ID | Case | Expected result | Result |
|---|---|---|---|
| TC-FE02-001 | toIsoDate at 23:59 local time | "2026-01-05", no time zone shift | PASSED |
| TC-FE02-002 | No cards, no packages | new-standard | PASSED |
| TC-FE02-003 | Only a STANDARD card, a cancelled card and a cancelled package | new-standard | PASSED |
| TC-FE02-004 | One package in PENDING_PAYMENT | after-purchase | PASSED |
| TC-FE02-005 | One paid card request in PENDING_PAYMENT | after-purchase | PASSED |
| TC-FE02-006 | Current card: ACTIVE and inside dates; pending, ended yesterday, starts tomorrow are not current; ends today is current | VIP or null as listed | PASSED |
| TC-FE02-007 | Gold 5% and VIP 10% both in force, both orders | VIP | PASSED |
| TC-FE02-008 | Active packages from a mixed list | Only ACTIVE not past end date, sorted by end date: ids 4, 1 | PASSED |
| TC-FE02-009 | startsLater | true only for a start after today | PASSED |
| TC-FE02-010 | sessionShare (changed in round 1) | 6 of 8 gives 0.75; clamped to 1; null remaining gives 0; no limit gives null; total 0 gives 0; 0 of 8 gives 0 | PASSED |
| TC-FE02-011 | Pending items from a pending card and a pending package | Newest first, titles "Sport package - Football - Coach-led" and "Membership card - VIP", payment codes, amounts "810.000 VND" and "600.000 VND", payment ids | PASSED |
| TC-FE02-012 | Pending package without payment | paymentId null, reference is the registration code | PASSED |
| TC-FE02-013 | Payment summary path | /member/payments/52 | PASSED |
| TC-FE02-014 | Upcoming bookings from past, cancelled, invalid and future bookings | Only confirmed from now on, soonest first: ids 3, 1 | PASSED |
| TC-FE02-015 | Labels | Self-training, humanized unknown code, title without format, "17:00" | PASSED |
| TC-FE02-016 | remainingSessionCount and isExhausted (round 1, B4, m4) | 12 of 8 gives 8; -2 gives 0; total 0 gives 0; no limit gives null; 0 of 8 and 0 of 0 are exhausted | PASSED |
| TC-FE02-017 | canBook (round 1, B4) | True for ACTIVE with sessions, no limit, or later start; false for 0 left, total 0, ended, pending | PASSED |
| TC-FE02-018 | isPaidCard with a missing tier code (round 1, m3) | null tier code counts as paid; "standard" in lower case is free; cancelled is not paid; state after-purchase | PASSED |
| TC-FE02-019 | heroVariant (round 1, B4, M2; changed in round 2: takes a facts object) | welcome, book, pay (with or without a past package), renew | PASSED |
| TC-FE02-120 | heroVariant with a paid card and no package ever (round 2, R2-M1) | first-package, not renew | PASSED |
| TC-FE02-121 | hasHadPackage (round 2, R2-M1) | False for none, pending and cancelled; true for ACTIVE or EXPIRED | PASSED |

### 2.2 Dashboard screen - `frontend/src/pages/member/memberDashboard.test.tsx`
The whole app is rendered at `/member` with a member session and a fake backend.

| ID | AC | Case | Expected result | Result |
|---|---|---|---|---|
| TC-FE02-020 | AC1 | New member (no cards, packages or bookings) | data-state new-standard; hero "Welcome, Taylor Nguyen."; tier Standard with "Explore cards for package discounts"; 0 active packages; 0 sessions; "No sport packages yet" with Explore packages link to /member/packages; "No upcoming sessions"; no Pending payments block | PASSED |
| TC-FE02-021 | AC1 | VIP card, one active package (6 of 8 sessions), one booking | data-state after-purchase; hero "Ready for your next session?"; tier VIP "Active - Until 04 Jul 2027 - 10% off packages"; package REG-0011 "Football - Coach-led", "Football 30 days - Ends 30 Oct 2026", "6 of 8 sessions remaining", progress bar 6 of 8, Book a session and Renew package links; session "14 Oct 2026, 17:00 to 18:30 - BK-0021" | PASSED |
| TC-FE02-022 | AC1 | Active package | Hero button Book a session to /member/schedule | PASSED |
| TC-FE02-023 | AC2 | Package in PENDING_PAYMENT with payment 52 | Pending payments block with title, "PAY-0052 - 810.000 VND", Pending payment badge, link "Pay now" to /member/payments/52; click opens My Payments and Receipts, not a 404 | PASSED |
| TC-FE02-024 | AC2 | Card request in PENDING_PAYMENT with payment 51 | "Pay now" to /member/payments/51; tier stays Standard | PASSED |
| TC-FE02-025 | AC2 | Pending package without payment id | "Pay at the front desk", no Pay now link | PASSED |
| TC-FE02-026 | - | Package that starts on 20 Oct 2026 | Badge "Starts 20 Oct 2026"; counted as active | PASSED |
| TC-FE02-027 | - | Self-training package without a session limit | "No session limit", no progress bar | PASSED |
| TC-FE02-028 | - | Calls | GET /users/me, /membership-cards/my, /packages/registrations/my, /bookings/my?when=UPCOMING, all with Bearer token | PASSED |
| TC-FE02-029 | - | Six bookings: past, cancelled and four future | Three listed, past and cancelled hidden, stat shows 4 | PASSED |
| TC-FE02-030 | - | Cards endpoint returns 500, then Try again with the backend fixed | Alert "We could not load your dashboard" with the backend message; after retry the dashboard shows VIP | PASSED |
| TC-FE02-031 | - | Bookings endpoint returns 503 | Only the Upcoming sessions block shows an error; stat "-" with "Could not load your bookings"; packages still shown | PASSED |
| TC-FE02-032 | - | Registrations endpoint returns an object instead of a list | Error "The server returned an unexpected response.", never an empty dashboard | PASSED |
| TC-FE02-033 | - | Quick links and Edit profile | Links to membership, packages, schedule, payments; Edit profile opens My Profile | PASSED |
| TC-FE02-034 | AC1 | Only an expired card | Tier Standard; state after-purchase | PASSED |
| TC-FE02-035 | B4 | Package with 0 of 8 sessions left | Badge "No sessions left", "0 of 8 sessions remaining", no Book a session, Renew package to /member/packages; hero variant renew "Find your next sport package." with Explore packages | PASSED |
| TC-FE02-036 | B4 | Package with a total of 0 sessions | "0 of 0 sessions remaining", "No sessions left", never "No session limit", no progress bar, no Book a session | PASSED |
| TC-FE02-037 | B4 | One exhausted and one bookable package | Hero "Ready for your next session?" with Book a session | PASSED |
| TC-FE02-038 | M2, AC2 | Only a pending package with payment 52 | Hero "Complete your payment to start training.", no "Welcome back", Pay now to /member/payments/52 | PASSED |
| TC-FE02-039 | M2 | Only a pending card without payment | Hero link "View pending payments" to #pending-payments, which is the Pending payments block | PASSED |
| TC-FE02-090 | M1 | View all packages | href /member/membership (F1-12, default tab Sport packages, "My registrations") | PASSED |
| TC-FE02-091 | m4 | 12 remaining of 8 | aria-valuenow 8, aria-valuemax 8, "8 of 8 sessions remaining" | PASSED |
| TC-FE02-092 | m6 | Cards fail, bookings load, then Try again | Cards called twice, bookings called once, Upcoming sessions shows 1 | PASSED |
| TC-FE02-093 | m6 | Cards and bookings fail, then Try again | Bookings called again and shown | PASSED |
| TC-FE02-122 | R2-M1 | ACTIVE VIP card, no registrations | Hero data-variant first-package, title "Choose your first sport package.", text "Your VIP card gives you 10% off 30 day and 90 day packages. Pick a sport to start training.", Explore packages to /member/packages; no "no sessions left" text | PASSED |
| TC-FE02-123 | R2-M1 | Only an expired card, no registrations | Hero first-package with "Pick a sport and a training style to start training." | PASSED |
| TC-FE02-124 | R2-m1 | One package with 0 sessions left | Active packages 1 (same as the list), hint "0 can be booked - 1 with no sessions left" | PASSED |
| TC-FE02-125 | R2-m1 | One bookable package | Hint "Your paid sport access" | PASSED |

### 2.3 My Profile screen - `frontend/src/pages/member/myProfile.test.tsx`

| ID | AC | Case | Expected result | Result |
|---|---|---|---|---|
| TC-FE02-040 | - | Load profile with BE-02 fields | All fields filled; email read only; summary shows TN and "Member code MEM-0002" | PASSED |
| TC-FE02-041 | - | Profile without BE-02 fields and an unknown goal code | Empty date and address; selects show Not set | PASSED |
| TC-FE02-042 | AC3, m5 | Edit name (with spaces), phone with spaces, level, clear address, Save changes (changed in round 1) | PUT /users/me body `{fullName: "Taylor Tran", phone: "0901234999", dateOfBirth: "2004-06-15", address: null, mainGoal: "IMPROVE_FITNESS", currentLevel: "INTERMEDIATE"}`; inline "Your profile was saved." with no role and no live ancestor; exactly one live region (the polite toast list) contains the message; header and summary show the new name | PASSED |
| TC-FE02-043 | AC3, m1 | Empty name, phone "12ab", date of birth 2030-01-01 (changed in round 1: message) | Three field errors ("Date of birth must be before today"), no PUT, focus on Full name | PASSED |
| TC-FE02-044 | AC3 | Edit a field that has an error | Its error disappears | PASSED |
| TC-FE02-045 | AC3 | Backend 400 with details for phone and an unknown key | Phone message under Phone (focused); unknown message above the form; no success message | PASSED |
| TC-FE02-046 | AC3 | Backend 500 | "Internal server error" above the form; Save changes enabled again | PASSED |
| TC-FE02-047 | - | Submit while saving | Fieldset disabled, aria-busy, second submit sends nothing; success after the answer | PASSED |
| TC-FE02-048 | AC3 | Backend answers 204 without a body | Success message; the sent address stays | PASSED |
| TC-FE02-049 | - | Page GET /users/me returns 503, then Try again | Alert "We could not load your profile"; retry shows the form | PASSED |
| TC-FE02-050 | B3, AC3 | Member without a phone changes the address | Phone not required; PUT body has phone null and the new address; success message | PASSED |
| TC-FE02-051 | B3 | Phone "1234567" | Pattern message, no PUT | PASSED |
| TC-FE02-052 | m1 | Date of birth today | Picker max 2026-10-12, min 1900-01-01; "Date of birth must be before today" | PASSED |
| TC-FE02-053 | m2 | maxLength attributes | Full name 100, Address 255 (from FULL_NAME_MAX_LENGTH and ADDRESS_MAX_LENGTH) | PASSED |
| TC-FE02-060 | AC3, m5 | Valid password change (changed in round 1) | PUT /users/me/password body `{currentPassword, newPassword}`; inline message not live, one live announcement through the toast; three fields cleared | PASSED |
| TC-FE02-061 | AC3, B2 | Backend 400 with `details.currentPassword` (changed in round 1: was a 400 without details) | Message under Current password (focused); new password kept; no success message | PASSED |
| TC-FE02-062 | AC3 | Submit empty | Three required messages, no request | PASSED |
| TC-FE02-063 | AC3 | Weak password and mismatch, then new equal to current | Strong password message and "Passwords do not match"; then "New password must be different from the current password" | PASSED |
| TC-FE02-064 | AC3 | Backend 400 details for newPassword | Message under New password | PASSED |
| TC-FE02-065 | AC3 | Backend 500 | Message above the form, button enabled again | PASSED |
| TC-FE02-066 | - | Type a new password | Live rules update; "Meets requirements" when valid | PASSED |
| TC-FE02-067 | R2-B1, AC3 | Wrong password: PUT /users/me/password always answers 401 (changed in round 2) | 401 -> one /auth/refresh -> retry with the new token -> 401; "Current password is incorrect" under Current password (focused); exactly one refresh; tokens rotated to member-access-2 and member-refresh-2; still on My Profile; Save changes still works afterwards | PASSED |
| TC-FE02-080 | R2-B1, AC3 | Expired access token, correct password: only the refreshed token is accepted | 401 -> refresh -> retry 204; one success announcement; one refresh, two password calls; tokens rotated; no field error; form cleared | PASSED |
| TC-FE02-081 | R2-B1 | Password call 401 and the refresh token rejected | Signed out like any other request: login page "Welcome back", tokens cleared | PASSED |
| TC-FE02-068 | B2 | 400 "Current password is incorrect" without details | Shown above the form (role alert), not under the field | PASSED |
| TC-FE02-069 | B2 | 400 "Password change is not allowed right now" without details | Shown above the form; Current password not marked invalid | PASSED |

### 2.4 Profile validation - `frontend/src/pages/member/profileValidation.test.ts`

| ID | Case | Expected result | Result |
|---|---|---|---|
| TC-FE02-070 | Complete profile; optional fields empty | No errors | PASSED |
| TC-FE02-071 | Full name empty, 101 and 100 characters | Required, max 100, valid | PASSED |
| TC-FE02-072 | Phone empty, blank, 7 digits, "(+84) 901-234-567" (changed in round 1, B3) | No error, no error, pattern message, valid | PASSED |
| TC-FE02-073 | Date of birth yesterday, today, tomorrow, 1899-12-31, "15/06/2004" (changed in round 1, m1) | Valid, "Date of birth must be before today" twice, invalid, invalid | PASSED |
| TC-FE02-074 | Address of 256 characters | Max 255 message | PASSED |
| TC-FE02-075 | Request building | Trimmed, phone without separators, empty optionals null | PASSED |
| TC-FE02-076 | Form from profile | Null phone empty, unknown goal empty, known level kept | PASSED |
| TC-FE02-077 | Password change rules | Valid, weak, same as current, mismatch; request keeps passwords as typed | PASSED |
| TC-FE02-078 | latestDateOfBirth (round 1, m1) | 2026-10-12, 2026-02-28, 2026-12-31 | PASSED |
| TC-FE02-079 | Empty phone and length messages (round 1, B3, m2) | Phone sent as null; messages built from FULL_NAME_MAX_LENGTH and ADDRESS_MAX_LENGTH | PASSED |

### 2.5 Load hook - `frontend/src/pages/member/useMemberResource.test.tsx` (new in round 1, M4)

| ID | Case | Expected result | Result |
|---|---|---|---|
| TC-FE02-100 | First load | loading, then ready with the data | PASSED |
| TC-FE02-101 | Unmount before the answer | Request aborted; the late answer sets no state; no React warning | PASSED |
| TC-FE02-102 | Reload twice, answers arrive newest first then older | loading right after reload; older request aborted; result is the newest data | PASSED |
| TC-FE02-103 | Loader changes after data was shown | loading at once, old request aborted, never the old data; then the new data | PASSED |
| TC-FE02-104 | StaleSessionError | Error state with "The signed in account changed while this request was running. Please try again."; Retry loads again | PASSED |

### 2.6 API client and menu lookup (new in round 1)

| ID | File | Case | Expected result | Result |
|---|---|---|---|---|
| TC-FE02-105 | `frontend/src/api/client.test.ts` (round 2) | authFailure refresh-then-throw, expired token | 401 -> refresh -> retry succeeds; tokens rotated | PASSED |
| TC-FE02-106 | `frontend/src/api/client.test.ts` (round 2) | authFailure refresh-then-throw, retry answers 401 | ApiError 401 after exactly one refresh; calls password, refresh, password; tokens are the rotated ones; session expired listener not called | PASSED |
| TC-FE02-107 | `frontend/src/api/client.test.ts` (round 2) | authFailure refresh-then-throw, refresh rejected | ApiError 401; tokens cleared; session expired listener called once | PASSED |
| TC-FE02-108 | `frontend/src/api/client.test.ts` (round 2) | authFailure refresh-then-throw, refresh answers 503 | ApiError 503; tokens kept; no sign out | PASSED |
| TC-FE02-109 | `frontend/src/api/client.test.ts` (round 2) | Password change and GET /users/me in parallel, both 401 | One refresh call shared; both succeed | PASSED |
| TC-FE02-112 | `frontend/src/api/client.test.ts` (round 2) | Same request without the option, retry answers 401 | Session ends as before (default unchanged) | PASSED |
| TC-FE02-110 | `frontend/src/layouts/navigation.test.ts` | requireNavItem for payments | Returns "My Payments and Receipts" | PASSED |
| TC-FE02-111 | `frontend/src/layouts/navigation.test.ts` | requireNavItem for a missing path | Throws 'Menu item "missing" is missing from /member in layouts/navigation.ts' | PASSED |

---

## 3. Changes to existing tests and deliberate regressions

### 3.1 First round (commit 9babb12)
Correction: the first round changed 5 FE-00 and FE-01 assertions (1 in `auth.flow.test.tsx`, 4 in `authScreens.test.tsx`), not 4 as written before. They selected the account switch notice by its class.

Deliberate regressions, each applied alone and reverted: dashboardState always after-purchase (3 failed), payment path without id (3), currentCard ignores dates (1), upcoming keeps cancelled (2), no profile success message (3), wrong current password not mapped (1), no reloadUser (1), password form not cleared (1), non list treated as empty (1), payment route removed (1). 10 of 10 caught.

### 3.2 Review round 1
Existing tests changed in round 1:
- `frontend/src/auth/auth.flow.test.tsx` (1 assertion) and `frontend/src/pages/public/authScreens.test.tsx` (4 assertions): reverted to the code on main and changed only the query from `getByRole('status')` to `getByRole('status', { name: 'Account switched' })`. Each assertion still checks the same notice text in the same situation. Production change: `AccountSwitchNotice` gains `aria-label="Account switched"` (M3).
- `dashboardModel.test.ts` TC-FE02-010: a total of 0 now gives 0 instead of null (B4).
- `profileValidation.test.ts` TC-FE02-072 (phone optional, B3) and TC-FE02-073 (today rejected, m1).
- `myProfile.test.tsx` TC-FE02-042 and TC-FE02-060 (single announcement, m5), TC-FE02-043 (new date of birth message, m1), TC-FE02-061 (400 now carries `details.currentPassword`, B2).

Deliberate regressions, each applied alone and reverted, full `npx vitest run`:

| # | Code broken on purpose | Item | Tests that failed |
|---|---|---|---|
| 1 | Client ignores skipAuthRefresh | B1 | 2 |
| 2 | changePassword does not pass skipAuthRefresh | B1 | 1 |
| 3 | 401 not mapped to Current password | B1, B2 | 1 |
| 4 | Phone required again | B3 | 2 |
| 5 | canBook ignores used up packages | B4 | 3 |
| 6 | Total of 0 treated as no limit | B4 | 4 |
| 7 | Hero pay variant removed | M2 | 3 |
| 8 | Hook shows old data after reload | M4 | 3 |
| 9 | Hook accepts late answers | M4 | 1 |
| 10 | aria-valuenow not clamped | m4 | 1 |
| 11 | Inline success notice live again | m5 | 2 |
| 12 | Retry reloads bookings that loaded | m6 | 1 |
| 13 | Today accepted as date of birth | m1 | 2 |
| 14 | Account switch notice without accessible name | M3 | 5 |

---

### 3.3 Review round 2
Existing tests changed in round 2:
- `frontend/src/api/client.test.ts`: the round 1 test "FE-02 skipAuthRefresh" was removed together with the option; replaced by TC-FE02-105 to TC-FE02-109 and TC-FE02-112. The 17 client tests from FE-00 and FE-01 are unchanged and pass.
- `frontend/src/pages/member/myProfile.test.tsx` TC-FE02-067: a 401 now refreshes once and retries; the test expects exactly one refresh and rotated tokens (was: no refresh). The fake backend gained a POST /auth/refresh handler.
- `frontend/src/pages/member/dashboardModel.test.ts` TC-FE02-019: heroVariant now takes a facts object; one extra assertion for pay without a past package.

Deliberate regressions, each applied alone and reverted, full `npx vitest run`:

| # | Code broken on purpose | Item | Result |
|---|---|---|---|
| 1 | A 401 on the retry ends the session again | R2-B1 | 2 tests failed |
| 2 | changePassword does not pass the option | R2-B1 | 1 test failed |
| 3 | The option skips the refresh (round 1 behavior) | R2-B1 | 8 tests failed |
| 4 | first-package branch removed | R2-M1 | 3 tests failed |
| 5 | Active packages hint always the default | R2-m1 | 1 test failed |
| 6 | My Payments menu entry renamed | R2-m2 | navigation test failed and 5 test files that render the app failed to load (module load error) |

## 4. Browser checks (Chromium 1280x900 and 390x800, mock API on port 8099)

| ID | Check | Result |
|---|---|---|
| TC-FE02-B01 | Log in as a new member: New Standard Member layout matches F1-07 State - New Standard Member | PASSED |
| TC-FE02-B02 | Log in as a member with VIP card, active package, pending package and two bookings: After Purchase layout | PASSED |
| TC-FE02-B03 | Pay now opens /member/payments/52 with the My Payments placeholder | PASSED |
| TC-FE02-B04 | Edit profile, Save changes: green success message and toast | PASSED |
| TC-FE02-B05 | Wrong current password: red message under the field | PASSED |
| TC-FE02-B06 | 390 px wide: one column, no horizontal page scroll (scrollWidth 390) | PASSED |
| TC-FE02-B07 | Header name follows the saved full name | PASSED |
| TC-FE02-B08 | Round 1: member with only a pending package sees "Complete your payment to start training." with Pay now | PASSED |
| TC-FE02-B09 | Round 1: member with a used up package sees "No sessions left", only Renew package, hero "Find your next sport package." | PASSED |
| TC-FE02-B10 | Round 1: member without a phone saves an address; a 401 on change password shows "Current password is incorrect" and the page stays on /member/profile | PASSED on the round 1 code. Superseded by round 2 (a 401 now refreshes first); not re-run in the browser, covered by TC-FE02-067, TC-FE02-080 and TC-FE02-081 |

---

## 5. Manual test cases against the real backend (run before merge)

Preconditions: BE-02 merged (PUT /users/me and PUT /users/me/password), backend running with `mvn spring-boot:run`, SQL Server with Flyway V1-V21, frontend `npm run dev` on http://localhost:5173.

### M01 - New Standard Member state (AC1)
- Steps: sign up a new member on /register.
- Expected: the dashboard shows "Welcome, <full name>.", tier Standard, 0 active packages, 0 upcoming sessions, "No sport packages yet".
- Result: NOT RUN

### M02 - After Purchase state after an online package request (AC1, AC2)
- Steps: as the member, register a package online (POST /packages/registrations, or FE-03 when merged); open /member.
- Expected: hero "Ready for your next session?"; Pending payments shows the package, its payment code and amount; Pay now goes to /member/payments/<paymentId>.
- Result: NOT RUN

### M03 - Pending card request (AC2)
- Steps: purchase a VIP card online (POST /membership-cards/purchase); open /member.
- Expected: the card is in Pending payments with Pay now; tier still Standard.
- Result: NOT RUN

### M04 - After the front desk confirms the payment (AC1)
- Steps: as a receptionist activate the package (PUT /packages/registrations/{id}/activate) and the card; reload /member as the member.
- Expected: Pending payments gone; package listed with remaining sessions and end date; tier VIP with "Active - Until <date>".
- Result: NOT RUN

### M05 - Upcoming bookings
- Steps: book a session with the active package; open /member.
- Expected: the session appears in Upcoming sessions with date and time; the count matches.
- Result: NOT RUN

### M06 - Profile edit success (AC3)
- Steps: change phone, date of birth, goal, level and address on /member/profile; Save changes; reload the page.
- Expected: "Your profile was saved."; values kept after reload; header name updated after a name change.
- Result: NOT RUN

### M07 - Profile edit error from the backend (AC3)
- Steps: send a phone that the backend rejects (for example 7 digits after disabling the client check, or a duplicate if BE-02 checks it).
- Expected: the backend message under Phone.
- Result: NOT RUN

### M08 - Password change success (AC3)
- Steps: change the password; log out; log in with the new password.
- Expected: success message; the old password fails, the new one works.
- Result: NOT RUN

### M09 - Wrong current password (AC3)
- Steps: change password with a wrong current password.
- Expected: message under Current password; the member stays signed in whether BE-02 answers 400 with `details.currentPassword` or 401 (with a 401 the Network tab shows exactly one POST /auth/refresh and one retry). A 400 without details is shown above the form.
- Result: NOT RUN

### M10 - Backend down
- Steps: stop the backend; reload /member, then click Try again after starting it.
- Expected: error state with the network message; Try again loads the dashboard.
- Result: NOT RUN

### M11 - Role guard
- Steps: open /member and /member/profile as a receptionist.
- Expected: 403 page.
- Result: NOT RUN

### M13 - Member without a phone (review round 1)
- Steps: as a member registered without a phone, change only the address on /member/profile.
- Expected: saved; PUT body has phone null; BE-02 accepts a null phone.
- Result: NOT RUN

### M14 - Change password after the access token expired (review round 2)
- Steps: log in as a member, open /member/profile and stay on the page longer than the access token lifetime (JWT expiry in backend/.env, or shorten it for the test). Then change the password with the correct current password.
- Expected: success message; DevTools Network shows PUT /users/me/password 401, POST /auth/refresh 200, PUT /users/me/password 200 or 204; the member stays signed in; the new password works at the next login.
- Result: NOT RUN

### M12 - Compare with Figma
- Steps: compare /member (both states) and /member/profile with the F1-07 and F1-08 exports.
- Expected: same structure and colours; known differences listed in the AI Audit Log (no Notifications, Recommended for you, Interested sports, AI Assistant).
- Result: NOT RUN
