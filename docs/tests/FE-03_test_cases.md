# Test Cases - Task FE-03 (Flow 1): Sport Packages (F1-09) and Package Detail and Registration (F1-10)

## 1. Overview
- Task ID: FE-03
- Flow: Flow 1 - User and membership management (Member)
- Branch: feat/f1-member-packages (stacked on feat/f1-member-dashboard, FE-02, which is not merged yet)
- Priority: P1
- Depends on: FE-00, BE-17 (both on main)
- Figma: `context/Flow 1 - User and membership management/Flow 1 - Member/F1-09 - Sport Packages.png`, `F1-10 - Package Detail & Registration.png`, `F1-10 Overlay - Payment Handoff.png`; `context/Flow 3 - Payment and report managmen/Flow 3 - Member/F3-01 - Payment Summary.png`
- Acceptance criteria:
  - AC1: filters work and prices with discount are correct for STANDARD, GOLD, VIP.
  - AC2: a past start date cannot be picked.
  - AC3: after registering, the user lands on the payment summary of the new payment.

### 1.1 Backend contract used (checked in the code on main, commit a47d126)
| Call | Use |
|---|---|
| `GET /api/v1/sports` (public) | Sport filter |
| `GET /api/v1/packages` (public, active packages) | List and detail (there is no `GET /packages/{id}`) |
| `GET /api/v1/membership-cards/my` | Card in force today, for the discount preview |
| `POST /api/v1/packages/registrations` body `{packageId, startDate, channel: "ONLINE"}` | Registration; for a member the backend forces ONLINE and PENDING_PAYMENT and creates a PENDING payment (BE-17), default method BANK_TRANSFER |
| `GET /api/v1/payments/{id}` | Read only payment summary |

