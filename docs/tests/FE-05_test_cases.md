# Test Cases - Task FE-05 (Flow 1): Receptionist Member Search and Profile (F1-13), Register and Renew (F1-14), Check-in (F1-15)

## 1. Overview
- Task ID: FE-05
- Flow: Flow 1 - User and membership management (Receptionist)
- Branch: feat/f1-reception-screens (rebased on main after FE-04, PR #38 and #39)
- Owner: Phat
- Priority: P1
- Depends on: FE-00 (on main), BE-05 and BE-19 (not on main yet, see 1.1)
- Figma: `context/Flow 1 - User and membership management/Flow 1 - Receptionist/F1-13*.png`, `F1-14*.png`, `F1-15*.png`
- Out of scope: F1-16 Support Requests (P3), Edit profile from the desk (no endpoint yet)
- Acceptance criteria:
  - AC1: the receptionist finds a member, creates one, sells a package at the desk and sees the receipt.
  - AC2: pending online requests open the confirm screen.
  - AC3: check-in shows Recorded or Rejected with the reason.
  - AC4: a MEMBER cannot open these routes.
- Review round 1 (2026-10-13): all 14 findings addressed, see section 5.

### 1.1 Backend contract used
| Call | On main | Contract used by the frontend |
|---|---|---|
| `GET /api/v1/members?query=&page=&size=` | yes (Spring Page) | Rows `{userId, memberCode, fullName, email, phone, currentLevel}`; userId and fullName required |
| `GET /api/v1/members/{id}/overview` | no, BE-05 | Exactly `{member: {userId, memberCode, fullName, email, phone, currentLevel, status}, cards: MemberCardResponse[], registrations: PackageRegistrationResponse[]}`. Every list element must be an object with a numeric id; anything else is "unexpected response" |
| 404 contract of the overview | BE-05 must agree | A missing member answers 404 with message exactly `Member not found` (same text as `CheckInServiceImpl`). Any other 404, such as `Resource not found` while the endpoint is not deployed, is an error with Retry |
| `POST /api/v1/members` | no, BE-05 | Body `{fullName, phone, email, dateOfBirth, mainGoal, currentLevel}`; answer is a member row with userId |
| `GET /api/v1/packages`, `GET /api/v1/membership-cards/tiers` (`cardsApi.tiers`) | yes | Price lists |
| `POST /api/v1/packages/registrations` | yes; payment fields BE-19 | Body `{memberId, packageId, startDate, channel: "RECEPTION", paymentMethod, paymentReference, amountReceived}`. On main the answer is ACTIVE with paymentId null, no Payment is created and the payment fields are dropped |
| `POST /api/v1/membership-cards/purchase` | yes; payment fields BE-19 | Body `{memberId, tierId, paymentMethod, paymentReference, amountReceived}`. Same behavior on main as above |
| `GET /api/v1/membership-cards/pending`, `GET /api/v1/packages/registrations/pending` | no, BE-05 (F1-B5) | Lists of MemberCardResponse and PackageRegistrationResponse |
| `GET /api/v1/membership-cards/member/{id}`, `GET /api/v1/packages/registrations/member/{id}` | yes | Check that the item to confirm belongs to the payment and is PENDING_PAYMENT |
| `PUT /api/v1/membership-cards/{id}/activate`, `PUT /api/v1/packages/registrations/{id}/activate` | yes (BE-17) | Body `{paymentMethod}` |
| `GET /api/v1/payments/{id}` | yes | Confirmation and receipt |
| `POST /api/v1/check-ins` | yes | Body `{identifier, note}`; result ALLOWED or DENIED with denialReason; 404 "Member not found" for an unknown code |
| `GET /api/v1/check-ins?date=YYYY-MM-DD` | no, date filter BE-05 (main requires memberId) | Today's list; a 400 or 5xx shows the notice "Today's list needs the date filter from BE-05." and the check-ins made on this screen |

Desk sale result (decided from the payment): ACTIVE with a paymentId -> receipt with View receipt; PENDING_PAYMENT -> warning with Confirm payment; ACTIVE with paymentId null -> warning "no payment record was created (needs BE-19), take the money manually and keep a paper slip", no receipt link.

Card rules: the FE-04 `planCard` (`src/pages/member/cardsModel.ts`) with the desk wording. new, renew, later and upgrade are sellable; blocked and unknown disable the sale and show the reason (unknown: the tier of the card in force is no longer listed).

Amount received: plain digits or digits grouped in threes with one kind of separator (dot, comma or space). Decimals and mixed separators are refused.

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm test
npm run lint -- --max-warnings 0
npx tsc -b
npm run build
```

### 1.3 Result summary (2026-10-13, after review round 1)
| Group | Cases | Result |
|---|---|---|
| Automated, rules (`src/pages/reception/receptionModel.test.ts`) | 20 | 20 PASSED |
| Automated, screens (`src/pages/reception/receptionScreens.test.tsx`) | 27 (TC-FE05-060 runs for 4 routes) | 27 PASSED |
| Automated, all other files (FE-00 to FE-04) | 352 | 352 PASSED |
| Lint (0 warnings), `tsc -b`, build | 3 | 3 PASSED |
| Manual against the real backend (section 4) | 12 | NOT RUN (M01 to M04, M09 need BE-05; M05 to M08 need BE-19) |

Total: 399 automated tests in 20 files, 399 passed, 0 failed. All automated cases run against a fake backend that follows 1.1; they are not verified on main until BE-05 and BE-19 are merged.

## 2. Automated cases - rules (`receptionModel.test.ts`)
| ID | Case | Expected | Result |
|---|---|---|---|
| TC-FE05-001 | Empty Create Member form | Full name, phone and email required | PASSED |
| TC-FE05-002 | Bad phone, bad email, date of birth today; then a valid form | Field messages; valid form has no error | PASSED |
| TC-FE05-003 | Cash 1.000.000 for 810.000 | Valid, change 190.000 | PASSED |
| TC-FE05-004 | Cash below the amount, text amount, empty amount | Three distinct messages | PASSED |
| TC-FE05-005 | Transfer without reference and with a different amount; unknown method | Reference required, exact amount required, no change, method message | PASSED |
| TC-FE05-006 | Amounts "810000", "810.000", "810,000", "810 000", "1.000.000"; "200000.00", "1.5", "1,000.000", "-5", "" | First five parsed; the rest null; "200000.00" shows "Amount received must be a whole number of VND" | PASSED |
| TC-FE05-007 | VIP to VIP | planCard renew 2027-07-05 to 2028-07-04, 600.000, sellable | PASSED |
| TC-FE05-008 | No paid card, VIP | new from today to 2027-10-12 | PASSED |
| TC-FE05-009 | VIP member buys Gold | later, from 2027-07-05, 300.000 | PASSED |
| TC-FE05-010 | Gold to VIP | upgrade today, keeps 2027-07-04, 300.000 | PASSED |
| TC-FE05-011 | Gold with a queued renewal to VIP | blocked, not sellable, desk wording "The member's card is already renewed from 2027-07-05 ..." | PASSED |
| TC-FE05-012 | Tier list with STANDARD and an inactive tier | Only paid active tiers sold | PASSED |
| TC-FE05-013 | One pending card and one pending package | Merged oldest first, member labels | PASSED |
| TC-FE05-014 | Registrations EXPIRED, ACTIVE, PENDING | Pending, active, then the rest | PASSED |
| TC-FE05-015 | Paths, ids, verdicts, identifier check | As listed in the test | PASSED |
| TC-FE05-016 | Overview reader: agreed shape; flat member, id instead of userId, activeCard, element without id | Agreed shape read; every other shape refused | PASSED |
| TC-FE05-017 | ACTIVE card of a tier that is no longer listed, VIP chosen | unknown, never new from today, not sellable, fixed message | PASSED |
| TC-FE05-018 | Sale outcome | ACTIVE + payment -> paid; ACTIVE + null -> unrecorded; PENDING_PAYMENT -> pending; other | PASSED |
| TC-FE05-019 | Confirmation check | ok; not-found; other-payment; not-pending | PASSED |
| TC-FE05-020 | 404 messages and router state | Only 404 "Member not found" is a missing member; only a string paidReference in state | PASSED |

## 3. Automated cases - screens (`receptionScreens.test.tsx`)
Signed in as RECEPTIONIST with a fake backend that follows 1.1; today is 2026-10-13.

| ID | AC | Case | Steps | Expected | Result |
|---|---|---|---|---|---|
| TC-FE05-030 | AC1 | Search, single result | `/reception`, search "Taylor" | One `GET /members?query=Taylor&page=0&size=10`, overview of member 2 with card, expiry, discount, pending REG-ONLINE-001 first, Renew package and Check-in links | PASSED |
| TC-FE05-031 | AC1 | Several results | Two members found, select the second | "2 members found", overview of member 3 | PASSED |
| TC-FE05-032 | AC1 | Empty search | Click Search with an empty box | "Enter a name, phone number or email", no request | PASSED |
| TC-FE05-033 | AC1 | Member Not Found and Create Member | Search with no result, Create member, Save empty, fill and save | Required messages; POST body as in 1.1; overview of member 7 opens | PASSED |
| TC-FE05-034 | AC1 | Duplicate member | POST /members answers 409 | Dialog stays open with the backend message and a hint to search | PASSED |
| TC-FE05-035 | AC1, AC2 | Payment Handoff and After Payment | Continue to payment, Open payment confirmation (item check passes), Cash, Confirm payment, Back to member profile | PUT activate body `{paymentMethod: "CASH"}`; "Payment receipt"; profile shows "Payment PAY-0041 is confirmed" from router state | PASSED |
| TC-FE05-036 | AC1 | Spoofed notice | `/reception?memberId=2&paid=ANY` | No After Payment notice | PASSED |
| TC-FE05-037 | AC1 | Overview 404 | Answer 404 "Resource not found"; then 404 "Member not found" | First: "We could not load the member profile", the message and Try again; second: Member not found | PASSED |
| TC-FE05-038 | AC2 | Item of another payment | `/reception/payments/41?item=package&itemId=10&memberId=2` (registration 10 has payment 30) | Warning "belongs to another payment", Confirm payment disabled, no PUT | PASSED |
| TC-FE05-040 | AC1 | Register without a member | `/reception/register` | "Choose a member" picker; after search the sale form | PASSED |
| TC-FE05-041 | AC1 | Desk package sale with cash (BE-19 contract) | Football coach-led 30 days, 1.000.000 | Fee summary 810.000; change 190.000; POST body as in 1.1; receipt with View receipt to `/reception/payments/60` | PASSED |
| TC-FE05-042 | AC1 | Transfer validation | Bank transfer, no reference, 800000 | Two field messages, no POST | PASSED |
| TC-FE05-043 | AC1 | Renew package | `?memberId=2&packageId=2` | Package 2 preselected, start 2026-10-25, Renewal | PASSED |
| TC-FE05-044 | AC1 | Backend validation error | 400 with `details.amountReceived` | Message under Amount received | PASSED |
| TC-FE05-045 | AC1 | Membership Card state | `tab=card`, VIP, card POS with reference | Renew, 05 Jul 2027 to 04 Jul 2028, 600.000; POST card body; View receipt `/reception/payments/70` | PASSED |
| TC-FE05-046 | AC1, AC2 | Sale answered as PENDING_PAYMENT (hypothetical backend, not main) | Package sale | Warning "saved as pending payment" and Confirm payment link | PASSED |
| TC-FE05-048 | AC1 | Main without BE-19 | Sale answers ACTIVE with paymentId null | Warning "no payment record was created (needs BE-19)", "No payment record", no "is recorded", no View receipt | PASSED |
| TC-FE05-049 | AC1 | Stale data after a sale | Sell VIP card, New sale, choose VIP again | Overview reloaded (2 calls); Renew, 05 Jul 2028 to 04 Jul 2029 | PASSED |
| TC-FE05-047 | AC2 | Pending online requests | `tab=pending` | Both lists, oldest first; the row link opens the confirmation | PASSED |
| TC-FE05-050 | AC3 | Check-in Recorded | `?code=MB-0248`, Confirm check-in | POST `{identifier: "MB-0248", note: null}`; Recorded with name, 17:02, BK-0021; list loaded twice; field cleared | PASSED |
| TC-FE05-051 | AC3 | Check-in Rejected | Backend answers DENIED | Rejected with "No confirmed booking for today", Register or renew link, Rejected badge | PASSED |
| TC-FE05-052 | AC3 | Empty and unknown code | Empty submit; then MB-9999 answered 404 | Required message without request; Rejected panel with "Member not found", field keeps MB-9999 and the focus | PASSED |
| TC-FE05-053 | AC3 | List without the date filter (main) | GET /check-ins answers 400; check in MB-0248, then MB-9999 | Neutral notice and Retry, no error alert; table lists MB-9999 rejected then Taylor Nguyen recorded (newest first) | PASSED |
| TC-FE05-060 | AC4 | MEMBER on `/reception`, `/reception/register`, `/reception/check-in`, `/reception/payments/41` | Sign in as MEMBER | Access denied, no receptionist request | PASSED (4 runs) |

## 4. Manual cases (real backend)
| ID | Case | Steps | Expected | Result |
|---|---|---|---|---|
| M01 | Search by phone | Log in as receptionist, search 0901234567 | Profile opens directly; Back returns to the page before the search | NOT RUN |
| M02 | Search by part of a name | Search "Nguyen" | List with count, select one | NOT RUN |
| M03 | Create member | Create with a new phone and email | Member code returned and profile opens | NOT RUN |
| M04 | Duplicate member | Create with an existing email | 409 message in the dialog | NOT RUN |
| M05 | Desk package sale, cash | Sell a 30-day package to a VIP member, give 1.000.000 | ACTIVE registration, SUCCESS payment, receipt, change shown (BE-19). On main: warning "no payment record" | NOT RUN |
| M06 | Desk package sale, transfer | Bank transfer with reference | Reference stored on the payment (BE-19) | NOT RUN |
| M07 | Desk card renewal | Sell VIP to a VIP member | New term starts the day after the current end | NOT RUN |
| M08 | Card upgrade | Sell VIP to a Gold member | Difference charged, end date kept | NOT RUN |
| M09 | Pending online request | Member requests a package online; desk opens the pending tab, confirms with cash | Registration ACTIVE, payment SUCCESS, profile After Payment notice | NOT RUN |
| M10 | Check-in allowed | Member with an active package and a confirmed booking today | Recorded | NOT RUN |
| M11 | Check-in twice | Same member checks in again without a booking id (the screen never sends one) | Rejected with "All confirmed bookings for today have already been checked in" ("Already checked in for this session" appears only when a bookingId is sent) | NOT RUN |
| M12 | Role guard | Log in as a member and open `/reception/check-in` | Access denied page | NOT RUN |

## 5. Review round 1 - findings and where they are covered
| # | Finding | Fix | Cases |
|---|---|---|---|
| 1 | Rebase on main (FE-04) | Rebased; both route sets kept; FE-05 entry above FE-04; numbers recounted | Full suite |
| 2 | Desk sale claimed a recorded payment | Outcome from the payment | TC-FE05-018, 046, 048 |
| 3 | Second copy of the card rules | FE-04 planCard with desk wording; CardTier and tiers from cardsApi | TC-FE05-007 to 012, 017 |
| 4 | Missing endpoint read as a missing member | Only 404 "Member not found" | TC-FE05-020, 037 |
| 5 | Decimals parsed as thousands | Strict amount pattern | TC-FE05-006 |
| 6 | Stale data after a sale | Overview reloaded, form remounted per sale | TC-FE05-049 |
| 7 | Confirm trusted the URL | Item loaded and checked against the payment | TC-FE05-019, 035, 038 |
| 8 | Spoofable notice | Router state | TC-FE05-035, 036 |
| 9 | Today's list on main | Neutral notice and local list | TC-FE05-053 |
| 10 | Speculative contract | One shape, list elements checked | TC-FE05-016 |
| 11 | Documentation accuracy | Audit log section 6 and M11 corrected | - |
| 12 | Hook and date helper in pages/member | `src/hooks/useResource.ts`, `src/utils/date.ts`, re-exported from the old paths | Full suite |
| 13 | Back after the automatic selection | `replace: true` for the single result | Code review, M01 |
| 14 | Unknown check-in code | Rejected panel "Member not found", field kept and focused, local list | TC-FE05-052, 053 |
