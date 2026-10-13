# Test Cases - Task FE-04 (Flow 1): Membership Cards (F1-11) and My Membership and Sport Packages (F1-12)

## 1. Overview
- Task ID: FE-04
- Flow: Flow 1 - User and membership management (Member)
- Branch: feat/f1-member-cards (stacked on feat/f1-member-packages, FE-03, and feat/f1-member-dashboard, FE-02, both not merged yet)
- Priority: P1
- Depends on: FE-00, BE-17 (both on main)
- Figma: `context/Flow 1 - User and membership management/Flow 1 - Member/F1-11 - Membership Cards.png`, `F1-11 Overlay - Renew Membership Card.png`, `F1-12 - My Membership & Sport Packages.png`, `F1-12 State - Active.png`, `F1-12 State - Expired.png`, `F1-12 State - Pending Payment.png`
- Acceptance criteria:
  - AC1: renew, upgrade and cheaper-tier cases show the right dates and price.
  - AC2: a second request while one is pending shows the backend error.
  - AC3: cancel works only for pending requests.
  - AC4: the three F1-12 states render correctly.

### 1.1 Backend contract used (checked in the code on main)
| Call | Use |
|---|---|
| `GET /api/v1/membership-cards/tiers` (public, active tiers) | Tier list: price, durationMonths, discountPercentage, description |
| `GET /api/v1/membership-cards/my` | Member cards: current tier, plan base, pending request, card tab |
| `POST /api/v1/membership-cards/purchase` body `{tierId}` | Creates a PENDING_PAYMENT card and a PENDING payment; 400 when a request already waits for payment |
| `PUT /api/v1/membership-cards/{id}/cancel` | Cancels a PENDING_PAYMENT request; 400 for any other status |
| `GET /api/v1/packages/registrations/my` | Sport packages tab |
| `GET /api/v1/payments/{id}` | Payment summary after Confirm |