Discount rule (same as `MembershipCardServiceImpl.getApplicableDiscountPercentage` and `SportPackageServiceImpl.registerPackage`): 0 for packages of 1 day or less; otherwise the highest discount of the ACTIVE card in force today (STANDARD 0, GOLD 5, VIP 10 in the seed); final = price * (100 - discount) / 100, half up to 2 decimals. End date = start date + duration - 1 day.

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm test
npm run lint -- --max-warnings 0
npm run build
```

### 1.3 Result summary (2026-10-13, after review round 2)
| Group | Cases | Result |
|---|---|---|
| Automated, rules (`src/pages/member/packagesModel.test.ts`) | 10 (3 new in round 1, TC-FE03-003 extended) | 10 PASSED |
| Automated, screens (`src/pages/member/sportPackages.test.tsx`) | 33 (8 new in round 1, 1 new in round 2; TC-FE03-048 extended in round 2) | 33 PASSED |
| Automated, all other files (FE-00, FE-01, FE-02) | 255 (4 FE-02 assertions on the discount text changed in round 1) | 255 PASSED |
| Lint (0 warnings), `tsc -b`, build | 3 | 3 PASSED |
| Manual checks against the real backend (section 3) | 8 | NOT RUN YET (needs SQL Server) |

Total: 298 Vitest tests in 16 files (286 before review round 1, 297 after it, 1 added in round 2).

Existing tests changed in review round 2:
- `src/pages/member/sportPackages.test.tsx` TC-FE03-048 (price placeholder); both `openDetail` helpers now wait until "Checking your registrations" is gone before the test acts.

Existing tests changed in review round 1:
- `src/pages/member/dashboardModel.test.ts` TC-FE02-127 and `src/pages/member/memberDashboard.test.tsx` TC-FE02-021, TC-FE02-122, TC-FE02-126: the card discount text is now "<n>% off packages longer than a single visit" (B2).
- `src/pages/member/sportPackages.test.tsx` TC-FE03-013 (same text) and TC-FE03-039 (real response shape).
- `src/pages/member/packagesModel.test.ts` TC-FE03-003 (two more rounding cases).

Existing tests changed in the first FE-03 commit:
- `src/pages/member/memberDashboard.test.tsx` TC-FE02-023: "Pay now" now opens the real Payment Summary page, so the expected heading is "Payment Summary" (was the FE-02 placeholder "My Payments and Receipts").
- `src/layouts/navigation.test.ts` removed (2 tests): `requireNavItem` is no longer used, because the payment route no longer reuses the My Payments menu entry.

---

## 2. Automated Test Cases

### 2.1 Rules - `frontend/src/pages/member/packagesModel.test.ts`
| ID | AC | Case | Expected result | Result |
|---|---|---|---|---|
| TC-FE03-001 | AC1 | Discount percent | No card 0, GOLD 5, VIP 10; single visit 0 with any card | PASSED |
| TC-FE03-002 | AC1 | Price breakdown, seeded prices | 900.000 -> 900.000 / 855.000 / 810.000; 1.350.000 VIP -> 1.215.000; single visit 80.000 unchanged | PASSED |
| TC-FE03-003 | AC1 | Rounding in integer cents (extended in round 1) | 0.35 VIP -> 0.32; 2.30 GOLD -> 2.19 (discount 0.11); 99999.99 VIP -> 89999.99; 10.05 GOLD -> 9.55, same as BigDecimal HALF_UP | PASSED |
| TC-FE03-004 | - | End date | 02 Oct + 30 days -> 31 Oct; single visit -> same day; 15 Dec + 90 days -> 14 Mar | PASSED |
| TC-FE03-005 | AC2 | Start date rule | Empty -> required; yesterday -> "Start date cannot be in the past"; today and later valid; bad format invalid | PASSED |
| TC-FE03-006 | AC1 | Filters | Sport, training type and duration combine; inactive hidden; sorted by sport, type, duration | PASSED |
| TC-FE03-008 | - | sessionsLabel (round 1, B1) | null and undefined -> "No session limit"; 1 -> "1 session"; 8 -> "8 sessions" | PASSED |
| TC-FE03-009 | AC1 | isDiscountEligible (round 1, B2) | 1 day false; 2 and 60 days true; 60 days GOLD 1.000.000 -> 950.000 | PASSED |
| TC-FE03-020 | AC1 | sanitizeFilters (round 1, M3) | Unknown sport, format and duration dropped; known values kept | PASSED |
| TC-FE03-007 | AC1 | Labels and URL | "30 days - 8 sessions", "Single visit - 1 session", duration options, query round trip | PASSED |

### 2.2 Screens - `frontend/src/pages/member/sportPackages.test.tsx`
| ID | AC | Case | Expected result | Result |
|---|---|---|---|---|
| TC-FE03-010 | - | List | 5 cards; coach-led 30 days shows 900.000 VND, "90 min / session", View package -> /member/packages/4; calls /sports, /packages, /membership-cards/my | PASSED |
| TC-FE03-011 | AC1 | STANDARD | All original prices | PASSED |
| TC-FE03-012 | AC1 | GOLD | 855.000, 2.280.000, 475.000; single visit 80.000 with "Single visit - no card discount" | PASSED |
| TC-FE03-013 | AC1 | VIP (note text changed in round 1) | 810.000, 2.160.000; single visit 80.000; note "10% off packages longer than a single visit" | PASSED |
| TC-FE03-014 | AC1 | Pending or expired card | No discount | PASSED |
| TC-FE03-015 | AC1 | Filters and reset | 5 -> 4 -> 2 -> 1 cards; detail link keeps the query; Reset filters -> 5 | PASSED |
| TC-FE03-016 | AC1 | No match | Empty state | PASSED |
| TC-FE03-017 | AC1 | Filters from URL | `?sport=2` shows 1 card | PASSED |
| TC-FE03-018 | - | Cards fail | Original prices, warning (role alert) | PASSED |
| TC-FE03-019 | - | Catalog fails, Try again | Error, then the list | PASSED |
| TC-FE03-030 | AC1 | Detail, VIP | Original 900.000, discount "VIP - 10% (-90.000 VND)", total 810.000, start 13 Oct 2026, expiry 11 Nov 2026 | PASSED |
| TC-FE03-031 | AC1 | Detail, GOLD | "Gold - 5% (-45.000 VND)", total 855.000 | PASSED |
| TC-FE03-032 | AC1 | Detail, STANDARD | "None (Standard)", total 900.000 | PASSED |
| TC-FE03-033 | AC1 | Detail, single visit with VIP | "Not applied to single visits", 80.000, expiry = start | PASSED |
| TC-FE03-034 | AC2 | Past date | Picker min and default = today; typed yesterday -> error, focus, no POST | PASSED |
| TC-FE03-035 | - | Later start | Expiry 30 Nov 2026; POST body `{packageId: 4, startDate: "2026-11-01", channel: "ONLINE"}` with Bearer token | PASSED |
| TC-FE03-036 | AC3 | Register -> overlay -> continue | Overlay "Payment summary": REG-0040, PAY-0052, 810.000 VND, View my registration; Continue to payment -> /member/payments/52 shows PAY-0052, 810.000 VND, Pending, bank transfer details | PASSED |
| TC-FE03-037 | - | Double submit | One POST while sending; locked after success | PASSED |
| TC-FE03-038 | - | Backend 400 | Message above the summary, no overlay, button enabled | PASSED |
| TC-FE03-039 | AC2 | Real backend answer 400 `{timestamp, status, message: "Start date cannot be in the past"}` without details (rewritten in round 1, B4) | Message under the date field, field invalid and focused, nothing above the summary | PASSED |
| TC-FE03-040 | - | Unknown package id | "This package is not available"; Back to packages keeps `?sport=1` | PASSED |
| TC-FE03-041 | - | Cards fail on detail | "Calculated when you confirm", total shows the original price | PASSED |
| TC-FE03-042 | - | Single visit detail (round 1, B1) | Heading "Football - Single visit - 1 session", Included sessions "1 session", never "1 sessions" | PASSED |
| TC-FE03-043 | AC1 | 60 day package, GOLD (round 1, B2) | 950.000 VND; note "5% off packages longer than a single visit", no "30 day and 90 day" | PASSED |
| TC-FE03-044 | - | Pending registration of this package (round 1, B3) | Notice with REG-0030, Continue to payment -> /member/payments/60, Confirm disabled, no POST | PASSED |
| TC-FE03-045 | - | Pending registration of another package (round 1, B3) | Not blocked | PASSED |
| TC-FE03-046 | - | Registrations fail to load (round 1, B3) | Not blocked, registration works | PASSED |
| TC-FE03-047 | AC2 | Page open past midnight (round 1, B4) | Opened 13 Oct 23:59, submit at 14 Oct 00:01 -> "Start date cannot be in the past", no POST, min 2026-10-14 | PASSED |
| TC-FE03-048 | AC1 | Cards still loading (round 1, M2; extended in round 2, N1) | Eligible card: price "-" and "Checking your card discount"; single visit keeps 80.000 VND; VIP price after the cards arrive | PASSED |
| TC-FE03-049 | AC1 | `?sport=999&format=abc&duration=7` (round 1, M3) | Ignored: 5 cards, empty selects, links without the bad query | PASSED |
| TC-FE03-052 | - | Registrations list never answers (round 2, B3b) | Confirm disabled, "Checking your registrations", click and form submit send no POST; after an empty list arrives Confirm is enabled and one POST is sent | PASSED |
| TC-FE03-050 | AC3 | Payment cannot be loaded | Error with the backend message | PASSED |
| TC-FE03-051 | - | Invalid payment id | "The payment link is not valid.", no request | PASSED |

---

## 3. Manual test cases against the real backend (run before merge)
Preconditions: backend with Flyway V1-V21 seed (1, 30 and 90 day packages), frontend `npm run dev`.

| ID | Steps | Expected | Result |
|---|---|---|---|
| M01 | Member without card opens Sport Packages | Original prices | NOT RUN |
| M02 | Activate a GOLD card for the member at the desk, reload | 30 and 90 day prices -5%, single visit unchanged | NOT RUN |
| M03 | Same with VIP | -10% | NOT RUN |
| M04 | Filter Football, Coach-led, 90 days | One package | NOT RUN |
| M05 | Detail: try to pick yesterday | Picker blocks it; typed value rejected | NOT RUN |
| M06 | Confirm registration (VIP, coach-led 30 days) | Overlay amount equals the preview (810.000 VND); Continue to payment shows the new PENDING payment with bank details | NOT RUN |
| M07 | Register again while the first is pending | Backend answer shown above the summary | NOT RUN |
| M08 | Front desk activates the registration | Payment summary shows Success after reload | NOT RUN |
