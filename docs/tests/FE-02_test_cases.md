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
| `PUT /api/v1/users/me` | Planned in BE-02. Assumed body `{fullName, phone, dateOfBirth, address, mainGoal, currentLevel}`, answer `UserProfileResponse` or 204, 400 `Validation Error` with `details` | Save changes |
| `PUT /api/v1/users/me/password` | Planned in BE-02. Assumed body `{currentPassword, newPassword}`, answer 200 or 204; wrong current password must be 400 (not 401) | Change password |

State rules: New Standard Member = no paid card (tier other than STANDARD, not CANCELLED) and no package registration (not CANCELLED). After Purchase = anything else, including a request in PENDING_PAYMENT. The tier shown is the ACTIVE card whose dates contain today (highest discount wins), otherwise "Standard".

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm install
npm test                         # automated cases below plus FE-00 and FE-01 tests
npm run lint -- --max-warnings 0
npm run build
```

### 1.3 Result summary (2026-10-13)
| Group | Cases | Result |
|---|---|---|
| Automated, dashboard rules (`src/pages/member/dashboardModel.test.ts`) | 15 | 15 PASSED |
| Automated, dashboard screen (`src/pages/member/memberDashboard.test.tsx`) | 15 | 15 PASSED |
| Automated, profile screen (`src/pages/member/myProfile.test.tsx`) | 17 | 17 PASSED |
| Automated, profile validation (`src/pages/member/profileValidation.test.ts`) | 8 | 8 PASSED |
| Automated, FE-00 and FE-01 tests (4 assertions updated, see section 3) | 157 | 157 PASSED |
| Lint, type check and build | 3 | 3 PASSED, 0 warnings |
| Deliberate regressions (section 3) | 10 | 10 caught |
| Browser checks in Chromium against a mock API (section 4) | 7 | 7 PASSED |
| Manual checks against the real backend (section 5) | 12 | NOT RUN YET (needs BE-02 merged and SQL Server) |

Total: 212 Vitest tests in 13 files, 55 of them new.

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
| TC-FE02-010 | sessionShare | 6 of 8 gives 0.75; clamped to 1; null remaining gives 0; no limit gives null | PASSED |
| TC-FE02-011 | Pending items from a pending card and a pending package | Newest first, titles "Sport package - Football - Coach-led" and "Membership card - VIP", payment codes, amounts "810.000 VND" and "600.000 VND", payment ids | PASSED |
| TC-FE02-012 | Pending package without payment | paymentId null, reference is the registration code | PASSED |
| TC-FE02-013 | Payment summary path | /member/payments/52 | PASSED |
| TC-FE02-014 | Upcoming bookings from past, cancelled, invalid and future bookings | Only confirmed from now on, soonest first: ids 3, 1 | PASSED |
| TC-FE02-015 | Labels | Self-training, humanized unknown code, title without format, "17:00" | PASSED |

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

### 2.3 My Profile screen - `frontend/src/pages/member/myProfile.test.tsx`

| ID | AC | Case | Expected result | Result |
|---|---|---|---|---|
| TC-FE02-040 | - | Load profile with BE-02 fields | All fields filled; email read only; summary shows TN and "Member code MEM-0002" | PASSED |
| TC-FE02-041 | - | Profile without BE-02 fields and an unknown goal code | Empty date and address; selects show Not set | PASSED |
| TC-FE02-042 | AC3 | Edit name (with spaces), phone with spaces, level, clear address, Save changes | PUT /users/me body `{fullName: "Taylor Tran", phone: "0901234999", dateOfBirth: "2004-06-15", address: null, mainGoal: "IMPROVE_FITNESS", currentLevel: "INTERMEDIATE"}`; message "Your profile was saved." (role status) and toast; header and summary show the new name | PASSED |
| TC-FE02-043 | AC3 | Empty name, phone "12ab", date of birth 2030-01-01 | Three field errors, no PUT, focus on Full name | PASSED |
| TC-FE02-044 | AC3 | Edit a field that has an error | Its error disappears | PASSED |
| TC-FE02-045 | AC3 | Backend 400 with details for phone and an unknown key | Phone message under Phone (focused); unknown message above the form; no success message | PASSED |
| TC-FE02-046 | AC3 | Backend 500 | "Internal server error" above the form; Save changes enabled again | PASSED |
| TC-FE02-047 | - | Submit while saving | Fieldset disabled, aria-busy, second submit sends nothing; success after the answer | PASSED |
| TC-FE02-048 | AC3 | Backend answers 204 without a body | Success message; the sent address stays | PASSED |
| TC-FE02-049 | - | Page GET /users/me returns 503, then Try again | Alert "We could not load your profile"; retry shows the form | PASSED |
| TC-FE02-060 | AC3 | Valid password change | PUT /users/me/password body `{currentPassword, newPassword}`; message "Your password was changed. Use the new password next time you log in." (role status); three fields cleared | PASSED |
| TC-FE02-061 | AC3 | Backend 400 "Current password is incorrect" | Message under Current password (focused); new password kept; no success message | PASSED |
| TC-FE02-062 | AC3 | Submit empty | Three required messages, no request | PASSED |
| TC-FE02-063 | AC3 | Weak password and mismatch, then new equal to current | Strong password message and "Passwords do not match"; then "New password must be different from the current password" | PASSED |
| TC-FE02-064 | AC3 | Backend 400 details for newPassword | Message under New password | PASSED |
| TC-FE02-065 | AC3 | Backend 500 | Message above the form, button enabled again | PASSED |
| TC-FE02-066 | - | Type a new password | Live rules update; "Meets requirements" when valid | PASSED |

### 2.4 Profile validation - `frontend/src/pages/member/profileValidation.test.ts`

| ID | Case | Expected result | Result |
|---|---|---|---|
| TC-FE02-070 | Complete profile; optional fields empty | No errors | PASSED |
| TC-FE02-071 | Full name empty, 101 and 100 characters | Required, max 100, valid | PASSED |
| TC-FE02-072 | Phone empty, 7 digits, "(+84) 901-234-567" | Required, pattern message, valid | PASSED |
| TC-FE02-073 | Date of birth today, tomorrow, 1899-12-31, "15/06/2004" | Valid, past message, invalid, invalid | PASSED |
| TC-FE02-074 | Address of 256 characters | Max 255 message | PASSED |
| TC-FE02-075 | Request building | Trimmed, phone without separators, empty optionals null | PASSED |
| TC-FE02-076 | Form from profile | Null phone empty, unknown goal empty, known level kept | PASSED |
| TC-FE02-077 | Password change rules | Valid, weak, same as current, mismatch; request keeps passwords as typed | PASSED |

---

## 3. Changes to existing tests and deliberate regressions

Updated (no behaviour change in FE-00 or FE-01 code):
- `src/auth/auth.flow.test.tsx` (1 assertion) and `src/pages/public/authScreens.test.tsx` (3 assertions): the account switch notice is read with a helper that picks the role status element with class `account-switch-notice`, because `/member` is now the real dashboard and its loading spinners also have role status.

Deliberate regressions, each applied alone and reverted, `npx vitest run src/pages/member`:

| # | Code broken on purpose | Tests that failed |
|---|---|---|
| 1 | dashboardState always returns after-purchase | 3 |
| 2 | paymentSummaryPath drops the payment id | 3 |
| 3 | currentCard ignores the card dates | 1 |
| 4 | upcomingBookings keeps cancelled bookings | 2 |
| 5 | Profile save does not show the success message | 3 |
| 6 | Wrong current password not mapped to its field | 1 |
| 7 | Profile save does not call reloadUser | 1 |
| 8 | Password form not cleared after success | 1 |
| 9 | Non list answer treated as an empty list | 1 |
| 10 | Route /member/payments/:paymentId removed | 1 |

---

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
- Expected: message under Current password; the member stays signed in (BE-02 must answer 400, not 401).
- Result: NOT RUN

### M10 - Backend down
- Steps: stop the backend; reload /member, then click Try again after starting it.
- Expected: error state with the network message; Try again loads the dashboard.
- Result: NOT RUN

### M11 - Role guard
- Steps: open /member and /member/profile as a receptionist.
- Expected: 403 page.
- Result: NOT RUN

### M12 - Compare with Figma
- Steps: compare /member (both states) and /member/profile with the F1-07 and F1-08 exports.
- Expected: same structure and colours; known differences listed in the AI Audit Log (no Notifications, Recommended for you, Interested sports, AI Assistant).
- Result: NOT RUN