Card plan rule (same as `MembershipCardServiceImpl.planCard`), with paid cards = ACTIVE, end date today or later, tier with a duration:
- no paid card: start today, end = start + N months - 1 day, full price;
- same tier as the card that ends last: start = its end + 1 day, full price;
- dearer tier than the card in force today: start today, keep its end date, pay the difference (blocked while a renewal is queued);
- otherwise: start = end of the last card + 1 day, full price.
Months are added like `LocalDate.plusMonths` (31 Jan + 1 month = 28 Feb).

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm test
npm run lint
npm run build
```

### 1.3 Result summary (2026-10-13)
| Group | Cases | Result |
|---|---|---|
| Automated, rules (`src/pages/member/cardsModel.test.ts`) | 19 | 19 PASSED |
| Automated, screens (`src/pages/member/membershipCards.test.tsx`) | 21 | 21 PASSED |
| Automated, all other files (FE-00 to FE-03) | 298 (1 FE-02 assertion updated for the card tab link) | 298 PASSED |
| Lint, `tsc -b`, build | 3 | 3 PASSED |
| Manual E2E against the real backend (section 4) | 10 | Not run (for the demo) |

Today is fixed at 2026-10-13 in the automated tests. Seed tiers: Standard (free), Gold 300.000 VND 5%, VIP 600.000 VND 10%, 12 months.

## 2. Automated - rules (cardsModel.test.ts)
| ID | Case | Expected |
|---|---|---|
| TC-FE04-001 | addMonths on short months | 2026-01-31 + 1 = 2026-02-28; 2024-01-31 + 1 = 2024-02-29 |
| TC-FE04-002 | Card end date | 2026-10-13 + 12 months -> 2027-10-12; 2027-07-05 -> 2028-07-04; 2024-02-29 -> 2025-02-27 |
| TC-FE04-003 | New card, no paid card | new, 13 Oct 2026 to 12 Oct 2027, 300.000 |
| TC-FE04-004 | Renew VIP (VIP until 2027-07-04) | renew, 2027-07-05 to 2028-07-04, 600.000 |
| TC-FE04-005 | Upgrade Gold -> VIP | upgrade, today to 2027-07-04, 300.000 (difference) |
| TC-FE04-006 | Cheaper tier VIP -> Gold | later, 2027-07-05 to 2028-07-04, 300.000 |
| TC-FE04-007 | Upgrade while a renewal is queued | blocked with the backend reason |
| TC-FE04-008 | VIP in force, Gold queued | VIP starts 2028-07-05 (after Gold); Gold is a renewal |
| TC-FE04-009 | Pending, cancelled, replaced, Standard, past-end cards | ignored, plan is new |
| TC-FE04-010 | Card ending today | still counts, renew from tomorrow |
| TC-FE04-011 | Current tier not on sale | unknown, price calculated on confirm |
| TC-FE04-012 | Decimal prices | difference exact (300000.20) |
| TC-FE04-013 | cardInForce with a queued card | queued card is not in force |
| TC-FE04-014 | Free tier, perks, validity, tier order | Standard free, perks text, "12 months validity", inactive tier hidden |
| TC-FE04-015 | canCancelCard | true only for PENDING_PAYMENT |
| TC-FE04-016 | pendingCard | newest pending request |
| TC-FE04-017 | displayStatus | ACTIVE past end date -> EXPIRED; ending today stays ACTIVE |
| TC-FE04-018 | Sort order | pending, active, expired, replaced, cancelled; newest first |
| TC-FE04-019 | termDays, tab param | 2 Oct to 31 Oct = 30 days; unknown tab -> packages |

## 3. Automated - screens (membershipCards.test.tsx)
| ID | AC | Case | Expected |
|---|---|---|---|
| TC-FE04-030 | - | Open `/member/membership/cards` without a card | 3 tiers with price, validity, perks; Standard "Current" with Explore sport packages; VIP "Get card" |
| TC-FE04-031 | AC1 | Gold, Get card | Overlay 300.000 VND, 13 Oct 2026 to 12 Oct 2027 |
| TC-FE04-032 | AC1 | VIP member, VIP Renew card | VIP marked Active; overlay 600.000 VND, 05 Jul 2027 to 04 Jul 2028, 10% |
| TC-FE04-033 | AC1 | Gold member, VIP Upgrade card | Card fee 600.000, credit -300.000, pay 300.000, 13 Oct 2026 to 04 Jul 2027 |
| TC-FE04-034 | AC1 | VIP member, Gold Buy after current card | 300.000 VND, 05 Jul 2027 to 04 Jul 2028 |
| TC-FE04-035 | - | Confirm | One POST `{tierId: 3}`, lands on Payment Summary with 600.000 VND |
| TC-FE04-036 | AC2 | Pending request exists, buy another | Pending notice shown; POST sent; backend 400 message shown in the overlay; overlay stays open |
| TC-FE04-037 | - | Double click Confirm | Only one POST |
| TC-FE04-038 | AC1 | Gold in force, Gold renewal queued | Gold "Starts 05 Jul 2027"; VIP shows the reason, no button |
| TC-FE04-039 | - | Cancel in the overlay | Closes, no request |
| TC-FE04-040 | - | Tiers request fails (500) | Error state; Try again loads the tiers |
| TC-FE04-050 | AC4 | No registrations | "No sport packages yet" with Explore packages |
| TC-FE04-051 | AC4 | Pending Payment registration | Badge, "30 days - 8 sessions", "02 Oct 2026 to 31 Oct 2026", 810.000 VND, 8 of 8, Pay now -> /member/payments/52 |
| TC-FE04-052 | AC4 | Active registration | Badge, 6 of 8 progress, Book a session, Renew package |
| TC-FE04-053 | AC4 | EXPIRED, and ACTIVE past its end date | Both "Expired", Renew package, View payment, no Book a session |
| TC-FE04-054 | - | `?tab=card` with a VIP card | Card tab selected; Active, validity; no Cancel request; View card tiers link |
| TC-FE04-055 | - | Switch tabs by click and arrow key | Panel and focus follow |
| TC-FE04-056 | AC3 | Cancel a pending request | Confirm dialog; PUT `/membership-cards/9/cancel`; list reloads, row Cancelled, no Cancel button |
| TC-FE04-057 | AC3 | Active, cancelled, replaced, expired cards | No Cancel request button; expired card offers Renew card |
| TC-FE04-058 | AC3 | Backend rejects the cancel (400) | Message shown in the dialog |
| TC-FE04-059 | - | Menu | My Membership is the active item on the cards page |

## 4. Manual E2E against the real backend (for the demo)
Precondition: backend running with the seed data, member account signed in, browser at `/member/membership/cards`.

| ID | AC | Steps | Expected |
|---|---|---|---|
| TC-FE04-M01 | - | New member opens Membership Cards | Standard is Current; Gold and VIP show Get card |
| TC-FE04-M02 | AC1 | Gold -> Get card | Overlay: 300.000 VND, today to today + 12 months - 1 day |
| TC-FE04-M03 | - | Confirm | Payment Summary of the new payment (PENDING, 300.000 VND) |
| TC-FE04-M04 | AC2 | Back to Membership Cards, VIP -> Get card -> Confirm | Pending notice at the top; overlay shows "A membership card request is already waiting for payment: CARD-.... Pay or cancel it first." |
| TC-FE04-M05 | AC3 | My Membership -> Membership card tab -> Cancel request -> Cancel request | Row becomes Cancelled; the payment becomes FAILED in the backend |
| TC-FE04-M06 | - | Request Gold again; receptionist activates it (`PUT /membership-cards/{id}/activate`) | Gold is Active on F1-11 and F1-12 |
| TC-FE04-M07 | AC1 | Gold -> Renew card | Starts the day after the Gold end date, 300.000 VND |
| TC-FE04-M08 | AC1 | VIP -> Upgrade card | Pay 300.000 VND, start today, end = Gold end date |
| TC-FE04-M09 | AC3 | Call `PUT /membership-cards/{active id}/cancel` with the member token (Postman) | 400 "Only a card request that is waiting for payment can be cancelled." (no button in the UI) |
| TC-FE04-M10 | AC4 | Register a package, then open My Membership | Pending Payment state with Pay now; after activation Active state |
